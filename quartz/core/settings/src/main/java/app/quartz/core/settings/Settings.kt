package app.quartz.core.settings

import app.quartz.core.caps.C2
import app.quartz.core.caps.natural.NaturalPrefs
import app.quartz.core.caps.options.Aspect
import app.quartz.core.caps.options.BitrateLevel
import app.quartz.core.caps.options.Codec
import kotlinx.serialization.Serializable

@Serializable
enum class Mode(val label: String, val video: Boolean) {
    LONG_EXPOSURE("Long exposure", false),
    PHOTO("Photo", false),
    INTERVAL("Interval", false),
    SLOW_MOTION("Slow motion", true),
    VIDEO("Video", true),
    TIMELAPSE("Time-lapse", true);

    companion object {
        val photoModes = listOf(LONG_EXPOSURE, PHOTO, INTERVAL)
        val videoModes = listOf(SLOW_MOTION, VIDEO, TIMELAPSE)
    }
}

@Serializable
enum class PhotoFormat(val label: String) {
    JPEG("JPEG"),
    RAW_JPEG("RAW + JPEG"),
    RAW("RAW"),
    HEIC("HEIC");

    val raw: Boolean get() = this == RAW || this == RAW_JPEG
}

@Serializable
enum class FlashMode { OFF, AUTO, ON }

@Serializable
enum class DynamicRange(val label: String) { SDR("SDR"), HLG("HDR · HLG 10-bit"), HDR10("HDR10 10-bit") }

@Serializable
enum class GridType(val label: String) { NONE("Off"), THIRDS("3 × 3"), SQUARE("4 × 4"), GOLDEN("Golden ratio") }

@Serializable
enum class LensSwitchPolicy(val label: String) {
    /** Switch lens and change to the nearest setting it supports (plan §7.4). */
    ADAPT("Switch lens, adapt settings"),

    /** Keep the settings and crop digitally on the current lens. */
    CROP("Keep settings, crop on current lens"),
}

@Serializable
enum class LongPressAction(val label: String) { BURST("Burst"), NONE("Nothing") }

@Serializable
enum class VolumeKeyAction(val label: String) { SHUTTER("Shutter"), ZOOM("Zoom"), NONE("Volume") }

/** Photo settings for one lens. width/height 0 = default for the lens. */
@Serializable
data class PhotoConfig(
    val width: Int = 0,
    val height: Int = 0,
    val fullSensor: Boolean = false,
    val format: PhotoFormat = PhotoFormat.JPEG,
)

/** Video settings for one lens (plan §7.1). width/height 0 = default for the lens. */
@Serializable
data class VideoConfig(
    val width: Int = 0,
    val height: Int = 0,
    val fps: Int = 30,
    val codec: Codec? = null,
    val dynamicRange: DynamicRange = DynamicRange.SDR,
    val bitrate: BitrateLevel = BitrateLevel.STANDARD,
    val customMbps: Float = 50f,
    val audio: Boolean = true,
    val stereo: Boolean = true,
    val audioKbps: Int = 256,
)

@Serializable
data class SlowMoConfig(
    val width: Int = 0,
    val height: Int = 0,
    val fps: Int = 240,
    /** Retime to 30 fps so any player shows slow motion (otherwise real rate). */
    val conform: Boolean = true,
)

@Serializable
data class TimelapseConfig(
    val width: Int = 0,
    val height: Int = 0,
    val intervalMs: Int = 1000,
    val playbackFps: Int = 30,
)

@Serializable
data class IntervalConfig(val seconds: Int = 5, val count: Int = 0)

@Serializable
data class LongExposureConfig(
    val exposureNs: Long = 1_000_000_000L,
    /** 0 = the sensor's lowest ISO. */
    val iso: Int = 0,
)

@Serializable
data class QuartzSettings(
    val mode: Mode = Mode.PHOTO,
    val facing: Int = C2.FACING_BACK,

    // Per-lens settings, keyed by Lens.key / LensSet.autoKey.
    val photo: Map<String, PhotoConfig> = emptyMap(),
    val video: Map<String, VideoConfig> = emptyMap(),
    val slowMo: Map<String, SlowMoConfig> = emptyMap(),
    val timelapse: Map<String, TimelapseConfig> = emptyMap(),

    val interval: IntervalConfig = IntervalConfig(),
    val longExposure: LongExposureConfig = LongExposureConfig(),
    val aspect: Aspect = Aspect.R4_3,
    val flash: FlashMode = FlashMode.OFF,
    val timerSeconds: Int = 0,
    val jpegQuality: Int = 95,

    // Viewfinder aids (never saved into files).
    val grid: GridType = GridType.NONE,
    val level: Boolean = false,
    val histogram: Boolean = false,
    val peaking: Boolean = false,
    val zebra: Boolean = false,

    val natural: NaturalPrefs = NaturalPrefs(),

    // Zoom
    val zoomStops: List<Float> = emptyList(),
    val maxZoom: Float = 10f,
    val nativeCropZoom: Boolean = false,
    val lensSwitch: LensSwitchPolicy = LensSwitchPolicy.ADAPT,
    /** Lens lock: a Lens.key, or null for automatic lens switching. */
    val lockedLens: String? = null,

    // Video
    val allowLowLightFpsDrop: Boolean = false,
    val unprocessedAudio: Boolean = true,

    // Behaviour
    val previewFps60: Boolean = false,
    val shutterSound: Boolean = true,
    val haptics: Boolean = true,
    val location: Boolean = false,
    val volumeKeys: VolumeKeyAction = VolumeKeyAction.SHUTTER,
    val longPress: LongPressAction = LongPressAction.BURST,
    val filePrefix: String = "QTZ",

    // Advanced
    val probeHiddenIds: Boolean = false,
    val showUnsupported: Boolean = false,
    val onboarded: Boolean = false,
)
