package dev.tidewall.ui.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tidewall.core.Connection
import dev.tidewall.core.Engine
import dev.tidewall.ui.common.Badge
import dev.tidewall.ui.common.EmptyState
import dev.tidewall.ui.common.formatBytes
import dev.tidewall.ui.common.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Live list of connections going through the proxy engine. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(onBack: () -> Unit) {
    var conns by remember { mutableStateOf<List<Connection>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            conns = Engine.io { runCatching { Engine.connections() }.getOrDefault(emptyList()) }
                .sortedByDescending { it.start }
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val shown = if (query.isBlank()) conns else conns.filter {
        it.host.contains(query, true) || it.rule.contains(query, true) || it.chains.orEmpty().any { c -> c.contains(query, true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connections (${conns.size})") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { scope.launch { Engine.io { runCatching { Engine.closeConnection("") } } } }) {
                        Icon(Icons.Rounded.ClearAll, "Close all")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                query, { query = it },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text("Filter by host, rule or proxy") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (shown.isEmpty()) {
                EmptyState(Icons.Rounded.SwapVert, "No connections", "Connections appear here while apps use the proxy.")
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown, key = { it.id }) { c ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Badge(c.network.uppercase())
                                        Text(c.host, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(
                                        c.chains.orEmpty().reversed().joinToString(" → ").ifBlank { "—" },
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${c.rule} · ↑ ${formatBytes(c.upload)} ↓ ${formatBytes(c.download)} · ${formatDuration(now - c.start)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = { scope.launch { Engine.io { runCatching { Engine.closeConnection(c.id) } } } }) {
                                    Icon(Icons.Rounded.Close, "Close connection")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
