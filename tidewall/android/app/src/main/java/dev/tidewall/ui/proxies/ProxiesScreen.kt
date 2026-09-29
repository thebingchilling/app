package dev.tidewall.ui.proxies

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.tidewall.TidewallApp
import dev.tidewall.core.Engine
import dev.tidewall.core.ProxyGroup
import dev.tidewall.core.ProxyItem
import dev.tidewall.data.ProfileKind
import dev.tidewall.data.ProxiesSort
import dev.tidewall.ui.MainViewModel
import dev.tidewall.ui.common.Corner
import dev.tidewall.ui.common.DelayText
import dev.tidewall.ui.common.FlCard
import dev.tidewall.ui.common.NullStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max

class ProxiesViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TidewallApp

    private val _groups = MutableStateFlow<List<ProxyGroup>>(emptyList())
    val groups: StateFlow<List<ProxyGroup>> = _groups.asStateFlow()

    /** Proxy names with a latency test in flight. */
    private val _pending = MutableStateFlow<Set<String>>(emptySet())
    val pending: StateFlow<Set<String>> = _pending.asStateFlow()

    val loading = app.core.loading
    val error = app.core.error
    val version = app.core.version

    suspend fun refresh() {
        _groups.value = withContext(Dispatchers.IO) { runCatching { Engine.proxies().groups.orEmpty() }.getOrDefault(emptyList()) }
    }

    fun select(profileId: String?, group: String, name: String, onError: (String) -> Unit) = viewModelScope.launch {
        withContext(Dispatchers.IO) { runCatching { Engine.selectProxy(group, name) } }
            .onSuccess { if (profileId != null) app.profiles.setSelected(profileId, group, name) }
            .onFailure { onError(it.message ?: "Failed") }
        refresh()
    }

    fun testOne(name: String, url: String) = viewModelScope.launch {
        _pending.value += name
        withContext(Dispatchers.IO) { runCatching { Engine.testDelay(name, url) } }
        _pending.value -= name
        refresh()
    }

    suspend fun testGroup(group: ProxyGroup, url: String) {
        val names = group.proxies.orEmpty().map { it.name }.toSet()
        _pending.value += names
        withContext(Dispatchers.IO) { runCatching { Engine.testGroupDelay(group.name, url) } }
        _pending.value -= names
        refresh()
    }
}

/**
 * FlClash's Proxies page (tab layout): one tab per proxy group, the group's
 * proxies as a grid of cards, and a "Delay test" button. Works before
 * connecting: the selected profile is kept loaded by [dev.tidewall.core.ProxyCore].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxiesScreen(vm: MainViewModel, pvm: ProxiesViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val groups by pvm.groups.collectAsStateWithLifecycle()
    val pending by pvm.pending.collectAsStateWithLifecycle()
    val loading by pvm.loading.collectAsStateWithLifecycle()
    val error by pvm.error.collectAsStateWithLifecycle()
    val version by pvm.version.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var settingsSheet by remember { mutableStateOf(false) }
    var groupSheet by remember { mutableStateOf(false) }
    var testingAll by remember { mutableStateOf(false) }

    LaunchedEffect(version, settings.mode) {
        while (true) {
            pvm.refresh()
            delay(3000)
        }
    }

    val pager = rememberPagerState { groups.size }
    val grids = remember { mutableMapOf<String, androidx.compose.foundation.lazy.grid.LazyGridState>() }
    val current = groups.getOrNull(pager.currentPage)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Proxies") },
                actions = {
                    IconButton(onClick = vm::reportIp) { Icon(Icons.Filled.Public, "Check IP") }
                    IconButton(onClick = {
                        val g = current ?: return@IconButton
                        val i = sorted(g.proxies.orEmpty(), settings.proxiesSort).indexOfFirst { it.name == g.now }
                        if (i >= 0) scope.launch { grids[g.name]?.animateScrollToItem(i) }
                    }) { Icon(Icons.Filled.Adjust, "Scroll to selected") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Filled.Tune, null) },
                                onClick = { menu = false; settingsSheet = true },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (current != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (testingAll) return@ExtendedFloatingActionButton
                        testingAll = true
                        scope.launch {
                            pvm.testGroup(current, settings.testUrl)
                            testingAll = false
                        }
                    },
                    icon = {
                        if (testingAll) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp) else Icon(Icons.Filled.NetworkPing, null)
                    },
                    text = { Text("Delay test") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                selected?.kind != ProfileKind.CLASH -> NullStatus("No proxies yet", Icons.AutoMirrored.Filled.Article)
                groups.isEmpty() -> NullStatus(error?.let { "Could not load the profile:\n$it" } ?: "No proxies yet", Icons.AutoMirrored.Filled.Article)
                else -> {
                    Box(Modifier.fillMaxWidth()) {
                        PrimaryScrollableTabRow(
                            selectedTabIndex = pager.currentPage.coerceIn(0, groups.size - 1),
                            edgePadding = 16.dp,
                            divider = {},
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.padding(end = 40.dp),
                            indicator = {
                                TabRowDefaults.PrimaryIndicator(
                                    Modifier.tabIndicatorOffset(pager.currentPage.coerceIn(0, groups.size - 1), matchContentSize = true),
                                    width = Dp.Unspecified,
                                )
                            },
                        ) {
                            groups.forEachIndexed { i, g ->
                                Tab(
                                    selected = i == pager.currentPage,
                                    onClick = { scope.launch { pager.animateScrollToPage(i) } },
                                    text = { Text(g.name, maxLines = 1) },
                                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { groupSheet = true }, modifier = Modifier.align(Alignment.CenterEnd)) {
                            Icon(Icons.Filled.ExpandMore, "More groups")
                        }
                    }
                    HorizontalPager(pager, Modifier.fillMaxSize(), key = { groups.getOrNull(it)?.name ?: it }) { page ->
                        val g = groups.getOrNull(page) ?: return@HorizontalPager
                        val grid = rememberLazyGridState()
                        SideEffect { grids[g.name] = grid }
                        GroupGrid(
                            g,
                            sorted(g.proxies.orEmpty(), settings.proxiesSort),
                            pending,
                            grid,
                            onSelect = { p ->
                                if (g.selectable) {
                                    pvm.select(selected?.id, g.name, p.name) { vm.toast(it) }
                                } else {
                                    vm.toast("The current proxy group cannot be selected.")
                                }
                            },
                            onTest = { p -> pvm.testOne(p.name, settings.testUrl) },
                        )
                    }
                }
            }
        }
    }

    if (settingsSheet) {
        ModalBottomSheet(onDismissRequest = { settingsSheet = false }) {
            Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Text("Sort", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            FlowRow(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProxiesSort.entries.forEach { s ->
                    FilterChip(
                        selected = settings.proxiesSort == s,
                        onClick = { vm.updateSettings { it.copy(proxiesSort = s) } },
                        label = { Text(s.label) },
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (groupSheet) {
        ModalBottomSheet(onDismissRequest = { groupSheet = false }) {
            Text("Proxy group", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            FlowRow(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                groups.forEachIndexed { i, g ->
                    FlCard(selected = i == pager.currentPage, onClick = {
                        groupSheet = false
                        scope.launch { pager.animateScrollToPage(i) }
                    }) {
                        Text(g.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun sorted(list: List<ProxyItem>, sort: ProxiesSort): List<ProxyItem> = when (sort) {
    ProxiesSort.DEFAULT -> list
    ProxiesSort.NAME -> list.sortedBy { it.name.lowercase() }
    // Untested after tested, timeouts last.
    ProxiesSort.DELAY -> list.sortedBy { if (it.delay > 0) it.delay else if (it.delay == 0) Int.MAX_VALUE - 1 else Int.MAX_VALUE }
}

@Composable
private fun GroupGrid(
    g: ProxyGroup,
    proxies: List<ProxyItem>,
    pending: Set<String>,
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    onSelect: (ProxyItem) -> Unit,
    onTest: (ProxyItem) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // FlClash: one column per 250 dp, at least two.
        val columns = max(ceil((maxWidth.value - 32) / 250).toInt(), 2)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = state,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(proxies, key = { it.name }) { p ->
                ProxyCard(
                    p,
                    selected = p.name == g.now,
                    computed = !g.selectable,
                    pending = p.name in pending,
                    onClick = { onSelect(p) },
                    onTest = { onTest(p) },
                )
            }
        }
    }
}

/** FlClash ProxyCard: name (two lines), type and latency; selected cards are tinted. */
@Composable
private fun ProxyCard(p: ProxyItem, selected: Boolean, computed: Boolean, pending: Boolean, onClick: () -> Unit, onTest: () -> Unit) {
    Box {
        FlCard(Modifier.fillMaxWidth().height(84.dp), selected = selected && !computed, radius = Corner.lg, onClick = onClick) {
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.Center) {
                Text(
                    p.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().height(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        p.type,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    when {
                        pending -> CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                        p.delay == 0 -> CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                            IconButton(onClick = onTest, modifier = Modifier.size(16.dp)) {
                                Icon(Icons.Filled.Bolt, "Delay test", Modifier.size(14.dp))
                            }
                        }
                        else -> DelayText(p.delay, Modifier.clickable(onClick = onTest))
                    }
                }
            }
        }
        // url-test / fallback groups pick for themselves; mark their choice.
        if (computed && selected) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Icon(Icons.Filled.Check, "Selected", Modifier.padding(4.dp).size(12.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}
