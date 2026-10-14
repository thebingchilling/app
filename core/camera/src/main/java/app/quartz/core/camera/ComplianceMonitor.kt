package app.quartz.core.camera

import android.hardware.camera2.CaptureResult
import app.quartz.core.caps.natural.NaturalReporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reads back what the camera actually did (plan §3.3). The Natural report
 * compares these values with what NaturalPolicy asked for.
 */
class ComplianceMonitor {
    private val _observed = MutableStateFlow<Map<String, Int>>(emptyMap())
    val observed: StateFlow<Map<String, Int>> = _observed.asStateFlow()
    private var frames = 0

    fun reset() {
        frames = 0
        _observed.value = emptyMap()
    }

    fun onResult(result: CaptureResult) {
        if (frames++ % 15 != 0) return
        val m = mutableMapOf<String, Int>()
        result.get(CaptureResult.NOISE_REDUCTION_MODE)?.let { m[NaturalReporter.NOISE_REDUCTION] = it }
        result.get(CaptureResult.EDGE_MODE)?.let { m[NaturalReporter.SHARPENING] = it }
        result.get(CaptureResult.TONEMAP_MODE)?.let { m[NaturalReporter.TONE_CURVE] = it }
        result.get(CaptureResult.HOT_PIXEL_MODE)?.let { m[NaturalReporter.HOT_PIXELS] = it }
        result.get(CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE)?.let { m[NaturalReporter.STABILIZATION] = it }
        if (m != _observed.value) _observed.value = m
    }

    /** One line for EXIF UserComment describing a still capture's processing. */
    fun summary(result: CaptureResult): String {
        val nr = NaturalReporter.nrName(result.get(CaptureResult.NOISE_REDUCTION_MODE))
        val edge = NaturalReporter.edgeName(result.get(CaptureResult.EDGE_MODE))
        val tone = when (result.get(CaptureResult.TONEMAP_MODE)) {
            0 -> "sRGB curve"
            4 -> "sRGB"
            null -> "unset"
            else -> "device"
        }
        return "Quartz natural: noise reduction $nr, sharpening $edge, tone $tone"
    }
}
