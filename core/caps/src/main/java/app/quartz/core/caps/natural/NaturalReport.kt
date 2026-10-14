package app.quartz.core.caps.natural

import app.quartz.core.caps.C2
import app.quartz.core.caps.CameraInfo

enum class Verdict {
    /** Every enhancement stage is off. */
    NATURAL,
    /** The lowest available setting is still a light one (e.g. minimal noise reduction). */
    LIGHT,
    /** The camera applies processing that it won't let us turn off. */
    PROCESSED,
}

data class ReportItem(
    val stage: String,
    val requested: String,
    val verdict: Verdict,
    val note: String? = null,
)

data class NaturalReport(
    val cameraId: String,
    val items: List<ReportItem>,
    val rawAvailable: Boolean,
) {
    val verdict: Verdict get() = items.maxOfOrNull { it.verdict } ?: Verdict.NATURAL

    val summary: String
        get() = when (verdict) {
            Verdict.NATURAL -> "Fully natural"
            Verdict.LIGHT -> "Natural, with light processing this camera can't turn off"
            Verdict.PROCESSED -> if (rawAvailable) {
                "This camera applies processing it won't disable. Use RAW for unprocessed output."
            } else {
                "This camera applies processing it won't disable."
            }
        }
}

/**
 * Builds the per-camera Natural report (plan §3.4) from what the camera lists
 * and, once it has been used, what it actually did ([observed]: stage name →
 * value read back from capture results).
 */
object NaturalReporter {
    const val NOISE_REDUCTION = "Noise reduction"
    const val SHARPENING = "Sharpening"
    const val TONE_CURVE = "Tone curve"
    const val HOT_PIXELS = "Hot pixel correction"
    const val STABILIZATION = "Video stabilisation"

    fun build(
        cam: CameraInfo,
        prefs: NaturalPrefs,
        sdk: Int,
        observed: Map<String, Int> = emptyMap(),
    ): NaturalReport {
        val req = NaturalPolicy.resolve(cam, prefs, sdk)
        val items = buildList {
            add(
                when (req.noiseReduction) {
                    C2.NR_OFF -> ReportItem(NOISE_REDUCTION, "Off", Verdict.NATURAL)
                    C2.NR_MINIMAL -> ReportItem(NOISE_REDUCTION, "Minimal", Verdict.LIGHT, "Lowest setting this camera offers")
                    else -> ReportItem(NOISE_REDUCTION, "Device default", Verdict.PROCESSED, "This camera can't turn noise reduction off")
                },
            )
            add(
                if (req.edge == C2.EDGE_OFF) {
                    ReportItem(SHARPENING, "Off", Verdict.NATURAL)
                } else {
                    ReportItem(SHARPENING, "Device default", Verdict.PROCESSED, "This camera can't turn sharpening off")
                },
            )
            add(
                when (req.tonemap) {
                    Tonemap.PresetSrgb -> ReportItem(TONE_CURVE, "sRGB", Verdict.NATURAL)
                    is Tonemap.Curve -> ReportItem(TONE_CURVE, "sRGB (custom curve)", Verdict.NATURAL)
                    is Tonemap.Device -> ReportItem(TONE_CURVE, "Device curve", Verdict.LIGHT, "May add contrast; can't be replaced on this camera")
                },
            )
            if (!prefs.hotPixelCorrection && cam.hotPixelModes.isNotEmpty() && req.hotPixel != C2.OFF) {
                add(ReportItem(HOT_PIXELS, "Device default", Verdict.LIGHT))
            }
            // What the camera actually did, if it disagrees with what we asked.
            observed[NOISE_REDUCTION]?.let { actual ->
                if (req.noiseReduction != null && actual != req.noiseReduction && actual != C2.NR_OFF) {
                    add(ReportItem(NOISE_REDUCTION, "Requested ${nrName(req.noiseReduction)}", Verdict.PROCESSED, "Camera used ${nrName(actual)}"))
                }
            }
            observed[SHARPENING]?.let { actual ->
                if (req.edge != null && actual != req.edge && actual != C2.EDGE_OFF) {
                    add(ReportItem(SHARPENING, "Requested Off", Verdict.PROCESSED, "Camera used ${edgeName(actual)}"))
                }
            }
        }
        return NaturalReport(cam.id, items, cam.raw)
    }

    fun nrName(v: Int?) = when (v) {
        C2.NR_OFF -> "Off"
        C2.NR_FAST -> "Fast"
        C2.NR_HIGH_QUALITY -> "High quality"
        C2.NR_MINIMAL -> "Minimal"
        C2.NR_ZERO_SHUTTER_LAG -> "Zero shutter lag"
        else -> "Unset"
    }

    fun edgeName(v: Int?) = when (v) {
        C2.EDGE_OFF -> "Off"
        C2.EDGE_FAST -> "Fast"
        C2.EDGE_HIGH_QUALITY -> "High quality"
        C2.EDGE_ZERO_SHUTTER_LAG -> "Zero shutter lag"
        else -> "Unset"
    }
}
