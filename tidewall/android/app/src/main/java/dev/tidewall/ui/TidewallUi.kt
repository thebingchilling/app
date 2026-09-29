package dev.tidewall.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.tidewall.data.ProfileKind
import dev.tidewall.ui.connections.ConnectionsScreen
import dev.tidewall.ui.dashboard.DashboardScreen
import dev.tidewall.ui.logs.LogsScreen
import dev.tidewall.ui.profiles.LoginDialog
import dev.tidewall.ui.profiles.ProfileEditorScreen
import dev.tidewall.ui.profiles.ProfilesScreen
import dev.tidewall.ui.profiles.ShadowsocksEditorScreen
import dev.tidewall.ui.proxies.ProxiesScreen
import dev.tidewall.ui.settings.AppPickerScreen
import dev.tidewall.ui.settings.AutoConnectScreen
import dev.tidewall.ui.settings.SettingsScreen
import dev.tidewall.ui.theme.TidewallTheme
import dev.tidewall.ui.tools.AboutScreen
import dev.tidewall.ui.tools.ApplicationScreen
import dev.tidewall.ui.tools.ThemeScreen
import dev.tidewall.ui.tools.ToolsScreen

/** FlClash's mobile navigation: Dashboard, Proxies (proxy profiles only), Profiles, Tools. */
private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    DASHBOARD("dashboard", "Dashboard", Icons.Filled.SpaceDashboard),
    PROXIES("proxies", "Proxies", Icons.AutoMirrored.Filled.Article),
    PROFILES("profiles", "Profiles", Icons.Filled.Folder),
    TOOLS("tools", "Tools", Icons.Filled.Construction),
}

/** Snackbar host shared by every screen. */
val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

@Composable
fun TidewallUi(vm: MainViewModel, onConnect: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    TidewallTheme(settings.themeMode, settings.dynamicColor) {
        val nav = rememberNavController()
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

        val selected by vm.selected.collectAsStateWithLifecycle()
        val tabs = Tab.entries.filter { it != Tab.PROXIES || selected?.kind == ProfileKind.CLASH }
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        val tab = tabs.firstOrNull { it.route == route }
        val wide = LocalConfiguration.current.screenWidthDp >= 600

        // The Proxies tab disappears for WireGuard/OpenVPN profiles.
        LaunchedEffect(route, tabs.size) {
            if (route == Tab.PROXIES.route && Tab.PROXIES !in tabs) nav.navigateTab(Tab.DASHBOARD.route)
        }

        val login by vm.login.collectAsStateWithLifecycle()
        login?.let { req ->
            LoginDialog(req, onDismiss = vm::dismissLogin, onConfirm = vm::provideLogin)
        }

        CompositionLocalProvider(LocalSnackbar provides snackbar) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                containerColor = MaterialTheme.colorScheme.surface,
                bottomBar = {
                    if (!wide && tab != null) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                            tabs.forEach { t ->
                                NavigationBarItem(
                                    selected = t == tab,
                                    onClick = { nav.navigateTab(t.route) },
                                    icon = { Icon(t.icon, null) },
                                    label = { Text(t.label) },
                                    alwaysShowLabel = true,
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    if (wide && tab != null) {
                        NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                            tabs.forEach { t ->
                                NavigationRailItem(
                                    selected = t == tab,
                                    onClick = { nav.navigateTab(t.route) },
                                    icon = { Icon(t.icon, null) },
                                    label = { Text(t.label) },
                                )
                            }
                        }
                    }
                    AppNavHost(nav, vm, onConnect)
                }
            }
        }
    }
}

private fun NavHostController.navigateTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun AppNavHost(nav: NavHostController, vm: MainViewModel, onConnect: () -> Unit) {
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(nav, startDestination = Tab.DASHBOARD.route) {
        composable(Tab.DASHBOARD.route) {
            DashboardScreen(vm, onConnect = onConnect, openProfiles = { nav.navigateTab(Tab.PROFILES.route) })
        }
        composable(Tab.PROXIES.route) { ProxiesScreen(vm) }
        composable(Tab.PROFILES.route) {
            ProfilesScreen(
                vm,
                openEditor = { nav.navigate("edit/${it.id}") },
                openShadowsocks = { nav.navigate("ss") },
            )
        }
        composable(Tab.TOOLS.route) {
            ToolsScreen(
                vm,
                open = { nav.navigate(it) },
            )
        }
        composable("connections") { ConnectionsScreen(onBack = back) }
        composable("logs") { LogsScreen(onBack = back) }
        composable("apps") { AppPickerScreen(vm, onBack = back) }
        composable("autoconnect") { AutoConnectScreen(vm, onBack = back) }
        composable("config") { SettingsScreen(vm, onBack = back) }
        composable("theme") { ThemeScreen(vm, onBack = back) }
        composable("application") { ApplicationScreen(vm, onBack = back, openAutoConnect = { nav.navigate("autoconnect") }) }
        composable("about") { AboutScreen(vm, onBack = back) }
        composable("ss") { ShadowsocksEditorScreen(vm, onBack = back) }
        composable("edit/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            ProfileEditorScreen(vm, entry.arguments?.getString("id").orEmpty(), onBack = back)
        }
    }
}
