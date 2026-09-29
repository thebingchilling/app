package dev.tidewall.ui.logs

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tidewall.core.LogBuffer
import dev.tidewall.core.LogLine
import dev.tidewall.ui.common.EmptyState
import dev.tidewall.ui.theme.LocalStatusColors
import dev.tidewall.ui.theme.MonoStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val levels = listOf("all", "debug", "info", "warning", "error")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(onBack: () -> Unit) {
    val live by LogBuffer.lines.collectAsStateWithLifecycle()
    var paused by remember { mutableStateOf<List<LogLine>?>(null) }
    var level by remember { mutableStateOf("all") }
    val lines = (paused ?: live).let { l -> if (level == "all") l else l.filter { it.level == level } }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val time = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    LaunchedEffect(lines.size, paused) {
        if (paused == null && lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { paused = if (paused == null) live else null }) {
                        Icon(if (paused == null) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (paused == null) "Pause" else "Resume")
                    }
                    IconButton(onClick = {
                        val text = lines.joinToString("\n") { "${time.format(Date(it.time))} [${it.level}] ${it.message}" }
                        context.startActivity(
                            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share logs"),
                        )
                    }) { Icon(Icons.Rounded.Share, "Share") }
                    IconButton(onClick = { LogBuffer.clear(); paused = null }) { Icon(Icons.Rounded.DeleteSweep, "Clear") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                levels.forEach { l ->
                    FilterChip(selected = level == l, onClick = { level = l }, label = { Text(l.replaceFirstChar(Char::uppercase)) })
                }
            }
            if (lines.isEmpty()) {
                EmptyState(Icons.AutoMirrored.Rounded.Article, "No logs yet", "Engine logs appear here once you connect.")
            } else {
                val status = LocalStatusColors.current
                LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    items(lines, key = { it.id }) { l ->
                        val color = when (l.level) {
                            "error" -> status.poor
                            "warning" -> status.fair
                            "debug" -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Text(
                            "${time.format(Date(l.time))} ${l.message}",
                            style = MonoStyle.merge(MaterialTheme.typography.bodySmall),
                            color = color,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
