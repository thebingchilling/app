package dev.tidewall.core

import android.content.Context
import dev.tidewall.libcore.Libcore
import dev.tidewall.libcore.LogListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class ProxyItem(
    val name: String,
    val type: String,
    val udp: Boolean = false,
    val delay: Int = 0,
    val group: Boolean = false,
)

@Serializable
data class ProxyGroup(
    val name: String,
    val type: String,
    val now: String = "",
    val selectable: Boolean = false,
    val hidden: Boolean = false,
    val icon: String = "",
    val proxies: List<ProxyItem>? = null,
)

@Serializable
data class ProxiesState(val mode: String = "rule", val groups: List<ProxyGroup>? = null)

@Serializable
data class Connection(
    val id: String,
    val network: String = "",
    val host: String = "",
    val dest: String = "",
    val chains: List<String>? = null,
    val rule: String = "",
    val upload: Long = 0,
    val download: Long = 0,
    val start: Long = 0,
)

@Serializable
data class IpInfo(val ip: String = "", val country: String = "")

@Serializable
data class ProfileStats(val proxies: Int = 0, val groups: Int = 0, val providers: Int = 0, val rules: Int = 0)

@Serializable
data class OvpnInfo(
    val name: String = "",
    val server: String = "",
    val port: Int = 0,
    val proto: String = "",
    val needsPassword: Boolean = false,
    val username: String? = null,
    val password: String? = null,
    val unsupported: List<String>? = null,
)

@Serializable
data class WireGuardInfo(
    val addresses: List<String>? = null,
    val dns: List<String>? = null,
    val search: List<String>? = null,
    val mtu: Int = 1280,
    val routes: List<String>? = null,
    val endpoints: List<String>? = null,
    val amnezia: Boolean = false,
)

/** Kotlin face of the Go engine (mihomo + AmneziaWG) built by ../core. */
object Engine {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val home = File(context.filesDir, "mihomo").apply { mkdirs() }
            Libcore.init(home.absolutePath)
            initialized = true
        }
    }

    val version: String get() = Libcore.version()

    fun setLogLevel(level: String) {
        Libcore.setLogListener(object : LogListener {
            override fun onLog(level: String?, message: String?) {
                LogBuffer.add(level ?: "info", message ?: "")
            }
        }, level)
    }

    suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    // Profiles and importers (all throw on invalid input).
    fun validateProfile(yaml: String) = Libcore.validateProfile(yaml)
    fun profileFromLinks(text: String): String = Libcore.profileFromLinks(text)
    fun profileFromOvpn(text: String, name: String, user: String, pass: String): String =
        Libcore.profileFromOvpn(text, name, user, pass)

    fun profileFromWireGuard(text: String, name: String): String = Libcore.profileFromWireGuard(text, name)
    fun addProxyToProfile(profile: String, proxyYaml: String): String = Libcore.addProxyToProfile(profile, proxyYaml)
    fun profileStats(yaml: String): ProfileStats = json.decodeFromString(Libcore.profileStats(yaml))
    fun inspectOvpn(text: String): OvpnInfo = json.decodeFromString(Libcore.inspectOvpn(text))
    fun inspectWireGuard(text: String): WireGuardInfo = json.decodeFromString(Libcore.inspectWireGuard(text))

    // Proxy engine: loaded (browse/select before connecting) or running.
    fun loadProfile(yaml: String, optionsJson: String) = Libcore.loadProfile(yaml, optionsJson)
    fun detectIp(timeoutMs: Long = 8000): IpInfo = json.decodeFromString(Libcore.detectIP(timeoutMs))
    fun proxies(): ProxiesState = json.decodeFromString(Libcore.proxiesJSON())
    fun selectProxy(group: String, name: String) = Libcore.selectProxy(group, name)
    fun testDelay(name: String, url: String, timeoutMs: Long = 5000): Int =
        Libcore.testDelay(name, url, timeoutMs).toInt()

    fun testGroupDelay(group: String, url: String, timeoutMs: Long = 5000): Map<String, Int> =
        json.decodeFromString(Libcore.testGroupDelay(group, url, timeoutMs))

    fun setMode(mode: String) = Libcore.setMode(mode)
    fun connections(): List<Connection> = json.decodeFromString(Libcore.connectionsJSON())
    fun closeConnection(id: String) = Libcore.closeConnection(id)
}
