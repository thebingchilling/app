package app.quartz.core.caps.options

import kotlin.math.ln
import kotlin.math.pow

/**
 * White balance by colour temperature for cameras below API 36 (where
 * COLOR_CORRECTION_MODE_CCT isn't available). Gains are relative to the
 * camera's own auto white balance gains at a known reference temperature,
 * which avoids needing a full sensor colour model: moving the target from the
 * scene's estimated temperature to the chosen one scales red and blue by the
 * ratio of a blackbody's RGB at the two temperatures.
 */
object WhiteBalance {
    const val MIN_K = 2000
    const val MAX_K = 10000

    /** Approximate sRGB of a blackbody at [kelvin] (Tanner Helland's fit), 0..1. */
    fun blackbodyRgb(kelvin: Int): Triple<Double, Double, Double> {
        val t = kelvin.coerceIn(1000, 40000) / 100.0
        val r = if (t <= 66) 255.0 else (329.698727446 * (t - 60).pow(-0.1332047592)).coerceIn(0.0, 255.0)
        val g = if (t <= 66) {
            (99.4708025861 * ln(t) - 161.1195681661).coerceIn(0.0, 255.0)
        } else {
            (288.1221695283 * (t - 60).pow(-0.0755148492)).coerceIn(0.0, 255.0)
        }
        val b = when {
            t >= 66 -> 255.0
            t <= 19 -> 0.0
            else -> (138.5177312231 * ln(t - 10) - 305.0447927307).coerceIn(0.0, 255.0)
        }
        return Triple(r / 255, g / 255, b / 255)
    }

    /**
     * RGGB gains that render a light of [kelvin] as neutral, given the AWB
     * [awbGains] (R, Geven, Godd, B) that the camera used for a scene it judged
     * to be [sceneKelvin]. [tint] (-1..1) shifts green/magenta.
     */
    fun gainsFor(kelvin: Int, sceneKelvin: Int, awbGains: FloatArray, tint: Float = 0f): FloatArray {
        val (r0, g0, b0) = blackbodyRgb(sceneKelvin)
        val (r1, g1, b1) = blackbodyRgb(kelvin)
        // A light that is redder than the scene needs less red gain, and so on.
        val rScale = (r0 / g0) / (r1 / g1).coerceAtLeast(1e-3)
        val bScale = (b0 / g0) / (b1 / g1).coerceAtLeast(1e-3)
        val gScale = 1.0 - tint.coerceIn(-1f, 1f) * 0.15
        return floatArrayOf(
            (awbGains[0] * rScale).toFloat(),
            (awbGains[1] * gScale).toFloat(),
            (awbGains[2] * gScale).toFloat(),
            (awbGains[3] * bScale).toFloat(),
        )
    }

    /** The Pixel-style warm/cool slider (-1..1) as a temperature shift from the scene's. */
    fun shiftKelvin(sceneKelvin: Int, shift: Float): Int =
        (sceneKelvin * 2.0.pow(shift.coerceIn(-1f, 1f) * 0.6)).toInt().coerceIn(MIN_K, MAX_K)

    /** Rough colour temperature of an AWB preset, for the Kelvin readout. */
    fun presetKelvin(awbMode: Int): Int = when (awbMode) {
        2 -> 2850 // incandescent
        3 -> 4200 // fluorescent
        4 -> 3000 // warm fluorescent
        5 -> 5500 // daylight
        6 -> 6500 // cloudy
        7 -> 15000 // twilight
        8 -> 7500 // shade
        else -> 5500
    }
}
