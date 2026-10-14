@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.quartz.camera.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.quartz.core.caps.natural.NaturalReporter
import app.quartz.core.caps.natural.Verdict
import app.quartz.core.caps.options.PhotoOptions
import app.quartz.core.caps.options.VideoOptions
import app.quartz.core.settings.PhotoConfig
import app.quartz.core.settings.SlowMoConfig
import app.quartz.core.settings.VideoConfig
import app.quartz.core.ui.components.ChoiceChip
import app.quartz.core.ui.icons.SymbolIcon
import app.quartz.core.ui.icons.Symbols
import app.quartz.core.ui.theme.tabular
import kotlin.math.roundToInt

@Composable
fun SheetHost(vm: CameraViewModel) {
    val ui by vm.ui.collectAsState()
    val sheet = ui.sheet ?: return
    ModalBottomSheet(onDismissRequest = { vm.setSheet(null) }) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (sheet) {
                Sheet.VIDEO_GRID -> VideoGridSheet(vm)
                Sheet.PHOTO_SIZE -> PhotoSizeSheet(vm)
                Sheet.SLOW_MOTION -> SlowMotionSheet(vm)
                Sheet.NATURAL_REPORT -> NaturalReportSheet(vm)
                Sheet.LENS_INFO -> LensInfoSheet(vm)
            }
        }
    }
}

@Composable
private fun SheetTitle(text: String, subtitle: String? = null) {
    Column {
        Text(text, style = MaterialTheme.typography.headlineSmall)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Every resolution × frame rate the camera and an encoder both support (plan §7.3). */
@Composable
private fun VideoGridSheet(vm: CameraViewModel) {
    val sel by vm.selection.collectAsState()
    val caps by vm.caps.collectAsState()
    val settings by vm.settings.collectAsState()
    val selection = sel ?: return
    val c = caps ?: return
    val info = selection.spec.lensInfo.takeIf { selection.spec.physicalId != null } ?: selection.spec.info
    val cells = VideoOptions.cells(info, c.encoders)
    val video = selection.spec.video
    val key = selection.settingsKey
    val cfg = settings.video[key] ?: VideoConfig()
    SheetTitle("Resolution and frame rate", "Only what this camera and its encoders can really record")
    val ui by vm.ui.collectAsState()
    val fpsColumns = cells.map { it.fps }.distinct().sorted()
    cells.map { it.size }.distinct().filter { it !in ui.refusedVideoSizes || settings.showUnsupported }.forEach { size ->
        val refused = size in ui.refusedVideoSizes
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${VideoOptions.sizeLabel(size)}  ·  ${size.w}×${size.h}" + if (refused) "  ·  camera refuses this size" else "",
                style = MaterialTheme.typography.titleSmall.tabular,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fpsColumns.forEach { fps ->
                    val cell = cells.firstOrNull { it.size == size && it.fps == fps }
                    if (cell != null || settings.showUnsupported) {
                        ChoiceChip(
                            selected = video != null && video.size == size && video.fps == fps,
                            onClick = {
                                vm.updateSettings { it.copy(video = it.video + (key to cfg.copy(width = size.w, height = size.h, fps = fps))) }
                                vm.setSheet(null)
                            },
                            label = "$fps",
                            enabled = cell != null && !refused,
                            onScrim = false,
                        )
                    }
                }
            }
        }
    }
    if (cells.isEmpty()) Text("This camera has no recordable video sizes.")
}

@Composable
private fun PhotoSizeSheet(vm: CameraViewModel) {
    val sel by vm.selection.collectAsState()
    val settings by vm.settings.collectAsState()
    val selection = sel ?: return
    val info = selection.spec.lensInfo.takeIf { selection.spec.physicalId != null } ?: selection.spec.info
    val key = selection.settingsKey
    val cfg = settings.photo[key] ?: PhotoConfig()
    val still = selection.spec.still
    SheetTitle("Photo resolution", "Every size this lens outputs. Full sensor sizes are slower and can't burst.")
    PhotoOptions.sizes(info).groupBy { app.quartz.core.caps.options.Aspect.of(it.size) }.forEach { (aspect, sizes) ->
        Text(aspect.label, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sizes.forEach { ps ->
                ChoiceChip(
                    selected = still != null && still.size == ps.size && still.fullSensor == ps.fullSensor,
                    onClick = {
                        vm.updateSettings {
                            it.copy(
                                aspect = if (aspect == app.quartz.core.caps.options.Aspect.OTHER) it.aspect else aspect,
                                photo = it.photo + (key to cfg.copy(width = ps.size.w, height = ps.size.h, fullSensor = ps.fullSensor)),
                            )
                        }
                        vm.setSheet(null)
                    },
                    label = "${ps.label} · ${ps.size.w}×${ps.size.h}",
                    onScrim = false,
                )
            }
        }
    }
}

@Composable
private fun SlowMotionSheet(vm: CameraViewModel) {
    val sel by vm.selection.collectAsState()
    val caps by vm.caps.collectAsState()
    val settings by vm.settings.collectAsState()
    val selection = sel ?: return
    val c = caps ?: return
    val key = selection.settingsKey
    val cfg = settings.slowMo[key] ?: SlowMoConfig()
    val video = selection.spec.video
    SheetTitle("Slow motion", "High-speed modes this camera offers. No frames are invented.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VideoOptions.slowMotion(selection.spec.info, c.encoders).forEach { cell ->
            ChoiceChip(
                selected = video != null && video.size == cell.size && video.fps == cell.fps,
                onClick = {
                    vm.updateSettings { it.copy(slowMo = it.slowMo + (key to cfg.copy(width = cell.size.w, height = cell.size.h, fps = cell.fps))) }
                    vm.setSheet(null)
                },
                label = cell.label,
                onScrim = false,
            )
        }
    }
}

@Composable
fun NaturalReportSheet(vm: CameraViewModel) {
    val report by vm.naturalReport.collectAsState()
    val r = report ?: return
    SheetTitle("Natural report", r.summary)
    Text(
        "Quartz turns off every enhancement the camera allows: noise reduction, sharpening, " +
            "HDR, night modes, scene detection, beautification. It keeps only the corrections " +
            "that undo the lens and sensor's own flaws.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    r.items.forEach { item ->
        ListItem(
            headlineContent = { Text(item.stage) },
            supportingContent = item.note?.let { { Text(it) } },
            trailingContent = { Text(item.requested, style = MaterialTheme.typography.labelLarge) },
            leadingContent = {
                SymbolIcon(
                    when (item.verdict) {
                        Verdict.NATURAL -> Symbols.check
                        Verdict.LIGHT -> Symbols.info
                        Verdict.PROCESSED -> Symbols.warning
                    },
                    null,
                    Modifier.size(24.dp),
                    tint = if (item.verdict == Verdict.PROCESSED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            },
        )
    }
    if (r.rawAvailable) {
        Text("RAW (DNG) is available on this lens for output that skips the camera's processing entirely.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LensInfoSheet(vm: CameraViewModel) {
    val sel by vm.selection.collectAsState()
    val frame by vm.frame.collectAsState()
    val target by vm.liveTarget.collectAsState()
    val selection = sel ?: return
    val lens = (target ?: selection.target).lens
    SheetTitle(lens.description, "Camera ${selection.spec.cameraId}" + (lens.physicalId?.let { " · lens $it" } ?: ""))
    val rows = listOf(
        "Zoom" to Format.zoom(vm.zoom.value),
        "35 mm equivalent" to "${lens.eqMm.roundToInt()} mm",
        "Focal length" to "%.2f mm".format(frame.focalMm ?: lens.focalMm),
        "Aperture" to (frame.aperture?.let { "f/%.1f".format(it) } ?: "–"),
        "ISO" to (frame.iso?.toString() ?: "–"),
        "Shutter" to Format.shutter(frame.exposureNs),
        "Focus" to Format.focus(frame.focusDiopters),
        "Active lens" to (frame.activePhysicalId ?: selection.spec.cameraId),
        "Preview" to "${selection.spec.previewSize}",
        "Hardware level" to when (selection.spec.info.hardwareLevel) {
            0 -> "Limited"
            1 -> "Full"
            2 -> "Legacy"
            3 -> "Level 3"
            else -> "External"
        },
        "Android" to "API ${Build.VERSION.SDK_INT}",
    )
    Column {
        rows.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(k, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(v, style = MaterialTheme.typography.bodyLarge.tabular, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
        }
    }
    Spacer(Modifier.height(8.dp))
    NaturalReporter.build(selection.spec.lensInfo, vm.settings.value.natural, Build.VERSION.SDK_INT).let { r ->
        Text(r.summary, style = MaterialTheme.typography.titleSmall)
    }
}
