@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package app.quartz.camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.quartz.core.caps.C2
import app.quartz.core.caps.Size
import app.quartz.core.caps.options.BitrateLevel
import app.quartz.core.caps.options.PhotoOptions
import app.quartz.core.caps.options.VideoOptions
import app.quartz.core.caps.options.WhiteBalance
import app.quartz.core.settings.DynamicRange
import app.quartz.core.settings.FlashMode
import app.quartz.core.settings.GridType
import app.quartz.core.settings.Mode
import app.quartz.core.settings.PhotoConfig
import app.quartz.core.settings.PhotoFormat
import app.quartz.core.settings.SlowMoConfig
import app.quartz.core.settings.TimelapseConfig
import app.quartz.core.settings.VideoConfig
import app.quartz.core.ui.components.ChoiceChip
import app.quartz.core.ui.icons.Symbols
import app.quartz.core.ui.theme.ViewfinderColors
import app.quartz.core.ui.theme.tabular
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

@Composable
fun SettingsPanel(vm: CameraViewModel) {
    val ui by vm.ui.collectAsState()
    val tab = ui.panel ?: Panel.GENERAL
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .background(ViewfinderColors.ScrimStrong, RoundedCornerShape(28.dp))
            .heightIn(max = 460.dp),
    ) {
        PrimaryTabRow(
            selectedTabIndex = tab.ordinal,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            indicator = { TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(tab.ordinal, matchContentSize = true), color = Color.White) },
            divider = {},
        ) {
            Tab(tab == Panel.GENERAL, onClick = { vm.setPanel(Panel.GENERAL) }, text = { Text("General") })
            Tab(tab == Panel.PRO, onClick = { vm.setPanel(Panel.PRO) }, text = { Text("Pro") })
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (tab) {
                Panel.GENERAL -> GeneralTab(vm)
                Panel.PRO -> ProTab(vm)
            }
            TextButton(onClick = { vm.setScreen(Screen.SETTINGS) }, modifier = Modifier.align(Alignment.End)) {
                Text("More settings", color = Color.White)
            }
        }
    }
}

@Composable
fun PanelRow(title: String, value: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
            if (value != null) Text(value, style = MaterialTheme.typography.labelLarge.tabular, color = Color.White)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun GeneralTab(vm: CameraViewModel) {
    val s by vm.settings.collectAsState()
    val sel by vm.selection.collectAsState()
    val caps by vm.caps.collectAsState()
    val selection = sel ?: return
    val info = selection.spec.lensInfo.takeIf { selection.spec.physicalId != null } ?: selection.spec.info
    val key = selection.settingsKey

    when (selection.mode) {
        Mode.PHOTO, Mode.LONG_EXPOSURE, Mode.INTERVAL -> {
            if (info.flash) {
                PanelRow("Flash") {
                    ChoiceChip(s.flash == FlashMode.OFF, { vm.updateSettings { it.copy(flash = FlashMode.OFF) } }, icon = Symbols.flashOff, contentDescription = "Flash off")
                    ChoiceChip(s.flash == FlashMode.AUTO, { vm.updateSettings { it.copy(flash = FlashMode.AUTO) } }, icon = Symbols.flashAuto, contentDescription = "Flash auto")
                    ChoiceChip(s.flash == FlashMode.ON, { vm.updateSettings { it.copy(flash = FlashMode.ON) } }, icon = Symbols.flashOn, contentDescription = "Flash on")
                }
            }
            if (selection.mode != Mode.INTERVAL) {
                PanelRow("Timer") {
                    listOf(0 to Symbols.timerOff, 3 to Symbols.timer3, 10 to Symbols.timer10).forEach { (sec, icon) ->
                        ChoiceChip(s.timerSeconds == sec, { vm.updateSettings { it.copy(timerSeconds = sec) } }, icon = icon, contentDescription = "Timer $sec seconds")
                    }
                }
            }
            val aspects = PhotoOptions.aspects(info)
            if (aspects.size > 1) {
                PanelRow("Ratio") {
                    aspects.forEach { a -> ChoiceChip(s.aspect == a, { vm.updateSettings { it.copy(aspect = a) } }, label = a.label) }
                }
            }
            val still = selection.spec.still
            val cfg = s.photo[key] ?: PhotoConfig()
            val sizes = PhotoOptions.sizes(info).filter { s.aspect.matches(it.size) }
            val shown = (sizes.filter { !it.fullSensor }.take(3) + sizes.filter { it.fullSensor }.take(1))
            if (shown.isNotEmpty()) {
                PanelRow("Resolution") {
                    shown.forEach { ps ->
                        ChoiceChip(
                            still != null && still.size == ps.size && still.fullSensor == ps.fullSensor,
                            { vm.updateSettings { it.copy(photo = it.photo + (key to cfg.copy(width = ps.size.w, height = ps.size.h, fullSensor = ps.fullSensor))) } },
                            label = ps.label.replace(" (full sensor)", " full"),
                        )
                    }
                    if (sizes.size > shown.size) ChoiceChip(false, { vm.setSheet(Sheet.PHOTO_SIZE) }, label = "All…")
                }
            }
            val formats = buildList {
                add(PhotoFormat.JPEG)
                if (info.raw) {
                    add(PhotoFormat.RAW_JPEG)
                    add(PhotoFormat.RAW)
                }
                if (info.heic) add(PhotoFormat.HEIC)
            }
            if (formats.size > 1) {
                PanelRow("Format") {
                    formats.forEach { f ->
                        ChoiceChip(still?.format == f, { vm.updateSettings { it.copy(photo = it.photo + (key to cfg.copy(format = f))) } }, label = f.label)
                    }
                }
            }
            if (selection.mode == Mode.LONG_EXPOSURE) {
                val range = info.exposureRangeNs
                val choices = listOf(500_000_000L, 1_000_000_000L, 2_000_000_000L, 4_000_000_000L, 8_000_000_000L, 15_000_000_000L, 30_000_000_000L)
                    .filter { range == null || it <= range.upper }
                PanelRow("Shutter") {
                    choices.forEach { ns ->
                        ChoiceChip(s.longExposure.exposureNs == ns, { vm.updateSettings { it.copy(longExposure = it.longExposure.copy(exposureNs = ns)) } }, label = Format.shutter(ns))
                    }
                }
                val iso = info.isoRange
                PanelRow("ISO") {
                    ChoiceChip(s.longExposure.iso == 0, { vm.updateSettings { it.copy(longExposure = it.longExposure.copy(iso = 0)) } }, label = "Lowest")
                    listOf(100, 200, 400, 800, 1600).filter { iso == null || it in iso.lower..iso.upper }.forEach { v ->
                        ChoiceChip(s.longExposure.iso == v, { vm.updateSettings { it.copy(longExposure = it.longExposure.copy(iso = v)) } }, label = "$v")
                    }
                }
            }
            if (selection.mode == Mode.INTERVAL) {
                PanelRow("Every") {
                    listOf(2, 5, 10, 30, 60).forEach { sec ->
                        ChoiceChip(s.interval.seconds == sec, { vm.updateSettings { it.copy(interval = it.interval.copy(seconds = sec)) } }, label = "${sec}s")
                    }
                }
                PanelRow("Shots") {
                    listOf(0, 10, 50, 100, 500).forEach { n ->
                        ChoiceChip(s.interval.count == n, { vm.updateSettings { it.copy(interval = it.interval.copy(count = n)) } }, label = if (n == 0) "∞" else "$n")
                    }
                }
            }
            PanelRow("Grid") {
                GridType.entries.forEach { g -> ChoiceChip(s.grid == g, { vm.updateSettings { it.copy(grid = g) } }, label = g.label) }
            }
            PanelRow("Level") {
                ChoiceChip(s.level, { vm.updateSettings { it.copy(level = !it.level) } }, icon = Symbols.straighten, contentDescription = "Level")
            }
        }
        Mode.VIDEO -> {
            val c = caps ?: return
            val cfg = s.video[key] ?: VideoConfig()
            val video = selection.spec.video ?: return
            val cells = VideoOptions.cells(info, c.encoders)
            val cell = cells.firstOrNull { it.size == video.size && it.fps == video.fps }
            PanelRow("Resolution · frame rate", "${VideoOptions.sizeLabel(video.size)} · ${video.fps}") {
                cells.filter { it.fps == video.fps }.map { it.size }.distinct().take(4).forEach { size ->
                    ChoiceChip(size == video.size, { vm.updateSettings { it.copy(video = it.video + (key to cfg.copy(width = size.w, height = size.h, fps = video.fps))) } }, label = VideoOptions.sizeLabel(size))
                }
                cells.filter { it.size == video.size }.map { it.fps }.forEach { fps ->
                    ChoiceChip(fps == video.fps, { vm.updateSettings { it.copy(video = it.video + (key to cfg.copy(width = video.size.w, height = video.size.h, fps = fps))) } }, label = "$fps")
                }
                ChoiceChip(false, { vm.setSheet(Sheet.VIDEO_GRID) }, label = "All…")
            }
            val pool = if (video.dynamicRange != DynamicRange.SDR) cell?.tenBitCodecs else cell?.codecs
            if ((pool?.size ?: 0) > 1) {
                PanelRow("Codec") {
                    pool!!.forEach { codec ->
                        ChoiceChip(video.codec == codec, { vm.updateSettings { it.copy(video = it.video + (key to cfg.copy(codec = codec))) } }, label = codec.label)
                    }
                }
            }
            if (cell?.tenBitCodecs?.isNotEmpty() == true) {
                PanelRow("Dynamic range") {
                    DynamicRange.entries.filter { it != DynamicRange.HDR10 || C2.DR_HDR10 in info.dynamicRangeProfiles }.forEach { dr ->
                        ChoiceChip(video.dynamicRange == dr, { vm.updateSettings { it.copy(video = it.video + (key to cfg.copy(dynamicRange = dr))) } }, label = dr.label)
                    }
                }
            }
            PanelRow("Bitrate", "%.0f Mb/s".format(video.bitrate / 1e6)) {
                listOf(BitrateLevel.STANDARD, BitrateLevel.HIGH, BitrateLevel.MAX).forEach { b ->
                    ChoiceChip(cfg.bitrate == b, { vm.updateSettings { it.copy(video = it.video + (key to cfg.copy(bitrate = b))) } }, label = b.label)
                }
            }
            if (C2.VSTAB_ON in info.videoStabModes) {
                PanelRow("Electronic stabilisation (crops and warps frames)") {
                    ChoiceChip(!s.natural.videoStabilization, { vm.updateSettings { it.copy(natural = it.natural.copy(videoStabilization = false)) } }, label = "Off")
                    ChoiceChip(s.natural.videoStabilization, { vm.updateSettings { it.copy(natural = it.natural.copy(videoStabilization = true)) } }, label = "On")
                }
            }
            PanelRow("Sound") {
                ChoiceChip(!cfg.audio, { vm.updateSettings { it.copy(video = it.video + (key to cfg.copy(audio = false))) } }, icon = Symbols.micOff, contentDescription = "No sound")
                ChoiceChip(cfg.audio && !cfg.stereo, { vm.updateSettings { it.copy(video = it.video + (key to cfg.copy(audio = true, stereo = false))) } }, label = "Mono")
                ChoiceChip(cfg.audio && cfg.stereo, { vm.updateSettings { it.copy(video = it.video + (key to cfg.copy(audio = true, stereo = true))) } }, label = "Stereo")
            }
            if (info.flash) {
                val manual by vm.manual.collectAsState()
                PanelRow("Torch") {
                    ChoiceChip(manual.torch, { vm.setManual { it.copy(torch = !it.torch) } }, icon = Symbols.flashOn, contentDescription = "Torch")
                }
            }
            PanelRow("Grid") {
                GridType.entries.forEach { g -> ChoiceChip(s.grid == g, { vm.updateSettings { it.copy(grid = g) } }, label = g.label) }
            }
        }
        Mode.SLOW_MOTION -> {
            val c = caps ?: return
            val cfg = s.slowMo[key] ?: SlowMoConfig()
            val video = selection.spec.video ?: return
            val cells = VideoOptions.slowMotion(info, c.encoders)
            PanelRow("Resolution · frame rate", "${VideoOptions.sizeLabel(video.size)} · ${video.fps}") {
                cells.forEach { cell ->
                    ChoiceChip(cell.size == video.size && cell.fps == video.fps, {
                        vm.updateSettings { it.copy(slowMo = it.slowMo + (key to cfg.copy(width = cell.size.w, height = cell.size.h, fps = cell.fps))) }
                    }, label = cell.label)
                }
            }
            PanelRow("Playback") {
                ChoiceChip(cfg.conform, { vm.updateSettings { it.copy(slowMo = it.slowMo + (key to cfg.copy(conform = true))) } }, label = "Slowed to 30 fps")
                ChoiceChip(!cfg.conform, { vm.updateSettings { it.copy(slowMo = it.slowMo + (key to cfg.copy(conform = false))) } }, label = "Real time, ${video.fps} fps")
            }
        }
        Mode.TIMELAPSE -> {
            val c = caps ?: return
            val cfg = s.timelapse[key] ?: TimelapseConfig()
            val video = selection.spec.video ?: return
            val sizes = VideoOptions.cells(info, c.encoders).filter { it.fps == 30 }.map { it.size }.distinct()
            PanelRow("Resolution") {
                sizes.take(5).forEach { size: Size ->
                    ChoiceChip(size == video.size, { vm.updateSettings { it.copy(timelapse = it.timelapse + (key to cfg.copy(width = size.w, height = size.h))) } }, label = VideoOptions.sizeLabel(size))
                }
            }
            PanelRow("One frame every") {
                listOf(500, 1000, 2000, 5000, 10000, 30000).forEach { ms ->
                    ChoiceChip(cfg.intervalMs == ms, { vm.updateSettings { it.copy(timelapse = it.timelapse + (key to cfg.copy(intervalMs = ms))) } }, label = Format.interval(ms))
                }
            }
            PanelRow("Playback") {
                listOf(24, 30, 60).forEach { fps ->
                    ChoiceChip(cfg.playbackFps == fps, { vm.updateSettings { it.copy(timelapse = it.timelapse + (key to cfg.copy(playbackFps = fps))) } }, label = "$fps fps")
                }
            }
        }
    }
}

@Composable
private fun ProTab(vm: CameraViewModel) {
    val s by vm.settings.collectAsState()
    val sel by vm.selection.collectAsState()
    val manual by vm.manual.collectAsState()
    val frame by vm.frame.collectAsState()
    val selection = sel ?: return
    val info = selection.spec.lensInfo
    val set = selection.lensSet

    if (set.lenses.size > 1) {
        PanelRow("Lens") {
            ChoiceChip(s.lockedLens == null, { vm.updateSettings { it.copy(lockedLens = null) } }, label = "Auto")
            set.lenses.forEach { lens ->
                ChoiceChip(s.lockedLens == lens.key, {
                    vm.updateSettings { it.copy(lockedLens = lens.key) }
                    vm.setZoom(lens.ratio)
                }, label = "${lens.name} ${lens.eqMm.roundToInt()}mm")
            }
        }
    }

    if (selection.mode == Mode.SLOW_MOTION) {
        Text("Slow motion runs on automatic exposure and focus.", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
        return
    }

    if (info.manualSensor) {
        val iso = info.isoRange
        if (iso != null) {
            PanelRow("ISO", if (manual.iso == null) "Auto · ${frame.iso ?: "–"}" else "${manual.iso}") {
                ChoiceChip(manual.iso == null, { vm.setManual { it.copy(iso = null) } }, label = "Auto")
            }
            LogSlider(manual.iso?.toFloat() ?: (frame.iso ?: iso.lower).toFloat(), iso.lower.toFloat(), iso.upper.toFloat()) { v ->
                vm.setManual { it.copy(iso = v.roundToInt()) }
            }
        }
        val exp = info.exposureRangeNs
        if (exp != null) {
            val maxNs = if (selection.mode.video) {
                minOf(exp.upper, 1_000_000_000L / (selection.spec.video?.fps ?: 30))
            } else {
                exp.upper
            }
            PanelRow("Shutter", if (manual.exposureNs == null) "Auto · ${Format.shutter(frame.exposureNs)}" else Format.shutter(manual.exposureNs)) {
                ChoiceChip(manual.exposureNs == null, { vm.setManual { it.copy(exposureNs = null) } }, label = "Auto")
            }
            LogSlider((manual.exposureNs ?: frame.exposureNs ?: 10_000_000L).toFloat(), exp.lower.toFloat(), maxNs.toFloat()) { v ->
                vm.setManual { it.copy(exposureNs = v.toLong()) }
            }
            if ((manual.iso == null) != (manual.exposureNs == null)) {
                Text(
                    if (manual.iso != null) "ISO priority: shutter follows the light" else "Shutter priority: ISO follows the light",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    if (info.manualFocus) {
        PanelRow("Focus", if (manual.focusDiopters == null) "Auto" else Format.focus(manual.focusDiopters)) {
            ChoiceChip(manual.focusDiopters == null, { vm.setManual { it.copy(focusDiopters = null) } }, label = "Auto")
        }
        Slider(
            value = manual.focusDiopters ?: (frame.focusDiopters ?: 0f),
            onValueChange = { v -> vm.setManual { it.copy(focusDiopters = v) } },
            valueRange = 0f..info.minFocusDistance,
            colors = scrimSliderColors(),
        )
    }

    PanelRow("White balance", manual.kelvin?.let { "$it K" }) {
        val presets = listOf(
            C2.AWB_AUTO to Symbols.wbAuto,
            C2.AWB_INCANDESCENT to Symbols.wbIncandescent,
            C2.AWB_FLUORESCENT to Symbols.wbIridescent,
            C2.AWB_DAYLIGHT to Symbols.wbSunny,
            C2.AWB_CLOUDY to Symbols.filterDrama,
            C2.AWB_SHADE to Symbols.wbShade,
        ).filter { it.first in info.awbModes }
        presets.forEach { (mode, icon) ->
            ChoiceChip(
                manual.awbMode == mode && manual.kelvin == null,
                { vm.setManual { it.copy(awbMode = mode, kelvin = null, wbShift = 0f) } },
                icon = icon,
                contentDescription = "White balance preset",
            )
        }
    }
    if (android.os.Build.VERSION.SDK_INT >= 36 && C2.CC_CCT in info.colorCorrectionModes) {
        Slider(
            value = (manual.kelvin ?: 5500).toFloat(),
            onValueChange = { v -> vm.setManual { it.copy(kelvin = (v / 50).roundToInt() * 50) } },
            valueRange = WhiteBalance.MIN_K.toFloat()..WhiteBalance.MAX_K.toFloat(),
            colors = scrimSliderColors(),
        )
    }

    val ev = info.aeCompRange
    if (ev.upper > ev.lower) {
        PanelRow("Exposure compensation", Format.ev(manual.evIndex, info.aeCompStep)) {}
        Slider(
            value = manual.evIndex.toFloat(),
            onValueChange = { v -> vm.setManual { it.copy(evIndex = v.roundToInt()) } },
            valueRange = ev.lower.toFloat()..ev.upper.toFloat(),
            steps = (ev.upper - ev.lower - 1).coerceAtLeast(0),
            colors = scrimSliderColors(),
        )
    }

    PanelRow("Lock") {
        ChoiceChip(manual.aeLock, { vm.setManual { it.copy(aeLock = !it.aeLock) } }, label = "AE")
        ChoiceChip(manual.awbLock, { vm.setManual { it.copy(awbLock = !it.awbLock) } }, label = "AWB")
    }

    PanelRow("Viewfinder aids (never saved)") {
        ChoiceChip(s.histogram, { vm.updateSettings { it.copy(histogram = !it.histogram) } }, icon = Symbols.barChart, label = "Histogram")
        ChoiceChip(s.peaking, { vm.updateSettings { it.copy(peaking = !it.peaking) } }, icon = Symbols.centerFocusStrong, label = "Peaking")
        ChoiceChip(s.zebra, { vm.updateSettings { it.copy(zebra = !it.zebra) } }, icon = Symbols.gradient, label = "Zebras")
    }

    if (manual.any || manual.evIndex != 0) {
        TextButton(onClick = vm::resetManual) { Text("Reset to auto", color = Color.White) }
    }
}

/** Slider on a logarithmic scale (ISO and shutter speeds span several decades). */
@Composable
private fun LogSlider(value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    if (max <= min || min <= 0f) return
    val lo = ln(min)
    val hi = ln(max)
    Slider(
        value = ((ln(value.coerceIn(min, max)) - lo) / (hi - lo)),
        onValueChange = { t -> onChange(exp(lo + t * (hi - lo))) },
        colors = scrimSliderColors(),
    )
}

