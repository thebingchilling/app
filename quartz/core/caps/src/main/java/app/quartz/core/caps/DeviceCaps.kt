package app.quartz.core.caps

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Everything Quartz knows about this phone's cameras and encoders, built once
 * by the capability probe and cached as JSON. The UI only offers what is in
 * here: if it isn't in DeviceCaps, it isn't on screen.
 */
@Serializable
data class DeviceCaps(
    val schema: Int = SCHEMA,
    val fingerprint: String,
    val manufacturer: String,
    val model: String,
    val sdk: Int,
    val cameras: List<CameraInfo>,
    val encoders: List<EncoderInfo>,
    val audioUnprocessed: Boolean = false,
    /** True for hand-written test fixtures, false for real probe output. */
    val synthetic: Boolean = false,
) {
    fun camera(id: String): CameraInfo? = cameras.firstOrNull { it.id == id }

    companion object {
        const val SCHEMA = 1
    }
}

@Serializable
data class Size(val w: Int, val h: Int) : Comparable<Size> {
    val pixels: Long get() = w.toLong() * h
    val megapixels: Double get() = pixels / 1_000_000.0
    val aspect: Float get() = max(w, h).toFloat() / min(w, h)

    fun sameAspect(other: Size, tolerance: Float = 0.02f) = abs(aspect - other.aspect) <= tolerance
    override fun compareTo(other: Size) = pixels.compareTo(other.pixels)
    override fun toString() = "${w}x$h"

    companion object {
        fun parse(s: String): Size? = s.split('x').takeIf { it.size == 2 }
            ?.let { (a, b) -> a.toIntOrNull()?.let { w -> b.toIntOrNull()?.let { h -> Size(w, h) } } }
    }
}

@Serializable
data class SizeF(val w: Float, val h: Float)

@Serializable
data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

@Serializable
data class IntSpan(val lower: Int, val upper: Int) {
    operator fun contains(v: Int) = v in lower..upper
    fun clamp(v: Int) = v.coerceIn(lower, upper)
}

@Serializable
data class LongSpan(val lower: Long, val upper: Long) {
    operator fun contains(v: Long) = v in lower..upper
    fun clamp(v: Long) = v.coerceIn(lower, upper)
}

@Serializable
data class FloatSpan(val lower: Float, val upper: Float) {
    operator fun contains(v: Float) = v in lower..upper
    fun clamp(v: Float) = v.coerceIn(lower, upper)
}

/** One output size with its minimum frame duration and stall duration (ns). */
@Serializable
data class StreamSize(val w: Int, val h: Int, val minFrameNs: Long = 0, val stallNs: Long = 0) {
    val size: Size get() = Size(w, h)

    /** Highest frame rate this size can stream at, from its minimum frame duration. */
    val maxFps: Double get() = if (minFrameNs > 0) 1e9 / minFrameNs else Double.MAX_VALUE
}

@Serializable
data class FpsRange(val lower: Int, val upper: Int) {
    val fixed: Boolean get() = lower == upper
    override fun toString() = "[$lower,$upper]"
}

@Serializable
data class HighSpeedConfig(val w: Int, val h: Int, val fpsRanges: List<FpsRange>) {
    val size: Size get() = Size(w, h)
}

/** Keys of [CameraInfo.outputs]. */
object Outputs {
    const val JPEG = "JPEG"
    const val YUV = "YUV"
    const val RAW = "RAW"
    const val PRIVATE = "PRIVATE"
    const val HEIC = "HEIC"
    /** Sizes a MediaCodec input surface can receive (getOutputSizes(MediaCodec.class)). */
    const val VIDEO = "VIDEO"
    /** Sizes a SurfaceView can receive (getOutputSizes(SurfaceHolder.class)). */
    const val PREVIEW = "PREVIEW"
}

/** Colour calibration from the sensor, used to set white balance by temperature below API 36. */
@Serializable
data class WbCalibration(
    val illuminant1: Int,
    val illuminant2: Int,
    /** Row-major 3x3 XYZ→camera matrices for each illuminant. */
    val colorMatrix1: List<Float>,
    val colorMatrix2: List<Float>,
)

@Serializable
data class CameraInfo(
    val id: String,
    /** In getCameraIdList() (or found by the hidden-ID probe), so openCamera() works. */
    val openable: Boolean = true,
    /** For a physical camera found behind a logical multi-camera: the logical camera's ID. */
    val logicalParent: String? = null,
    val physicalIds: List<String> = emptyList(),
    val facing: Int,
    val hardwareLevel: Int,
    val capabilities: List<Int>,
    val sensorOrientation: Int = 90,
    val focalLengths: List<Float> = emptyList(),
    val apertures: List<Float> = emptyList(),
    val physicalSizeMm: SizeF? = null,
    val pixelArray: Size? = null,
    val activeArray: Rect? = null,
    val maxResActiveArray: Rect? = null,
    val oisModes: List<Int> = emptyList(),
    val isoRange: IntSpan? = null,
    val exposureRangeNs: LongSpan? = null,
    val maxFrameDurationNs: Long? = null,
    /** Diopters; 0 means fixed focus. */
    val minFocusDistance: Float = 0f,
    val focusCalibration: Int = C2.FOCUS_UNCALIBRATED,
    val aeModes: List<Int> = listOf(C2.AE_ON),
    val afModes: List<Int> = emptyList(),
    val awbModes: List<Int> = listOf(C2.AWB_AUTO),
    val nrModes: List<Int> = emptyList(),
    val edgeModes: List<Int> = emptyList(),
    val tonemapModes: List<Int> = emptyList(),
    val hotPixelModes: List<Int> = emptyList(),
    val shadingModes: List<Int> = emptyList(),
    val aberrationModes: List<Int> = emptyList(),
    val distortionModes: List<Int> = emptyList(),
    val sceneModes: List<Int> = emptyList(),
    val effectModes: List<Int> = emptyList(),
    val videoStabModes: List<Int> = emptyList(),
    val extendedSceneModes: List<Int> = emptyList(),
    val aePriorityModes: List<Int> = emptyList(),
    val colorCorrectionModes: List<Int> = emptyList(),
    val aeCompRange: IntSpan = IntSpan(0, 0),
    val aeCompStep: Float = 1f / 3,
    val maxTonemapPoints: Int = 0,
    val flash: Boolean = false,
    val maxDigitalZoom: Float = 1f,
    val zoomRatioRange: FloatSpan? = null,
    val timestampSource: Int = C2.TIMESTAMP_UNKNOWN,
    val maxRegionsAe: Int = 0,
    val maxRegionsAf: Int = 0,
    val outputs: Map<String, List<StreamSize>> = emptyMap(),
    val maxResOutputs: Map<String, List<StreamSize>> = emptyMap(),
    val highSpeed: List<HighSpeedConfig> = emptyList(),
    val aeFpsRanges: List<FpsRange> = emptyList(),
    val dynamicRangeProfiles: List<Long> = emptyList(),
    val wb: WbCalibration? = null,
) {
    fun has(capability: Int) = capability in capabilities
    fun sizes(key: String): List<StreamSize> = outputs[key].orEmpty()
    fun maxResSizes(key: String): List<StreamSize> = maxResOutputs[key].orEmpty()

    val isLogicalMulti: Boolean get() = has(C2.CAP_LOGICAL_MULTI_CAMERA)
    val manualSensor: Boolean get() = has(C2.CAP_MANUAL_SENSOR)
    val manualPostProcessing: Boolean get() = has(C2.CAP_MANUAL_POST_PROCESSING)
    val raw: Boolean get() = has(C2.CAP_RAW) && sizes(Outputs.RAW).isNotEmpty()
    val heic: Boolean get() = sizes(Outputs.HEIC).isNotEmpty()
    val fullSensor: Boolean get() = has(C2.CAP_ULTRA_HIGH_RESOLUTION_SENSOR) && maxResSizes(Outputs.JPEG).isNotEmpty()
    val highSpeedVideo: Boolean get() = has(C2.CAP_CONSTRAINED_HIGH_SPEED_VIDEO) && highSpeed.isNotEmpty()
    val tenBit: Boolean get() = has(C2.CAP_DYNAMIC_RANGE_TEN_BIT)
    val hasAutoFocus: Boolean get() = minFocusDistance > 0f && afModes.any { it != C2.AF_OFF }
    val manualFocus: Boolean get() = minFocusDistance > 0f && manualSensor
    val isColor: Boolean get() = !has(C2.CAP_MONOCHROME)

    /** Cameras that can produce ordinary photos (not depth-only or monochrome helpers). */
    val usableForPhotos: Boolean get() = has(C2.CAP_BACKWARD_COMPATIBLE) && isColor

    fun streamFor(key: String, size: Size): StreamSize? =
        (sizes(key) + maxResSizes(key)).firstOrNull { it.w == size.w && it.h == size.h }
}

/** A hardware (or, failing that, software) video encoder and what it supports. */
@Serializable
data class EncoderInfo(
    val mime: String,
    val name: String,
    val hardware: Boolean,
    val maxBitrate: Int,
    val main10: Boolean = false,
    /** MediaCodecInfo.EncoderCapabilities bitrate modes: 0 CQ, 1 VBR, 2 CBR. */
    val bitrateModes: List<Int> = emptyList(),
    /** "WxH@fps" entries that areSizeAndRateSupported() accepted. */
    val support: List<String> = emptyList(),
) {
    @Transient
    private val supportSet: Set<String> = support.toSet()

    fun supports(w: Int, h: Int, fps: Int) = "${w}x$h@$fps" in supportSet

    companion object {
        fun key(w: Int, h: Int, fps: Int) = "${w}x$h@$fps"
    }
}
