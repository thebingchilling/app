package app.quartz.core.settings

import app.quartz.core.caps.C2
import app.quartz.core.caps.CameraInfo
import app.quartz.core.caps.EncoderInfo
import app.quartz.core.caps.Outputs
import app.quartz.core.caps.Size
import app.quartz.core.caps.options.Aspect
import app.quartz.core.caps.options.Bitrates
import app.quartz.core.caps.options.Codec
import app.quartz.core.caps.options.PhotoOptions
import app.quartz.core.caps.options.VideoCell
import app.quartz.core.caps.options.VideoOptions

data class ResolvedPhoto(
    val size: Size,
    val fullSensor: Boolean,
    val format: PhotoFormat,
    /** The saved choice wasn't possible on this camera and was replaced. */
    val adapted: Boolean,
)

data class ResolvedVideo(
    val cell: VideoCell,
    val codec: Codec,
    val dynamicRange: DynamicRange,
    val bitrate: Int,
    val adapted: Boolean,
)

/**
 * Turns saved per-lens settings into something the given camera can really do
 * (plan §7.4). Pure, so it is unit tested against device fixtures.
 */
object Resolver {

    fun photo(cam: CameraInfo, cfg: PhotoConfig, aspect: Aspect): ResolvedPhoto? {
        val format = when {
            cfg.format.raw && !cam.raw -> PhotoFormat.JPEG
            cfg.format == PhotoFormat.HEIC && !cam.heic -> PhotoFormat.JPEG
            else -> cfg.format
        }
        val key = if (format == PhotoFormat.HEIC) Outputs.HEIC else Outputs.JPEG
        val sizes = PhotoOptions.sizes(cam, key)
        if (sizes.isEmpty()) return null
        val wanted = Size(cfg.width, cfg.height)
        val exact = sizes.firstOrNull { it.size == wanted && it.fullSensor == cfg.fullSensor && aspect.matches(it.size) }
        if (exact != null) return ResolvedPhoto(exact.size, exact.fullSensor, format, format != cfg.format)
        val fallback = PhotoOptions.default(cam, aspect) ?: sizes.first()
        val chosen = cfg.width > 0
        return ResolvedPhoto(fallback.size, false, format, chosen || format != cfg.format)
    }

    fun video(cam: CameraInfo, encoders: List<EncoderInfo>, cfg: VideoConfig): ResolvedVideo? {
        val cells = VideoOptions.cells(cam, encoders)
        if (cells.isEmpty()) return null
        val wanted = Size(cfg.width, cfg.height)
        val cell = if (cfg.width > 0) VideoOptions.nearest(cells, wanted, cfg.fps) else VideoOptions.default(cells)
        cell ?: return null
        val adapted = cfg.width > 0 && (cell.size != wanted || cell.fps != cfg.fps)
        val wantsTenBit = cfg.dynamicRange != DynamicRange.SDR
        val tenBitOk = wantsTenBit && cell.tenBitCodecs.isNotEmpty() &&
            (cfg.dynamicRange != DynamicRange.HDR10 || C2.DR_HDR10 in cam.dynamicRangeProfiles)
        val pool = if (tenBitOk) cell.tenBitCodecs else cell.codecs
        val codec = cfg.codec?.takeIf { it in pool } ?: if (tenBitOk) pool.first() else VideoOptions.defaultCodec(cell)
        val encoderMax = encoders.filter { it.mime == codec.mime }.maxOfOrNull { it.maxBitrate } ?: 0
        return ResolvedVideo(
            cell = cell,
            codec = codec,
            dynamicRange = if (tenBitOk) cfg.dynamicRange else DynamicRange.SDR,
            bitrate = Bitrates.resolve(cfg.bitrate, cfg.customMbps, cell.size, cell.fps, codec, encoderMax),
            adapted = adapted || (wantsTenBit && !tenBitOk),
        )
    }

    fun slowMo(cam: CameraInfo, encoders: List<EncoderInfo>, cfg: SlowMoConfig): VideoCell? {
        val cells = VideoOptions.slowMotion(cam, encoders)
        if (cells.isEmpty()) return null
        return cells.firstOrNull { it.size == Size(cfg.width, cfg.height) && it.fps == cfg.fps }
            ?: cells.filter { it.fps == cfg.fps }.maxByOrNull { it.size.pixels }
            ?: cells.filter { it.size.w <= 1920 }.maxWithOrNull(compareBy<VideoCell> { it.size.pixels }.thenBy { it.fps })
            ?: cells.first()
    }

    /** Time-lapse uses normal video sizes; frame rate is the playback rate. */
    fun timelapse(cam: CameraInfo, encoders: List<EncoderInfo>, cfg: TimelapseConfig): VideoCell? {
        val cells = VideoOptions.cells(cam, encoders).filter { it.fps == 30 || it.fps == cfg.playbackFps }
        if (cells.isEmpty()) return null
        return cells.firstOrNull { it.size == Size(cfg.width, cfg.height) } ?: VideoOptions.default(cells)
    }
}
