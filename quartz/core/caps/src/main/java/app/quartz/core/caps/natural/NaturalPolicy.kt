package app.quartz.core.caps.natural

import app.quartz.core.caps.C2
import app.quartz.core.caps.CameraInfo
import kotlinx.serialization.Serializable
import kotlin.math.pow

/** User choices that touch processing (plan §17 decisions D1–D3). */
@Serializable
data class NaturalPrefs(
    /** Lens shading, chromatic aberration and distortion correction (D2). */
    val lensCorrections: Boolean = true,
    /** Hot-pixel correction (D3). Off by default. */
    val hotPixelCorrection: Boolean = false,
    /** Electronic video stabilisation, opt-in, video only (D1). */
    val videoStabilization: Boolean = false,
)

sealed interface Tonemap {
    /** TONEMAP_MODE_PRESET_CURVE + TONEMAP_PRESET_CURVE_SRGB. */
    data object PresetSrgb : Tonemap

    /** TONEMAP_MODE_CONTRAST_CURVE with an sRGB curve; interleaved (in, out) pairs. */
    class Curve(val points: FloatArray) : Tonemap {
        override fun equals(other: Any?) = other is Curve && points.contentEquals(other.points)
        override fun hashCode() = points.contentHashCode()
    }

    /** The device's own curve (mode, or null to leave the key unset). Reported as not natural. */
    data class Device(val mode: Int?) : Tonemap
}

/**
 * The processing-related request values for one camera. `null` means the key
 * is left unset because the camera lists no acceptable value for it.
 */
data class NaturalRequest(
    val noiseReduction: Int?,
    val edge: Int?,
    val hotPixel: Int?,
    val shading: Int?,
    val aberration: Int?,
    val distortion: Int?,
    val tonemap: Tonemap,
    val videoStabilization: Int,
    val ois: Int?,
    val sceneMode: Int = C2.SCENE_DISABLED,
    val effectMode: Int = C2.EFFECT_OFF,
    val extendedSceneMode: Int? = null,
    val faceDetect: Int = C2.FACE_DETECT_OFF,
    /** Set CONTROL_AUTOFRAMING to OFF (API 34+). */
    val autoframingOff: Boolean = false,
)

/**
 * The single place that decides every processing-related request value
 * (plan §3.2). Every capture request — preview, still, video, burst,
 * high-speed — goes through this, so the viewfinder matches the file.
 *
 * Rule: enhancement stages (noise reduction, sharpening, hot pixels unless
 * opted in, scene/effect/bokeh modes, low-light boost, auto-framing) get the
 * lowest value the camera lists. Hardware corrections (shading, aberration,
 * distortion) stay on unless the user turns lens corrections off. Tone curve
 * is standard sRGB where the camera allows it.
 */
object NaturalPolicy {

    fun resolve(cam: CameraInfo, prefs: NaturalPrefs, sdk: Int, video: Boolean = false): NaturalRequest {
        val corrections = if (prefs.lensCorrections) {
            listOf(C2.FAST, C2.HIGH_QUALITY, C2.OFF)
        } else {
            listOf(C2.OFF, C2.FAST)
        }
        return NaturalRequest(
            noiseReduction = pick(cam.nrModes, C2.NR_OFF, C2.NR_MINIMAL, C2.NR_FAST),
            edge = pick(cam.edgeModes, C2.EDGE_OFF, C2.EDGE_FAST),
            hotPixel = if (prefs.hotPixelCorrection) {
                pick(cam.hotPixelModes, C2.FAST, C2.HIGH_QUALITY, C2.OFF)
            } else {
                pick(cam.hotPixelModes, C2.OFF, C2.FAST)
            },
            shading = pick(cam.shadingModes, *corrections.toIntArray()),
            aberration = pick(cam.aberrationModes, *corrections.toIntArray()),
            distortion = if (sdk >= 28) pick(cam.distortionModes, *corrections.toIntArray()) else null,
            tonemap = tonemap(cam),
            videoStabilization = if (video && prefs.videoStabilization && C2.VSTAB_ON in cam.videoStabModes) {
                C2.VSTAB_ON
            } else {
                C2.VSTAB_OFF
            },
            ois = when {
                C2.OIS_ON in cam.oisModes -> C2.OIS_ON
                cam.oisModes.isNotEmpty() -> C2.OIS_OFF
                else -> null
            },
            extendedSceneMode = if (sdk >= 30 && cam.extendedSceneModes.isNotEmpty()) C2.EXTENDED_SCENE_DISABLED else null,
            autoframingOff = sdk >= 34,
        )
    }

    /** First of [order] the camera lists, or null. Never falls through to anything not in [order]. */
    fun pick(available: List<Int>, vararg order: Int): Int? = order.firstOrNull { it in available }

    private fun tonemap(cam: CameraInfo): Tonemap = when {
        C2.TONEMAP_PRESET_CURVE in cam.tonemapModes -> Tonemap.PresetSrgb
        C2.TONEMAP_CONTRAST_CURVE in cam.tonemapModes && cam.maxTonemapPoints >= 2 ->
            Tonemap.Curve(srgbCurve(cam.maxTonemapPoints.coerceAtMost(64)))
        C2.TONEMAP_FAST in cam.tonemapModes -> Tonemap.Device(C2.TONEMAP_FAST)
        else -> Tonemap.Device(null)
    }

    /**
     * The sRGB transfer function sampled at [n] points, as interleaved (in, out)
     * pairs. Inputs are spaced on a power curve so the steep shadow part of the
     * curve gets more points than the nearly straight highlights.
     */
    fun srgbCurve(n: Int): FloatArray {
        require(n >= 2)
        val out = FloatArray(n * 2)
        for (i in 0 until n) {
            val x = (i.toDouble() / (n - 1)).pow(2.2)
            out[i * 2] = x.toFloat()
            out[i * 2 + 1] = srgbEncode(x).toFloat()
        }
        return out
    }

    fun srgbEncode(linear: Double): Double =
        if (linear <= 0.0031308) 12.92 * linear else 1.055 * linear.pow(1 / 2.4) - 0.055

    /** AE modes Quartz may ever request. Low-light boost is never one of them. */
    fun allowedAeMode(mode: Int): Boolean = mode != C2.AE_ON_LOW_LIGHT_BOOST
}
