package app.quartz.camera.ui

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size as AndroidSize
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.quartz.camera.QuartzApp
import app.quartz.camera.Selection
import app.quartz.camera.SpecBuilder
import app.quartz.camera.util.LocationTracker
import app.quartz.core.camera.Controls
import app.quartz.core.camera.EngineEvent
import app.quartz.core.camera.EngineState
import app.quartz.core.camera.RecordRequest
import app.quartz.core.camera.SessionKind
import app.quartz.core.camera.ShotRequest
import app.quartz.core.caps.C2
import app.quartz.core.caps.DeviceCaps
import app.quartz.core.caps.FpsRange
import app.quartz.core.caps.Rect
import app.quartz.core.caps.Size
import app.quartz.core.caps.lens.LensMap
import app.quartz.core.caps.lens.LensSet
import app.quartz.core.caps.lens.MeteringMath
import app.quartz.core.caps.lens.ZoomMath
import app.quartz.core.caps.lens.ZoomTarget
import app.quartz.core.caps.natural.NaturalReport
import app.quartz.core.caps.natural.NaturalReporter
import app.quartz.core.settings.FlashMode
import app.quartz.core.settings.Mode
import app.quartz.core.settings.QuartzSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** The preview surface and the buffer size it was set to. */
data class PreviewSurface(val surface: Surface, val size: Size)

enum class Panel { GENERAL, PRO }

enum class Screen { CAMERA, SETTINGS, REVIEW }

enum class Sheet { VIDEO_GRID, PHOTO_SIZE, SLOW_MOTION, NATURAL_REPORT, LENS_INFO }

data class FocusUi(val x: Float, val y: Float, val locked: Boolean, val sliders: Boolean, val id: Long)

/** Manual controls the user has set in the Pro tab or on the focus ring. */
data class ManualState(
    val evIndex: Int = 0,
    val iso: Int? = null,
    val exposureNs: Long? = null,
    val focusDiopters: Float? = null,
    val awbMode: Int = C2.AWB_AUTO,
    val kelvin: Int? = null,
    val wbShift: Float = 0f,
    val aeLock: Boolean = false,
    val awbLock: Boolean = false,
    val torch: Boolean = false,
) {
    val any: Boolean get() = iso != null || exposureNs != null || focusDiopters != null || awbMode != C2.AWB_AUTO || kelvin != null
}

data class UiState(
    val screen: Screen = Screen.CAMERA,
    val panel: Panel? = null,
    val sheet: Sheet? = null,
    val focus: FocusUi? = null,
    val countdown: Int? = null,
    val exposureProgress: Float? = null,
    val thumbnail: Bitmap? = null,
    val thumbnailUri: Uri? = null,
    val busy: Boolean = false,
    val shutterFlash: Int = 0,
    val burstCount: Int = 0,
    val intervalShots: Int? = null,
    val pinching: Boolean = false,
    val captureIntent: CaptureIntent? = null,
    val secure: Boolean = false,
)

/** A camera intent from another app (ACTION_IMAGE_CAPTURE / ACTION_VIDEO_CAPTURE). */
data class CaptureIntent(val video: Boolean, val output: Uri?)

sealed interface CaptureOutcome {
    data class Done(val uri: Uri?, val thumbnail: Bitmap?) : CaptureOutcome
    data object Cancelled : CaptureOutcome
}

@OptIn(FlowPreview::class)
class CameraViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as QuartzApp).graph
    val engine = graph.engine
    private val repo = graph.settings
    private val location = LocationTracker(app)

    val settings: StateFlow<QuartzSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, QuartzSettings())

    private val _caps = MutableStateFlow<DeviceCaps?>(null)
    val caps: StateFlow<DeviceCaps?> = _caps.asStateFlow()

    val zoom = MutableStateFlow(1f)
    private val committedZoom = MutableStateFlow(1f)
    val deviceRotation = MutableStateFlow(0)
    private val surface = MutableStateFlow<PreviewSurface?>(null)
    private val active = MutableStateFlow(false)
    private val _manual = MutableStateFlow(ManualState())
    val manual: StateFlow<ManualState> = _manual.asStateFlow()
    private val meteringRegion = MutableStateFlow<Rect?>(null)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val notices: SharedFlow<String> = _notices.asSharedFlow()
    private val _outcomes = MutableSharedFlow<CaptureOutcome>(extraBufferCapacity = 2)
    val outcomes: SharedFlow<CaptureOutcome> = _outcomes.asSharedFlow()

    val engineState = engine.state
    val frame = engine.frame
    val recording = engine.recording
    val analysis = engine.analysis

    val lensSet: StateFlow<LensSet?> = combine(caps, settings.map { it.facing }.distinctUntilChanged()) { c, facing ->
        c?.let { LensMap.build(it, facing) ?: LensMap.build(it, if (facing == C2.FACING_BACK) C2.FACING_FRONT else C2.FACING_BACK) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val selection: StateFlow<Selection?> = combine(caps, settings, lensSet, committedZoom) { c, s, set, z ->
        if (c == null || set == null) null else SpecBuilder.build(c, s, set, z)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Live zoom target: follows the pinch, but stays on the session's camera until the zoom settles. */
    val liveTarget: StateFlow<ZoomTarget?> = combine(zoom, selection, settings) { z, sel, s ->
        sel ?: return@combine null
        val set = sel.lensSet
        val lock = when {
            s.lockedLens != null -> set.lensForKey(s.lockedLens!!)
            set.usesZoomRatio -> null
            else -> sel.target.lens
        }
        ZoomMath.target(set, z, s.maxZoom, lock).let { t ->
            if (t.cameraId == sel.target.cameraId) t else ZoomMath.target(set, z, s.maxZoom, sel.target.lens)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val pills: StateFlow<List<Float>> = combine(lensSet, settings) { set, s ->
        set?.let { ZoomMath.pills(it, s.zoomStops, s.maxZoom) }.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Natural verdict for the active lens (static + what the camera reported). */
    val naturalReport: StateFlow<NaturalReport?> = combine(selection, settings, engine.compliance.observed) { sel, s, observed ->
        sel?.let { NaturalReporter.build(it.spec.lensInfo, s.natural, Build.VERSION.SDK_INT, observed) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var intervalJob: Job? = null
    private var burstJob: Job? = null
    private var focusResetJob: Job? = null
    private var lastAdaptedNote: String? = null

    init {
        viewModelScope.launch {
            val s = settings.first()
            _caps.value = runCatching { graph.probe.load(probeHiddenIds = s.probeHiddenIds) }
                .onFailure { _notices.tryEmit("Couldn't read the camera's capabilities: ${it.message}") }
                .getOrNull()
            engine.unprocessedAudioSupported = _caps.value?.audioUnprocessed == true
        }
        // Zoom: commit after the gesture settles (may switch camera on some phones).
        viewModelScope.launch { zoom.debounce(250).collect { committedZoom.value = it } }

        // Configure the camera whenever the session spec or surface changes.
        viewModelScope.launch {
            combine(selection.map { it?.spec }.distinctUntilChanged(), surface, active) { spec, surf, act -> Triple(spec, surf, act) }
                .collectLatest { (spec, surf, act) ->
                    if (!act || spec == null || surf == null) {
                        if (!act || surf == null) engine.close()
                        return@collectLatest
                    }
                    if (surf.size != spec.previewSize) return@collectLatest
                    engine.configure(spec, surf.surface)
                    pushControls()
                }
        }
        // Controls follow zoom, manual settings, flash and metering.
        viewModelScope.launch {
            combine(liveTarget, _manual, settings, meteringRegion) { _, _, _, _ -> Unit }.collect { pushControls() }
        }
        // Tell the user when saved settings had to be adapted.
        viewModelScope.launch {
            selection.collect { sel ->
                val note = sel?.adaptedNote
                if (note != null && note != lastAdaptedNote) _notices.tryEmit(note)
                lastAdaptedNote = note
            }
        }
        viewModelScope.launch {
            settings.map { Triple(it.histogram, it.peaking, it.zebra) }.distinctUntilChanged().collect { (h, p, z) ->
                engine.setAnalysis(h, p, z)
            }
        }
        viewModelScope.launch { settings.map { it.shutterSound }.distinctUntilChanged().collect { engine.sounds.enabled = it } }
        viewModelScope.launch {
            engine.events.collect { e ->
                when (e) {
                    is EngineEvent.Saved -> {
                        val uri = e.uris.firstOrNull()
                        _ui.update { it.copy(thumbnail = e.thumbnail ?: it.thumbnail, thumbnailUri = uri ?: it.thumbnailUri) }
                    }
                    is EngineEvent.Failed -> _notices.tryEmit(e.message)
                    is EngineEvent.Notice -> _notices.tryEmit(e.message)
                    is EngineEvent.ShutterFired -> _ui.update { it.copy(shutterFlash = it.shutterFlash + 1) }
                    is EngineEvent.RecordingFinished -> e.uri?.let { uri ->
                        val thumb = withContext(Dispatchers.IO) { videoThumbnail(uri) }
                        _ui.update { it.copy(thumbnail = thumb ?: it.thumbnail, thumbnailUri = uri) }
                        _ui.value.captureIntent?.let { if (it.video) _outcomes.tryEmit(CaptureOutcome.Done(uri, null)) }
                    }
                    is EngineEvent.RecordingStarted -> Unit
                }
            }
        }
        viewModelScope.launch {
            engineState.collect { st -> if (st is EngineState.Error) _notices.tryEmit(st.message) }
        }
        viewModelScope.launch { loadLatestThumbnail() }
    }

    // ------------------------------------------------------------------
    // Lifecycle and surface
    // ------------------------------------------------------------------

    fun setActive(value: Boolean) {
        active.value = value
        if (value && settings.value.location) location.start() else location.stop()
        if (!value) {
            stopInterval()
            if (recording.value.active) viewModelScope.launch { engine.stopRecording() }
        }
    }

    fun onSurface(s: PreviewSurface?) {
        if (s == null) {
            surface.value = null
            // The camera must stop drawing into the surface before it's destroyed.
            runBlocking { withTimeoutOrNull(1500) { engine.close() } }
        } else {
            surface.value = s
        }
    }

    fun setCaptureIntent(intent: CaptureIntent?, secure: Boolean) {
        _ui.update { it.copy(captureIntent = intent, secure = secure) }
        if (intent != null) {
            val mode = if (intent.video) Mode.VIDEO else Mode.PHOTO
            if (settings.value.mode != mode) updateSettings { it.copy(mode = mode) }
        }
    }

    // ------------------------------------------------------------------
    // Controls
    // ------------------------------------------------------------------

    private fun pushControls() {
        val sel = selection.value ?: return
        val m = _manual.value
        val s = settings.value
        val target = liveTarget.value ?: sel.target
        engine.setControls(
            Controls(
                zoom = target,
                evIndex = m.evIndex,
                aeLock = m.aeLock,
                awbLock = m.awbLock,
                meteringRegion = meteringRegion.value,
                iso = m.iso,
                exposureNs = m.exposureNs,
                focusDiopters = m.focusDiopters,
                awbMode = m.awbMode,
                kelvin = m.kelvin,
                wbShift = m.wbShift,
                flash = if (sel.mode.video) FlashMode.OFF else s.flash,
                torch = m.torch,
                fpsRange = sel.spec.previewFps,
            ),
        )
    }

    fun setManual(transform: (ManualState) -> ManualState) = _manual.update(transform)

    fun resetManual() {
        _manual.value = ManualState(torch = _manual.value.torch)
    }

    fun updateSettings(transform: (QuartzSettings) -> QuartzSettings) {
        viewModelScope.launch { repo.update(transform) }
    }

    fun setZoom(ratio: Float) {
        val set = lensSet.value ?: return
        zoom.value = ZoomMath.clampZoom(set, ratio, settings.value.maxZoom)
    }

    fun zoomBy(factor: Float) = setZoom(zoom.value * factor)

    fun setPinching(p: Boolean) = _ui.update { it.copy(pinching = p) }

    fun nextPill() {
        val p = pills.value
        if (p.isEmpty()) return
        val next = p.firstOrNull { it > zoom.value * 1.01f } ?: p.first()
        setZoom(next)
    }

    fun setMode(mode: Mode) {
        if (recording.value.active || intervalJob != null) return
        updateSettings { it.copy(mode = mode) }
        _ui.update { it.copy(panel = null) }
    }

    fun swipeMode(direction: Int) {
        val current = selection.value?.mode ?: settings.value.mode
        val list = if (current.video) Mode.videoModes else Mode.photoModes
        val caps = _caps.value
        val cam = selection.value?.spec?.info
        val usable = list.filter { m -> caps == null || cam == null || SpecBuilder.modeSupported(m, cam, caps) }
        val i = usable.indexOf(current)
        usable.getOrNull(i + direction)?.let { setMode(it) }
    }

    fun flipCamera() {
        if (recording.value.active) return
        val set = lensSet.value ?: return
        val other = if (set.facing == C2.FACING_BACK) C2.FACING_FRONT else C2.FACING_BACK
        if (_caps.value?.let { LensMap.build(it, other) } == null) return
        zoom.value = 1f
        committedZoom.value = 1f
        meteringRegion.value = null
        _ui.update { it.copy(focus = null) }
        updateSettings { it.copy(facing = other, lockedLens = null) }
    }

    fun setPanel(panel: Panel?) = _ui.update { it.copy(panel = panel) }
    fun setSheet(sheet: Sheet?) = _ui.update { it.copy(sheet = sheet) }
    fun setScreen(screen: Screen) = _ui.update { it.copy(screen = screen, panel = null, sheet = null) }

    // ------------------------------------------------------------------
    // Focus and metering
    // ------------------------------------------------------------------

    /** Tap at a normalised position (0..1) in the preview. */
    fun onTap(x: Float, y: Float, lock: Boolean = false) {
        if (_ui.value.panel != null) {
            setPanel(null)
            return
        }
        val sel = selection.value ?: return
        val info = sel.spec.info
        val (sx, sy) = MeteringMath.viewToSensor(x, y, info.sensorOrientation, info.facing == C2.FACING_FRONT)
        val frame = meteringFrame(sel) ?: return
        val region = MeteringMath.region(sx, sy, frame)
        meteringRegion.value = region
        if (lock) setManual { it.copy(aeLock = true, awbLock = false) } else setManual { it.copy(aeLock = false, evIndex = 0, wbShift = 0f) }
        _ui.update { it.copy(focus = FocusUi(x, y, locked = lock, sliders = true, id = System.nanoTime())) }
        viewModelScope.launch { engine.focusAt(region) }
        focusResetJob?.cancel()
        if (!lock) {
            focusResetJob = viewModelScope.launch {
                delay(8000)
                clearFocus()
            }
        }
    }

    fun clearFocus() {
        focusResetJob?.cancel()
        meteringRegion.value = null
        setManual { it.copy(aeLock = false, evIndex = 0, wbShift = 0f) }
        _ui.update { it.copy(focus = null) }
        viewModelScope.launch { engine.resetFocus() }
    }

    fun touchFocusTimer() {
        val f = _ui.value.focus ?: return
        if (f.locked) return
        focusResetJob?.cancel()
        focusResetJob = viewModelScope.launch {
            delay(8000)
            clearFocus()
        }
    }

    /**
     * The sensor region the preview shows, in the coordinates CONTROL_*_REGIONS
     * use: with CONTROL_ZOOM_RATIO that's the active array mapped onto the
     * zoomed view; with a crop region it's the crop itself.
     */
    private fun meteringFrame(sel: Selection): Rect? {
        val a = sel.spec.info.activeArray ?: return null
        val full = Rect(0, 0, a.width, a.height)
        val t = liveTarget.value ?: sel.target
        val base = if (t.zoomRatio != null || t.cropFactor <= 1.001f) full else ZoomMath.cropRegion(full, t.cropFactor)
        // The preview may be a crop of the sensor's aspect (16:9 from 4:3).
        val p = sel.spec.previewSize
        val previewAspect = maxOf(p.w, p.h).toFloat() / minOf(p.w, p.h)
        val baseAspect = base.width.toFloat() / base.height
        return if (previewAspect > baseAspect + 0.01f) {
            val h = (base.width / previewAspect).roundToInt()
            val top = base.top + (base.height - h) / 2
            Rect(base.left, top, base.right, top + h)
        } else if (previewAspect < baseAspect - 0.01f) {
            val w = (base.height * previewAspect).roundToInt()
            val left = base.left + (base.width - w) / 2
            Rect(left, base.top, left + w, base.bottom)
        } else {
            base
        }
    }

    // ------------------------------------------------------------------
    // Capture
    // ------------------------------------------------------------------

    fun onShutter() {
        val sel = selection.value ?: return
        when (sel.mode) {
            Mode.VIDEO, Mode.SLOW_MOTION, Mode.TIMELAPSE -> toggleRecording()
            Mode.INTERVAL -> if (intervalJob != null) stopInterval() else startInterval()
            Mode.PHOTO, Mode.LONG_EXPOSURE -> takePhoto(withTimer = true)
        }
    }

    private fun shotRequest(sel: Selection, quick: Boolean = false): ShotRequest {
        val s = settings.value
        val lens = (liveTarget.value ?: sel.target).lens
        return ShotRequest(
            deviceRotation = deviceRotation.value,
            flash = s.flash,
            jpegQuality = s.jpegQuality,
            filePrefix = s.filePrefix,
            location = if (s.location) location.last else null,
            longExposure = if (sel.mode == Mode.LONG_EXPOSURE) s.longExposure else null,
            quick = quick,
            target = _ui.value.captureIntent?.takeIf { !it.video }?.output,
            lensDescription = lens.description,
            focal35 = lens.eqMm.roundToInt(),
        )
    }

    fun takePhoto(withTimer: Boolean) {
        if (_ui.value.busy) return
        val sel = selection.value ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            try {
                val timer = if (withTimer) settings.value.timerSeconds else 0
                for (i in timer downTo 1) {
                    _ui.update { it.copy(countdown = i) }
                    delay(1000)
                }
                _ui.update { it.copy(countdown = null) }
                val req = shotRequest(sel)
                val progress = req.longExposure?.let { le ->
                    launch {
                        val start = System.nanoTime()
                        while (isActive) {
                            val p = ((System.nanoTime() - start).toFloat() / le.exposureNs).coerceIn(0f, 1f)
                            _ui.update { it.copy(exposureProgress = p) }
                            if (p >= 1f) break
                            delay(50)
                        }
                    }
                }
                val saved = engine.takePhoto(req)
                progress?.cancel()
                _ui.update { it.copy(exposureProgress = null) }
                _ui.value.captureIntent?.takeIf { !it.video }?.let { intent ->
                    val uris = saved.await()
                    _outcomes.tryEmit(CaptureOutcome.Done(intent.output ?: uris.firstOrNull(), _ui.value.thumbnail))
                }
            } catch (e: Exception) {
                _notices.tryEmit(e.message ?: "Couldn't take the photo")
            } finally {
                _ui.update { it.copy(busy = false, countdown = null, exposureProgress = null) }
            }
        }
    }

    /** Hold the shutter: frames as fast as the camera allows, each saved on its own. */
    fun startBurst() {
        val sel = selection.value ?: return
        if (sel.mode != Mode.PHOTO || burstJob != null) return
        burstJob = viewModelScope.launch {
            var n = 0
            while (isActive && n < 200) {
                runCatching { engine.takePhoto(shotRequest(sel, quick = true)) }.onFailure { return@launch }
                n++
                _ui.update { it.copy(burstCount = n) }
            }
        }
    }

    fun stopBurst() {
        burstJob?.cancel()
        burstJob = null
        _ui.update { it.copy(burstCount = 0) }
    }

    private fun startInterval() {
        val sel = selection.value ?: return
        intervalJob = viewModelScope.launch {
            val cfg = settings.value.interval
            var n = 0
            while (isActive && (cfg.count == 0 || n < cfg.count)) {
                runCatching { engine.takePhoto(shotRequest(sel)) }.onFailure { _notices.tryEmit(it.message ?: "Capture failed") }
                n++
                _ui.update { it.copy(intervalShots = n) }
                delay(cfg.seconds * 1000L)
            }
            intervalJob = null
            _ui.update { it.copy(intervalShots = null) }
        }
        _ui.update { it.copy(intervalShots = 0) }
    }

    fun stopInterval() {
        intervalJob?.cancel()
        intervalJob = null
        _ui.update { it.copy(intervalShots = null) }
    }

    fun toggleRecording() {
        viewModelScope.launch {
            try {
                if (recording.value.active) {
                    engine.stopRecording()
                } else {
                    val s = settings.value
                    engine.startRecording(
                        RecordRequest(
                            deviceRotation = deviceRotation.value,
                            filePrefix = s.filePrefix,
                            location = if (s.location) location.last else null,
                            target = _ui.value.captureIntent?.takeIf { it.video }?.output,
                        ),
                    )
                }
            } catch (e: Exception) {
                _notices.tryEmit(e.message ?: "Recording failed")
            }
        }
    }

    fun togglePause() {
        if (recording.value.paused) engine.resumeRecording() else engine.pauseRecording()
    }

    fun snapshot() {
        val sel = selection.value ?: return
        if (sel.spec.kind != SessionKind.VIDEO) return
        viewModelScope.launch {
            runCatching { engine.takePhoto(shotRequest(sel, quick = true)) }.onFailure { _notices.tryEmit(it.message ?: "Snapshot failed") }
        }
    }

    fun onVolumeKey(up: Boolean): Boolean {
        val s = settings.value
        return when (s.volumeKeys) {
            app.quartz.core.settings.VolumeKeyAction.SHUTTER -> {
                onShutter()
                true
            }
            app.quartz.core.settings.VolumeKeyAction.ZOOM -> {
                zoomBy(if (up) 1.25f else 0.8f)
                true
            }
            app.quartz.core.settings.VolumeKeyAction.NONE -> false
        }
    }

    // ------------------------------------------------------------------

    suspend fun exportCaps(): java.io.File? = _caps.value?.let { graph.probe.export(it) }

    fun reprobe() {
        viewModelScope.launch {
            _caps.value = runCatching { graph.probe.load(probeHiddenIds = settings.value.probeHiddenIds, force = true) }.getOrNull() ?: _caps.value
            _notices.tryEmit("Camera capabilities re-read")
        }
    }

    suspend fun recentMedia() = graph.media.recent()

    suspend fun deleteMedia(uri: Uri) = graph.media.delete(uri)

    private suspend fun loadLatestThumbnail() {
        val item = runCatching { graph.media.recent(1).firstOrNull() }.getOrNull() ?: return
        val bmp = withContext(Dispatchers.IO) {
            if (item.video) {
                videoThumbnail(item.uri)
            } else {
                runCatching {
                    if (Build.VERSION.SDK_INT >= 29) {
                        getApplication<Application>().contentResolver.loadThumbnail(item.uri, AndroidSize(256, 256), null)
                    } else {
                        null
                    }
                }.getOrNull()
            }
        }
        _ui.update { if (it.thumbnailUri == null) it.copy(thumbnail = bmp, thumbnailUri = item.uri) else it }
    }

    private fun videoThumbnail(uri: Uri): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(getApplication(), uri)
            r.getFrameAtTime(0)?.let { Bitmap.createScaledBitmap(it, 256, 256 * it.height / it.width.coerceAtLeast(1), true) }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    override fun onCleared() {
        location.stop()
        kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch { engine.close() }
    }

    val fpsChoices: List<FpsRange> get() = selection.value?.spec?.info?.aeFpsRanges.orEmpty()
}
