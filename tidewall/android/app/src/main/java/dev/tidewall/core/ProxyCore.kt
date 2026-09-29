package dev.tidewall.core

import dev.tidewall.TidewallApp
import dev.tidewall.data.ProfileKind
import dev.tidewall.libcore.Libcore
import dev.tidewall.vpn.VpnStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Keeps the selected proxy profile applied to mihomo without a TUN, like
 * FlClash does, so its proxy groups can be browsed, chosen and latency-tested
 * before connecting. Connecting then only attaches the VPN; the choices made
 * here (remembered per profile) are what the VPN starts with.
 */
class ProxyCore(private val app: TidewallApp) {
    private val mutex = Mutex()
    private var loadedKey: String? = null

    /** Bumped after every load, so screens re-read the groups. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Why the selected profile could not be loaded, if it could not. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(
                app.settings.settings,
                app.profiles.profiles,
                VpnStateHolder.state.map { it.active }.distinctUntilChanged(),
            ) { s, list, vpnActive ->
                val p = list.firstOrNull { it.id == s.selectedProfileId } ?: list.firstOrNull()
                // Everything that changes the applied config; not the mode or
                // selections, which are applied live.
                val key = p?.takeIf { it.kind == ProfileKind.CLASH }?.let {
                    listOf(it.id, it.updatedAt, s.ipv6, s.overrideDns, s.sniffing, s.logLevel).joinToString("|")
                }
                key to vpnActive
            }.distinctUntilChanged().collectLatest { (key, _) -> if (key != null) ensureLoaded(key) }
        }
    }

    private suspend fun ensureLoaded(key: String) = mutex.withLock {
        if (key == loadedKey) return@withLock
        withContext(Dispatchers.IO) {
            // A running engine is on whatever profile the VPN started; load
            // again once it stops.
            if (Libcore.isProxyRunning()) {
                loadedKey = null
                return@withContext
            }
            val settings = app.settings.current()
            val profile = app.profiles.get(key.substringBefore('|')) ?: return@withContext
            _loading.value = true
            try {
                Libcore.loadProfile(app.profiles.content(profile), settings.engineOptionsJson(profile.selected))
                Libcore.setMode(settings.mode.id)
                loadedKey = key
                _error.value = null
            } catch (e: Exception) {
                if (Libcore.isProxyRunning()) {
                    // The VPN started meanwhile and applied the profile itself.
                    loadedKey = null
                } else {
                    _error.value = e.message ?: e.javaClass.simpleName
                    LogBuffer.add("error", "Tidewall: could not load ${profile.name}: ${_error.value}")
                }
            } finally {
                _loading.value = false
                _version.value++
            }
        }
    }
}
