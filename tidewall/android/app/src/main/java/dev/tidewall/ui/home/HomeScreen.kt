package dev.tidewall.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tidewall.data.ProfileKind
import dev.tidewall.data.RoutingMode
import dev.tidewall.ui.MainViewModel
import dev.tidewall.ui.common.Badge
import dev.tidewall.ui.common.StatBlock
import dev.tidewall.ui.common.formatBytes
import dev.tidewall.ui.common.formatDuration
import dev.tidewall.ui.common.formatSpeed
import dev.tidewall.vpn.VpnStatus
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onConnect: () -> Unit,
    openProfiles: () -> Unit,
    openProxies: () -> Unit,
    openConnections: () -> Unit,
    openLogs: () -> Unit,
) {
    val state by vm.vpn.collectAsStateWithLifecycle()
    val traffic by vm.traffic.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val selected = profiles.firstOrNull { it.id == settings.selectedProfileId } ?: profiles.firstOrNull()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.status) {
        while (state.status == VpnStatus.CONNECTED) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tidewall") },
                actions = {
                    IconButton(onClick = openLogs) { Icon(Icons.AutoMirrored.Rounded.Article, "Logs") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Spacer(Modifier.height(8.dp))
                ConnectButton(
                    status = state.status,
                    onClick = { if (state.active) vm.disconnect() else onConnect() },
                )
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    val title = when (state.status) {
                        VpnStatus.IDLE -> "Not connected"
                        VpnStatus.CONNECTING -> "Connecting…"
                        VpnStatus.RECONNECTING -> "Reconnecting…"
                        VpnStatus.STOPPING -> "Disconnecting…"
                        VpnStatus.CONNECTED -> "Connected"
                    }
                    Text(title, style = MaterialTheme.typography.headlineMedium)
                    val sub = when {
                        state.status == VpnStatus.CONNECTED ->
                            "${state.kind?.engine ?: ""} · ${formatDuration(now - state.since)}"
                        state.error != null -> state.error
                        else -> "Tap to connect"
                    }
                    Text(
                        sub ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.error != null && state.status == VpnStatus.IDLE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Profile
                ElevatedCard(onClick = openProfiles, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Profile", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (selected == null) {
                                Text("No profiles yet — tap to add one", style = MaterialTheme.typography.titleMedium)
                            } else {
                                Text(selected.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Badge(selected.badge)
                                    if (selected.summary.isNotBlank()) {
                                        Text(selected.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                    }
                }

                // Routing mode (proxy profiles only)
                if (selected?.kind == ProfileKind.CLASH) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Routing", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            RoutingMode.entries.forEachIndexed { i, m ->
                                SegmentedButton(
                                    selected = settings.mode == m,
                                    onClick = { vm.setMode(m) },
                                    shape = SegmentedButtonDefaults.itemShape(i, RoutingMode.entries.size),
                                ) { Text(m.label) }
                            }
                        }
                    }
                }

                // Traffic
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatBlock("Upload", formatSpeed(traffic.up), Modifier.weight(1f), Icons.Rounded.ArrowUpward)
                        StatBlock("Download", formatSpeed(traffic.down), Modifier.weight(1f), Icons.Rounded.ArrowDownward)
                    }
                    Text(
                        "Session: ↑ ${formatBytes(traffic.upTotal)} · ↓ ${formatBytes(traffic.downTotal)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    )
                }

                if (state.status == VpnStatus.CONNECTED && state.kind == ProfileKind.CLASH) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ShortcutCard("Proxies", "Pick servers", Icons.Rounded.Hub, openProxies, Modifier.weight(1f))
                        ShortcutCard("Connections", "Live traffic", Icons.Rounded.SwapVert, openConnections, Modifier.weight(1f))
                    }
                }
                if (state.detail != null && state.status == VpnStatus.CONNECTED) {
                    Text(state.detail ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ConnectButton(status: VpnStatus, onClick: () -> Unit) {
    val on = status == VpnStatus.CONNECTED
    val busy = status == VpnStatus.CONNECTING || status == VpnStatus.RECONNECTING || status == VpnStatus.STOPPING
    val container by animateColorAsState(
        when {
            on -> MaterialTheme.colorScheme.primary
            busy -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        label = "container",
    )
    val content by animateColorAsState(
        when {
            on -> MaterialTheme.colorScheme.onPrimary
            busy -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "content",
    )
    val scale by animateFloatAsState(if (on) 1f else 0.94f, label = "scale")
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = container,
            contentColor = content,
            tonalElevation = 2.dp,
            shadowElevation = if (on) 6.dp else 0.dp,
            modifier = Modifier
                .size(152.dp)
                .scale(scale)
                .semantics { contentDescription = if (on) "Disconnect" else "Connect" }
                .clickable(enabled = status != VpnStatus.STOPPING, onClick = onClick),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PowerSettingsNew, null, Modifier.size(64.dp))
            }
        }
    }
}

@Composable
private fun ShortcutCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier) {
    Card(onClick = onClick, modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
