@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package app.quartz.camera.ui

import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.quartz.camera.BuildConfig
import app.quartz.core.caps.C2
import app.quartz.core.caps.lens.LensMap
import app.quartz.core.caps.natural.NaturalReporter
import app.quartz.core.caps.natural.Verdict
import app.quartz.core.settings.GridType
import app.quartz.core.settings.LensSwitchPolicy
import app.quartz.core.settings.LongPressAction
import app.quartz.core.settings.QuartzSettings
import app.quartz.core.settings.VolumeKeyAction
import app.quartz.core.ui.components.ChoiceChip
import app.quartz.core.ui.icons.SymbolIcon
import app.quartz.core.ui.icons.Symbols
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: CameraViewModel, onLocationEnabled: () -> Unit) {
    val s by vm.settings.collectAsState()
    val caps by vm.caps.collectAsState()
    val lensSet by vm.lensSet.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    BackHandler { vm.setScreen(Screen.CAMERA) }
    fun set(transform: (QuartzSettings) -> QuartzSettings) = vm.updateSettings(transform)

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { vm.setScreen(Screen.CAMERA) }) {
                        Icon(painterResource(Symbols.arrowBack.outlined), "Back")
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Group("Natural") {
                    SwitchRow("Lens corrections", "Shading, colour fringing and distortion. These undo the lens's flaws; turning them off shows the raw optics.", s.natural.lensCorrections) {
                        set { st -> st.copy(natural = st.natural.copy(lensCorrections = it)) }
                    }
                    SwitchRow("Hot pixel correction", "Hides stuck pixels. Off keeps every pixel as the sensor read it.", s.natural.hotPixelCorrection) {
                        set { st -> st.copy(natural = st.natural.copy(hotPixelCorrection = it)) }
                    }
                    SwitchRow("Electronic video stabilisation", "Crops and warps frames to smooth shake. Optical stabilisation is always on where the lens has it.", s.natural.videoStabilization) {
                        set { st -> st.copy(natural = st.natural.copy(videoStabilization = it)) }
                    }
                }
            }
            item {
                Group("Natural report") {
                    val c = caps
                    if (c == null) {
                        Row("Reading camera capabilities…")
                    } else {
                        c.cameras.filter { it.usableForPhotos && (it.openable || it.logicalParent != null) }.forEach { cam ->
                            val r = NaturalReporter.build(cam, s.natural, Build.VERSION.SDK_INT)
                            val eq = LensMap.eqFocal(cam)?.roundToInt()
                            val facing = if (cam.facing == C2.FACING_FRONT) "Front" else "Back"
                            ListItem(
                                headlineContent = { Text("$facing camera ${cam.id}" + (eq?.let { " · $it mm" } ?: "")) },
                                supportingContent = { Text(r.summary + r.items.filter { it.verdict != Verdict.NATURAL }.joinToString("") { "\n• ${it.stage}: ${it.note ?: it.requested}" }) },
                                leadingContent = {
                                    SymbolIcon(
                                        if (r.verdict == Verdict.NATURAL) Symbols.verified else Symbols.warning,
                                        null,
                                        Modifier.size(24.dp),
                                        filled = r.verdict == Verdict.NATURAL,
                                        tint = if (r.verdict == Verdict.PROCESSED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            )
                        }
                    }
                }
            }
            item {
                Group("Photo") {
                    Row("JPEG quality", "${s.jpegQuality}") {
                        Slider(s.jpegQuality.toFloat(), { v -> set { it.copy(jpegQuality = v.roundToInt()) } }, valueRange = 50f..100f, steps = 49)
                    }
                    SwitchRow("Zoomed photos at true resolution", "Crop instead of upscaling: a 3× crop of a 12 MP photo is saved as 1.3 MP, with no interpolated pixels.", s.nativeCropZoom) {
                        set { st -> st.copy(nativeCropZoom = it) }
                    }
                    ChoiceRow("Hold the shutter", LongPressAction.entries.map { it.label }, s.longPress.ordinal) { i -> set { it.copy(longPress = LongPressAction.entries[i]) } }
                }
            }
            item {
                Group("Video") {
                    SwitchRow("Unprocessed microphone", "No automatic gain, noise suppression or wind filter, where the phone supports it" + if (caps?.audioUnprocessed == false) " (this phone doesn't, so the camcorder microphone is used)" else "", s.unprocessedAudio) {
                        set { st -> st.copy(unprocessedAudio = it) }
                    }
                    SwitchRow("Allow lower frame rate in low light", "Lets the camera drop below the chosen frame rate to expose longer. Off keeps the frame rate fixed.", s.allowLowLightFpsDrop) {
                        set { st -> st.copy(allowLowLightFpsDrop = it) }
                    }
                }
            }
            item {
                Group("Zoom and lenses") {
                    Row("Maximum zoom", "${s.maxZoom.roundToInt()}×") {
                        val hw = lensSet?.hardwareMaxZoom() ?: 10f
                        Slider(s.maxZoom, { v -> set { it.copy(maxZoom = v.roundToInt().toFloat()) } }, valueRange = 2f..maxOf(hw, 2.1f))
                    }
                    Row("Extra zoom buttons", "Added to the lens buttons") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0.6f, 0.7f, 1.5f, 3f, 4f, 6f, 8f, 10f, 15f, 20f).forEach { stop ->
                                val on = s.zoomStops.any { kotlin.math.abs(it - stop) < 0.01f }
                                ChoiceChip(on, {
                                    set { st -> st.copy(zoomStops = if (on) st.zoomStops.filter { kotlin.math.abs(it - stop) >= 0.01f } else (st.zoomStops + stop).sorted()) }
                                }, label = LensMap.ratioLabel(stop), onScrim = false)
                            }
                        }
                    }
                    ChoiceRow(
                        "When a lens can't do the video setting",
                        LensSwitchPolicy.entries.map { it.label },
                        s.lensSwitch.ordinal,
                    ) { i -> set { it.copy(lensSwitch = LensSwitchPolicy.entries[i]) } }
                }
            }
            item {
                Group("Viewfinder") {
                    ChoiceRow("Grid", GridType.entries.map { it.label }, s.grid.ordinal) { i -> set { it.copy(grid = GridType.entries[i]) } }
                    SwitchRow("Level", null, s.level) { set { st -> st.copy(level = it) } }
                    SwitchRow("Histogram", "Preview only", s.histogram) { set { st -> st.copy(histogram = it) } }
                    SwitchRow("Focus peaking", "Preview only", s.peaking) { set { st -> st.copy(peaking = it) } }
                    SwitchRow("Zebras", "Preview only: stripes over clipped highlights", s.zebra) { set { st -> st.copy(zebra = it) } }
                    SwitchRow("60 fps viewfinder", "Smoother preview where the camera allows; uses more battery", s.previewFps60) { set { st -> st.copy(previewFps60 = it) } }
                }
            }
            item {
                Group("Behaviour") {
                    SwitchRow("Shutter sound", "Some countries require it; it plays there regardless", s.shutterSound) { set { st -> st.copy(shutterSound = it) } }
                    SwitchRow("Save location", "Adds GPS position to photos and videos", s.location) {
                        set { st -> st.copy(location = it) }
                        if (it) onLocationEnabled()
                    }
                    ChoiceRow("Volume keys", VolumeKeyAction.entries.map { it.label }, s.volumeKeys.ordinal) { i -> set { it.copy(volumeKeys = VolumeKeyAction.entries[i]) } }
                    var prefix by remember { mutableStateOf(s.filePrefix) }
                    Row("File name prefix") {
                        OutlinedTextField(
                            prefix,
                            { v ->
                                prefix = v.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(12)
                                if (prefix.isNotEmpty()) set { it.copy(filePrefix = prefix) }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item {
                Group("Advanced") {
                    SwitchRow("Show unsupported options", "Greyed out, so you can see what this phone can't do", s.showUnsupported) { set { st -> st.copy(showUnsupported = it) } }
                    SwitchRow("Look for hidden lenses", "Some phones can open lenses they don't list. Re-reads the cameras.", s.probeHiddenIds) {
                        set { st -> st.copy(probeHiddenIds = it) }
                        vm.reprobe()
                    }
                    ListItem(
                        headlineContent = { Text("Export capability report") },
                        supportingContent = { Text("A JSON file describing this phone's cameras, for bug reports") },
                        trailingContent = {
                            TextButton(onClick = {
                                scope.launch {
                                    val file = vm.exportCaps() ?: return@launch
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(send, "Share capability report"))
                                }
                            }) { Text("Share") }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                    ListItem(
                        headlineContent = { Text("Re-read cameras") },
                        trailingContent = { TextButton(onClick = vm::reprobe) { Text("Re-read") } },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                }
            }
            item {
                Group("About") {
                    Row("Quartz ${BuildConfig.VERSION_NAME}", "A camera that never enhances your photos. No internet access, no analytics.")
                    Row(
                        "Open source",
                        "Material Symbols (Apache 2.0) · Google Sans Flex (SIL Open Font License 1.1) · AndroidX (Apache 2.0)",
                    )
                }
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun Row(title: String, subtitle: String? = null, content: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content?.invoke()
    }
}

/** Material 3 Expressive switch row: check / close icon in the thumb, as in Pixel Camera 10. */
@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                thumbContent = {
                    Icon(
                        painterResource(if (checked) Symbols.check.outlined else Symbols.close.outlined),
                        null,
                        Modifier.size(SwitchDefaults.IconSize),
                    )
                },
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    )
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest)
}

@Composable
private fun ChoiceRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(title) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, label -> ChoiceChip(i == selected, { onSelect(i) }, label = label, onScrim = false) }
        }
    }
}
