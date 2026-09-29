package dev.tidewall.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tidewall.BuildConfig
import dev.tidewall.data.PerAppMode
import dev.tidewall.data.ThemeMode
import dev.tidewall.ui.MainViewModel
import dev.tidewall.ui.common.ClickRow
import dev.tidewall.ui.common.SectionHeader
import dev.tidewall.ui.common.SwitchRow
import dev.tidewall.vpn.VpnStateHolder
import dev.tidewall.work.SubscriptionWorker

private val stacks = listOf("gvisor" to "gVisor", "system" to "System", "mixed" to "Mixed")
private val logLevels = listOf("debug", "info", "warning", "error", "silent")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, openApps: () -> Unit, openAutoConnect: () -> Unit, openLogs: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val vpn by VpnStateHolder.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var mtuDialog by remember { mutableStateOf(false) }
    var urlDialog by remember { mutableStateOf(false) }
    var stackDialog by remember { mutableStateOf(false) }
    var logDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            if (vpn.active) {
                Text(
                    "Connection settings apply the next time you connect.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            SectionHeader("Appearance")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = s.themeMode == m,
                            onClick = { vm.updateSettings { it.copy(themeMode = m) } },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        ) { Text(m.name.lowercase().replaceFirstChar(Char::uppercase)) }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= 31) {
                SwitchRow("Dynamic color", s.dynamicColor, { v -> vm.updateSettings { it.copy(dynamicColor = v) } }, "Use colors from your wallpaper", icon = Icons.Rounded.Palette)
            }

            SectionHeader("Connection")
            SwitchRow("Bypass LAN", s.bypassLan, { v -> vm.updateSettings { it.copy(bypassLan = v) } }, "Local network traffic (printers, casting, router) skips the VPN", icon = Icons.Rounded.Lan)
            SwitchRow("IPv6", s.ipv6, { v -> vm.updateSettings { it.copy(ipv6 = v) } }, "Route IPv6 through Proxy-mode profiles", icon = Icons.Rounded.TravelExplore)
            ClickRow(
                "Per-app VPN",
                when (s.perAppMode) {
                    PerAppMode.OFF -> "All apps use the VPN"
                    PerAppMode.INCLUDE -> "Only ${s.perAppPackages.size} selected apps"
                    PerAppMode.EXCLUDE -> "All apps except ${s.perAppPackages.size} selected"
                },
                Icons.Rounded.Apps,
                onClick = openApps,
            )
            ClickRow(
                "Auto-connect",
                if (s.autoConnect) "On · ${s.trustedSsids.size} trusted Wi-Fi networks" else "Connect automatically on untrusted networks",
                Icons.Rounded.Wifi,
                onClick = openAutoConnect,
            )
            SwitchRow("Connect on boot", s.startOnBoot, { v -> vm.updateSettings { it.copy(startOnBoot = v) } }, "Reconnect the selected profile after the phone restarts", icon = Icons.Rounded.PowerSettingsNew)
            ClickRow("Always-on VPN & kill switch", "Set in Android's VPN settings: tap the gear next to Tidewall", Icons.Rounded.VpnLock) {
                context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
            }

            SectionHeader("Proxy engine (mihomo)")
            ClickRow("TUN stack", stacks.first { it.first == s.stack }.second + " — how the VPN interface hands traffic to mihomo", Icons.Rounded.Speed) { stackDialog = true }
            ClickRow("MTU", "${s.mtu}", Icons.Rounded.Straighten) { mtuDialog = true }
            SwitchRow("Override profile DNS", s.overrideDns, { v -> vm.updateSettings { it.copy(overrideDns = v) } }, "Use Tidewall's encrypted DNS (fake-ip) instead of the profile's", icon = Icons.Rounded.Dns)
            SwitchRow("Domain sniffing", s.sniffing, { v -> vm.updateSettings { it.copy(sniffing = v) } }, "Read TLS/HTTP host names so domain rules match apps that bypass DNS")
            ClickRow("Latency test URL", s.testUrl) { urlDialog = true }
            ClickRow("Log level", s.logLevel.replaceFirstChar(Char::uppercase), Icons.AutoMirrored.Rounded.Article) { logDialog = true }
            ClickRow("View logs", null, onClick = openLogs)

            SectionHeader("Direct OpenVPN")
            SwitchRow(
                "Allow legacy ciphers",
                s.openVpnLegacyCiphers,
                { v -> vm.updateSettings { it.copy(openVpnLegacyCiphers = v) } },
                "BF-CBC and other outdated algorithms, for old servers only",
            )

            SectionHeader("Subscriptions")
            ClickRow("Update all subscriptions", "Runs in the background", Icons.Rounded.Refresh) {
                SubscriptionWorker.updateAllNow(context)
                vm.toast("Updating subscriptions…")
            }

            SectionHeader("About")
            ClickRow(
                "Tidewall ${BuildConfig.VERSION_NAME}",
                "Engines: ${vm.engineVersion} · AmneziaWG · OpenVPN 3 (release/3.11.7)\nLicensed under GPL-3.0",
                Icons.Rounded.Info,
            ) {}
            HorizontalDivider(Modifier.padding(top = 8.dp))
            Text(
                "Tidewall is not affiliated with the mihomo, WireGuard, AmneziaWG or OpenVPN projects.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
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
            title = { Text("Latency test URL") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)) },
            confirmButton = {
                TextButton(enabled = text.startsWith("http"), onClick = { urlDialog = false; vm.updateSettings { it.copy(testUrl = text.trim()) } }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { urlDialog = false }) { Text("Cancel") } },
        )
    }
    if (stackDialog) {
        ChoiceDialog("TUN stack", stacks, s.stack, onDismiss = { stackDialog = false }) { v -> vm.updateSettings { it.copy(stack = v) } }
    }
    if (logDialog) {
        ChoiceDialog("Log level", logLevels.map { it to it.replaceFirstChar(Char::uppercase) }, s.logLevel, onDismiss = { logDialog = false }) { v ->
            vm.updateSettings { it.copy(logLevel = v) }
        }
    }
}

@Composable
private fun ChoiceDialog(title: String, options: List<Pair<String, String>>, selected: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
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
