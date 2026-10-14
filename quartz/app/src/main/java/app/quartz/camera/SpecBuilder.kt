package app.quartz.camera

import app.quartz.core.camera.AudioSpec
import app.quartz.core.camera.SessionKind
import app.quartz.core.camera.SessionSpec
import app.quartz.core.camera.StillSpec
import app.quartz.core.camera.TimelapseSpec
import app.quartz.core.camera.VideoSpec
import app.quartz.core.caps.CameraInfo
import app.quartz.core.caps.DeviceCaps
import app.quartz.core.caps.FpsRange
import app.quartz.core.caps.Outputs
import app.quartz.core.caps.Size
import app.quartz.core.caps.lens.Lens
import app.quartz.core.caps.lens.LensSet
import app.quartz.core.caps.lens.ZoomMath
import app.quartz.core.caps.lens.ZoomTarget
import app.quartz.core.caps.options.VideoOptions
import app.quartz.core.settings.LensSwitchPolicy
import app.quartz.core.settings.Mode
import app.quartz.core.settings.PhotoConfig
import app.quartz.core.settings.PhotoFormat
import app.quartz.core.settings.QuartzSettings
import app.quartz.core.settings.Resolver
import app.quartz.core.settings.SlowMoConfig
import app.quartz.core.settings.TimelapseConfig
import app.quartz.core.settings.VideoConfig

/** What the camera screen is set up for: the session plus how it was chosen. */
data class Selection(
    val spec: SessionSpec,
    val target: ZoomTarget,
    val lensSet: LensSet,
    /** Per-lens settings key used for this session. */
    val settingsKey: String,
    /** Human-readable note when saved settings had to be adapted, e.g. "Telephoto: 4K · 30". */
    val adaptedNote: String?,
    /** The mode actually used (a mode the camera can't do falls back to Photo/Video). */
    val mode: Mode,
)

/** Builds the [SessionSpec] for the current settings, lens and zoom (plan §7). */
object SpecBuilder {

    fun modeSupported(mode: Mode, cam: CameraInfo, caps: DeviceCaps): Boolean = when (mode) {
        Mode.LONG_EXPOSURE -> cam.manualSensor && (cam.exposureRangeNs?.upper ?: 0) >= 250_000_000L
        Mode.SLOW_MOTION -> VideoOptions.slowMotion(cam, caps.encoders).isNotEmpty()
        else -> true
    }

    fun build(caps: DeviceCaps, s: QuartzSettings, set: LensSet, zoom: Float): Selection? {
        val locked = s.lockedLens?.let { set.lensForKey(it) }
        var target = ZoomMath.target(set, zoom, s.maxZoom, locked)

        // "Keep settings, crop on current lens": on phones that switch lenses by
        // switching cameras, stay on a lens that can do the chosen video setting.
        if (s.mode == Mode.VIDEO && s.lensSwitch == LensSwitchPolicy.CROP && locked == null && !set.usesZoomRatio) {
            val wanted = s.video[target.lens.key] ?: s.video[set.main.key]
            if (wanted != null && wanted.width > 0) {
                val r = Resolver.video(target.lens.info, caps.encoders, wanted)
                if (r == null || r.adapted) {
                    val keep = set.lenses.filter { it.ratio <= target.lens.ratio }.lastOrNull { l ->
                        Resolver.video(l.info, caps.encoders, wanted)?.adapted == false
                    }
                    if (keep != null) target = ZoomMath.target(set, zoom, s.maxZoom, keep)
                }
            }
        }

        val opened = caps.camera(target.cameraId) ?: return null
        // Physical-stream lens lock: sizes and processing come from the physical lens.
        val lensInfo = target.physicalId?.let { caps.camera(it) } ?: if (target.zoomRatio != null) opened else target.lens.info
        val outputsFrom = if (target.physicalId != null) lensInfo else opened
        val key = locked?.key ?: if (set.usesZoomRatio) set.autoKey else target.lens.key
        val mode = if (modeSupported(s.mode, outputsFrom, caps)) s.mode else if (s.mode.video) Mode.VIDEO else Mode.PHOTO
        val lensName = lensLabel(target.lens, set)

        return when (mode) {
            Mode.PHOTO, Mode.LONG_EXPOSURE, Mode.INTERVAL -> {
                val cfg = s.photo[key] ?: PhotoConfig()
                val rp = Resolver.photo(outputsFrom, cfg, s.aspect) ?: return null
                val nativeCrop = s.nativeCropZoom && (rp.format == PhotoFormat.JPEG || rp.format == PhotoFormat.RAW_JPEG) && !rp.fullSensor &&
                    outputsFrom.sizes(Outputs.YUV).isNotEmpty()
                val still = StillSpec(rp.size, rp.fullSensor, rp.format, nativeCrop)
                val preview = previewSize(outputsFrom, rp.size) ?: return null
                val fps = if (s.previewFps60) outputsFrom.aeFpsRanges.firstOrNull { it.fixed && it.upper == 60 } else null
                Selection(
                    SessionSpec(
                        cameraId = target.cameraId, physicalId = target.physicalId, info = opened, lensInfo = lensInfo,
                        kind = SessionKind.PHOTO, previewSize = preview, still = still,
                        analysis = s.histogram || s.peaking || s.zebra, natural = s.natural, previewFps = fps,
                    ),
                    target, set, key,
                    if (rp.adapted && cfg.width > 0) "$lensName: ${rp.size.megapixelsLabel()} ${rp.format.label}" else null,
                    mode,
                )
            }
            Mode.VIDEO -> {
                val cfg = s.video[key] ?: VideoConfig()
                val rv = Resolver.video(outputsFrom, caps.encoders, cfg) ?: return null
                val cell = rv.cell
                val video = VideoSpec(
                    size = cell.size, fps = cell.fps, codec = rv.codec, bitrate = rv.bitrate, dynamicRange = rv.dynamicRange,
                    audio = if (cfg.audio) AudioSpec(cfg.stereo, cfg.audioKbps * 1000, s.unprocessedAudio) else null,
                )
                val fps = if (s.allowLowLightFpsDrop) {
                    outputsFrom.aeFpsRanges.filter { it.upper == cell.fps && it.lower < cell.fps }.minByOrNull { it.lower } ?: FpsRange(cell.fps, cell.fps)
                } else {
                    FpsRange(cell.fps, cell.fps)
                }
                val preview = previewSize(outputsFrom, cell.size) ?: return null
                Selection(
                    SessionSpec(
                        cameraId = target.cameraId, physicalId = target.physicalId, info = opened, lensInfo = lensInfo,
                        kind = SessionKind.VIDEO, previewSize = preview, video = video, snapshot = true,
                        analysis = s.histogram || s.peaking || s.zebra, natural = s.natural, previewFps = fps,
                    ),
                    target, set, key,
                    if (rv.adapted) "$lensName: ${cell.label}" else null,
                    mode,
                )
            }
            Mode.SLOW_MOTION -> {
                val cfg = s.slowMo[key] ?: SlowMoConfig()
                val cell = Resolver.slowMo(outputsFrom, caps.encoders, cfg) ?: return null
                val bitrate = app.quartz.core.caps.options.Bitrates.standard(cell.size, 60, VideoOptions.defaultCodec(cell))
                val video = VideoSpec(cell.size, cell.fps, VideoOptions.defaultCodec(cell), bitrate, app.quartz.core.settings.DynamicRange.SDR, null, conform = cfg.conform)
                Selection(
                    SessionSpec(
                        cameraId = target.cameraId, physicalId = target.physicalId, info = opened, lensInfo = lensInfo,
                        kind = SessionKind.HIGH_SPEED, previewSize = cell.size, video = video, natural = s.natural,
                    ),
                    target, set, key, null, mode,
                )
            }
            Mode.TIMELAPSE -> {
                val cfg = s.timelapse[key] ?: TimelapseConfig()
                val cell = Resolver.timelapse(outputsFrom, caps.encoders, cfg) ?: return null
                val codec = VideoOptions.defaultCodec(cell)
                val bitrate = app.quartz.core.caps.options.Bitrates.standard(cell.size, cfg.playbackFps, codec)
                val video = VideoSpec(cell.size, cfg.playbackFps, codec, bitrate, app.quartz.core.settings.DynamicRange.SDR, null)
                val preview = previewSize(outputsFrom, cell.size) ?: return null
                val captureFps = outputsFrom.aeFpsRanges.firstOrNull { it.fixed && it.upper == 30 } ?: FpsRange(15, 30)
                Selection(
                    SessionSpec(
                        cameraId = target.cameraId, physicalId = target.physicalId, info = opened, lensInfo = lensInfo,
                        kind = SessionKind.TIMELAPSE, previewSize = preview, video = video,
                        timelapse = TimelapseSpec(cfg.intervalMs, cfg.playbackFps),
                        analysis = s.histogram || s.peaking || s.zebra, natural = s.natural, previewFps = captureFps,
                    ),
                    target, set, key, null, mode,
                )
            }
        }
    }

    /**
     * Viewfinder stream: the largest SurfaceView size with the capture's
     * aspect ratio, up to 1920×1440 (more is wasted on a phone screen).
     */
    fun previewSize(cam: CameraInfo, capture: Size): Size? {
        val all = cam.sizes(Outputs.PREVIEW).map { it.size }
        val max = 1920L * 1440
        return all.filter { it.sameAspect(capture) && it.pixels <= max }.maxByOrNull { it.pixels }
            ?: all.filter { it.pixels <= 1920L * 1080 }.maxByOrNull { it.pixels }
            ?: all.minByOrNull { it.pixels }
    }

    fun lensLabel(lens: Lens, set: LensSet): String = if (set.lenses.size <= 1) "Camera" else lens.name

    private fun Size.megapixelsLabel(): String =
        if (megapixels >= 10) "${megapixels.toInt()} MP" else "%.1f MP".format(megapixels)
}
