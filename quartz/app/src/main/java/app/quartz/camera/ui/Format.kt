package app.quartz.camera.ui

import kotlin.math.roundToInt
import kotlin.math.roundToLong

object Format {
    /** 1/8000, 1/125, 0.5″, 2″, 30″. */
    fun shutter(ns: Long?): String {
        ns ?: return "Auto"
        val s = ns / 1e9
        return if (s >= 0.3) {
            val v = if (s >= 10) s.roundToInt().toString() else "%.1f".format(s).trimEnd('0').trimEnd('.')
            "$v″"
        } else {
            "1/${(1 / s).roundToLong()}"
        }
    }

    fun iso(iso: Int?): String = iso?.let { "ISO $it" } ?: "ISO Auto"

    /** Diopters → metres. */
    fun focus(diopters: Float?): String = when {
        diopters == null -> "Auto"
        diopters <= 0.01f -> "∞"
        else -> {
            val m = 1f / diopters
            if (m < 1f) "${(m * 100).roundToInt()} cm" else "%.1f m".format(m)
        }
    }

    fun ev(index: Int, step: Float): String {
        val v = index * step
        return if (v == 0f) "±0" else "%+.1f".format(v)
    }

    fun duration(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total / 60) % 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun zoom(r: Float): String =
        if (r < 1f) "%.1f×".format(r) else if (r < 10f) "%.1f×".format(r).replace(".0×", "×") else "${r.roundToInt()}×"

    fun interval(ms: Int): String = if (ms < 1000) "${ms / 1000.0}s".replace(".0s", "s") else "${ms / 1000}s"
}
