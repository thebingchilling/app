package app.quartz.core.caps.lens

import app.quartz.core.caps.C2
import app.quartz.core.caps.CameraInfo
import app.quartz.core.caps.DeviceCaps
import app.quartz.core.caps.FloatSpan
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * One physical lens.
 *
 * @param openId camera ID to open for this lens on its own (lens lock). For a
 *   physical lens that isn't in getCameraIdList(), this is the logical camera,
 *   and [physicalId] is streamed with OutputConfiguration.setPhysicalCameraId().
 */
data class Lens(
    val openId: String,
    val physicalId: String?,
    val info: CameraInfo,
    val focalMm: Float,
    val eqMm: Float,
    /** Zoom ratio relative to the main lens (0.5 = ultrawide, 1 = main, 5 = telephoto). */
    val ratio: Float,
) {
    /** Stable key for per-lens settings. */
    val key: String get() = physicalId?.let { "$openId:$it" } ?: openId
    val label: String get() = LensMap.ratioLabel(ratio)
    val name: String
        get() = when {
            ratio < 0.8f -> "Ultrawide"
            ratio < 1.25f -> "Main"
            else -> "Telephoto"
        }
    val description: String get() = "$name ${eqMm.roundToInt()} mm" + (info.apertures.firstOrNull()?.let { " f/%.1f".format(it) } ?: "")
}

/**
 * The lenses on one side of the phone.
 *
 * @param primary the camera Quartz opens by default (a logical multi-camera if
 *   the phone has one).
 * @param zoomRatioRange set when [primary] supports CONTROL_ZOOM_RATIO
 *   (API 30+): the HAL then switches lenses itself as zoom changes.
 */
data class LensSet(
    val facing: Int,
    val primary: CameraInfo,
    val lenses: List<Lens>,
    val main: Lens,
    val zoomRatioRange: FloatSpan?,
) {
    val usesZoomRatio: Boolean get() = zoomRatioRange != null
    val widest: Lens get() = lenses.first()
    val longest: Lens get() = lenses.last()

    /** Settings key for "auto" (HAL-switched lenses on the primary camera). */
    val autoKey: String get() = primary.id

    fun minZoom(): Float = zoomRatioRange?.lower ?: widest.ratio

    /** Highest zoom the hardware allows (before the user's own cap). */
    fun hardwareMaxZoom(): Float = zoomRatioRange?.upper ?: (longest.ratio * longest.info.maxDigitalZoom)

    fun lensForKey(key: String): Lens? = lenses.firstOrNull { it.key == key }
}

object LensMap {
    /** 35 mm film diagonal in mm. */
    private const val FULL_FRAME_DIAGONAL = 43.27f
    private const val DEFAULT_MAIN_EQ = 26f

    /**
     * 35 mm equivalent focal length: focal × 43.27 / sensor diagonal, using the
     * active (imaging) part of the sensor rather than the whole pixel array.
     */
    fun eqFocal(info: CameraInfo, focal: Float = info.focalLengths.firstOrNull() ?: 0f): Float? {
        val phys = info.physicalSizeMm ?: return null
        if (focal <= 0f) return null
        val pa = info.pixelArray
        val aa = info.activeArray
        val (w, h) = if (pa != null && aa != null && pa.w > 0 && pa.h > 0) {
            phys.w * aa.width / pa.w to phys.h * aa.height / pa.h
        } else {
            phys.w to phys.h
        }
        val diag = hypot(w, h)
        if (diag <= 0f) return null
        return focal * FULL_FRAME_DIAGONAL / diag
    }

    fun build(caps: DeviceCaps, facing: Int, sdk: Int = caps.sdk): LensSet? {
        val candidates = caps.cameras.filter {
            it.facing == facing && it.openable && it.logicalParent == null && it.usableForPhotos
        }
        if (candidates.isEmpty()) return null

        // Prefer a logical multi-camera that can zoom across lenses; otherwise
        // the first camera the system lists for this side (usually "0" / "1").
        val primary = candidates
            .filter { it.isLogicalMulti && it.physicalIds.isNotEmpty() }
            .maxWithOrNull(compareBy<CameraInfo>({ if (sdk >= 30 && it.zoomRatioRange != null) 1 else 0 }, { it.physicalIds.size }))
            ?: candidates.first()

        val mainEq = eqFocal(primary) ?: DEFAULT_MAIN_EQ
        val zoomRange = if (sdk >= 30) primary.zoomRatioRange?.takeIf { it.upper > it.lower } else null

        val raw = mutableListOf<Lens>()
        if (primary.isLogicalMulti && primary.physicalIds.isNotEmpty()) {
            for (pid in primary.physicalIds) {
                val info = caps.camera(pid) ?: continue
                if (!info.usableForPhotos) continue
                val eq = eqFocal(info) ?: continue
                val openId = if (info.openable) pid else primary.id
                raw += Lens(openId, if (info.openable) null else pid, info, info.focalLengths.firstOrNull() ?: 0f, eq, eq / mainEq)
            }
        }
        // Other cameras the system lists separately on this side (older phones
        // expose the ultrawide or telephoto as their own camera IDs).
        for (info in candidates) {
            if (info === primary && raw.isNotEmpty()) continue
            if (info.isLogicalMulti && info !== primary) continue
            val eq = eqFocal(info) ?: if (info === primary) mainEq else continue
            raw += Lens(info.id, null, info, info.focalLengths.firstOrNull() ?: 0f, eq, eq / mainEq)
        }

        // One entry per focal length (within 4%); keep the first found.
        val lenses = mutableListOf<Lens>()
        for (lens in raw.sortedBy { it.eqMm }) {
            if (lenses.none { abs(it.eqMm - lens.eqMm) / lens.eqMm < 0.04f }) lenses += lens
        }
        if (lenses.isEmpty()) {
            lenses += Lens(primary.id, null, primary, primary.focalLengths.firstOrNull() ?: 0f, mainEq, 1f)
        }

        // With CONTROL_ZOOM_RATIO the HAL's own range is the truth for the widest lens.
        if (zoomRange != null && zoomRange.lower < 1f && lenses.first().ratio < 0.9f) {
            lenses[0] = lenses[0].copy(ratio = zoomRange.lower)
        }
        val main = lenses.minBy { abs(it.ratio - 1f) }
        return LensSet(facing, primary, lenses, main, zoomRange)
    }

    /** "0.5", "1×", "2", "5", "3.5": like Pixel Camera's zoom buttons. */
    fun ratioLabel(ratio: Float): String {
        val r = if (ratio < 1f) (ratio * 10).roundToInt() / 10f else (ratio * 2).roundToInt() / 2f
        val text = if (r == r.roundToInt().toFloat()) r.roundToInt().toString() else "%.1f".format(r).trimEnd('0').trimEnd('.')
        return if (abs(r - 1f) < 0.01f) "1×" else text
    }

    /** Facing constants re-exported for callers. */
    const val BACK = C2.FACING_BACK
    const val FRONT = C2.FACING_FRONT
}
