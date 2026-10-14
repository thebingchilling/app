package app.quartz.core.caps.options

import app.quartz.core.caps.C2
import app.quartz.core.caps.CameraInfo
import app.quartz.core.caps.EncoderInfo
import app.quartz.core.caps.FpsRange
import app.quartz.core.caps.Outputs
import app.quartz.core.caps.Size
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Aspect(val ratio: Float, val label: String) {
    R4_3(4f / 3, "4:3"),
    R16_9(16f / 9, "16:9"),
    R1_1(1f, "1:1"),
    OTHER(0f, "Other");

    fun matches(size: Size) = this != OTHER && abs(size.aspect - ratio) < 0.02f

    companion object {
        fun of(size: Size): Aspect = entries.firstOrNull { it.matches(size) } ?: OTHER
    }
}

enum class Codec(val mime: String, val label: String) {
    H264("video/avc", "H.264"),
    HEVC("video/hevc", "HEVC"),
    AV1("video/av01", "AV1");

    companion object {
        fun ofMime(mime: String) = entries.firstOrNull { it.mime == mime }
    }
}

data class PhotoSize(val size: Size, val fullSensor: Boolean) {
    val label: String
        get() {
            val mp = size.megapixels
            val mpText = if (mp >= 10) "${mp.roundToInt()} MP" else "%.1f MP".format(mp)
            return mpText + if (fullSensor) " (full sensor)" else ""
        }
}

/** Every photo size a camera really outputs (plan §7.2). */
object PhotoOptions {
    fun sizes(cam: CameraInfo, key: String = Outputs.JPEG): List<PhotoSize> {
        val normal = cam.sizes(key).map { PhotoSize(it.size, false) }
        val full = if (cam.fullSensor && key == Outputs.JPEG) {
            cam.maxResSizes(Outputs.JPEG).map { PhotoSize(it.size, true) }
        } else {
            emptyList()
        }
        val maxNormal = normal.maxOfOrNull { it.size.pixels } ?: 0
        return (normal + full.filter { it.size.pixels > maxNormal })
            .distinctBy { it.size to it.fullSensor }
            .sortedByDescending { it.size.pixels }
    }

    fun aspects(cam: CameraInfo): List<Aspect> =
        sizes(cam).map { Aspect.of(it.size) }.filter { it != Aspect.OTHER }.distinct().sortedBy { it.ordinal }

    /** Largest normal (binned) size for an aspect: the default, like Pixel's 12 MP. */
    fun default(cam: CameraInfo, aspect: Aspect): PhotoSize? =
        sizes(cam).filter { !it.fullSensor && aspect.matches(it.size) }.maxByOrNull { it.size.pixels }
            ?: sizes(cam).filter { !it.fullSensor }.maxByOrNull { it.size.pixels }
}

/**
 * One cell of the resolution × frame-rate grid (plan §7.3).
 * [codecs] are the codecs with an encoder that accepts this size and rate.
 */
data class VideoCell(
    val size: Size,
    val fps: Int,
    val codecs: List<Codec>,
    val tenBitCodecs: List<Codec> = emptyList(),
) {
    val label: String get() = "${VideoOptions.sizeLabel(size)} · $fps"
}

object VideoOptions {
    val FPS = listOf(24, 25, 30, 48, 50, 60, 90, 120)

    fun sizeLabel(size: Size): String {
        val short = minOf(size.w, size.h)
        return if (Aspect.R16_9.matches(size)) {
            when (short) {
                4320 -> "8K"
                2160 -> "4K"
                1440 -> "1440p"
                1080 -> "1080p"
                720 -> "720p"
                480 -> "480p"
                else -> "${short}p"
            }
        } else {
            "${size.w}×${size.h}"
        }
    }

    private fun usableEncoders(encoders: List<EncoderInfo>): List<EncoderInfo> {
        val hw = encoders.filter { it.hardware }
        return hw.ifEmpty { encoders }
    }

    /**
     * Grid cells for a normal (not high-speed) session: camera output size for
     * a MediaCodec surface ∩ a fixed [fps, fps] AE range ∩ the size's minimum
     * frame duration ∩ an encoder that accepts size and rate.
     */
    fun cells(cam: CameraInfo, encoders: List<EncoderInfo>): List<VideoCell> {
        val enc = usableEncoders(encoders)
        val tenBitCam = cam.tenBit && C2.DR_HLG10 in cam.dynamicRangeProfiles
        val fixed = cam.aeFpsRanges.filter { it.fixed }.map { it.upper }.toSet()
        val out = mutableListOf<VideoCell>()
        for (s in cam.sizes(Outputs.VIDEO).filter { minOf(it.w, it.h) >= 360 }) {
            for (fps in FPS) {
                if (fps !in fixed) continue
                if (s.maxFps < fps * 0.99) continue
                val codecs = Codec.entries.filter { c -> enc.any { it.mime == c.mime && it.supports(s.w, s.h, fps) } }
                if (codecs.isEmpty()) continue
                val tenBit = if (tenBitCam) {
                    Codec.entries.filter { c -> c != Codec.H264 && enc.any { it.mime == c.mime && it.main10 && it.supports(s.w, s.h, fps) } }
                } else {
                    emptyList()
                }
                out += VideoCell(s.size, fps, codecs, tenBit)
            }
        }
        return out.distinctBy { it.size to it.fps }
            .sortedWith(compareByDescending<VideoCell> { it.size.pixels }.thenBy { it.fps })
    }

    /** Slow-motion cells from the constrained high-speed configurations. */
    fun slowMotion(cam: CameraInfo, encoders: List<EncoderInfo>): List<VideoCell> {
        if (!cam.highSpeedVideo) return emptyList()
        val enc = usableEncoders(encoders)
        val out = mutableListOf<VideoCell>()
        for (hs in cam.highSpeed) {
            val rates = hs.fpsRanges.filter { it.fixed && it.upper >= 90 }.map { it.upper }.distinct()
            for (fps in rates) {
                val codecs = Codec.entries.filter { c -> enc.any { it.mime == c.mime && it.supports(hs.w, hs.h, fps) } }
                    .ifEmpty { listOf(Codec.H264).filter { enc.any { e -> e.mime == it.mime } } }
                if (codecs.isNotEmpty()) out += VideoCell(hs.size, fps, codecs)
            }
        }
        return out.distinctBy { it.size to it.fps }
            .sortedWith(compareByDescending<VideoCell> { it.size.pixels }.thenBy { it.fps })
    }

    /** The AE range to preview a high-speed session before recording (API docs: [30, fps]). */
    fun highSpeedPreviewRange(cam: CameraInfo, size: Size, fps: Int): FpsRange =
        cam.highSpeed.firstOrNull { it.size == size }?.fpsRanges
            ?.firstOrNull { it.upper == fps && it.lower < fps } ?: FpsRange(fps, fps)

    /**
     * When the chosen cell isn't available (other lens, other camera), the
     * nearest one: same size at the highest rate not above the wanted one,
     * else the biggest smaller size at that rate, else the default.
     */
    fun nearest(cells: List<VideoCell>, size: Size, fps: Int): VideoCell? {
        cells.firstOrNull { it.size == size && it.fps == fps }?.let { return it }
        cells.filter { it.size == size && it.fps <= fps }.maxByOrNull { it.fps }?.let { return it }
        cells.filter { it.size.pixels <= size.pixels && it.fps == fps && it.size.sameAspect(size) }
            .maxByOrNull { it.size.pixels }?.let { return it }
        cells.filter { it.size.pixels <= size.pixels && it.fps <= fps }
            .maxWithOrNull(compareBy<VideoCell> { it.size.pixels }.thenBy { it.fps })?.let { return it }
        return default(cells)
    }

    /** 1080p30 if available (Pixel Camera's default), else the closest. */
    fun default(cells: List<VideoCell>): VideoCell? =
        cells.firstOrNull { it.size == Size(1920, 1080) && it.fps == 30 }
            ?: cells.filter { it.fps == 30 && it.size.pixels <= 1920L * 1080 }.maxByOrNull { it.size.pixels }
            ?: cells.minByOrNull { abs(it.size.pixels - 1920L * 1080) + abs(it.fps - 30) * 10_000L }

    /** Preferred codec: HEVC when a hardware encoder does it, else H.264, else whatever there is. */
    fun defaultCodec(cell: VideoCell): Codec =
        listOf(Codec.HEVC, Codec.H264, Codec.AV1).firstOrNull { it in cell.codecs } ?: cell.codecs.first()
}

enum class BitrateLevel(val label: String) { STANDARD("Standard"), HIGH("High"), MAX("Maximum"), CUSTOM("Custom") }

object Bitrates {
    /**
     * Bits per pixel per frame at the Standard level. Generous by phone
     * standards (4K30: ~55 Mb/s H.264, ~35 Mb/s HEVC) because compression is
     * the one processing step video can't avoid, so Quartz keeps it light.
     */
    private fun bpp(codec: Codec) = when (codec) {
        Codec.H264 -> 0.22
        Codec.HEVC -> 0.14
        Codec.AV1 -> 0.11
    }

    fun standard(size: Size, fps: Int, codec: Codec): Int {
        // Above 30 fps each extra frame changes less, so scale with the square root.
        val effectiveFps = if (fps <= 30) fps.toDouble() else 30 * kotlin.math.sqrt(fps / 30.0)
        return (size.pixels * effectiveFps * bpp(codec)).toInt().coerceAtLeast(2_000_000)
    }

    fun resolve(level: BitrateLevel, customMbps: Float, size: Size, fps: Int, codec: Codec, encoderMax: Int): Int {
        val max = if (encoderMax > 0) encoderMax else Int.MAX_VALUE
        val bps = when (level) {
            BitrateLevel.STANDARD -> standard(size, fps, codec)
            BitrateLevel.HIGH -> (standard(size, fps, codec) * 1.6).toInt()
            BitrateLevel.MAX -> max
            BitrateLevel.CUSTOM -> (customMbps * 1_000_000).toInt()
        }
        return bps.coerceIn(500_000, max)
    }
}
