package dev.tidewall.ui.settings

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tidewall.data.PerAppMode
import dev.tidewall.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppEntry(val pkg: String, val label: String, val system: Boolean, val info: ApplicationInfo)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(vm: MainViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pm = context.packageManager
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }

    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.packageName != context.packageName }
                .map { AppEntry(it.packageName, pm.getApplicationLabel(it).toString(), it.flags and ApplicationInfo.FLAG_SYSTEM != 0, it) }
                .sortedBy { it.label.lowercase() }
        }
    }
    val selected = s.perAppPackages
    val shown = apps.orEmpty()
        .filter { showSystem || !it.system || it.pkg in selected }
        .filter { query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true) }
        .sortedByDescending { it.pkg in selected }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Per-app VPN") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val modes = listOf(PerAppMode.OFF to "All apps", PerAppMode.INCLUDE to "Only selected", PerAppMode.EXCLUDE to "Except selected")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { i, (m, label) ->
                        SegmentedButton(
                            selected = s.perAppMode == m,
                            onClick = { vm.updateSettings { it.copy(perAppMode = m) } },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                        ) { Text(label, maxLines = 1) }
                    }
                }
                OutlinedTextField(
                    query, { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text("Search apps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FilterChip(selected = showSystem, onClick = { showSystem = !showSystem }, label = { Text("Show system apps") })
            }
            if (apps == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.pkg }) { app ->
                    val checked = app.pkg in selected
                    val toggle = {
                        vm.updateSettings { st ->
                            val set = if (checked) st.perAppPackages - app.pkg else st.perAppPackages + app.pkg
                            st.copy(perAppPackages = set, perAppMode = if (st.perAppMode == PerAppMode.OFF) PerAppMode.EXCLUDE else st.perAppMode)
                        }
                    }
                    ListItem(
                        headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(app.pkg, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) },
                        leadingContent = { AppIcon(app.info, pm) },
                        trailingContent = { Checkbox(checked = checked, onCheckedChange = { toggle() }) },
                        modifier = Modifier.clickable(onClick = toggle),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppIcon(info: ApplicationInfo, pm: PackageManager) {
    val icon by produceState<ImageBitmap?>(null, info.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { pm.getApplicationIcon(info).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }
    Box(Modifier.size(40.dp)) {
        icon?.let { Image(it, null, Modifier.size(40.dp)) }
    }
}
