package app.quartz.core.camera.video

import android.location.Location
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import app.quartz.core.camera.VideoSpec
import app.quartz.core.caps.options.Codec
import app.quartz.core.settings.DynamicRange
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.io.FileDescriptor

/**
 * One recording: a surface-input video encoder (MediaCodec), optional AAC
 * audio, and an [Mp4Sink]. Built and configured before recording starts so
 * the camera session can already include its input surface.
 *
 * @param persistentSurface a MediaCodec persistent input surface that stays in
 *   the camera session across recordings; null to let the codec create its
 *   own (time-lapse, fed by the GL renderer).
 */
class Recorder(
    private val spec: VideoSpec,
    private val timing: Timing,
    private val cameraClockRealtime: Boolean,
    private val unprocessedAudioSupported: Boolean,
    persistentSurface: Surface?,
) {
    private val thread = HandlerThread("QuartzEncoder").apply { start() }
    private val handler = Handler(thread.looper)
    private val codec: MediaCodec
    val inputSurface: Surface
    private var audio: AudioEncoder? = null
    @Volatile private var sink: Mp4Sink? = null
    private val videoEos = CompletableDeferred<Unit>()
    var hasAudio = false
        private set

    init {
        val format = MediaFormat.createVideoFormat(spec.codec.mime, spec.size.w, spec.size.h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, spec.bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, spec.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            if (timing is Timing.Real && spec.fps > 60) setFloat(MediaFormat.KEY_CAPTURE_RATE, spec.fps.toFloat())
            if (spec.dynamicRange != DynamicRange.SDR) {
                val profile = when (spec.codec) {
                    Codec.AV1 -> if (spec.dynamicRange == DynamicRange.HDR10) {
                        MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10
                    } else {
                        MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10
                    }
                    else -> if (spec.dynamicRange == DynamicRange.HDR10) {
                        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
                    } else {
                        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
                    }
                }
                setInteger(MediaFormat.KEY_PROFILE, profile)
                setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT2020)
                setInteger(
                    MediaFormat.KEY_COLOR_TRANSFER,
                    if (spec.dynamicRange == DynamicRange.HDR10) MediaFormat.COLOR_TRANSFER_ST2084 else MediaFormat.COLOR_TRANSFER_HLG,
                )
                setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
            }
        }
        // Prefer the encoder the system picks for this exact format (hardware first).
        val name = runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format) }.getOrNull()
        codec = if (name != null) MediaCodec.createByCodecName(name) else MediaCodec.createEncoderByType(spec.codec.mime)
        codec.setCallback(Callback(), handler)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = if (persistentSurface != null) {
            codec.setInputSurface(persistentSurface)
            persistentSurface
        } else {
            codec.createInputSurface()
        }

    }

    fun start(fd: FileDescriptor, orientationHint: Int, location: Location?) {
        // The microphone opens only when recording starts, never while previewing.
        spec.audio?.let { a ->
            if (timing is Timing.Real) {
                val enc = AudioEncoder(a, unprocessedAudioSupported)
                runCatching { enc.prepare() }
                    .onSuccess { audio = enc; hasAudio = true }
                    .onFailure { Log.w(TAG, "no audio", it); enc.release() }
            }
        }
        val offsetUs = if (cameraClockRealtime) (SystemClock.elapsedRealtimeNanos() - System.nanoTime()) / 1000 else 0L
        val tracks = if (hasAudio) setOf(Track.VIDEO, Track.AUDIO) else setOf(Track.VIDEO)
        val s = Mp4Sink(fd, tracks, timing, orientationHint, location, offsetUs)
        s.setFrameRate(spec.fps)
        sink = s
        audio?.sink = s
        codec.start()
        audio?.start()
    }

    fun pause() {
        sink?.pause()
    }

    fun resume() {
        sink?.resume()
        runCatching { codec.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) }) }
    }

    /** Ends the streams and finalises the file. True if the file has content. */
    suspend fun stop(): Boolean {
        runCatching { codec.signalEndOfInputStream() }
        audio?.stop()
        withTimeoutOrNull(3000) { videoEos.await() }
        runCatching { codec.stop() }
        return sink?.close() ?: false
    }

    /** Releases the codec. The persistent surface is not released here. */
    fun release() {
        audio?.release()
        runCatching { codec.release() }
        thread.quitSafely()
    }

    private inner class Callback : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) = Unit

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            try {
                val buf = codec.getOutputBuffer(index)
                if (buf != null) sink?.write(Track.VIDEO, buf, info)
                codec.releaseOutputBuffer(index, false)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "video output", e)
            }
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) videoEos.complete(Unit)
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            Log.e(TAG, "encoder error", e)
            videoEos.complete(Unit)
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            sink?.addTrack(Track.VIDEO, format)
        }
    }

    companion object {
        private const val TAG = "QuartzRecorder"
    }
}
