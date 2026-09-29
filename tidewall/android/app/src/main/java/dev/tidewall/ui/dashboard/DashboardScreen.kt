package dev.tidewall.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DataSaverOff
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tidewall.data.Profile
import dev.tidewall.data.ProfileKind
import dev.tidewall.data.RoutingMode
import dev.tidewall.ui.IpState
import dev.tidewall.ui.MainViewModel
import dev.tidewall.ui.common.Corner
import dev.tidewall.ui.common.DashboardCard
import dev.tidewall.ui.common.FlCard
import dev.tidewall.ui.common.GridSpacing
import dev.tidewall.ui.common.InfoHeader
import dev.tidewall.ui.common.formatBytes
import dev.tidewall.vpn.VpnStatus
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.asin
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: MainViewModel, onConnect: () -> Unit, openProfiles: () -> Unit) {
    val state by vm.vpn.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Dashboard") }) },
        floatingActionButton = {
            if (profiles.isNotEmpty()) {
                StartButton(state.status, state.since) { if (state.active) vm.disconnect() else onConnect() }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .padding(bottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(GridSpacing),
            ) {
                StatusNotice(vm, selected, state.detail.takeIf { state.status == VpnStatus.CONNECTED }, openProfiles)
                NetworkSpeed(vm)
                Row(horizontalArrangement = Arrangement.spacedBy(GridSpacing)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GridSpacing)) {
                        if (selected == null || selected?.kind == ProfileKind.CLASH) OutboundMode(vm) else ProfileCard(selected!!, openProfiles)
                        IntranetIp(vm)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GridSpacing)) {
                        NetworkDetection(vm)
                        TrafficUsage(vm)
                    }
                }
            }
        }
    }
}

/** Problems worth a card: a missing OpenVPN login, or a WireGuard server that never answered. */
@Composable
private fun StatusNotice(vm: MainViewModel, selected: Profile?, detail: String?, openProfiles: () -> Unit) {
    val text = when {
        selected?.missingLogin == true -> "${selected.name} needs a username and password before it can connect."
        detail != null -> detail
        else -> return
    }
    FlCard(Modifier.fillMaxWidth(), radius = Corner.lg) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (selected?.missingLogin == true) {
                TextButton(onClick = { vm.editLogin(selected) }) {
                    Icon(Icons.Rounded.Key, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Login")
                }
            } else {
                TextButton(onClick = openProfiles) { Text("Profiles") }
            }
        }
    }
}

// --- Start button ------------------------------------------------------------------

/** FlClash's start FAB: a play icon, which grows into pause + run time while connected. */
@Composable
private fun StartButton(status: VpnStatus, since: Long, onClick: () -> Unit) {
    val running = status != VpnStatus.IDLE
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) {
        while (running) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    FloatingActionButton(onClick = onClick, modifier = Modifier.height(56.dp)) {
        Row(Modifier.animateContentSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            when (status) {
                VpnStatus.CONNECTING, VpnStatus.RECONNECTING, VpnStatus.STOPPING ->
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                VpnStatus.CONNECTED -> Icon(Icons.Filled.Pause, "Stop")
                VpnStatus.IDLE -> Icon(Icons.Filled.PlayArrow, "Start")
            }
            AnimatedVisibility(running, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                Text(
                    runTime(if (status == VpnStatus.CONNECTED) now - since else 0),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

private fun runTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

// --- Network speed ------------------------------------------------------------------

@Composable
private fun NetworkSpeed(vm: MainViewModel) {
    val traffic by vm.traffic.collectAsStateWithLifecycle()
    val history by vm.speedHistory.collectAsStateWithLifecycle()
    DashboardCard(lines = 2, modifier = Modifier.fillMaxWidth()) {
        InfoHeader("Network speed", Icons.Filled.Speed) {
            Text(
                "↑ ${formatBytes(traffic.up)}/s   ↓ ${formatBytes(traffic.down)}/s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
        LineChart(listOf(0L, 0L) + history, MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth().weight(1f).padding(top = 16.dp))
    }
}

/** Smooth line with a gradient fill underneath (FlClash LineChart). */
@Composable
private fun LineChart(points: List<Long>, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val maxY = max(points.max().toFloat(), 1f)
        val stepX = size.width / (points.size - 1)
        val stroke = 2.dp.toPx()
        fun pt(i: Int) = Offset(i * stepX, size.height - (points[i] / maxY) * (size.height - stroke) - stroke / 2)
        val line = Path().apply {
            moveTo(pt(0).x, pt(0).y)
            for (i in 1 until points.size) {
                val a = pt(i - 1)
                val b = pt(i)
                val mx = (a.x + b.x) / 2
                cubicTo(mx, a.y, mx, b.y, b.x, b.y)
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

// --- Outbound mode / profile -----------------------------------------------------------

@Composable
private fun OutboundMode(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    DashboardCard(lines = 2, label = "Outbound mode", icon = Icons.AutoMirrored.Filled.CallSplit) {
        Column(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.SpaceEvenly) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                RoutingMode.entries.forEach { m ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = settings.mode == m, role = Role.RadioButton) { vm.setMode(m) }
                            .padding(start = 12.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = settings.mode == m, onClick = null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(m.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/** Stands in for Outbound mode when a WireGuard/OpenVPN profile is selected. */
@Composable
private fun ProfileCard(p: Profile, openProfiles: () -> Unit) {
    DashboardCard(lines = 2, label = "Profile", icon = Icons.Filled.Folder, onClick = openProfiles) {
        Column(Modifier.padding(16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom)) {
            Text(p.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(p.kind.engine, style = MaterialTheme.typography.bodySmall)
            if (p.summary.isNotBlank()) {
                Text(p.summary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// --- Network detection / intranet IP ------------------------------------------------------

@Composable
private fun NetworkDetection(vm: MainViewModel) {
    val ip by vm.ip.collectAsStateWithLifecycle()
    var tip by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (ip.info == null && !ip.loading) vm.detectIp() }
    DashboardCard(lines = 1, onClick = vm::detectIp) {
        InfoHeader(
            "Network detection",
            Icons.Filled.NetworkCheck,
            modifier = Modifier.padding(end = 0.dp),
            leading = ip.info?.country?.let { flag(it) }?.let { f -> { Text(f, fontSize = 18.sp) } },
        ) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                IconButton(onClick = { tip = true }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Outlined.Info, "Tip", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        IpLine(ip)
    }
    if (tip) {
        AlertDialog(
            onDismissRequest = { tip = false },
            title = { Text("Tip") },
            text = { Text("Relies on a third-party API; for reference only") },
            confirmButton = { TextButton(onClick = { tip = false }) { Text("OK") } },
        )
    }
}

@Composable
private fun IpLine(ip: IpState) {
    Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).height(20.dp), contentAlignment = Alignment.CenterStart) {
        when {
            ip.loading -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            ip.info != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f, fill = false)) { ValueText(ip.info.ip) }
                if (ip.preview) {
                    // Not connected yet: this is where the selected proxy leads.
                    Text(" · via proxy", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            ip.note != null -> Text(ip.note, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            else -> Text("Timeout", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFF44336))
        }
    }
}

@Composable
private fun ValueText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Light),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Country code -> flag emoji (regional indicator symbols). */
private fun flag(code: String): String? {
    val c = code.uppercase()
    if (c.length != 2 || !c.all { it in 'A'..'Z' }) return null
    return String(Character.toChars(0x1F1E6 + (c[0] - 'A'))) + String(Character.toChars(0x1F1E6 + (c[1] - 'A')))
}

@Composable
private fun IntranetIp(vm: MainViewModel) {
    val ip by vm.intranetIp.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshIntranetIp() }
    DashboardCard(lines = 1, label = "Intranet IP", icon = Icons.Filled.Devices, onClick = vm::refreshIntranetIp) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).height(20.dp), contentAlignment = Alignment.CenterStart) {
            when (val v = ip) {
                null -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                "" -> ValueText("No network")
                else -> ValueText(v)
            }
        }
    }
}

// --- Traffic usage --------------------------------------------------------------------

/** FlClash blends container colors toward black (light) or white (dark) for the chart. */
@Composable
private fun darken(c: Color, factor: Float): Color {
    val dark = MaterialTheme.colorScheme.surface.run { red + green + blue } < 1.5f
    return lerp(c, if (dark) Color.White else Color.Black, factor)
}

@Composable
private fun TrafficUsage(vm: MainViewModel) {
    val traffic by vm.traffic.collectAsStateWithLifecycle()
    val upColor = darken(MaterialTheme.colorScheme.primaryContainer, 0.3f)
    val downColor = darken(MaterialTheme.colorScheme.secondaryContainer, 0.2f)
    DashboardCard(lines = 2, label = "Traffic usage", icon = Icons.Filled.DataSaverOff) {
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Row(Modifier.weight(1f).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Donut(listOf(traffic.upTotal to upColor, traffic.downTotal to downColor), Modifier.fillMaxHeight().aspectRatio(1f))
                Spacer(Modifier.width(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Legend(upColor, "Upload")
                    Legend(downColor, "Download")
                }
            }
            TrafficRow(Icons.Filled.ArrowUpward, upColor, traffic.upTotal)
            Spacer(Modifier.height(8.dp))
            TrafficRow(Icons.Filled.ArrowDownward, downColor, traffic.downTotal)
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 20.dp, height = 8.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

@Composable
private fun TrafficRow(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, bytes: Long) {
    val (value, unit) = formatBytes(bytes).split(' ').let { it[0] to it.getOrElse(1) { "" } }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = color)
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
        Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}

/** Two-segment ring with rounded ends and gaps, animated on change (FlClash DonutChart). */
@Composable
private fun Donut(data: List<Pair<Long, Color>>, modifier: Modifier) {
    // FlClash adds 1 to every value so an empty chart still shows both segments.
    val anim = data.map { (v, _) -> remember { Animatable(v + 1f) } }
    data.forEachIndexed { i, (v, _) -> LaunchedEffect(v) { anim[i].animateTo(v + 1f, tween(300)) } }
    Canvas(modifier) {
        val values = anim.map { it.value }
        val total = values.sum()
        if (total <= 0f) return@Canvas
        val strokePx = 10.dp.toPx()
        val radius = min(size.width, size.height) / 2 - strokePx / 2
        val gap = Math.toDegrees(2 * asin(strokePx / (2 * radius)).toDouble() * 1.2).toFloat()
        val available = 360f - data.size * gap
        var start = -90f + gap / 2
        val topLeft = Offset(center.x - radius, center.y - radius)
        values.forEachIndexed { i, v ->
            val sweep = available * v / total
            if (sweep > 0) {
                drawArc(data[i].second, start, sweep, false, topLeft, Size(radius * 2, radius * 2), style = Stroke(strokePx, cap = StrokeCap.Round))
            }
            start += sweep + gap
        }
    }
}
