package dev.tidewall.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tidewall.ui.MainViewModel
import dev.tidewall.ui.common.ClickRow
import dev.tidewall.ui.common.ListHeader
import dev.tidewall.ui.common.SwitchRow
import dev.tidewall.vpn.VpnStateHolder

private val stacks = listOf("gvisor" to "gVisor", "system" to "System", "mixed" to "Mixed")
private val logLevels = listOf("debug", "info", "warning", "error", "silent")

/** FlClash "Basic configuration": engine, network and DNS settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val vpn by VpnStateHolder.state.collectAsStateWithLifecycle()
    var mtuDialog by remember { mutableStateOf(false) }
    var urlDialog by remember { mutableStateOf(false) }
    var stackDialog by remember { mutableStateOf(false) }
    var logDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Basic configuration") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            if (vpn.active) {
                Text(
                    "Connection settings apply the next time you connect.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            ListHeader("General")
            ClickRow("Log level", s.logLevel.replaceFirstChar(Char::uppercase), Icons.AutoMirrored.Rounded.Article) { logDialog = true }
            ClickRow("Test URL", s.testUrl, Icons.Rounded.Speed) { urlDialog = true }

            ListHeader("Network")
            SwitchRow("Bypass LAN", s.bypassLan, { v -> vm.updateSettings { it.copy(bypassLan = v) } }, "Local network traffic (printers, casting, router) skips the VPN", icon = Icons.Rounded.Lan)
            SwitchRow("IPv6", s.ipv6, { v -> vm.updateSettings { it.copy(ipv6 = v) } }, "Route IPv6 through proxy profiles", icon = Icons.Rounded.TravelExplore)
            ClickRow("Stack", stacks.first { it.first == s.stack }.second + " — how the VPN interface hands traffic to mihomo", Icons.Rounded.Layers) { stackDialog = true }
            ClickRow("MTU", "${s.mtu}", Icons.Rounded.Straighten) { mtuDialog = true }

            ListHeader("DNS")
            SwitchRow("Override DNS", s.overrideDns, { v -> vm.updateSettings { it.copy(overrideDns = v) } }, "Use Tidewall's encrypted DNS (fake-ip) instead of the profile's", icon = Icons.Rounded.Dns)
            SwitchRow("Domain sniffing", s.sniffing, { v -> vm.updateSettings { it.copy(sniffing = v) } }, "Read TLS/HTTP host names so domain rules match apps that bypass DNS", icon = Icons.Rounded.Search)

            ListHeader("Direct OpenVPN")
            SwitchRow(
                "Allow legacy ciphers",
                s.openVpnLegacyCiphers,
                { v -> vm.updateSettings { it.copy(openVpnLegacyCiphers = v) } },
                "BF-CBC and other outdated algorithms, for old servers only",
                icon = Icons.Rounded.History,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (mtuDialog) {
        var text by remember { mutableStateOf(s.mtu.toString()) }
        val v = text.toIntOrNull()
        AlertDialog(
            onDismissRequest = { mtuDialog = false },
            title = { Text("MTU") },
            text = {
                OutlinedTextField(
                    text, { text = it.filter(Char::isDigit).take(5) },
                    supportingText = { Text("1280–9000. Proxy mode default 9000.") },
                    isError = v == null || v !in 1280..9000,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(enabled = v != null && v in 1280..9000, onClick = {
                    mtuDialog = false
                    vm.updateSettings { it.copy(mtu = v!!) }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { mtuDialog = false }) { Text("Cancel") } },
        )
    }
    if (urlDialog) {
        var text by remember { mutableStateOf(s.testUrl) }
        AlertDialog(
            onDismissRequest = { urlDialog = false },
            title = { Text("Test URL") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)) },
            confirmButton = {
                TextButton(enabled = text.startsWith("http"), onClick = { urlDialog = false; vm.updateSettings { it.copy(testUrl = text.trim()) } }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { urlDialog = false }) { Text("Cancel") } },
        )
    }
    if (stackDialog) {
        ChoiceDialog("Stack", stacks, s.stack, onDismiss = { stackDialog = false }) { v -> vm.updateSettings { it.copy(stack = v) } }
    }
    if (logDialog) {
        ChoiceDialog("Log level", logLevels.map { it to it.replaceFirstChar(Char::uppercase) }, s.logLevel, onDismiss = { logDialog = false }) { v ->
            vm.updateSettings { it.copy(logLevel = v) }
        }
    }
}

@Composable
fun ChoiceDialog(title: String, options: List<Pair<String, String>>, selected: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        androidx.compose.material3.RadioButton(selected = value == selected, onClick = { onSelect(value); onDismiss() })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
