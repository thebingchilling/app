package app.quartz.core.camera.video

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import app.quartz.core.caps.Size
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch

/**
 * Time-lapse (plan §10.3): the camera streams into a SurfaceTexture; every
 * [intervalMs] one frame is copied unchanged into the encoder's input surface
 * with a timestamp of n / playbackFps. The shader is a straight texture copy:
 * no colour maths, no blending between frames.
 */
class TimelapseRenderer(size: Size, private val intervalMs: Int, private val playbackFps: Int) {
    private val thread = HandlerThread("QuartzTimelapse").apply { start() }
    private val handler = Handler(thread.looper)

    private lateinit var display: EGLDisplay
    private lateinit var context: EGLContext
    private lateinit var config: EGLConfig
    private lateinit var pbuffer: EGLSurface
    private var encoderSurface: EGLSurface? = null
    private var program = 0
    private var texture = 0
    private lateinit var surfaceTexture: SurfaceTexture
    lateinit var cameraSurface: Surface
        private set

    private val matrix = FloatArray(16)
    private var lastPickedNs = -1L
    private var frameIndex = 0L
    @Volatile var framesWritten = 0L
        private set

    init {
        runSync {
            initEgl()
            surfaceTexture = SurfaceTexture(texture).apply {
                setDefaultBufferSize(size.w, size.h)
                setOnFrameAvailableListener({ onFrame() }, handler)
            }
            cameraSurface = Surface(surfaceTexture)
        }
    }

    fun start(encoderInput: Surface) = runSync {
        encoderSurface = EGL14.eglCreateWindowSurface(display, config, encoderInput, intArrayOf(EGL14.EGL_NONE), 0)
        lastPickedNs = -1
        frameIndex = 0
        framesWritten = 0
    }

    fun stop() = runSync {
        encoderSurface?.let {
            EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)
            EGL14.eglDestroySurface(display, it)
        }
        encoderSurface = null
    }

    fun release() {
        runSync {
            stopInternal()
            runCatching { cameraSurface.release() }
            runCatching { surfaceTexture.release() }
            GLES20.glDeleteProgram(program)
            GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, pbuffer)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
        thread.quitSafely()
    }

    private fun stopInternal() {
        encoderSurface?.let { EGL14.eglDestroySurface(display, it) }
        encoderSurface = null
    }

    private fun onFrame() {
        val enc = encoderSurface
        EGL14.eglMakeCurrent(display, enc ?: pbuffer, enc ?: pbuffer, context)
        surfaceTexture.updateTexImage()
        if (enc == null) return
        val ts = surfaceTexture.timestamp
        if (lastPickedNs >= 0 && ts - lastPickedNs < intervalMs * 1_000_000L) return
        lastPickedNs = ts
        surfaceTexture.getTransformMatrix(matrix)
        draw()
        EGLExt.eglPresentationTimeANDROID(display, enc, frameIndex * 1_000_000_000L / playbackFps)
        EGL14.eglSwapBuffers(display, enc)
        frameIndex++
        framesWritten++
    }

    private fun draw() {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        val pos = GLES20.glGetAttribLocation(program, "aPos")
        val tex = GLES20.glGetAttribLocation(program, "aTex")
        GLES20.glEnableVertexAttribArray(pos)
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 0, QUAD)
        GLES20.glEnableVertexAttribArray(tex)
        GLES20.glVertexAttribPointer(tex, 2, GLES20.GL_FLOAT, false, 0, TEX)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uTex"), 1, false, matrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun initEgl() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize" }
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val n = IntArray(1)
        check(EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, n, 0) && n[0] > 0) { "eglChooseConfig" }
        config = configs[0]!!
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        pbuffer = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)

        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        texture = tex[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        program = link(VERTEX, FRAGMENT)
    }

    private fun link(vs: String, fs: String): Int {
        fun compile(type: Int, src: String) = GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, src)
            GLES20.glCompileShader(it)
        }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(p)
        return p
    }

    private fun runSync(block: () -> Unit) {
        if (Thread.currentThread() == thread) return block()
        val latch = CountDownLatch(1)
        var error: Throwable? = null
        handler.post {
            try {
                block()
            } catch (t: Throwable) {
                error = t
            } finally {
                latch.countDown()
            }
        }
        latch.await()
        error?.let { throw it }
    }

    companion object {
        private const val EGL_RECORDABLE_ANDROID = 0x3142

        private const val VERTEX = """
            attribute vec4 aPos;
            attribute vec4 aTex;
            uniform mat4 uTex;
            varying vec2 vTex;
            void main() {
                gl_Position = aPos;
                vTex = (uTex * aTex).xy;
            }
        """

        // A straight copy of the camera frame.
        private const val FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTex;
            uniform samplerExternalOES sTex;
            void main() {
                gl_FragColor = texture2D(sTex, vTex);
            }
        """

        private fun floats(vararg v: Float): FloatBuffer =
            ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(v); position(0) }

        private val QUAD = floats(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        private val TEX = floats(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
    }
}
