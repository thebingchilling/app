package dev.tidewall.vpn

import android.net.VpnService
import android.os.Build
import dev.tidewall.BuildConfig
import dev.tidewall.core.LogBuffer
import dev.tidewall.data.AppSettings
import dev.tidewall.data.Profile
import dev.tidewall.ovpn3.ClientAPI_Config
import dev.tidewall.ovpn3.ClientAPI_Event
import dev.tidewall.ovpn3.ClientAPI_ExternalPKICertRequest
import dev.tidewall.ovpn3.ClientAPI_ExternalPKISignRequest
import dev.tidewall.ovpn3.ClientAPI_LogInfo
import dev.tidewall.ovpn3.ClientAPI_OpenVPNClient
import dev.tidewall.ovpn3.ClientAPI_ProvideCreds
import dev.tidewall.ovpn3.DnsOptions
import dev.tidewall.ovpn3.OpenVpn3
import java.net.InetAddress

/**
 * One Direct OpenVPN connection, driven by the official OpenVPN 3 core.
 * OpenVPN 3 calls the tun_builder_* methods to describe the tunnel; they map
 * onto Android's [VpnService.Builder].
 */
class OpenVpnSession(
    private val service: TidewallVpnService,
    private val profile: Profile,
    private val ovpn: String,
    private val settings: AppSettings,
    private val listener: Listener,
) : ClientAPI_OpenVPNClient() {

    companion object {
        const val AUTH_FAILED = "The OpenVPN server rejected the username or password"

        init {
            // Must run before the ClientAPI_OpenVPNClient constructor calls into native code.
            OpenVpn3.load()
        }
    }

    interface Listener {
        fun onConnected(info: String)
        fun onReconnecting()
        fun onFatal(message: String)
        fun onDisconnected()
    }

    private var builder: VpnService.Builder? = null
    private var thread: Thread? = null
    @Volatile private var stopping = false

    /** Validates the profile and starts connecting on a background thread. */
    fun start() {
        val cfg = ClientAPI_Config().apply {
            content = ovpn
            guiVersion = "Tidewall ${BuildConfig.VERSION_NAME}"
            platformVersion = "Android ${Build.VERSION.RELEASE}"
            info = true
            compressionMode = "asym"
            tunPersist = true
            connTimeout = 0 // keep retrying, like the official client
            allowLocalLanAccess = settings.bypassLan
            enableLegacyAlgorithms = settings.openVpnLegacyCiphers
            sslDebugLevel = 0
        }
        val eval = eval_config(cfg)
        if (eval.error) throw IllegalArgumentException("OpenVPN profile error: ${eval.message}")
        if (eval.externalPki) throw IllegalArgumentException("Profiles using an Android keystore certificate are not supported yet")
        if (!eval.autologin) {
            val user = profile.username
            if (user.isNullOrBlank()) throw IllegalArgumentException(TidewallVpnService.LOGIN_NEEDED)
            val creds = ClientAPI_ProvideCreds().apply {
                username = user
                password = profile.password.orEmpty()
            }
            val st = provide_creds(creds)
            if (st.error) throw IllegalArgumentException("Credentials rejected: ${st.message}")
        }
        thread = Thread({
            val status = runCatching { connect() }.getOrNull()
            if (!stopping && status != null && status.error) {
                listener.onFatal(status.message.ifBlank { status.status })
            } else if (!stopping) {
                listener.onDisconnected()
            }
        }, "openvpn3").apply { start() }
    }

    fun shutdown() {
        stopping = true
        runCatching { stop() }
        thread?.join(5000)
        thread = null
    }

    /** (bytes in, bytes out) since the session started. */
    fun totals(): Pair<Long, Long> = runCatching { transport_stats().let { it.bytesIn to it.bytesOut } }.getOrDefault(0L to 0L)

    private fun log(msg: String) = LogBuffer.add("info", "[OpenVPN] $msg")

    private inline fun tun(name: String, block: (VpnService.Builder) -> Unit): Boolean = try {
        block(builder ?: throw IllegalStateException("no builder"))
        true
    } catch (e: Exception) {
        LogBuffer.add("warning", "[OpenVPN] $name failed: ${e.message}")
        false
    }

    // --- TunBuilderBase ----------------------------------------------------

    override fun tun_builder_new(): Boolean {
        builder = service.newBuilder(settings, profile.name)
        return true
    }

    override fun tun_builder_set_layer(layer: Int): Boolean {
        if (layer != 3) log("Only routed (tun) mode is supported; the server asked for layer $layer")
        return layer == 3
    }

    override fun tun_builder_set_remote_address(address: String, ipv6: Boolean) = true

    override fun tun_builder_add_address(address: String, prefix_length: Int, gateway: String, ipv6: Boolean, net30: Boolean) =
        tun("add_address") { it.addAddress(address, prefix_length) }

    override fun tun_builder_reroute_gw(ipv4: Boolean, ipv6: Boolean, flags: Long) = tun("reroute_gw") { b ->
        if (ipv4) Routes.defaultRoutes4(settings.bypassLan).forEach { b.addRoute(it.address, it.length) }
        if (ipv6) Routes.defaultRoutes6(settings.bypassLan).forEach { b.addRoute(it.address, it.length) }
    }

    override fun tun_builder_add_route(address: String, prefix_length: Int, metric: Int, ipv6: Boolean) =
        tun("add_route") { it.addRoute(address, prefix_length) }

    override fun tun_builder_exclude_route(address: String, prefix_length: Int, metric: Int, ipv6: Boolean) =
        tun("exclude_route") {
            if (Build.VERSION.SDK_INT >= 33) {
                it.excludeRoute(android.net.IpPrefix(InetAddress.getByName(address), prefix_length))
            } else {
                log("Ignoring excluded route $address/$prefix_length (needs Android 13)")
            }
        }

    override fun tun_builder_set_dns_options(dns: DnsOptions) = tun("set_dns_options") { b ->
        for (server in dns.servers.values) {
            for (addr in server.addresses) b.addDnsServer(addr.address)
        }
        for (domain in dns.search_domains) b.addSearchDomain(domain.domain)
    }

    override fun tun_builder_set_mtu(mtu: Int) = tun("set_mtu") { it.setMtu(mtu) }

    override fun tun_builder_set_session_name(name: String) = tun("set_session_name") { it.setSession("${profile.name} ($name)") }

    override fun tun_builder_set_allow_family(af: Int, allow: Boolean) = true

    override fun tun_builder_set_allow_local_dns(allow: Boolean) = true

    override fun tun_builder_persist() = true

    override fun tun_builder_establish(): Int {
        val pfd = runCatching { builder?.establish() }.getOrNull() ?: run {
            LogBuffer.add("error", "[OpenVPN] Could not create the VPN interface")
            return -1
        }
        return pfd.detachFd()
    }

    override fun tun_builder_teardown(disconnect: Boolean) {}

    // --- OpenVPNClient callbacks -----------------------------------------------

    override fun socket_protect(socket: Int, remote: String, ipv6: Boolean): Boolean = service.protect(socket)

    override fun pause_on_connection_timeout() = false

    override fun event(ev: ClientAPI_Event) {
        val name = ev.name
        val info = ev.info
        LogBuffer.add(if (ev.error) "error" else "info", "[OpenVPN] $name${if (info.isNotBlank()) ": $info" else ""}")
        when {
            name == "CONNECTED" -> listener.onConnected(info)
            name == "RECONNECTING" -> listener.onReconnecting()
            name == "AUTH_FAILED" -> listener.onFatal(AUTH_FAILED + if (info.isNotBlank()) " ($info)" else "")
            ev.fatal -> listener.onFatal("$name${if (info.isNotBlank()) ": $info" else ""}")
        }
    }

    override fun log(info: ClientAPI_LogInfo) = log(info.text.trimEnd())

    override fun external_pki_cert_request(req: ClientAPI_ExternalPKICertRequest) {
        req.error = true
        req.errorText = "External PKI is not supported"
    }

    override fun external_pki_sign_request(req: ClientAPI_ExternalPKISignRequest) {
        req.error = true
        req.errorText = "External PKI is not supported"
    }
}
