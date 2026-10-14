package app.quartz.core.camera

import android.graphics.Bitmap
import android.location.Location
import android.net.Uri
import app.quartz.core.caps.C2
import app.quartz.core.caps.CameraInfo
import app.quartz.core.caps.FpsRange
import app.quartz.core.caps.Rect
import app.quartz.core.caps.Size
import app.quartz.core.caps.lens.ZoomTarget
import app.quartz.core.caps.natural.NaturalPrefs
import app.quartz.core.caps.options.Codec
import app.quartz.core.settings.DynamicRange
import app.quartz.core.settings.FlashMode
import app.quartz.core.settings.LongExposureConfig
import app.quartz.core.settings.PhotoFormat

enum class SessionKind { PHOTO, VIDEO, HIGH_SPEED, TIMELAPSE }

data class StillSpec(
    val size: Size,
    val fullSensor: Boolean,
    val format: PhotoFormat,
    /** Capture YUV and crop zoom in software at true pixel count (plan §8.4). */
    val nativeCrop: Boolean,
)

data class AudioSpec(val stereo: Boolean, val bitrate: Int, val unprocessed: Boolean)

data class VideoSpec(
    val size: Size,
    /** Capture rate. */
    val fps: Int,
    val codec: Codec,
    val bitrate: Int,
    val dynamicRange: DynamicRange,
    val audio: AudioSpec?,
    /** Slow motion: write frames 1/30 s apart. */
    val conform: Boolean = false,
)

data class TimelapseSpec(val intervalMs: Int, val playbackFps: Int)

/** Everything needed to configure one capture session. */
data class SessionSpec(
    /** Camera to open. */
    val cameraId: String,
    /** Physical lens to stream from [cameraId] (lens lock on a lens that can't be opened alone). */
    val physicalId: String?,
    /** The opened camera's info (for a logical camera, the logical one). */
    val info: CameraInfo,
    /** Info for the lens that actually captures, used for the processing policy. */
    val lensInfo: CameraInfo,
    val kind: SessionKind,
    val previewSize: Size,
    val still: StillSpec? = null,
    val video: VideoSpec? = null,
    val timelapse: TimelapseSpec? = null,
    val analysis: Boolean = false,
    val snapshot: Boolean = false,
    val natural: NaturalPrefs = NaturalPrefs(),
    val previewFps: FpsRange? = null,
)

/** Per-frame controls, applied to the repeating request and copied into captures. */
data class Controls(
    val zoom: ZoomTarget? = null,
    val evIndex: Int = 0,
    val aeLock: Boolean = false,
    val awbLock: Boolean = false,
    /** Metering/focus region in the coordinates CONTROL_*_REGIONS expects. */
    val meteringRegion: Rect? = null,
    val iso: Int? = null,
    val exposureNs: Long? = null,
    val focusDiopters: Float? = null,
    val awbMode: Int = C2.AWB_AUTO,
    /** Colour temperature (API 36+ CCT mode only). */
    val kelvin: Int? = null,
    val tint: Float = 0f,
    /** Pixel-style warm/cool slider, -1..1, relative to auto white balance. */
    val wbShift: Float = 0f,
    val flash: FlashMode = FlashMode.OFF,
    val torch: Boolean = false,
    val fpsRange: FpsRange? = null,
)

/** Live values read back from the camera, for the UI. */
data class FrameInfo(
    val iso: Int? = null,
    val exposureNs: Long? = null,
    val focusDiopters: Float? = null,
    val afState: Int? = null,
    val aeState: Int? = null,
    val zoomRatio: Float? = null,
    val activePhysicalId: String? = null,
    val focalMm: Float? = null,
    val aperture: Float? = null,
    val frameDurationNs: Long? = null,
    val awbGains: FloatArray? = null,
    val colorTemperature: Int? = null,
)

sealed interface EngineState {
    data object Closed : EngineState
    data object Opening : EngineState
    data class Ready(val spec: SessionSpec) : EngineState
    data class Error(val message: String, val recoverable: Boolean) : EngineState
}

sealed interface EngineEvent {
    data object ShutterFired : EngineEvent
    data class Saved(val uris: List<Uri>, val thumbnail: Bitmap?) : EngineEvent
    data class Failed(val message: String) : EngineEvent
    data class RecordingStarted(val startedAtMs: Long) : EngineEvent
    data class RecordingFinished(val uri: Uri?) : EngineEvent
    data class Notice(val message: String) : EngineEvent
}

data class ShotRequest(
    /** Device rotation from OrientationEventListener, snapped to 0/90/180/270. */
    val deviceRotation: Int,
    val flash: FlashMode,
    val jpegQuality: Int,
    val filePrefix: String,
    val location: Location? = null,
    val longExposure: LongExposureConfig? = null,
    /** Burst frame: skip focus/exposure locking. */
    val quick: Boolean = false,
    /** ACTION_IMAGE_CAPTURE output. */
    val target: Uri? = null,
    /** "Main 24 mm f/1.7" for EXIF LensModel. */
    val lensDescription: String? = null,
    val focal35: Int? = null,
)

data class RecordRequest(
    val deviceRotation: Int,
    val filePrefix: String,
    val location: Location? = null,
    val target: Uri? = null,
)

/** Luma histogram and preview-only overlay mask from the analysis stream. */
class AnalysisFrame(
    val histogram: IntArray,
    /** ARGB overlay already rotated to the portrait preview, or null. */
    val overlay: Bitmap?,
)
