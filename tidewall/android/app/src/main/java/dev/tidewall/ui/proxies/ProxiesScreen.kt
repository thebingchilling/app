package dev.tidewall.ui.proxies

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.tidewall.core.Engine
import dev.tidewall.core.ProxyGroup
import dev.tidewall.data.ProfileKind
import dev.tidewall.ui.MainViewModel
import dev.tidewall.ui.common.Badge
import dev.tidewall.ui.common.DelayText
import dev.tidewall.ui.common.EmptyState
import dev.tidewall.vpn.VpnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProxiesViewModel : ViewModel() {
    private val _groups = MutableStateFlow<List<ProxyGroup>>(emptyList())
    val groups: StateFlow<List<ProxyGroup>> = _groups.asStateFlow()

    private val _testing = MutableStateFlow<Set<String>>(emptySet())
    val testing: StateFlow<Set<String>> = _testing.asStateFlow()

    suspend fun refresh() {
        _groups.value = withContext(Dispatchers.IO) { runCatching { Engine.proxies().groups.orEmpty() }.getOrDefault(emptyList()) }
    }

    fun select(group: String, name: String, onError: (String) -> Unit) = viewModelScope.launch {
        withContext(Dispatchers.IO) { runCatching { Engine.selectProxy(group, name) } }.onFailure { onError(it.message ?: "Failed") }
        refresh()
    }

    fun test(group: String, url: String) = viewModelScope.launch {
        _testing.value += group
        withContext(Dispatchers.IO) { runCatching { Engine.testGroupDelay(group, url) } }
        _testing.value -= group
        refresh()
    }

    fun testAll(url: String) = _groups.value.filter { it.selectable || it.type != "Selector" }.forEach { test(it.name, url) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxiesScreen(vm: MainViewModel, openHome: () -> Unit, pvm: ProxiesViewModel = viewModel()) {
    val state by vm.vpn.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val groups by pvm.groups.collectAsStateWithLifecycle()
    val testing by pvm.testing.collectAsStateWithLifecycle()
    val running = state.status == VpnStatus.CONNECTED && state.kind == ProfileKind.CLASH
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }

    LaunchedEffect(running, settings.mode) {
        while (running) {
            pvm.refresh()
            delay(3000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Proxies") },
                actions = {
                    if (running) {
                        IconButton(onClick = { pvm.testAll(settings.testUrl) }) { Icon(Icons.Rounded.Bolt, "Test all") }
                    }
                },
            )
        },
    ) { padding ->
        if (!running) {
            EmptyState(
                Icons.Rounded.Hub,
                "Not connected",
                "Connect a proxy profile to choose servers and test their latency.",
                Modifier.padding(padding),
            ) { Button(onClick = openHome) { Text("Go to Home") } }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            groups.forEach { g ->
                val open = g.name in expanded
                item(key = "g:${g.name}") {
                    GroupHeader(
                        g, open, g.name in testing,
                        onToggle = { expanded = if (open) expanded - g.name else expanded + g.name },
                        onTest = { pvm.test(g.name, settings.testUrl) },
                    )
                }
                if (open) {
                    items(g.proxies.orEmpty(), key = { "p:${g.name}:${it.name}" }) { p ->
                        val selected = p.name == g.now
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp)
                                .clickable(enabled = g.selectable) {
                                    pvm.select(g.name, p.name) { vm.toast(it) }
                                },
                        ) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (selected) FontWeight.SemiBold else null)
                                    Text(
                                        p.type + if (p.udp) " · UDP" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (!p.group) DelayText(p.delay)
                                if (selected) {
                                    Icon(Icons.Rounded.CheckCircle, "Selected", Modifier.padding(start = 8.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(g: ProxyGroup, open: Boolean, testing: Boolean, onToggle: () -> Unit, onTest: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(g.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Badge(g.type)
                }
                Text(
                    "${g.now.ifBlank { "—" }} · ${g.proxies?.size ?: 0} options",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (testing) {
                CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onTest) { Icon(Icons.Rounded.Bolt, "Test latency") }
            }
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.padding(end = 8.dp))
        }
    }
}
