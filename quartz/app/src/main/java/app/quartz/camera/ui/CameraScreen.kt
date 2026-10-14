@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.quartz.camera.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.quartz.core.camera.EngineState
import app.quartz.core.camera.RecordingState
import app.quartz.core.caps.C2
import app.quartz.core.caps.lens.LensMap
import app.quartz.core.caps.natural.Verdict
import app.quartz.core.caps.options.PhotoOptions
import app.quartz.core.settings.FlashMode
import app.quartz.core.settings.Mode
import app.quartz.core.ui.components.ShutterButton
import app.quartz.core.ui.components.ShutterStyle
import app.quartz.core.ui.components.ScrimIconButton
import app.quartz.core.ui.components.animatedDeviceRotation
import app.quartz.core.ui.components.thinOutline
import app.quartz.core.ui.icons.MsIcon
import app.quartz.core.ui.icons.SymbolIcon
import app.quartz.core.ui.icons.Symbols
import app.quartz.core.ui.theme.ViewfinderColors
import app.quartz.core.ui.theme.tabular
import kotlinx.coroutines.delay
import kotlin.math.ln

@Composable
fun CameraScreen(vm: CameraViewModel, levelRoll: Float) {
    val settings by vm.settings.collectAsState()
    val ui by vm.ui.collectAsState()
    val selection by vm.selection.collectAsState()
    val engineState by vm.engineState.collectAsState()
    val recording by vm.recording.collectAsState()
    val zoom by vm.zoom.collectAsState()
    val pills by vm.pills.collectAsState()
    val target by vm.liveTarget.collectAsState()
    val analysis by vm.analysis.collectAsState()
    val rotation by vm.deviceRotation.collectAsState()
    val report by vm.naturalReport.collectAsState()
    val caps by vm.caps.collectAsState()
    val iconRotation = animatedDeviceRotation(rotation)
    val haptics = LocalHapticFeedback.current

    val spec = selection?.spec
    val mode = selection?.mode ?: settings.mode
    val busyRecording = recording.active || ui.intervalShots != null

    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        vm.notices.collect {
            notice = it
        }
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2800)
            notice = null
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            StatusRow(vm, iconRotation, recording, report?.verdict)
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Viewfinder(
                    previewSize = spec?.previewSize,
                    sensorOrientation = spec?.info?.sensorOrientation ?: 90,
                    onSurface = vm::onSurface,
                    onTap = { x, y -> vm.onTap(x, y) },
                    onLongPress = { x, y ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.onTap(x, y, lock = true)
                    },
                    onDoubleTap = { vm.nextPill() },
                    onZoom = vm::zoomBy,
                    onPinch = vm::setPinching,
                    onSwipe = { vm.swipeMode(it) },
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    AnalysisOverlay(analysis, settings.histogram)
                    GridOverlay(settings.grid)
                    if (settings.level) LevelOverlay(levelRoll, rotation)
                    ui.focus?.let { FocusRing(it) }
                    ShutterFlash(ui.shutterFlash)
                    ui.countdown?.let {
                        Text(
                            "$it",
                            style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Medium),
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center).rotate(iconRotation),
                        )
                    }
                    if (engineState is EngineState.Opening || spec == null) {
                        LoadingIndicator(Modifier.align(Alignment.Center), color = Color.White)
                    }
                    (engineState as? EngineState.Error)?.let { err ->
                        Text(
                            err.message,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        )
                    }
                }

                // Focus sliders, zoom, notices: lower part of the viewfinder.
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AnimatedVisibility(notice != null, enter = fadeIn(), exit = fadeOut()) {
                        Text(
                            notice.orEmpty(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .background(ViewfinderColors.ScrimStrong, RoundedCornerShape(50))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                    val focus = ui.focus
                    AnimatedVisibility(focus != null && focus.sliders && ui.panel == null) {
                        FocusSliders(vm)
                    }
                    if (ui.pinching) {
                        ZoomRuler(vm, zoom, target?.digital == true)
                    } else {
                        ZoomPills(pills, zoom, iconRotation) { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); vm.setZoom(it) }
                    }
                }

                // Settings panel slides up over the lower viewfinder.
                PanelHost(vm, ui.panel != null, Modifier.align(Alignment.BottomCenter))
            }

            ModeCarousel(
                modes = if (mode.video) Mode.videoModes else Mode.photoModes,
                selected = mode,
                enabled = { m -> caps == null || spec == null || app.quartz.camera.SpecBuilder.modeSupported(m, spec.info, caps!!) },
                onSelect = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); vm.setMode(it) },
                locked = busyRecording,
            )
            ShutterRow(vm, mode, recording, iconRotation)
            BottomBar(vm, mode, iconRotation, busyRecording)
        }
    }

    SheetHost(vm)
}

/** Kept outside the Column so AnimatedVisibility isn't resolved against ColumnScope. */
@Composable
private fun PanelHost(vm: CameraViewModel, visible: Boolean, modifier: Modifier) {
    AnimatedVisibility(
        visible,
        modifier = modifier,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        SettingsPanel(vm)
    }
}

@Composable
private fun StatusRow(vm: CameraViewModel, iconRotation: Float, recording: RecordingState, verdict: Verdict?) {
    val settings by vm.settings.collectAsState()
    val selection by vm.selection.collectAsState()
    val mode = selection?.mode ?: settings.mode
    val spec = selection?.spec
    Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp)) {
        if (recording.active) {
            RecordingTimer(recording, Modifier.align(Alignment.Center))
            return@Box
        }
        Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!mode.video && spec?.info?.flash == true) {
                val (icon, next) = when (settings.flash) {
                    FlashMode.OFF -> Symbols.flashOff to FlashMode.AUTO
                    FlashMode.AUTO -> Symbols.flashAuto to FlashMode.ON
                    FlashMode.ON -> Symbols.flashOn to FlashMode.OFF
                }
                StatusIcon(icon, "Flash", iconRotation, settings.flash != FlashMode.OFF) { vm.updateSettings { it.copy(flash = next) } }
            }
            if (mode == Mode.PHOTO || mode == Mode.LONG_EXPOSURE) {
                val icon = when (settings.timerSeconds) {
                    3 -> Symbols.timer3
                    10 -> Symbols.timer10
                    else -> Symbols.timerOff
                }
                StatusIcon(icon, "Timer", iconRotation, settings.timerSeconds > 0) {
                    vm.updateSettings { it.copy(timerSeconds = if (it.timerSeconds == 0) 3 else if (it.timerSeconds == 3) 10 else 0) }
                }
            }
        }
        Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            val label = when {
                spec?.video != null && mode == Mode.TIMELAPSE ->
                    "${app.quartz.core.caps.options.VideoOptions.sizeLabel(spec.video!!.size)} · ${Format.interval(spec.timelapse?.intervalMs ?: 1000)}"
                spec?.video != null -> "${app.quartz.core.caps.options.VideoOptions.sizeLabel(spec.video!!.size)} · ${spec.video!!.fps}"
                spec?.still != null -> {
                    val st = spec.still!!
                    val mp = PhotoOptions.sizes(spec.info).firstOrNull { it.size == st.size }?.label ?: "${st.size}"
                    "$mp · ${st.format.label}"
                }
                else -> null
            }
            if (label != null) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge.tabular,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { vm.setSheet(if (mode == Mode.VIDEO) Sheet.VIDEO_GRID else if (mode == Mode.SLOW_MOTION) Sheet.SLOW_MOTION else Sheet.PHOTO_SIZE) }
                        .background(ViewfinderColors.Scrim)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            val natural = verdict ?: Verdict.NATURAL
            StatusIcon(
                if (natural == Verdict.NATURAL) Symbols.verified else Symbols.warning,
                "Natural report",
                iconRotation,
                natural == Verdict.NATURAL,
                tint = if (natural == Verdict.PROCESSED) ViewfinderColors.Warning else Color.White,
            ) { vm.setSheet(Sheet.NATURAL_REPORT) }
        }
    }
}

@Composable
private fun StatusIcon(icon: MsIcon, description: String, rotation: Float, on: Boolean, tint: Color = Color.White, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        SymbolIcon(icon, description, Modifier.size(24.dp).rotate(rotation), filled = on, tint = tint)
    }
}

@Composable
private fun RecordingTimer(rec: RecordingState, modifier: Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(rec.active) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val elapsed = (if (rec.paused) rec.pausedAtMs else now) - rec.startedAtMs - rec.pausedTotalMs
    Row(
        modifier.background(ViewfinderColors.Scrim, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).background(if (rec.paused) Color.White else ViewfinderColors.Record, CircleShape))
        Text(Format.duration(elapsed.coerceAtLeast(0)), style = MaterialTheme.typography.titleMedium.tabular, color = Color.White)
    }
}

@Composable
private fun ZoomPills(pills: List<Float>, zoom: Float, rotation: Float, onSelect: (Float) -> Unit) {
    if (pills.size <= 1) return
    Row(
        Modifier.background(ViewfinderColors.Scrim, RoundedCornerShape(50)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The active pill is the highest stop at or below the current zoom.
        val active = pills.lastOrNull { it <= zoom * 1.001f } ?: pills.first()
        pills.forEach { p ->
            val selected = p == active
            val size by animateDpAsState(if (selected) 44.dp else 38.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "pill")
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(if (selected) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                    .clickable { onSelect(p) },
                contentAlignment = Alignment.Center,
            ) {
                val text = if (selected) Format.zoom(zoom) else LensMap.ratioLabel(p)
                Text(
                    text,
                    style = (if (selected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium).tabular,
                    color = if (selected) ViewfinderColors.Warning else Color.White,
                    modifier = Modifier.rotate(rotation),
                )
            }
        }
    }
}

/** Shown while pinching: a log-scale ruler with a mark for every lens; amber past the longest lens. */
@Composable
private fun ZoomRuler(vm: CameraViewModel, zoom: Float, digital: Boolean) {
    val set by vm.lensSet.collectAsState()
    val settings by vm.settings.collectAsState()
    val lenses = set ?: return
    val lo = lenses.minZoom()
    val hi = maxOf(lenses.hardwareMaxZoom().coerceAtMost(settings.maxZoom.coerceAtLeast(lenses.longest.ratio)), lo * 1.01f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            Format.zoom(zoom),
            style = MaterialTheme.typography.titleLarge.tabular,
            color = if (digital) ViewfinderColors.Warning else Color.White,
            modifier = Modifier.background(ViewfinderColors.Scrim, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 4.dp),
        )
        Canvas(Modifier.fillMaxWidth(0.8f).height(28.dp)) {
            fun x(r: Float) = (ln(r / lo) / ln(hi / lo)).toFloat().coerceIn(0f, 1f) * size.width
            drawLine(Color.White.copy(alpha = 0.5f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
            lenses.lenses.forEach { l ->
                drawLine(Color.White, Offset(x(l.ratio), 4.dp.toPx()), Offset(x(l.ratio), size.height - 4.dp.toPx()), 2.dp.toPx())
            }
            drawCircle(if (digital) ViewfinderColors.Warning else Color.White, 6.dp.toPx(), Offset(x(zoom), size.height / 2))
        }
    }
}

@Composable
private fun FocusSliders(vm: CameraViewModel) {
    val manual by vm.manual.collectAsState()
    val selection by vm.selection.collectAsState()
    val info = selection?.spec?.info ?: return
    Column(
        Modifier
            .padding(horizontal = 32.dp, vertical = 4.dp)
            .background(ViewfinderColors.Scrim, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        val range = info.aeCompRange
        if (range.upper > range.lower) {
            SliderRow(Symbols.exposure, "Brightness") {
                Slider(
                    value = manual.evIndex.toFloat(),
                    onValueChange = { v -> vm.setManual { it.copy(evIndex = v.toInt()) }; vm.touchFocusTimer() },
                    valueRange = range.lower.toFloat()..range.upper.toFloat(),
                    steps = (range.upper - range.lower - 1).coerceAtLeast(0),
                    colors = scrimSliderColors(),
                )
            }
        }
        if (info.manualPostProcessing && C2.AWB_OFF in info.awbModes) {
            SliderRow(Symbols.wbAuto, "White balance") {
                Slider(
                    value = manual.wbShift,
                    onValueChange = { v -> vm.setManual { it.copy(wbShift = v) }; vm.touchFocusTimer() },
                    valueRange = -1f..1f,
                    colors = scrimSliderColors(),
                )
            }
        }
    }
}

@Composable
fun SliderRow(icon: MsIcon, description: String, slider: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SymbolIcon(icon, description, Modifier.size(20.dp), tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) { slider() }
    }
}

@Composable
fun scrimSliderColors() = SliderDefaults.colors(
    thumbColor = Color.White,
    activeTrackColor = Color.White,
    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
)

@Composable
private fun ModeCarousel(modes: List<Mode>, selected: Mode, enabled: (Mode) -> Boolean, onSelect: (Mode) -> Unit, locked: Boolean) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(48.dp)) {
        val itemWidth = 120.dp
        val index = modes.indexOf(selected).coerceAtLeast(0)
        val offset by animateDpAsState(
            maxWidth / 2 - itemWidth * index - itemWidth / 2,
            MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "carousel",
        )
        Row(Modifier.offset(x = offset).align(Alignment.CenterStart)) {
            modes.forEach { m ->
                val isSel = m == selected
                val ok = enabled(m) && !locked
                Box(Modifier.width(itemWidth), contentAlignment = Alignment.Center) {
                    Text(
                        m.label,
                        style = if (isSel) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
                        color = when {
                            isSel -> Color.Black
                            ok -> Color.White.copy(alpha = 0.8f)
                            else -> Color.White.copy(alpha = 0.3f)
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isSel) Color.White else Color.Transparent)
                            .clickable(enabled = ok && !isSel) { onSelect(m) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShutterRow(vm: CameraViewModel, mode: Mode, recording: RecordingState, rotation: Float) {
    val ui by vm.ui.collectAsState()
    val settings by vm.settings.collectAsState()
    Row(
        Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left: last capture, or pause while recording.
        if (recording.active && mode == Mode.VIDEO) {
            ScrimIconButton(
                if (recording.paused) Symbols.playArrow else Symbols.pause,
                if (recording.paused) "Resume" else "Pause",
                vm::togglePause,
                rotation = rotation,
                size = 56.dp,
            )
        } else {
            Thumbnail(ui, rotation, enabled = !ui.secure && !recording.active) { vm.setScreen(Screen.REVIEW) }
        }

        val style = when {
            recording.active -> ShutterStyle.RECORDING
            ui.intervalShots != null -> ShutterStyle.RUNNING
            mode.video -> ShutterStyle.VIDEO
            else -> ShutterStyle.PHOTO
        }
        Box(contentAlignment = Alignment.Center) {
            ShutterButton(
                style = style,
                onClick = vm::onShutter,
                progress = ui.exposureProgress,
                enabled = !ui.busy || recording.active,
                onLongPressStart = if (mode == Mode.PHOTO && settings.longPress == app.quartz.core.settings.LongPressAction.BURST) vm::startBurst else null,
                onLongPressEnd = vm::stopBurst,
                contentDescription = when (style) {
                    ShutterStyle.PHOTO -> "Take photo"
                    ShutterStyle.VIDEO -> "Start recording"
                    ShutterStyle.RECORDING -> "Stop recording"
                    ShutterStyle.RUNNING -> "Stop"
                },
            )
            val count = ui.burstCount.takeIf { it > 0 } ?: ui.intervalShots?.takeIf { it > 0 }
            if (count != null) {
                Text("$count", style = MaterialTheme.typography.titleMedium.tabular, color = Color.Black)
            }
        }

        // Right: switch camera, or snapshot while recording.
        if (recording.active && mode == Mode.VIDEO) {
            ScrimIconButton(Symbols.photoCamera, "Take photo while recording", vm::snapshot, rotation = rotation, size = 56.dp)
        } else {
            ScrimIconButton(
                Symbols.cameraswitch, "Switch camera", vm::flipCamera, rotation = rotation, size = 56.dp,
                enabled = !recording.active && ui.intervalShots == null,
            )
        }
    }
}

@Composable
private fun Thumbnail(ui: UiState, rotation: Float, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled && ui.thumbnailUri != null,
        shape = CircleShape,
        color = ViewfinderColors.Scrim,
        modifier = Modifier.size(56.dp).thinOutline(),
    ) {
        val bmp = ui.thumbnail
        if (bmp != null) {
            Image(
                bmp.asImageBitmap(),
                contentDescription = "Last photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().rotate(rotation),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                SymbolIcon(Symbols.photoLibrary, "Gallery", Modifier.size(24.dp), tint = Color.White)
            }
        }
    }
}

@Composable
private fun BottomBar(vm: CameraViewModel, mode: Mode, rotation: Float, locked: Boolean) {
    val ui by vm.ui.collectAsState()
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp).padding(horizontal = 24.dp),
    ) {
        ScrimIconButton(
            Symbols.settings,
            "Camera settings",
            { vm.setPanel(if (ui.panel == null) Panel.GENERAL else null) },
            modifier = Modifier.align(Alignment.CenterStart),
            rotation = rotation,
            filled = ui.panel != null,
        )
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ToggleButton(
                checked = !mode.video,
                onCheckedChange = { if (!locked && mode.video) vm.setMode(Mode.PHOTO) },
                enabled = !locked || !mode.video,
            ) {
                SymbolIcon(Symbols.photoCamera, "Photo", Modifier.size(22.dp).rotate(rotation), filled = !mode.video)
            }
            ToggleButton(
                checked = mode.video,
                onCheckedChange = { if (!locked && !mode.video) vm.setMode(Mode.VIDEO) },
                enabled = !locked || mode.video,
            ) {
                SymbolIcon(Symbols.videocam, "Video", Modifier.size(22.dp).rotate(rotation), filled = mode.video)
            }
        }
        ScrimIconButton(
            Symbols.lens,
            "Lens info",
            { vm.setSheet(Sheet.LENS_INFO) },
            modifier = Modifier.align(Alignment.CenterEnd),
            rotation = rotation,
        )
    }
}
