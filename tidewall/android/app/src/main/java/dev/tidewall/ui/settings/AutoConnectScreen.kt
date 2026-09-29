package dev.tidewall.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tidewall.autoconnect.AutoConnectService
import dev.tidewall.autoconnect.NetworkType
import dev.tidewall.ui.MainViewModel
import dev.tidewall.ui.common.SectionHeader
import dev.tidewall.ui.common.SwitchRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoConnectScreen(vm: MainViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val network by AutoConnectService.network.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var addDialog by remember { mutableStateOf(false) }
    var permTick by remember { mutableIntStateOf(0) }

    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    val fine = remember(permTick) { granted(Manifest.permission.ACCESS_FINE_LOCATION) }
    val background = remember(permTick) {
        Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }
    val locationPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permTick++ }
    val backgroundPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permTick++ }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auto-connect") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SwitchRow(
                "Auto-connect",
                s.autoConnect,
                { v ->
                    if (v && !fine) {
                        locationPerms.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                    vm.updateSettings { it.copy(autoConnect = v) }
                },
                "Watches your network and connects the selected profile automatically",
            )

            if (s.autoConnect && (!fine || !background)) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.LocationOn, null)
                            Text("Location access needed", style = MaterialTheme.typography.titleSmall)
                        }
                        Text(
                            "Android treats Wi-Fi network names as location data. Tidewall only reads the name of the network you're on to apply your trusted-network rules; it never stores or sends your location.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (!fine) {
                            FilledTonalButton(onClick = {
                                locationPerms.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            }) { Text("Allow location") }
                        } else if (!background) {
                            Text("Choose \"Allow all the time\" so rules work while Tidewall is in the background.", style = MaterialTheme.typography.bodySmall)
                            FilledTonalButton(onClick = {
                                if (Build.VERSION.SDK_INT >= 30) {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                                    )
                                } else if (Build.VERSION.SDK_INT >= 29) {
                                    backgroundPerm.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                }
                            }) { Text("Open app permissions") }
                        }
                    }
                }
            }

            network?.let { n ->
                ListItem(
                    headlineContent = {
                        Text(
                            when (n.type) {
                                NetworkType.WIFI -> "Wi-Fi: ${n.ssid ?: "name hidden (location off?)"}"
                                NetworkType.CELLULAR -> "Mobile data"
                                NetworkType.ETHERNET -> "Ethernet"
                                NetworkType.NONE -> "No network"
                            },
                        )
                    },
                    supportingContent = { Text("Current network") },
                    leadingContent = { Icon(Icons.Rounded.Wifi, null) },
                    trailingContent = {
                        val ssid = n.ssid
                        if (n.type == NetworkType.WIFI && ssid != null && ssid !in s.trustedSsids) {
                            OutlinedButton(onClick = { vm.updateSettings { it.copy(trustedSsids = it.trustedSsids + ssid) } }) { Text("Trust") }
                        }
                    },
                )
            }

            SectionHeader("Rules")
            SwitchRow("Connect on untrusted Wi-Fi", s.connectOnUntrustedWifi, { v -> vm.updateSettings { it.copy(connectOnUntrustedWifi = v) } })
            SwitchRow("Connect on mobile data", s.connectOnMobile, { v -> vm.updateSettings { it.copy(connectOnMobile = v) } })
            SwitchRow("Disconnect on trusted Wi-Fi", s.disconnectOnTrusted, { v -> vm.updateSettings { it.copy(disconnectOnTrusted = v) } })

            SectionHeader("Trusted Wi-Fi networks")
            if (s.trustedSsids.isEmpty()) {
                Text(
                    "None yet. Trusted networks (like home) don't trigger the VPN.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            s.trustedSsids.sorted().forEach { ssid ->
                ListItem(
                    headlineContent = { Text(ssid) },
                    leadingContent = { Icon(Icons.Rounded.Wifi, null) },
                    trailingContent = {
                        IconButton(onClick = { vm.updateSettings { it.copy(trustedSsids = it.trustedSsids - ssid) } }) {
                            Icon(Icons.Rounded.Close, "Remove $ssid")
                        }
                    },
                )
            }
            TextButton(onClick = { addDialog = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Icon(Icons.Rounded.Add, null)
                Text("Add network by name", Modifier.padding(start = 8.dp))
            }
        }
    }

    if (addDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addDialog = false },
            title = { Text("Trusted network") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Wi-Fi name (SSID)") }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    addDialog = false
                    vm.updateSettings { it.copy(trustedSsids = it.trustedSsids + name.trim()) }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addDialog = false }) { Text("Cancel") } },
        )
    }
}
