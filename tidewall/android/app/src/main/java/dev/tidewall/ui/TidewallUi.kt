package dev.tidewall.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material3.Icon
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
import dev.tidewall.ui.connections.ConnectionsScreen
import dev.tidewall.ui.home.HomeScreen
import dev.tidewall.ui.logs.LogsScreen
import dev.tidewall.ui.profiles.CredentialsDialog
import dev.tidewall.ui.profiles.ProfileEditorScreen
import dev.tidewall.ui.profiles.ProfilesScreen
import dev.tidewall.ui.profiles.ShadowsocksEditorScreen
import dev.tidewall.ui.proxies.ProxiesScreen
import dev.tidewall.ui.settings.AppPickerScreen
import dev.tidewall.ui.settings.AutoConnectScreen
import dev.tidewall.ui.settings.SettingsScreen
import dev.tidewall.ui.theme.TidewallTheme

private enum class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("home", "Home", Icons.Outlined.Shield, Icons.Rounded.Shield),
    PROXIES("proxies", "Proxies", Icons.Outlined.Hub, Icons.Rounded.Hub),
    PROFILES("profiles", "Profiles", Icons.Outlined.Source, Icons.Rounded.Source),
    SETTINGS("settings", "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
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

        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        val tab = Tab.entries.firstOrNull { it.route == route }
        val wide = LocalConfiguration.current.screenWidthDp >= 600

        val credentials by vm.credentials.collectAsStateWithLifecycle()
        credentials?.let { req ->
            CredentialsDialog(
                title = req.existing?.let { "Login for ${it.name}" } ?: "Login for ${req.name}",
                initialUser = req.existing?.username.orEmpty(),
                onDismiss = vm::dismissCredentials,
                onConfirm = vm::provideCredentials,
            )
        }

        CompositionLocalProvider(LocalSnackbar provides snackbar) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (!wide && tab != null) {
                        NavigationBar {
                            Tab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = t == tab,
                                    onClick = { nav.navigateTab(t.route) },
                                    icon = { Icon(if (t == tab) t.selectedIcon else t.icon, null) },
                                    label = { Text(t.label) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    if (wide && tab != null) {
                        NavigationRail {
                            Tab.entries.forEach { t ->
                                NavigationRailItem(
                                    selected = t == tab,
                                    onClick = { nav.navigateTab(t.route) },
                                    icon = { Icon(if (t == tab) t.selectedIcon else t.icon, null) },
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
    NavHost(nav, startDestination = Tab.HOME.route) {
        composable(Tab.HOME.route) {
            HomeScreen(
                vm,
                onConnect = onConnect,
                openProfiles = { nav.navigateTab(Tab.PROFILES.route) },
                openProxies = { nav.navigateTab(Tab.PROXIES.route) },
                openConnections = { nav.navigate("connections") },
                openLogs = { nav.navigate("logs") },
            )
        }
        composable(Tab.PROXIES.route) { ProxiesScreen(vm, openHome = { nav.navigateTab(Tab.HOME.route) }) }
        composable(Tab.PROFILES.route) {
            ProfilesScreen(
                vm,
                openEditor = { nav.navigate("edit/${it.id}") },
                openShadowsocks = { nav.navigate("ss") },
            )
        }
        composable(Tab.SETTINGS.route) {
            SettingsScreen(
                vm,
                openApps = { nav.navigate("apps") },
                openAutoConnect = { nav.navigate("autoconnect") },
                openLogs = { nav.navigate("logs") },
            )
        }
        composable("connections") { ConnectionsScreen(onBack = back) }
        composable("logs") { LogsScreen(onBack = back) }
        composable("apps") { AppPickerScreen(vm, onBack = back) }
        composable("autoconnect") { AutoConnectScreen(vm, onBack = back) }
        composable("ss") { ShadowsocksEditorScreen(vm, onBack = back) }
        composable("edit/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            ProfileEditorScreen(vm, entry.arguments?.getString("id").orEmpty(), onBack = back)
        }
    }
}
