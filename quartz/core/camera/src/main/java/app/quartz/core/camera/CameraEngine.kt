package app.quartz.core.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.YuvImage
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.RggbChannelVector
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.view.Surface
import androidx.exifinterface.media.ExifInterface
import app.quartz.core.caps.C2
import app.quartz.core.caps.FpsRange
import app.quartz.core.caps.Outputs
import app.quartz.core.caps.Size
import app.quartz.core.caps.lens.MeteringMath
import app.quartz.core.caps.natural.NaturalPolicy
import app.quartz.core.caps.natural.NaturalRequest
import app.quartz.core.caps.options.VideoOptions
import app.quartz.core.camera.video.Recorder
import app.quartz.core.camera.video.TimelapseRenderer
import app.quartz.core.camera.video.Timing
import app.quartz.core.media.CaptureMeta
import app.quartz.core.media.ExifWriter
import app.quartz.core.media.MediaWriter
import app.quartz.core.settings.DynamicRange
import app.quartz.core.settings.FlashMode
import app.quartz.core.settings.PhotoFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

data class RecordingState(
    val active: Boolean = false,
    val paused: Boolean = false,
    val startedAtMs: Long = 0,
    val pausedAtMs: Long = 0,
    val pausedTotalMs: Long = 0,
)

/**
 * The Camera2 engine (plan §9, §10, §12.2). All camera calls run on one
 * camera thread; public operations are serialised by a mutex so a fast
 * double tap can't race a session change.
 *
 * Every request goes through [NaturalPolicy] via [RequestApplier.natural].
 */
class CameraEngine(private val context: Context, private val media: MediaWriter) {

    private val manager = context.getSystemService(CameraManager::class.java)
    private val cameraThread = HandlerThread("QuartzCamera").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)
    private val readerThread = HandlerThread("QuartzReader").apply { start() }
    private val readerHandler = Handler(readerThread.looper)
    private val analysisThread = HandlerThread("QuartzAnalysis").apply { start() }
    private val analysisHandler = Handler(analysisThread.looper)
    private val cameraDispatcher = cameraHandler.asCoroutineDispatcher("QuartzCamera")
    private val executor = Executor { cameraHandler.post(it) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()

    val sounds = Sounds()
    val compliance = ComplianceMonitor()
    private val analyzer = Analyzer()

    private val _state = MutableStateFlow<EngineState>(EngineState.Closed)
    val state: StateFlow<EngineState> = _state.asStateFlow()
    private val _frame = MutableStateFlow(FrameInfo())
    val frame: StateFlow<FrameInfo> = _frame.asStateFlow()
    private val _analysis = MutableStateFlow<AnalysisFrame?>(null)
    val analysis: StateFlow<AnalysisFrame?> = _analysis.asStateFlow()
    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<EngineEvent> = _events.asSharedFlow()
    private val _recording = MutableStateFlow(RecordingState())
    val recording: StateFlow<RecordingState> = _recording.asStateFlow()

    /** Set from DeviceCaps: whether the UNPROCESSED microphone source works. */
    @Volatile var unprocessedAudioSupported = false

    // ---- Camera-thread state ----
    private var device: CameraDevice? = null
    private var chars: CameraCharacteristics? = null
    private var session: CameraCaptureSession? = null
    private var spec: SessionSpec? = null
    private var preview: Surface? = null
    private var stillReader: ImageReader? = null
    private var rawReader: ImageReader? = null
    private var snapshotReader: ImageReader? = null
    private var analysisReader: ImageReader? = null
    private var persistentSurface: Surface? = null
    private var nextRecorder: Recorder? = null
    private var recorder: Recorder? = null
    private var pendingVideo: MediaWriter.PendingVideo? = null
    private var timelapse: TimelapseRenderer? = null
    private var natural: NaturalRequest? = null
    @Volatile private var controls = Controls()
    private var recordingActive = false
    private var lastGains: RggbChannelVector? = null
    private var lastTransform: ColorSpaceTransform? = null
    private var lastAeExposure: Pair<Long, Int>? = null
    private var priorityMetering: Pair<Long, Int>? = null
    private var frameCount = 0L
    @Volatile private var controlsPending = false

    private val stillImages = Channel<Image>(Channel.UNLIMITED)
    private val rawImages = Channel<Image>(Channel.UNLIMITED)
    private val snapshotImages = Channel<Image>(Channel.UNLIMITED)
    private val results = MutableSharedFlow<TotalCaptureResult>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val currentSpec: SessionSpec? get() = spec

    // ------------------------------------------------------------------
    // Session lifecycle
    // ------------------------------------------------------------------

    /** Opens the camera if needed and configures a session for [newSpec]. */
    suspend fun configure(newSpec: SessionSpec, surface: Surface) = lock.withLock {
        withContext(cameraDispatcher) {
            try {
                _state.value = EngineState.Opening
                closeSessionInternal()
                if (device?.id != newSpec.cameraId) {
                    closeDeviceInternal()
                    device = openDevice(newSpec.cameraId)
                    chars = manager.getCameraCharacteristics(newSpec.cameraId)
                }
                spec = newSpec
                preview = surface
                // Zoom and metering from another camera don't apply to this one.
                if (controls.zoom?.cameraId != newSpec.cameraId || controls.zoom?.physicalId != newSpec.physicalId) {
                    controls = controls.copy(zoom = null, meteringRegion = null)
                }
                natural = NaturalPolicy.resolve(newSpec.lensInfo, newSpec.natural, Build.VERSION.SDK_INT, video = newSpec.kind != SessionKind.PHOTO)
                analyzer.histogram = false
                session = createSessionWithFallback(newSpec)
                compliance.reset()
                frameCount = 0
                startRepeating()
                _state.value = EngineState.Ready(newSpec)
            } catch (e: Exception) {
                Log.e(TAG, "configure failed", e)
                closeSessionInternal()
                _state.value = EngineState.Error(describe(e), recoverable = true)
            }
        }
    }

    suspend fun close() = lock.withLock {
        withContext(cameraDispatcher) {
            closeSessionInternal()
            closeDeviceInternal()
            _state.value = EngineState.Closed
        }
    }

    fun setAnalysis(histogram: Boolean, peaking: Boolean, zebra: Boolean) {
        analyzer.histogram = histogram
        analyzer.peaking = peaking
        analyzer.zebra = zebra
        if (!(histogram || peaking || zebra)) _analysis.value = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun openDevice(id: String): CameraDevice = suspendCancellableCoroutine { cont ->
        manager.openCamera(id, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                if (cont.isActive) cont.resume(camera) else camera.close()
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                if (cont.isActive) {
                    cont.resumeWithException(CameraAccessException(CameraAccessException.CAMERA_DISCONNECTED))
                } else {
                    lost(camera, "Another app is using the camera", recoverable = true)
                }
            }

            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                val msg = when (error) {
                    CameraDevice.StateCallback.ERROR_CAMERA_IN_USE, CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE -> "The camera is in use by another app"
                    CameraDevice.StateCallback.ERROR_CAMERA_DISABLED -> "The camera is disabled (device policy or privacy toggle)"
                    else -> "Camera error $error"
                }
                if (cont.isActive) cont.resumeWithException(IllegalStateException(msg)) else lost(camera, msg, recoverable = true)
            }
        }, cameraHandler)
    }

    private fun lost(camera: CameraDevice, message: String, recoverable: Boolean) {
        scope.launch(cameraDispatcher) {
            if (device === camera) {
                closeSessionInternal()
                device = null
                _state.value = EngineState.Error(message, recoverable)
            }
        }
    }

    private suspend fun createSessionWithFallback(s: SessionSpec): CameraCaptureSession {
        val attempts = listOf(
            StreamOptions(analysis = s.analysis, snapshot = s.snapshot),
            StreamOptions(analysis = false, snapshot = s.snapshot),
            StreamOptions(analysis = false, snapshot = false),
        ).distinct()
        var last: Exception? = null
        for (opt in attempts) {
            try {
                val outputs = createOutputs(s, opt)
                return createSession(outputs, s.kind == SessionKind.HIGH_SPEED)
            } catch (e: Exception) {
                Log.w(TAG, "session $opt failed", e)
                last = e
                releaseOutputs()
            }
        }
        throw last ?: IllegalStateException("Could not configure the camera")
    }

    private data class StreamOptions(val analysis: Boolean, val snapshot: Boolean)

    private fun createOutputs(s: SessionSpec, opt: StreamOptions): List<OutputConfiguration> {
        val sdk = Build.VERSION.SDK_INT
        val outs = mutableListOf<OutputConfiguration>()
        val dr = s.video?.dynamicRange?.takeIf { sdk >= 33 && it != DynamicRange.SDR }
        fun config(surface: Surface, maxRes: Boolean = false): OutputConfiguration =
            OutputConfiguration(surface).apply {
                if (s.physicalId != null && sdk >= 28) setPhysicalCameraId(s.physicalId)
                if (dr != null && sdk >= 33) {
                    setDynamicRangeProfile(if (dr == DynamicRange.HDR10) C2.DR_HDR10 else C2.DR_HLG10)
                }
                if (sdk >= 31 && s.still?.fullSensor == true) {
                    addSensorPixelModeUsed(
                        if (maxRes) CameraMetadata.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION else CameraMetadata.SENSOR_PIXEL_MODE_DEFAULT,
                    )
                }
            }

        outs += config(preview!!)
        when (s.kind) {
            SessionKind.PHOTO -> {
                val still = s.still ?: error("Photo session without still settings")
                val wantJpeg = still.format != PhotoFormat.RAW
                if (wantJpeg) {
                    val (format, size) = when {
                        still.format == PhotoFormat.HEIC && sdk >= 29 -> ImageFormat.HEIC to still.size
                        still.nativeCrop -> ImageFormat.YUV_420_888 to (yuvSizeFor(s, still.size) ?: still.size)
                        else -> ImageFormat.JPEG to still.size
                    }
                    stillReader = reader(size, format, 4, stillImages)
                    outs += config(stillReader!!.surface, maxRes = still.fullSensor)
                }
                // DngCreator needs the characteristics of the camera that made the frame, so no RAW from a physical stream.
                if (still.format.raw && s.lensInfo.raw && s.physicalId == null) {
                    val rawSize = s.info.sizes(Outputs.RAW).maxByOrNull { it.size.pixels }?.size
                    if (rawSize != null) {
                        rawReader = reader(rawSize, ImageFormat.RAW_SENSOR, 2, rawImages)
                        outs += config(rawReader!!.surface)
                    }
                }
            }
            SessionKind.VIDEO, SessionKind.HIGH_SPEED -> {
                val v = s.video ?: error("Video session without video settings")
                val surface = MediaCodec.createPersistentInputSurface()
                persistentSurface = surface
                nextRecorder = makeRecorder(s, v, surface)
                outs += config(surface)
                if (opt.snapshot && s.kind == SessionKind.VIDEO && dr == null) {
                    val snap = s.info.sizes(Outputs.JPEG)
                        .filter { it.size.sameAspect(v.size) && it.size.pixels <= v.size.pixels }
                        .maxByOrNull { it.size.pixels }?.size
                    if (snap != null) {
                        snapshotReader = reader(snap, ImageFormat.JPEG, 2, snapshotImages)
                        outs += config(snapshotReader!!.surface)
                    }
                }
            }
            SessionKind.TIMELAPSE -> {
                val v = s.video ?: error("Time-lapse without video settings")
                val t = s.timelapse ?: error("Time-lapse without interval")
                val r = TimelapseRenderer(v.size, t.intervalMs, t.playbackFps)
                timelapse = r
                outs += config(r.cameraSurface)
            }
        }
        if (opt.analysis && s.kind != SessionKind.HIGH_SPEED) {
            val size = s.info.sizes(Outputs.YUV)
                .filter { it.size.sameAspect(s.previewSize) && it.w <= 800 }
                .maxByOrNull { it.size.pixels }?.size
                ?: s.info.sizes(Outputs.YUV).filter { it.w <= 800 }.maxByOrNull { it.size.pixels }?.size
            if (size != null) {
                val r = ImageReader.newInstance(size.w, size.h, ImageFormat.YUV_420_888, 3)
                r.setOnImageAvailableListener({ onAnalysisImage(it) }, analysisHandler)
                analysisReader = r
                outs += config(r.surface)
            }
        }
        return outs
    }

    private fun yuvSizeFor(s: SessionSpec, size: Size): Size? {
        val yuv = s.info.sizes(Outputs.YUV)
        return yuv.firstOrNull { it.size == size }?.size
            ?: yuv.filter { it.size.sameAspect(size) }.maxByOrNull { it.size.pixels }?.size
    }

    private fun reader(size: Size, format: Int, max: Int, sink: Channel<Image>): ImageReader =
        ImageReader.newInstance(size.w, size.h, format, max).apply {
            setOnImageAvailableListener({ r ->
                val img = runCatching { r.acquireNextImage() }.getOrNull() ?: return@setOnImageAvailableListener
                if (sink.trySend(img).isFailure) img.close()
            }, readerHandler)
        }

    private fun makeRecorder(s: SessionSpec, v: VideoSpec, surface: Surface?): Recorder {
        val timing = when {
            s.kind == SessionKind.TIMELAPSE -> Timing.AsIs
            s.kind == SessionKind.HIGH_SPEED && v.conform -> Timing.Fixed(30)
            else -> Timing.Real
        }
        val audio = if (s.kind == SessionKind.VIDEO) v.audio else null
        return Recorder(
            spec = v.copy(audio = audio),
            timing = timing,
            cameraClockRealtime = s.info.timestampSource == C2.TIMESTAMP_REALTIME,
            unprocessedAudioSupported = unprocessedAudioSupported,
            persistentSurface = surface,
        )
    }

    private suspend fun createSession(outputs: List<OutputConfiguration>, highSpeed: Boolean): CameraCaptureSession =
        suspendCancellableCoroutine { cont ->
            val d = device ?: return@suspendCancellableCoroutine cont.resumeWithException(IllegalStateException("Camera closed"))
            val cb = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    if (cont.isActive) cont.resume(s) else s.close()
                }

                override fun onConfigureFailed(s: CameraCaptureSession) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException("This combination of settings isn't supported by the camera"))
                }
            }
            if (Build.VERSION.SDK_INT >= 28) {
                val type = if (highSpeed) SessionConfiguration.SESSION_HIGH_SPEED else SessionConfiguration.SESSION_REGULAR
                d.createCaptureSession(SessionConfiguration(type, outputs, executor, cb))
            } else if (highSpeed) {
                @Suppress("DEPRECATION")
                d.createConstrainedHighSpeedCaptureSession(outputs.map { it.surface!! }, cb, cameraHandler)
            } else {
                @Suppress("DEPRECATION")
                d.createCaptureSessionByOutputConfigurations(outputs, cb, cameraHandler)
            }
        }

    private suspend fun closeSessionInternal() {
        if (recorder != null) runCatching { stopRecordingInternal() }
        runCatching { session?.stopRepeating() }
        runCatching { session?.close() }
        session = null
        releaseOutputs()
    }

    private fun releaseOutputs() {
        nextRecorder?.release()
        nextRecorder = null
        listOf(stillReader, rawReader, snapshotReader, analysisReader).forEach { r -> runCatching { r?.close() } }
        stillReader = null
        rawReader = null
        snapshotReader = null
        analysisReader = null
        drain(stillImages)
        drain(rawImages)
        drain(snapshotImages)
        runCatching { persistentSurface?.release() }
        persistentSurface = null
        timelapse?.release()
        timelapse = null
    }

    private fun closeDeviceInternal() {
        runCatching { device?.close() }
        device = null
        chars = null
    }

    private fun drain(ch: Channel<Image>) {
        while (true) {
            val img = ch.tryReceive().getOrNull() ?: break
            runCatching { img.close() }
        }
    }

    // ------------------------------------------------------------------
    // Repeating request and controls
    // ------------------------------------------------------------------

    /** Updates per-frame controls (zoom, exposure, focus, white balance…). Coalesced. */
    fun setControls(c: Controls) {
        val old = controls
        val wasPriority = (old.iso != null) != (old.exposureNs != null)
        val isPriority = (c.iso != null) != (c.exposureNs != null)
        if (isPriority && (!wasPriority || old.iso != c.iso && old.exposureNs != c.exposureNs)) {
            priorityMetering = lastAeExposure
        }
        controls = c
        if (controlsPending) return
        controlsPending = true
        cameraHandler.post {
            controlsPending = false
            runCatching { startRepeating() }.onFailure { Log.w(TAG, "repeating", it) }
        }
    }

    val currentControls: Controls get() = controls

    private fun buildRepeating(withEncoder: Boolean): CaptureRequest.Builder {
        val s = spec ?: error("No session")
        val d = device ?: error("No camera")
        val template = if (s.kind == SessionKind.PHOTO) CameraDevice.TEMPLATE_PREVIEW else CameraDevice.TEMPLATE_RECORD
        val b = d.createCaptureRequest(template)
        b.addTarget(preview!!)
        if (s.kind != SessionKind.HIGH_SPEED) analysisReader?.let { b.addTarget(it.surface) }
        if (withEncoder) persistentSurface?.let { b.addTarget(it) }
        timelapse?.let { b.addTarget(it.cameraSurface) }
        natural?.let { RequestApplier.natural(b, it) }
        val c = controls
        val fps = c.fpsRange ?: s.previewFps
        RequestApplier.controls(b, s.info, c.copy(fpsRange = fps), video = s.kind != SessionKind.PHOTO, priorityMetering = priorityMetering)
        RequestApplier.whiteBalance(b, s.info, c, lastGains, lastTransform)
        return b
    }

    private fun startRepeating() {
        val sess = session ?: return
        val s = spec ?: return
        val b = buildRepeating(withEncoder = recordingActive && s.kind != SessionKind.TIMELAPSE)
        if (sess is CameraConstrainedHighSpeedCaptureSession) {
            val v = s.video!!
            val range = if (recordingActive) FpsRange(v.fps, v.fps) else VideoOptions.highSpeedPreviewRange(s.info, v.size, v.fps)
            b.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(range.lower, range.upper))
            sess.setRepeatingBurst(sess.createHighSpeedRequestList(b.build()), repeatingCallback, cameraHandler)
        } else {
            sess.setRepeatingRequest(b.build(), repeatingCallback, cameraHandler)
        }
    }

    private val repeatingCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            onResult(result)
        }
    }

    private fun onResult(result: TotalCaptureResult) {
        results.tryEmit(result)
        compliance.onResult(result)
        val aeMode = result.get(CaptureResult.CONTROL_AE_MODE)
        val exp = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
        val iso = result.get(CaptureResult.SENSOR_SENSITIVITY)
        if (aeMode != null && aeMode != C2.AE_OFF && exp != null && iso != null) lastAeExposure = exp to iso
        if (result.get(CaptureResult.CONTROL_AWB_MODE) == C2.AWB_AUTO) {
            result.get(CaptureResult.COLOR_CORRECTION_GAINS)?.let { lastGains = it }
            result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM)?.let { lastTransform = it }
        }
        if (frameCount++ % 4 != 0L) return
        val sdk = Build.VERSION.SDK_INT
        _frame.value = FrameInfo(
            iso = iso,
            exposureNs = exp,
            focusDiopters = result.get(CaptureResult.LENS_FOCUS_DISTANCE),
            afState = result.get(CaptureResult.CONTROL_AF_STATE),
            aeState = result.get(CaptureResult.CONTROL_AE_STATE),
            zoomRatio = if (sdk >= 30) result.get(CaptureResult.CONTROL_ZOOM_RATIO) else null,
            activePhysicalId = if (sdk >= 29) result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID) else null,
            focalMm = result.get(CaptureResult.LENS_FOCAL_LENGTH),
            aperture = result.get(CaptureResult.LENS_APERTURE),
            frameDurationNs = result.get(CaptureResult.SENSOR_FRAME_DURATION),
            awbGains = result.get(CaptureResult.COLOR_CORRECTION_GAINS)?.let { floatArrayOf(it.red, it.greenEven, it.greenOdd, it.blue) },
            colorTemperature = if (sdk >= 36) result.get(CaptureResult.COLOR_CORRECTION_COLOR_TEMPERATURE) else null,
        )
    }

    private fun onAnalysisImage(reader: ImageReader) {
        val img = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return
        try {
            val s = spec ?: return
            val front = s.info.facing == C2.FACING_FRONT
            analyzer.analyze(img, s.info.sensorOrientation, front)?.let { _analysis.value = it }
        } catch (e: Exception) {
            Log.w(TAG, "analysis", e)
        } finally {
            img.close()
        }
    }

    // ------------------------------------------------------------------
    // Focus
    // ------------------------------------------------------------------

    /** Tap to focus/meter at [region] (sensor coordinates): locks AF there. */
    suspend fun focusAt(region: app.quartz.core.caps.Rect) = withContext(cameraDispatcher) {
        setControls(controls.copy(meteringRegion = region))
        val s = spec ?: return@withContext
        val sess = session ?: return@withContext
        if (sess is CameraConstrainedHighSpeedCaptureSession) return@withContext
        if (!s.info.hasAutoFocus || controls.focusDiopters != null) return@withContext
        runCatching {
            val b = buildRepeating(withEncoder = recordingActive && s.kind != SessionKind.TIMELAPSE)
            b.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
            sess.capture(b.build(), null, cameraHandler)
            b.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
            sess.capture(b.build(), null, cameraHandler)
        }
    }

    /** Back to continuous focus and centre-weighted metering. */
    suspend fun resetFocus() = withContext(cameraDispatcher) {
        setControls(controls.copy(meteringRegion = null))
        val sess = session ?: return@withContext
        val s = spec ?: return@withContext
        if (sess is CameraConstrainedHighSpeedCaptureSession || !s.info.hasAutoFocus) return@withContext
        runCatching {
            val b = buildRepeating(withEncoder = recordingActive && s.kind != SessionKind.TIMELAPSE)
            b.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
            sess.capture(b.build(), null, cameraHandler)
        }
    }

    private suspend fun lockFocus(sess: CameraCaptureSession): Boolean {
        val b = buildRepeating(withEncoder = false)
        b.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
        sess.capture(b.build(), null, cameraHandler)
        return withTimeoutOrNull(1200) {
            results.first {
                val st = it.get(CaptureResult.CONTROL_AF_STATE)
                st == CameraMetadata.CONTROL_AF_STATE_FOCUSED_LOCKED || st == CameraMetadata.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED
            }
        } != null
    }

    private fun unlockFocus(sess: CameraCaptureSession) {
        runCatching {
            val b = buildRepeating(withEncoder = false)
            b.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
            sess.capture(b.build(), null, cameraHandler)
        }
    }

    private suspend fun precapture(sess: CameraCaptureSession) {
        val b = buildRepeating(withEncoder = false)
        b.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START)
        sess.capture(b.build(), null, cameraHandler)
        withTimeoutOrNull(1500) {
            results.first { it.get(CaptureResult.CONTROL_AE_STATE) == CameraMetadata.CONTROL_AE_STATE_PRECAPTURE }
            results.first { it.get(CaptureResult.CONTROL_AE_STATE) != CameraMetadata.CONTROL_AE_STATE_PRECAPTURE }
        }
    }

    // ------------------------------------------------------------------
    // Stills
    // ------------------------------------------------------------------

    /**
     * Takes one photo (or a video snapshot while recording). Returns once the
     * frame is captured; saving continues in the background and the result
     * completes with the saved URIs.
     */
    suspend fun takePhoto(req: ShotRequest): Deferred<List<Uri>> = lock.withLock {
        withContext(cameraDispatcher) { capture(req) }
    }

    private suspend fun capture(req: ShotRequest): Deferred<List<Uri>> {
        val s = spec ?: error("Camera not ready")
        val sess = session ?: error("Camera not ready")
        val d = device ?: error("Camera not ready")
        if (sess is CameraConstrainedHighSpeedCaptureSession) error("Photos aren't available in slow motion")
        val snapshot = s.kind != SessionKind.PHOTO
        val still = s.still
        val sdk = Build.VERSION.SDK_INT
        if (snapshot && snapshotReader == null) error("This camera can't take photos while recording at these settings")
        if (!snapshot && still == null) error("Camera not ready")

        val c = controls
        val manual = req.longExposure != null || (c.iso != null && c.exposureNs != null)
        val doAf = !snapshot && !req.quick && s.info.hasAutoFocus && c.focusDiopters == null && c.meteringRegion == null
        val doAe = !snapshot && !req.quick && !manual && req.flash != FlashMode.OFF && s.info.flash

        if (doAf) lockFocus(sess)
        if (doAe) precapture(sess)

        val template = if (snapshot) CameraDevice.TEMPLATE_VIDEO_SNAPSHOT else CameraDevice.TEMPLATE_STILL_CAPTURE
        val b = d.createCaptureRequest(template)
        if (snapshot) {
            b.addTarget(preview!!)
            if (recordingActive && s.kind != SessionKind.TIMELAPSE) persistentSurface?.let { b.addTarget(it) }
            timelapse?.let { b.addTarget(it.cameraSurface) }
            b.addTarget(snapshotReader!!.surface)
        } else {
            stillReader?.let { b.addTarget(it.surface) }
            rawReader?.let { b.addTarget(it.surface) }
        }
        natural?.let { RequestApplier.natural(b, it) }

        // Native crop: capture the lens unzoomed, crop in software afterwards.
        val nativeCrop = !snapshot && still!!.nativeCrop && stillReader?.imageFormat == ImageFormat.YUV_420_888
        val zoom = c.zoom
        var softwareCrop = 1f
        val captureControls = when {
            !snapshot && still!!.fullSensor -> c.copy(zoom = null)
            nativeCrop && zoom != null -> {
                val zr = zoom.zoomRatio
                softwareCrop = if (zr != null) zr / zoom.lens.ratio.coerceAtLeast(0.01f) else zoom.cropFactor
                softwareCrop = softwareCrop.coerceAtLeast(1f)
                c.copy(zoom = zoom.copy(zoomRatio = zoom.zoomRatio?.let { zoom.lens.ratio }, cropFactor = 1f))
            }
            else -> c
        }
        RequestApplier.controls(b, s.info, captureControls.copy(torch = false), video = snapshot, previewMaxExposureNs = null, priorityMetering = priorityMetering)
        RequestApplier.whiteBalance(b, s.info, c, lastGains, lastTransform)
        if (!snapshot && still!!.fullSensor && sdk >= 31) {
            b.set(CaptureRequest.SENSOR_PIXEL_MODE, CameraMetadata.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION)
        }
        if (manual && req.flash == FlashMode.ON && s.info.flash) b.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_SINGLE)
        req.longExposure?.let { le ->
            val exp = RequestApplier.clampExp(s.info, le.exposureNs)
            val iso = if (le.iso > 0) RequestApplier.clampIso(s.info, le.iso) else s.info.isoRange?.lower ?: 100
            b.set(CaptureRequest.CONTROL_AE_MODE, C2.AE_OFF)
            b.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exp)
            b.set(CaptureRequest.SENSOR_SENSITIVITY, iso)
            b.set(CaptureRequest.SENSOR_FRAME_DURATION, exp)
            b.set(CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST, 100)
        }
        val front = s.info.facing == C2.FACING_FRONT
        val rotation = MeteringMath.captureRotation(s.info.sensorOrientation, req.deviceRotation, front)
        b.set(CaptureRequest.JPEG_ORIENTATION, rotation)
        b.set(CaptureRequest.JPEG_QUALITY, req.jpegQuality.coerceIn(1, 100).toByte())
        req.location?.let { b.set(CaptureRequest.JPEG_GPS_LOCATION, it) }

        drain(stillImages)
        drain(rawImages)
        drain(snapshotImages)

        val result = captureOnce(sess, b.build())
        if (doAf) unlockFocus(sess)
        if (doAf || doAe) runCatching { startRepeating() }

        val waitMs = (req.longExposure?.exposureNs ?: 0L) / 1_000_000L + 5000L
        val primary = when {
            snapshot -> withTimeout(waitMs) { snapshotImages.receive() }
            stillReader != null -> withTimeout(waitMs) { stillImages.receive() }
            else -> null
        }
        val raw = if (!snapshot && rawReader != null) withTimeout(waitMs) { rawImages.receive() } else null

        // Copy pixels out on this thread so readers are freed for the next shot.
        val payload: Payload? = primary?.use { img ->
            when (img.format) {
                ImageFormat.YUV_420_888 -> Payload.Yuv(Yuv.toNv21(img), img.width, img.height)
                ImageFormat.HEIC -> Payload.Heic(bytes(img))
                else -> Payload.Jpeg(bytes(img))
            }
        }
        val characteristics = chars
        val meta = CaptureMeta(
            timestampMs = System.currentTimeMillis(),
            iso = result.get(CaptureResult.SENSOR_SENSITIVITY),
            exposureNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME),
            fNumber = result.get(CaptureResult.LENS_APERTURE),
            focalMm = result.get(CaptureResult.LENS_FOCAL_LENGTH),
            focal35Mm = req.focal35,
            lensDescription = req.lensDescription,
            location = req.location,
            naturalSummary = compliance.summary(result),
            whiteBalanceManual = result.get(CaptureResult.CONTROL_AWB_MODE) != C2.AWB_AUTO,
        )
        val name = media.fileName(req.filePrefix, meta.timestampMs)
        return scope.async(Dispatchers.IO) {
            val uris = mutableListOf<Uri>()
            var thumb: Bitmap? = null
            try {
                when (payload) {
                    is Payload.Jpeg -> {
                        uris += media.saveJpeg(payload.bytes, name, meta, req.target)
                        thumb = thumbnail(payload.bytes)
                    }
                    is Payload.Heic -> uris += media.saveHeic(payload.bytes, name, meta.timestampMs)
                    is Payload.Yuv -> {
                        val jpeg = cropToJpeg(payload, softwareCrop, req.jpegQuality)
                        val m = meta.copy(exifOrientation = ExifWriter.orientationFor(rotation))
                        uris += media.saveJpeg(jpeg, name, m, req.target)
                        thumb = thumbnail(jpeg)
                    }
                    null -> Unit
                }
                if (raw != null && characteristics != null) {
                    try {
                        val dng = DngCreator(characteristics, result).setOrientation(ExifWriter.orientationFor(rotation))
                        req.location?.let { dng.setLocation(it) }
                        uris += media.saveDng(name + if (payload != null) ".RAW" else "", meta.timestampMs) { out -> dng.writeImage(out, raw) }
                        dng.close()
                    } finally {
                        raw.close()
                    }
                }
                _events.tryEmit(EngineEvent.Saved(uris, thumb))
            } catch (e: Exception) {
                Log.e(TAG, "save failed", e)
                raw?.let { runCatching { it.close() } }
                _events.tryEmit(EngineEvent.Failed("Couldn't save: ${e.message}"))
            }
            uris
        }
    }

    private sealed interface Payload {
        class Jpeg(val bytes: ByteArray) : Payload
        class Heic(val bytes: ByteArray) : Payload
        class Yuv(val nv21: ByteArray, val w: Int, val h: Int) : Payload
    }

    private fun bytes(img: Image): ByteArray {
        val buf = img.planes[0].buffer
        val out = ByteArray(buf.remaining())
        buf.get(out)
        return out
    }

    private suspend fun captureOnce(sess: CameraCaptureSession, request: CaptureRequest): TotalCaptureResult =
        suspendCancellableCoroutine { cont ->
            sess.capture(request, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureStarted(session: CameraCaptureSession, request: CaptureRequest, timestamp: Long, frameNumber: Long) {
                    sounds.shutter()
                    _events.tryEmit(EngineEvent.ShutterFired)
                }

                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    if (cont.isActive) cont.resume(result)
                }

                override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException("Capture failed (${failure.reason})"))
                }
            }, cameraHandler)
        }

    /** Centre crop of the NV21 frame at its true pixel count; no resampling. */
    private fun cropToJpeg(p: Payload.Yuv, factor: Float, quality: Int): ByteArray {
        val f = factor.coerceAtLeast(1f)
        var w = (p.w / f).roundToInt() and 1.inv()
        var h = (p.h / f).roundToInt() and 1.inv()
        w = w.coerceIn(2, p.w)
        h = h.coerceIn(2, p.h)
        val left = ((p.w - w) / 2) and 1.inv()
        val top = ((p.h - h) / 2) and 1.inv()
        val out = ByteArrayOutputStream()
        YuvImage(p.nv21, ImageFormat.NV21, p.w, p.h, null)
            .compressToJpeg(android.graphics.Rect(left, top, left + w, top + h), quality.coerceIn(1, 100), out)
        return out.toByteArray()
    }

    private fun thumbnail(jpeg: ByteArray): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 256 && bounds.outHeight / (sample * 2) >= 256) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val degrees = ExifInterface(ByteArrayInputStream(jpeg)).rotationDegrees
        if (degrees == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
    }.getOrNull()

    // ------------------------------------------------------------------
    // Recording
    // ------------------------------------------------------------------

    suspend fun startRecording(req: RecordRequest) = lock.withLock {
        withContext(cameraDispatcher) {
            val s = spec ?: error("Camera not ready")
            val v = s.video ?: error("Not a video mode")
            check(recorder == null) { "Already recording" }
            val now = System.currentTimeMillis()
            val name = media.fileName(req.filePrefix, now)
            val pv = media.createVideo(name, now, req.target)
            val front = s.info.facing == C2.FACING_FRONT
            val orientation = MeteringMath.captureRotation(s.info.sensorOrientation, req.deviceRotation, front)
            try {
                val rec = when (s.kind) {
                    SessionKind.TIMELAPSE -> {
                        val t = s.timelapse!!
                        makeRecorder(s, v.copy(fps = t.playbackFps, audio = null), null).also { timelapse!!.start(it.inputSurface) }
                    }
                    else -> nextRecorder ?: error("Recorder not ready")
                }
                nextRecorder = null
                rec.start(pv.pfd.fileDescriptor, orientation, req.location)
                recorder = rec
                pendingVideo = pv
                recordingActive = true
                startRepeating()
                sounds.startRecording()
                _recording.value = RecordingState(active = true, startedAtMs = now)
                _events.tryEmit(EngineEvent.RecordingStarted(now))
            } catch (e: Exception) {
                media.finishVideo(pv, keep = false)
                recordingActive = false
                throw e
            }
        }
    }

    suspend fun stopRecording(): Uri? = lock.withLock { withContext(cameraDispatcher) { stopRecordingInternal() } }

    fun pauseRecording() {
        val r = recorder ?: return
        r.pause()
        _recording.value = _recording.value.copy(paused = true, pausedAtMs = System.currentTimeMillis())
    }

    fun resumeRecording() {
        val r = recorder ?: return
        r.resume()
        val st = _recording.value
        _recording.value = st.copy(paused = false, pausedTotalMs = st.pausedTotalMs + (System.currentTimeMillis() - st.pausedAtMs))
    }

    private suspend fun stopRecordingInternal(): Uri? {
        val rec = recorder ?: return null
        val pv = pendingVideo
        recordingActive = false
        runCatching { startRepeating() }
        timelapse?.stop()
        val ok = runCatching { rec.stop() }.getOrDefault(false)
        rec.release()
        recorder = null
        pendingVideo = null
        if (pv != null) media.finishVideo(pv, ok)
        sounds.stopRecording()
        _recording.value = RecordingState()
        val s = spec
        val surface = persistentSurface
        if (s?.video != null && surface != null && (s.kind == SessionKind.VIDEO || s.kind == SessionKind.HIGH_SPEED)) {
            nextRecorder = runCatching { makeRecorder(s, s.video, surface) }.onFailure { Log.e(TAG, "re-prime", it) }.getOrNull()
        }
        val uri = if (ok) pv?.uri else null
        _events.tryEmit(EngineEvent.RecordingFinished(uri))
        if (!ok) _events.tryEmit(EngineEvent.Failed("The recording was empty and has been discarded"))
        return uri
    }

    // ------------------------------------------------------------------

    private fun describe(e: Exception): String = when (e) {
        is CameraAccessException -> when (e.reason) {
            CameraAccessException.CAMERA_DISABLED -> "The camera is disabled (device policy or privacy toggle)"
            CameraAccessException.CAMERA_IN_USE, CameraAccessException.MAX_CAMERAS_IN_USE -> "The camera is in use by another app"
            CameraAccessException.CAMERA_DISCONNECTED -> "The camera was disconnected"
            else -> e.message ?: "Camera error"
        }
        is SecurityException -> "Camera permission is needed"
        else -> e.message ?: e.javaClass.simpleName
    }

    fun release() {
        scope.launch {
            close()
            sounds.release()
            cameraThread.quitSafely()
            readerThread.quitSafely()
            analysisThread.quitSafely()
        }
    }

    companion object {
        private const val TAG = "QuartzEngine"
    }
}
