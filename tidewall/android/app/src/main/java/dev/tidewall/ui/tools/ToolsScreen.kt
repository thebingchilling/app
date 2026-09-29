package dev.tidewall.ui.tools

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.Ballot
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tidewall.BuildConfig
import dev.tidewall.data.PerAppMode
import dev.tidewall.data.ThemeMode
import dev.tidewall.ui.MainViewModel
import dev.tidewall.ui.common.ClickRow
import dev.tidewall.ui.common.ListHeader
import dev.tidewall.ui.common.SwitchRow
import dev.tidewall.work.SubscriptionWorker

/** FlClash's Tools tab: "More" pages, then settings, then other. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(vm: MainViewModel, open: (String) -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Tools") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
            ListHeader("More")
            ClickRow("Connections", "Active connections through the proxy engine", Icons.Filled.Ballot) { open("connections") }
            HorizontalDivider()
            ClickRow("Logs", "Engine and connection logs", Icons.Filled.Adb) { open("logs") }

            ListHeader("Settings")
            ClickRow("Theme", "Set dark mode and adjust colors", Icons.Filled.Style) { open("theme") }
            ClickRow(
                "Access control",
                when (s.perAppMode) {
                    PerAppMode.OFF -> "Control which apps use the proxy"
                    PerAppMode.INCLUDE -> "Only ${s.perAppPackages.size} selected apps use the VPN"
                    PerAppMode.EXCLUDE -> "All apps except ${s.perAppPackages.size} selected use the VPN"
                },
                Icons.AutoMirrored.Filled.ViewList,
            ) { open("apps") }
            ClickRow("Basic configuration", "Modify the basic configuration globally", Icons.Filled.Edit) { open("config") }
            ClickRow("Application", "Adjust application settings", Icons.Filled.Settings) { open("application") }

            ListHeader("Other")
            ClickRow("About", null, Icons.Filled.Info) { open("about") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) { content() }
    }
}

@Composable
fun ThemeScreen(vm: MainViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    SubPage("Theme", onBack) {
        ListHeader("Theme mode")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            ThemeMode.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = s.themeMode == m,
                    onClick = { vm.updateSettings { it.copy(themeMode = m) } },
                    shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                    icon = {},
                ) { Text(if (m == ThemeMode.SYSTEM) "Auto" else m.name.lowercase().replaceFirstChar(Char::uppercase)) }
            }
        }
        ListHeader("Color")
        if (Build.VERSION.SDK_INT >= 31) {
            SwitchRow(
                "Use system colors",
                s.dynamicColor,
                { v -> vm.updateSettings { it.copy(dynamicColor = v) } },
                "Colors from your wallpaper instead of FlClash's default palette",
                icon = Icons.Filled.Palette,
            )
        } else {
            ClickRow("Default palette", "Dynamic color needs Android 12", Icons.Filled.DarkMode) {}
        }
    }
}

@Composable
fun ApplicationScreen(vm: MainViewModel, onBack: () -> Unit, openAutoConnect: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SubPage("Application", onBack) {
        ListHeader("Startup")
        SwitchRow(
            "Auto run",
            s.startOnBoot,
            { v -> vm.updateSettings { it.copy(startOnBoot = v) } },
            "Connect the selected profile when the phone starts",
            icon = Icons.Filled.PowerSettingsNew,
        )
        ClickRow(
            "Auto-connect",
            if (s.autoConnect) "On · ${s.trustedSsids.size} trusted Wi-Fi networks" else "Connect automatically on untrusted networks",
            Icons.Filled.Wifi,
            onClick = openAutoConnect,
        )
        ClickRow("Always-on VPN & kill switch", "Set in Android's VPN settings: tap the gear next to Tidewall", Icons.Filled.VpnLock) {
            context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
        }
        ListHeader("Profiles")
        ClickRow("Update all subscriptions", "Runs in the background", Icons.Filled.Sync) {
            SubscriptionWorker.updateAllNow(context)
            vm.toast("Updating subscriptions…")
        }
    }
}

@Composable
fun AboutScreen(vm: MainViewModel, onBack: () -> Unit) {
    SubPage("About", onBack) {
        ClickRow("Tidewall ${BuildConfig.VERSION_NAME}", "A VPN client with a FlClash-style interface", Icons.Filled.Info) {}
        ClickRow("Engines", "${vm.engineVersion}\nAmneziaWG (Direct WireGuard)\nOpenVPN 3 core 3.11.7 (Direct OpenVPN)", Icons.Filled.Build) {}
        Spacer(Modifier.height(8.dp))
        Text(
            "Licensed under GPL-3.0. The interface follows FlClash (GPL-3.0). Tidewall is not affiliated with the FlClash, " +
                "mihomo, WireGuard, AmneziaWG or OpenVPN projects.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}
