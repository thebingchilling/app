package app.quartz.core.caps.lens

import app.quartz.core.caps.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Where a zoom ratio lands.
 *
 * @param lens the lens that serves it (for CONTROL_ZOOM_RATIO this is the
 *   lens the HAL is expected to use; the session stays on [LensSet.primary]).
 * @param cameraId camera that must be open.
 * @param physicalId physical stream on that camera, for a locked lens that
 *   can't be opened on its own.
 * @param zoomRatio value for CONTROL_ZOOM_RATIO, or null to use a crop region.
 * @param cropFactor digital crop on [lens] when [zoomRatio] is null (1 = none).
 */
data class ZoomTarget(
    val lens: Lens,
    val cameraId: String,
    val physicalId: String?,
    val zoomRatio: Float?,
    val cropFactor: Float,
    /** Past the longest lens: digital crop, shown amber in the ruler. */
    val digital: Boolean,
)

object ZoomMath {
    fun clampZoom(set: LensSet, ratio: Float, userMax: Float): Float {
        val hi = min(set.hardwareMaxZoom(), max(userMax, set.longest.ratio))
        return ratio.coerceIn(set.minZoom(), max(hi, set.minZoom()))
    }

    fun target(set: LensSet, ratio: Float, userMax: Float, lock: Lens? = null): ZoomTarget {
        val r = clampZoom(set, ratio, userMax)
        if (lock == null && set.zoomRatioRange != null) {
            val lens = lensForRatio(set, r)
            return ZoomTarget(lens, set.primary.id, null, r, 1f, r > set.longest.ratio * 1.01f)
        }
        val lens = lock ?: lensForRatio(set, r)
        val crop = (r / lens.ratio).coerceIn(1f, max(1f, lens.info.maxDigitalZoom))
        return ZoomTarget(lens, lens.openId, lens.physicalId, null, crop, lock == null && r > set.longest.ratio * 1.01f || lock != null && crop > 1.01f)
    }

    /** The longest lens whose own ratio is at or below [ratio] (the widest lens below that). */
    fun lensForRatio(set: LensSet, ratio: Float): Lens =
        set.lenses.lastOrNull { it.ratio <= ratio * 1.001f } ?: set.widest

    /**
     * Zoom buttons: one per lens, a 2× crop on the main lens if there's no lens
     * near 2×, and the user's own stops. Sorted, de-duplicated, within range.
     */
    fun pills(set: LensSet, userStops: List<Float>, userMax: Float): List<Float> {
        val lo = set.minZoom()
        val hi = clampZoom(set, Float.MAX_VALUE, userMax)
        val stops = mutableListOf<Float>()
        set.lenses.forEach { stops += it.ratio }
        if (set.lenses.none { it.ratio in 1.6f..2.6f } && 2f <= hi) stops += 2f
        stops += userStops
        val sorted = stops.filter { it in lo * 0.999f..hi * 1.001f }.sorted()
        val out = mutableListOf<Float>()
        for (s in sorted) if (out.none { abs(it - s) < 0.05f }) out += s
        return out
    }

    /** Centred crop of [active] for a digital zoom [factor] (SCALER_CROP_REGION). */
    fun cropRegion(active: Rect, factor: Float): Rect {
        val f = max(1f, factor)
        val w = (active.width / f).roundToInt()
        val h = (active.height / f).roundToInt()
        val left = active.left + (active.width - w) / 2
        val top = active.top + (active.height - h) / 2
        return Rect(left, top, left + w, top + h)
    }
}

/**
 * Converts a tap on the (portrait, letterboxed, possibly mirrored) preview into
 * a metering region in the sensor's coordinate system.
 */
object MeteringMath {
    /**
     * @param vx, vy normalised tap position in the preview view (0..1, portrait).
     * @param displayRotation degrees the display is rotated from the device's
     *   natural orientation (0 for a portrait-locked phone).
     * @return normalised position in the sensor's frame (0..1).
     */
    fun viewToSensor(vx: Float, vy: Float, sensorOrientation: Int, front: Boolean, displayRotation: Int = 0): Pair<Float, Float> {
        // The display shows the sensor image rotated by (sensorOrientation ∓ displayRotation)
        // clockwise, mirrored horizontally for the front camera.
        val x0 = if (front) 1f - vx else vx
        val rotation = if (front) {
            (sensorOrientation + displayRotation) % 360
        } else {
            (sensorOrientation - displayRotation + 360) % 360
        }
        return when (rotation) {
            90 -> vy to 1f - x0
            180 -> 1f - x0 to 1f - vy
            270 -> 1f - vy to x0
            else -> x0 to vy
        }
    }

    /** A square metering rectangle of [sizeFraction] of the shorter side around ([sx], [sy]) in [frame]. */
    fun region(sx: Float, sy: Float, frame: Rect, sizeFraction: Float = 0.1f): Rect {
        val half = (min(frame.width, frame.height) * sizeFraction / 2).roundToInt().coerceAtLeast(1)
        val cx = frame.left + (sx.coerceIn(0f, 1f) * frame.width).roundToInt()
        val cy = frame.top + (sy.coerceIn(0f, 1f) * frame.height).roundToInt()
        val left = (cx - half).coerceIn(frame.left, frame.right - 1)
        val top = (cy - half).coerceIn(frame.top, frame.bottom - 1)
        val right = (cx + half).coerceIn(left + 1, frame.right)
        val bottom = (cy + half).coerceIn(top + 1, frame.bottom)
        return Rect(left, top, right, bottom)
    }

    /** Clockwise rotation (degrees) to apply to sensor-oriented pixels for an upright picture. */
    fun captureRotation(sensorOrientation: Int, deviceRotation: Int, front: Boolean): Int =
        if (front) (sensorOrientation - deviceRotation + 360) % 360 else (sensorOrientation + deviceRotation) % 360
}
