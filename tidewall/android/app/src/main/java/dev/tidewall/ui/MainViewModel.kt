package dev.tidewall.ui

import android.app.Application
import android.net.ConnectivityManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.tidewall.TidewallApp
import dev.tidewall.core.Engine
import dev.tidewall.core.IpInfo
import dev.tidewall.data.AppSettings
import dev.tidewall.data.Profile
import dev.tidewall.data.ProfileKind
import dev.tidewall.data.RoutingMode
import dev.tidewall.libcore.Libcore
import dev.tidewall.vpn.OpenVpnSession
import dev.tidewall.vpn.TidewallVpnService
import dev.tidewall.vpn.VpnLauncher
import dev.tidewall.vpn.VpnStateHolder
import dev.tidewall.vpn.VpnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL

/** An OpenVPN profile waiting for the user's username/password. */
data class LoginRequest(val profile: Profile, val reason: String? = null, val connectAfter: Boolean = false)

/**
 * Result of the dashboard's "Network detection" (exit IP and country).
 * [preview] = not connected: the IP is where the selected proxy would take apps.
 */
data class IpState(val loading: Boolean = false, val info: IpInfo? = null, val note: String? = null, val preview: Boolean = false)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TidewallApp
    val settings: StateFlow<AppSettings> = app.settings.settings
    val profiles: StateFlow<List<Profile>> = app.profiles.profiles
    val vpn = VpnStateHolder.state
    val traffic = VpnStateHolder.traffic

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _login = MutableStateFlow<LoginRequest?>(null)
    val login: StateFlow<LoginRequest?> = _login.asStateFlow()

    /** The profile Connect starts (the selected one, or the first). */
    val selected: StateFlow<Profile?> = combine(settings, profiles) { s, list ->
        list.firstOrNull { it.id == s.selectedProfileId } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Last 30 seconds of total throughput (bytes/s), for the network speed chart. */
    private val _speedHistory = MutableStateFlow<List<Long>>(emptyList())
    val speedHistory: StateFlow<List<Long>> = _speedHistory.asStateFlow()

    private val _ip = MutableStateFlow(IpState())
    val ip: StateFlow<IpState> = _ip.asStateFlow()

    private val _intranetIp = MutableStateFlow<String?>(null)
    val intranetIp: StateFlow<String?> = _intranetIp.asStateFlow()

    private var ipJob: Job? = null

    init {
        viewModelScope.launch {
            traffic.collect { t -> _speedHistory.value = (_speedHistory.value + (t.up + t.down)).takeLast(30) }
        }
        // Failed connections: offer the OpenVPN login when that is the cause, otherwise say why.
        viewModelScope.launch {
            vpn.map { Triple(it.status, it.error, it.profileId) }.distinctUntilChanged().collect { (status, error, id) ->
                if (status != VpnStatus.IDLE || error == null) return@collect
                val p = app.profiles.get(id)
                when {
                    p != null && p.kind == ProfileKind.OPENVPN && error == TidewallVpnService.LOGIN_NEEDED ->
                        _login.value = LoginRequest(p, connectAfter = true)
                    p != null && p.kind == ProfileKind.OPENVPN && error.startsWith(OpenVpnSession.AUTH_FAILED) ->
                        _login.value = LoginRequest(p, reason = error, connectAfter = true)
                    else -> toast(error)
                }
            }
        }
        // Re-check through the new choice when a proxy, the mode or the loaded profile changes.
        viewModelScope.launch {
            combine(selected.map { it?.id to it?.selected }, settings.map { it.mode }, app.core.version) { a, b, c -> Triple(a, b, c) }
                .distinctUntilChanged()
                .collectLatest {
                    delay(600)
                    if (vpn.value.status == VpnStatus.IDLE || Libcore.isProxyRunning()) detectIp()
                }
        }
        // Re-check the exit IP whenever the connection settles, like FlClash.
        viewModelScope.launch {
            vpn.map { it.status }.distinctUntilChanged().collectLatest { st ->
                if (st == VpnStatus.CONNECTED || st == VpnStatus.IDLE) {
                    delay(1200)
                    detectIp()
                    refreshIntranetIp()
                }
            }
        }
    }

    fun toast(msg: String) {
        _messages.tryEmit(msg)
    }

    private fun work(success: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
                success?.let(::toast)
            } catch (e: Exception) {
                toast(e.message ?: e.javaClass.simpleName)
            } finally {
                _busy.value = false
            }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { app.settings.update(transform) }
    }

    fun selectedProfile(): Profile? = app.profiles.get(settings.value.selectedProfileId)

    fun selectProfile(p: Profile) {
        viewModelScope.launch {
            app.settings.update { it.copy(selectedProfileId = p.id) }
            val s = vpn.value
            if (s.active && s.profileId != p.id) {
                if (p.missingLogin) _login.value = LoginRequest(p, connectAfter = true) else VpnLauncher.start(app, p.id)
            }
        }
    }

    fun setMode(mode: RoutingMode) {
        updateSettings { it.copy(mode = mode) }
        if (selected.value?.kind == ProfileKind.CLASH && Libcore.isProfileLoaded()) {
            runCatching { Libcore.setMode(mode.id) }
        }
    }

    fun connect() {
        val p = selected.value
        if (p == null) {
            toast("Add a profile first")
            return
        }
        if (p.missingLogin) {
            _login.value = LoginRequest(p, connectAfter = true)
            return
        }
        VpnLauncher.start(app, p.id)
    }

    fun disconnect() = VpnLauncher.stop(app)

    // --- Dashboard ---------------------------------------------------------------

    fun detectIp() {
        ipJob?.cancel()
        ipJob = viewModelScope.launch {
            _ip.value = IpState(loading = true)
            val s = vpn.value
            val proxyProfile = selected.value?.kind == ProfileKind.CLASH
            _ip.value = withContext(Dispatchers.IO) {
                when {
                    Libcore.isProxyRunning() ->
                        runCatching { IpState(info = Engine.detectIp()) }.getOrElse { IpState() }
                    // Not connected: check through the loaded profile, so the
                    // chosen proxy's IP and country show before connecting.
                    proxyProfile && s.status == VpnStatus.IDLE && Libcore.isProfileLoaded() ->
                        runCatching { IpState(info = Engine.detectIp(), preview = true) }.getOrElse { IpState() }
                    // Tidewall keeps its own traffic out of the tunnel (its
                    // WireGuard/OpenVPN sockets must not loop), so it cannot
                    // see the tunnel's exit IP from here.
                    s.status == VpnStatus.CONNECTED ->
                        IpState(note = "Not checked for ${s.kind?.engine ?: "direct"} tunnels")
                    else -> runCatching { IpState(info = directIpInfo()) }.getOrElse { IpState() }
                }
            }
        }
    }

    /** Checks the exit IP now and reports it in a snackbar (Proxies screen). */
    fun reportIp() {
        viewModelScope.launch {
            detectIp()
            ipJob?.join()
            val st = _ip.value
            toast(
                st.info?.let { "IP ${it.ip}" + (if (it.country.isNotBlank()) " (${it.country})" else "") + if (st.preview) " via the selected proxy" else "" }
                    ?: st.note ?: "IP check timed out",
            )
        }
    }

    private fun directIpInfo(): IpInfo {
        var last: Exception? = null
        for ((url, countryKey) in listOf("https://ipinfo.io/json" to "country", "https://api.ip.sb/geoip" to "country_code")) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("Accept", "application/json")
                val body = try {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    conn.disconnect()
                }
                val obj = Engine.json.parseToJsonElement(body) as kotlinx.serialization.json.JsonObject
                fun str(k: String) = (obj[k] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                if (str("ip").isNotBlank()) return IpInfo(str("ip"), str(countryKey))
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("no answer")
    }

    fun refreshIntranetIp() {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        // Tidewall is excluded from its own VPN, so its active network is the physical one.
        val addrs = runCatching { cm.getLinkProperties(cm.activeNetwork)?.linkAddresses }.getOrNull()
        _intranetIp.value = when {
            addrs == null -> ""
            else -> (addrs.firstOrNull { it.address is Inet4Address } ?: addrs.firstOrNull())?.address?.hostAddress.orEmpty()
        }
    }

    // --- Import ----------------------------------------------------------------

    private suspend fun afterImport(p: Profile) {
        if (settings.value.selectedProfileId == null || app.profiles.get(settings.value.selectedProfileId) == null) {
            app.settings.update { it.copy(selectedProfileId = p.id) }
        }
        toast("Added ${p.name}")
        if (p.missingLogin) _login.value = LoginRequest(p)
    }

    fun importText(text: String, name: String? = null, fileName: String? = null) = work {
        afterImport(app.profiles.importText(text, name, fileName))
    }

    fun importUrl(url: String, name: String? = null) = work {
        afterImport(app.profiles.importUrl(url, name))
    }

    fun importUri(uri: Uri) = work {
        val (text, fileName) = withContext(Dispatchers.IO) {
            val resolver = app.contentResolver
            val name = runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull() ?: uri.lastPathSegment
            val body = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("Could not read the file")
            if (body.size > 8 * 1024 * 1024) throw IllegalArgumentException("File is too large")
            String(body, Charsets.UTF_8) to name
        }
        afterImport(app.profiles.importText(text, null, fileName))
    }

    fun dismissLogin() {
        _login.value = null
    }

    fun provideLogin(user: String, pass: String) {
        val req = _login.value ?: return
        _login.value = null
        work("Saved login for ${req.profile.name}") {
            app.profiles.setCredentials(req.profile, user, pass)
            if (req.connectAfter) VpnLauncher.start(app, req.profile.id)
        }
    }

    fun editLogin(p: Profile) {
        _login.value = LoginRequest(p)
    }

    // --- Profile actions ----------------------------------------------------------

    fun updateProfile(p: Profile) = work("Updated ${p.name}") { refresh(p) }

    /** Downloads a subscription again and hot-reloads it if it is the running profile. */
    private suspend fun refresh(p: Profile) {
        val updated = app.profiles.update(p)
        val s = vpn.value
        if (s.status == VpnStatus.CONNECTED && s.profileId == p.id && Libcore.isProxyRunning()) {
            withContext(Dispatchers.IO) {
                Libcore.reloadProfile(app.profiles.content(updated), settings.value.engineOptionsJson(updated.selected))
            }
        }
    }

    fun deleteProfile(p: Profile) = work("Deleted ${p.name}") {
        if (vpn.value.profileId == p.id && vpn.value.active) VpnLauncher.stop(app)
        app.profiles.delete(p)
        if (settings.value.selectedProfileId == p.id) {
            app.settings.update { it.copy(selectedProfileId = app.profiles.profiles.value.firstOrNull()?.id) }
        }
    }

    fun updateAll() = work("Updated subscriptions") {
        profiles.value.filter { it.url != null }.forEach { p ->
            runCatching { refresh(p) }.onFailure { toast("${p.name}: ${it.message}") }
        }
    }

    fun renameProfile(p: Profile, name: String) = work { app.profiles.rename(p, name) }

    fun setUpdateInterval(p: Profile, hours: Int) = work { app.profiles.setUpdateInterval(p, hours) }

    fun convertToProxy(p: Profile) = work {
        val np = app.profiles.convertToProxy(p)
        toast("Created ${np.name}")
    }

    suspend fun content(p: Profile): String = app.profiles.content(p)

    /** Validates and saves edited profile text; returns an error message or null. */
    suspend fun saveContent(p: Profile, text: String): String? = try {
        app.profiles.saveContent(p, text)
        null
    } catch (e: Exception) {
        e.message ?: "Invalid profile"
    }

    /** Adds one proxy (as a JSON/YAML mapping) to [target] or a new profile. */
    suspend fun addProxy(target: Profile?, proxy: String, newProfileName: String): String? = try {
        val p = app.profiles.addProxy(target, proxy, newProfileName)
        if (settings.value.selectedProfileId == null) app.settings.update { it.copy(selectedProfileId = p.id) }
        null
    } catch (e: Exception) {
        e.message ?: "Invalid proxy"
    }

    val engineVersion: String get() = runCatching { Engine.version }.getOrDefault("?")
}
