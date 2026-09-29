package dev.tidewall.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class PerAppMode { OFF, INCLUDE, EXCLUDE }
/** How the Proxies tab orders a group's members (FlClash's sort options). */
enum class ProxiesSort(val label: String) { DEFAULT("Default"), DELAY("Delay"), NAME("Name") }

enum class RoutingMode(val id: String, val label: String) {
    RULE("rule", "Rule"), GLOBAL("global", "Global"), DIRECT("direct", "Direct");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id?.lowercase() } ?: RULE
    }
}

data class AppSettings(
    val selectedProfileId: String? = null,
    val mode: RoutingMode = RoutingMode.RULE,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val proxiesSort: ProxiesSort = ProxiesSort.DEFAULT,
    // VPN
    val ipv6: Boolean = false,
    val bypassLan: Boolean = true,
    val mtu: Int = 9000,
    val stack: String = "gvisor",
    val perAppMode: PerAppMode = PerAppMode.OFF,
    val perAppPackages: Set<String> = emptySet(),
    val startOnBoot: Boolean = false,
    // Proxy engine
    val overrideDns: Boolean = false,
    val sniffing: Boolean = true,
    val logLevel: String = "info",
    val testUrl: String = "https://www.gstatic.com/generate_204",
    // OpenVPN
    val openVpnLegacyCiphers: Boolean = false,
    // Auto-connect
    val autoConnect: Boolean = false,
    val trustedSsids: Set<String> = emptySet(),
    val connectOnUntrustedWifi: Boolean = true,
    val connectOnMobile: Boolean = true,
    val disconnectOnTrusted: Boolean = true,
) {
    /** JSON options passed to the Go engine; [selected] restores the profile's proxy choices. */
    fun engineOptionsJson(selected: Map<String, String> = emptyMap()): String = Json.encodeToString(
        kotlinx.serialization.json.JsonObject.serializer(),
        buildJsonObject {
            put("mode", mode.id)
            put("ipv6", ipv6)
            put("stack", stack)
            put("mtu", mtu)
            put("logLevel", logLevel)
            put("overrideDns", overrideDns)
            put("sniffing", sniffing)
            put("selected", buildJsonObject { selected.forEach { (g, p) -> put(g, p) } })
        },
    )
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context, scope: CoroutineScope) {
    private val store = context.applicationContext.dataStore

    private object K {
        val selected = stringPreferencesKey("selected_profile")
        val mode = stringPreferencesKey("mode")
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val proxiesSort = stringPreferencesKey("proxies_sort")
        val ipv6 = booleanPreferencesKey("ipv6")
        val bypassLan = booleanPreferencesKey("bypass_lan")
        val mtu = intPreferencesKey("mtu")
        val stack = stringPreferencesKey("stack")
        val perAppMode = stringPreferencesKey("per_app_mode")
        val perAppPackages = stringSetPreferencesKey("per_app_packages")
        val startOnBoot = booleanPreferencesKey("start_on_boot")
        val overrideDns = booleanPreferencesKey("override_dns")
        val sniffing = booleanPreferencesKey("sniffing")
        val logLevel = stringPreferencesKey("log_level")
        val testUrl = stringPreferencesKey("test_url")
        val ovpnLegacy = booleanPreferencesKey("ovpn_legacy")
        val autoConnect = booleanPreferencesKey("auto_connect")
        val trusted = stringSetPreferencesKey("trusted_ssids")
        val untrustedWifi = booleanPreferencesKey("connect_untrusted_wifi")
        val mobile = booleanPreferencesKey("connect_mobile")
        val disconnectTrusted = booleanPreferencesKey("disconnect_trusted")
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            selectedProfileId = this[K.selected],
            mode = RoutingMode.of(this[K.mode]),
            themeMode = this[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            dynamicColor = this[K.dynamic] ?: d.dynamicColor,
            proxiesSort = this[K.proxiesSort]?.let { runCatching { ProxiesSort.valueOf(it) }.getOrNull() } ?: d.proxiesSort,
            ipv6 = this[K.ipv6] ?: d.ipv6,
            bypassLan = this[K.bypassLan] ?: d.bypassLan,
            mtu = this[K.mtu] ?: d.mtu,
            stack = this[K.stack] ?: d.stack,
            perAppMode = this[K.perAppMode]?.let { runCatching { PerAppMode.valueOf(it) }.getOrNull() } ?: d.perAppMode,
            perAppPackages = this[K.perAppPackages] ?: d.perAppPackages,
            startOnBoot = this[K.startOnBoot] ?: d.startOnBoot,
            overrideDns = this[K.overrideDns] ?: d.overrideDns,
            sniffing = this[K.sniffing] ?: d.sniffing,
            logLevel = this[K.logLevel] ?: d.logLevel,
            testUrl = this[K.testUrl] ?: d.testUrl,
            openVpnLegacyCiphers = this[K.ovpnLegacy] ?: d.openVpnLegacyCiphers,
            autoConnect = this[K.autoConnect] ?: d.autoConnect,
            trustedSsids = this[K.trusted] ?: d.trustedSsids,
            connectOnUntrustedWifi = this[K.untrustedWifi] ?: d.connectOnUntrustedWifi,
            connectOnMobile = this[K.mobile] ?: d.connectOnMobile,
            disconnectOnTrusted = this[K.disconnectTrusted] ?: d.disconnectOnTrusted,
        )
    }

    val settings: StateFlow<AppSettings> = store.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    suspend fun current(): AppSettings = store.data.first().toSettings()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val s = transform(prefs.toSettings())
            if (s.selectedProfileId != null) prefs[K.selected] = s.selectedProfileId else prefs.remove(K.selected)
            prefs[K.mode] = s.mode.id
            prefs[K.theme] = s.themeMode.name
            prefs[K.dynamic] = s.dynamicColor
            prefs[K.proxiesSort] = s.proxiesSort.name
            prefs[K.ipv6] = s.ipv6
            prefs[K.bypassLan] = s.bypassLan
            prefs[K.mtu] = s.mtu
            prefs[K.stack] = s.stack
            prefs[K.perAppMode] = s.perAppMode.name
            prefs[K.perAppPackages] = s.perAppPackages
            prefs[K.startOnBoot] = s.startOnBoot
            prefs[K.overrideDns] = s.overrideDns
            prefs[K.sniffing] = s.sniffing
            prefs[K.logLevel] = s.logLevel
            prefs[K.testUrl] = s.testUrl
            prefs[K.ovpnLegacy] = s.openVpnLegacyCiphers
            prefs[K.autoConnect] = s.autoConnect
            prefs[K.trusted] = s.trustedSsids
            prefs[K.untrustedWifi] = s.connectOnUntrustedWifi
            prefs[K.mobile] = s.connectOnMobile
            prefs[K.disconnectTrusted] = s.disconnectOnTrusted
        }
    }
}
