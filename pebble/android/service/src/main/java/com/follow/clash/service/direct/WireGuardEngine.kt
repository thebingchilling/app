package com.follow.clash.service.direct

import android.content.Context
import android.os.Build
import android.system.OsConstants
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.util.SharedLibraryLoader
import java.io.BufferedReader
import java.io.StringReader
import java.lang.reflect.Method
import java.net.InetAddress

/** What the VPN interface needs from a parsed WireGuard/AmneziaWG config. */
internal class WireGuardSetup(
    val addresses: List<Pair<InetAddress, Int>>,
    val dnsServers: List<InetAddress>,
    val searchDomains: List<String>,
    val routes: List<Pair<InetAddress, Int>>,
    val peerCount: Int,
    val mtu: Int?,
    val includedApplications: Set<String>,
    val excludedApplications: Set<String>,
    /** The config in the engine's UAPI form, with endpoints already resolved. */
    val settings: String,
)

/** One of the two Go engines: the official WireGuard library or Amnezia's fork of it. */
internal interface WireGuardLibrary {
    val name: String

    fun load(context: Context)

    fun parse(text: String): WireGuardSetup

    fun turnOn(interfaceName: String, tunFd: Int, settings: String): Int

    fun turnOff(handle: Int)

    fun socketV4(handle: Int): Int

    fun socketV6(handle: Int): Int

    fun config(handle: Int): String?
}

// GoBackend resolves each endpoint before building the UAPI string and
// retries for a while, since the network may still be coming up.
private const val DNS_RESOLUTION_RETRIES = 10

private fun <T> resolveEndpoints(endpoints: List<T>, host: (T) -> String, resolved: (T) -> Boolean) {
    for (endpoint in endpoints) {
        var attempt = 0
        while (!resolved(endpoint)) {
            if (++attempt >= DNS_RESOLUTION_RETRIES) {
                error("Could not resolve the server address ${host(endpoint)}")
            }
            Thread.sleep(1000)
        }
    }
}

/**
 * The official WireGuard library (com.wireguard.android:tunnel). Its JNI entry points are private
 * to GoBackend, which only drives them from its own VpnService; Pebble calls the same functions
 * from its VpnService, so it reaches them by reflection.
 */
internal object OfficialWireGuard : WireGuardLibrary {
    override val name = "WireGuard"

    private fun native(name: String, vararg types: Class<*>): Method =
        GoBackend::class.java.getDeclaredMethod(name, *types).apply { isAccessible = true }

    private val wgTurnOn by lazy {
        native("wgTurnOn", String::class.java, Int::class.javaPrimitiveType!!, String::class.java)
    }
    private val wgTurnOff by lazy { native("wgTurnOff", Int::class.javaPrimitiveType!!) }
    private val wgGetSocketV4 by lazy { native("wgGetSocketV4", Int::class.javaPrimitiveType!!) }
    private val wgGetSocketV6 by lazy { native("wgGetSocketV6", Int::class.javaPrimitiveType!!) }
    private val wgGetConfig by lazy { native("wgGetConfig", Int::class.javaPrimitiveType!!) }

    override fun load(context: Context) = SharedLibraryLoader.loadSharedLibrary(context, "wg-go")

    override fun parse(text: String): WireGuardSetup {
        val config = com.wireguard.config.Config.parse(BufferedReader(StringReader(text)))
        val endpoints = config.peers.mapNotNull { it.endpoint.orElse(null) }
        resolveEndpoints(endpoints, { it.host }, { it.resolved.isPresent })
        val iface = config.`interface`
        return WireGuardSetup(
            addresses = iface.addresses.map { it.address to it.mask },
            dnsServers = iface.dnsServers.toList(),
            searchDomains = iface.dnsSearchDomains.toList(),
            routes = config.peers.flatMap { peer -> peer.allowedIps.map { it.address to it.mask } },
            peerCount = config.peers.size,
            mtu = iface.mtu.orElse(null),
            includedApplications = iface.includedApplications,
            excludedApplications = iface.excludedApplications,
            settings = config.toWgUserspaceString(),
        )
    }

    override fun turnOn(interfaceName: String, tunFd: Int, settings: String): Int =
        wgTurnOn.invoke(null, interfaceName, tunFd, settings) as Int

    override fun turnOff(handle: Int) {
        wgTurnOff.invoke(null, handle)
    }

    override fun socketV4(handle: Int): Int = wgGetSocketV4.invoke(null, handle) as Int

    override fun socketV6(handle: Int): Int = wgGetSocketV6.invoke(null, handle) as Int

    override fun config(handle: Int): String? = wgGetConfig.invoke(null, handle) as String?
}

/** AmneziaWG's engine (amneziawg-android), built by :amneziawg as libawg-go.so. */
internal object AmneziaWg : WireGuardLibrary {
    override val name = "AmneziaWG"

    override fun load(context: Context) = System.loadLibrary("awg-go")

    override fun parse(text: String): WireGuardSetup {
        val config = org.amnezia.awg.config.Config.parse(BufferedReader(StringReader(text)))
        val endpoints = config.peers.mapNotNull { it.endpoint.orElse(null) }
        resolveEndpoints(endpoints, { it.host }, { it.resolved.isPresent })
        val iface = config.`interface`
        return WireGuardSetup(
            addresses = iface.addresses.map { it.address to it.mask },
            dnsServers = iface.dnsServers.toList(),
            searchDomains = iface.dnsSearchDomains.toList(),
            routes = config.peers.flatMap { peer -> peer.allowedIps.map { it.address to it.mask } },
            peerCount = config.peers.size,
            mtu = iface.mtu.orElse(null),
            includedApplications = iface.includedApplications,
            excludedApplications = iface.excludedApplications,
            settings = config.toAwgUserspaceString(),
        )
    }

    override fun turnOn(interfaceName: String, tunFd: Int, settings: String): Int =
        org.amnezia.awg.GoBackend.awgTurnOn(interfaceName, tunFd, settings)

    override fun turnOff(handle: Int) = org.amnezia.awg.GoBackend.awgTurnOff(handle)

    override fun socketV4(handle: Int): Int = org.amnezia.awg.GoBackend.awgGetSocketV4(handle)

    override fun socketV6(handle: Int): Int = org.amnezia.awg.GoBackend.awgGetSocketV6(handle)

    override fun config(handle: Int): String? = org.amnezia.awg.GoBackend.awgGetConfig(handle)
}

/**
 * Brings a WireGuard or AmneziaWG tunnel up the way the libraries' own GoBackend does: the VPN
 * interface gets the config's addresses, DNS, allowed IPs and MTU, then its file descriptor goes
 * to the Go engine, whose sockets are kept out of the VPN.
 */
internal class WireGuardEngine(
    private val host: TunnelHost,
    private val library: WireGuardLibrary,
    private val text: String,
) : DirectEngine {
    @Volatile
    private var handle = -1

    override fun start() {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            "${library.name} needs Android 7 or newer"
        }
        library.load(host.context)
        val setup = library.parse(text)
        val builder = host.newBuilder(setup.includedApplications, setup.excludedApplications)
        setup.addresses.forEach { (address, prefix) -> builder.addAddress(address, prefix) }
        setup.dnsServers.forEach { builder.addDnsServer(it.hostAddress!!) }
        setup.searchDomains.forEach { builder.addSearchDomain(it) }
        var sawDefaultRoute = false
        setup.routes.forEach { (address, prefix) ->
            if (prefix == 0) sawDefaultRoute = true
            builder.addRoute(address, prefix)
        }
        // GoBackend's kill-switch rule: a single peer carrying all traffic
        // blocks what the tunnel does not route; otherwise other traffic
        // keeps its normal path.
        if (!(sawDefaultRoute && setup.peerCount == 1)) {
            builder.allowFamily(OsConstants.AF_INET)
            builder.allowFamily(OsConstants.AF_INET6)
        }
        builder.setMtu(setup.mtu ?: 1280)
        builder.setBlocking(true)
        val tun = builder.establish() ?: error("Android refused to create the VPN interface")
        handle = library.turnOn("Pebble", tun.detachFd(), setup.settings)
        check(handle >= 0) { "${library.name} could not start (error $handle)" }
        host.protect(library.socketV4(handle))
        host.protect(library.socketV6(handle))
        host.log("${library.name} tunnel up")
    }

    override fun stop() {
        val current = handle
        handle = -1
        if (current >= 0) {
            library.turnOff(current)
        }
    }

    override fun traffic(): Pair<Long, Long> {
        val current = handle
        if (current < 0) return 0L to 0L
        var rx = 0L
        var tx = 0L
        library.config(current)?.lineSequence()?.forEach { line ->
            when {
                line.startsWith("rx_bytes=") -> rx += line.substring(9).toLongOrNull() ?: 0
                line.startsWith("tx_bytes=") -> tx += line.substring(9).toLongOrNull() ?: 0
            }
        }
        return tx to rx
    }
}
