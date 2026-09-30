package com.follow.clash.service.direct

import android.net.IpPrefix
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import java.io.File
import java.io.FileDescriptor
import java.io.InputStream
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Runs OpenVPN 2 as OpenVPN for Android does: the libovpnexec.so executable reads the config on
 * stdin and connects back to a management socket. Over it, OpenVPN asks for the login, has its
 * sockets kept out of the VPN (PROTECTFD), reports the tunnel's addresses, routes and DNS, and
 * finally asks for the VPN interface (OPENTUN), whose file descriptor is passed back to it.
 */
internal class OpenVpnEngine(
    private val host: TunnelHost,
    private val tunnel: DirectTunnel,
) : DirectEngine {
    private val context = host.context
    private val socketFile = File(context.cacheDir, "openvpn-management")
    private var process: Process? = null
    private var listener: LocalServerSocket? = null
    private var listenerSocket: LocalSocket? = null

    @Volatile
    private var management: LocalSocket? = null

    @Volatile
    private var stopping = false

    @Volatile
    private var tunnelUp = false

    @Volatile
    private var failure: String? = null
    private val firstTunnel = CountDownLatch(1)
    private val receivedFds = ArrayDeque<FileDescriptor>()
    private val recentLog = ArrayDeque<String>()

    @Volatile
    private var bytesIn = 0L

    @Volatile
    private var bytesOut = 0L
    private var holdReleased = false

    // The interface OpenVPN describes before each OPENTUN.
    private var localIp: Pair<String, Int>? = null
    private var localIpv6: Pair<String, Int>? = null
    private var remoteGateway: String? = null
    private var mtu = 1500
    private val dnsServers = mutableListOf<String>()
    private val searchDomains = mutableListOf<String>()
    private val routes = mutableListOf<Route>()

    private data class Route(val address: String, val prefix: Int, val included: Boolean)

    override fun start() {
        val executable = File(context.applicationInfo.nativeLibraryDir, "libovpnexec.so")
        check(executable.exists()) { "OpenVPN is missing from this build" }
        socketFile.delete()
        val bound = LocalSocket()
        bound.bind(LocalSocketAddress(socketFile.path, LocalSocketAddress.Namespace.FILESYSTEM))
        listenerSocket = bound
        listener = LocalServerSocket(bound.fileDescriptor)
        thread(name = "OpenVPN management", isDaemon = true) { runManagement() }

        val started = ProcessBuilder(executable.path, "--config", "stdin")
            .redirectErrorStream(true)
            .apply {
                environment()["LD_LIBRARY_PATH"] = context.applicationInfo.nativeLibraryDir
                environment()["TMPDIR"] = context.cacheDir.path
            }
            .start()
        process = started
        started.outputStream.use { it.write(openVpnConfig(tunnel.config, socketFile.path).toByteArray()) }
        thread(name = "OpenVPN log", isDaemon = true) { readLog(started.inputStream) }

        // FlClash reports the VPN as running once start() returns, so wait
        // until OpenVPN has the interface, a login was refused, or it gives up.
        if (!firstTunnel.await(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            failure = "No answer from the OpenVPN server within $CONNECT_TIMEOUT_SECONDS s" +
                lastLogLine()
        }
        failure?.let { reason ->
            stop()
            error(reason)
        }
        host.log("OpenVPN tunnel up")
    }

    override fun stop() {
        if (stopping) return
        stopping = true
        send("signal SIGINT\n")
        val current = process
        if (current != null) {
            val exited = thread(isDaemon = true) { runCatching { current.waitFor() } }
            exited.join(STOP_TIMEOUT_MILLIS)
            current.destroy()
        }
        runCatching { management?.close() }
        if (management == null) {
            // accept() does not return when the listener closes; connect once to release it.
            runCatching {
                LocalSocket().connect(
                    LocalSocketAddress(socketFile.path, LocalSocketAddress.Namespace.FILESYSTEM),
                )
            }
        }
        runCatching { listener?.close() }
        runCatching { listenerSocket?.close() }
        socketFile.delete()
        synchronized(receivedFds) {
            receivedFds.forEach { runCatching { Os.close(it) } }
            receivedFds.clear()
        }
        firstTunnel.countDown()
    }

    override fun traffic(): Pair<Long, Long> = bytesOut to bytesIn

    private fun fail(reason: String) {
        if (stopping || failure != null) return
        failure = reason
        host.log("OpenVPN: $reason")
        if (tunnelUp) {
            host.onEngineStopped(reason)
        } else {
            firstTunnel.countDown()
        }
    }

    private fun lastLogLine(): String = synchronized(recentLog) {
        recentLog.lastOrNull()?.let { ": $it" }.orEmpty()
    }

    private fun readLog(input: InputStream) {
        input.bufferedReader().forEachLine { line ->
            // machine-readable-output: "time,flags,message"
            val message = line.split(',', limit = 3).getOrNull(2) ?: line
            synchronized(recentLog) {
                recentLog.addLast(message)
                while (recentLog.size > 20) recentLog.removeFirst()
            }
            host.log("OpenVPN: $message")
        }
        fail("OpenVPN stopped" + lastLogLine())
    }

    private fun send(command: String, fd: FileDescriptor? = null): Boolean {
        val socket = management ?: return false
        return synchronized(socket) {
            runCatching {
                if (fd != null) socket.setFileDescriptorsForSend(arrayOf(fd))
                socket.outputStream.write(command.toByteArray())
                socket.outputStream.flush()
                if (fd != null) socket.setFileDescriptorsForSend(null)
            }.isSuccess
        }
    }

    private fun runManagement() {
        val socket = runCatching { listener!!.accept() }.getOrNull() ?: return
        if (stopping) {
            runCatching { socket.close() }
            return
        }
        management = socket
        runCatching { listener?.close() }
        send("version 3\n")
        val input = socket.inputStream
        val buffer = ByteArray(4096)
        var pending = ""
        while (!stopping) {
            val read = runCatching { input.read(buffer) }.getOrDefault(-1)
            if (read <= 0) break
            socket.ancillaryFileDescriptors?.let { fds ->
                synchronized(receivedFds) { receivedFds.addAll(fds) }
            }
            pending += String(buffer, 0, read, Charsets.UTF_8)
            while (true) {
                val end = pending.indexOf('\n')
                if (end < 0) break
                val line = pending.substring(0, end).trimEnd('\r')
                pending = pending.substring(end + 1)
                runCatching { handle(line) }.onFailure { error ->
                    fail("Management error: ${error.message}")
                }
            }
        }
    }

    private fun handle(line: String) {
        if (!line.startsWith(">")) return
        val colon = line.indexOf(':')
        if (colon < 0) return
        val argument = line.substring(colon + 1)
        when (line.substring(1, colon)) {
            "HOLD" -> releaseHold(argument)
            "PASSWORD" -> answerPassword(argument)
            "NEED-OK" -> answerNeedOk(argument)
            "BYTECOUNT" -> argument.split(',').let { parts ->
                bytesIn = parts.getOrNull(0)?.toLongOrNull() ?: bytesIn
                bytesOut = parts.getOrNull(1)?.toLongOrNull() ?: bytesOut
            }
            "STATE" -> argument.split(',').getOrNull(1)?.let { state ->
                host.log("OpenVPN state: $state")
            }
            "FATAL" -> fail(argument)
        }
    }

    // ">HOLD:Waiting for hold release:N": N is OpenVPN's own reconnect back-off.
    private fun releaseHold(argument: String) {
        val wait = argument.substringAfterLast(':').trim().toLongOrNull() ?: 0
        if (holdReleased && wait > 0) Thread.sleep(wait * 1000)
        holdReleased = true
        send("hold release\n")
        send("bytecount 2\n")
        send("state on\n")
    }

    private fun answerPassword(argument: String) {
        if (argument.startsWith("Auth-Token:")) return
        val needed = argument.substringAfter('\'', "").substringBefore('\'', "")
        if (argument.startsWith("Verification Failed")) {
            fail(
                if (needed == "Auth") {
                    "The server rejected the username or password. VPN providers often use " +
                        "separate OpenVPN (service) credentials; re-import the file to enter them again."
                } else {
                    "Verification failed: $needed"
                },
            )
            return
        }
        val username = tunnel.username
        if (needed == "Auth" && username != null) {
            send("username 'Auth' ${escape(username)}\n")
            send("password 'Auth' ${escape(tunnel.password.orEmpty())}\n")
        } else {
            fail("OpenVPN asks for '$needed', which Pebble cannot provide")
        }
    }

    // ">NEED-OK:Need 'NAME' confirmation MSG:extra"
    private fun answerNeedOk(argument: String) {
        val needed = argument.substringAfter('\'', "").substringBefore('\'', "")
        val extra = argument.substringAfter("MSG:", "")
        var status = "ok"
        when (needed) {
            "PROTECTFD" -> protectReceivedFd()
            "DNSSERVER", "DNS6SERVER" -> dnsServers += extra
            "DNSDOMAIN" -> searchDomains += extra
            "ROUTE" -> addRoute(extra.split(' '))
            "ROUTE6" -> extra.split(' ').let { parts ->
                val (address, prefix) = splitCidr(parts[0])
                routes += Route(address, prefix, isTunDevice(parts.getOrNull(1)))
            }
            "IFCONFIG" -> setLocalIp(extra.split(' '))
            "IFCONFIG6" -> extra.split(' ').let { parts ->
                localIpv6 = splitCidr(parts[0])
                parts.getOrNull(1)?.toIntOrNull()?.let { mtu = it }
            }
            "PERSIST_TUN_ACTION" -> status = "OPEN_BEFORE_CLOSE"
            "OPENTUN" -> {
                if (extra != "tun") {
                    fail("OpenVPN wants a $extra device; Android VPNs only support tun")
                    status = "cancel"
                } else {
                    openTun()
                    return
                }
            }
            "HTTPPROXY" -> Unit
            else -> host.log("OpenVPN: unhandled request $needed")
        }
        send("needok '$needed' $status\n")
    }

    private fun protectReceivedFd() {
        val fd = synchronized(receivedFds) { receivedFds.removeFirstOrNull() } ?: return
        ParcelFileDescriptor.dup(fd).use { host.protect(it.fd) }
        Os.close(fd)
    }

    // "local netmask mtu topology": with net30/p2p the "netmask" is the peer.
    private fun setLocalIp(parts: List<String>) {
        val local = parts[0]
        val netmask = parts.getOrElse(1) { "255.255.255.255" }
        val mode = parts.getOrNull(3)
        parts.getOrNull(2)?.toIntOrNull()?.let { mtu = it }
        var prefix = prefixOfMask(netmask) ?: 32
        if (prefix == 32 && netmask != "255.255.255.255") {
            val (length, mask) = if (mode == "net30") 30 to 0xfffffffcL else 31 to 0xfffffffeL
            prefix = if (ipv4(netmask) and mask == ipv4(local) and mask) length else 32
        }
        localIp = local to prefix
        remoteGateway = netmask
        // Android does not route the VPN's own subnet by itself.
        if (prefix <= 31) {
            routes += Route(network(local, prefix), prefix, true)
        }
    }

    // "network netmask gateway [dev iface]"
    private fun addRoute(parts: List<String>) {
        // After OPENTUN OpenVPN still reports its default route through a
        // placeholder gateway; like OpenVPN for Android, only routes that
        // come with an interface address count.
        val local = localIp ?: return
        val prefix = prefixOfMask(parts[1]) ?: 32
        val gateway = parts.getOrNull(2)
        var included = isTunDevice(parts.getOrNull(4))
        if (gateway != null && gateway.isIpv4() &&
            network(gateway, local.second) == network(local.first, local.second)
        ) {
            included = true
        }
        if (gateway == "255.255.255.255" || gateway == remoteGateway) included = true
        routes += Route(network(parts[0], prefix), prefix, included)
    }

    private fun openTun() {
        val builder = host.newBuilder()
        localIp?.let { (address, prefix) -> builder.addAddress(address, prefix) }
        localIpv6?.let { (address, prefix) -> builder.addAddress(address, prefix) }
        dnsServers.forEach { server ->
            runCatching { builder.addDnsServer(server) }
                .onFailure { host.log("OpenVPN: DNS server $server skipped: ${it.message}") }
        }
        searchDomains.forEach { builder.addSearchDomain(it) }
        routes.forEach { route ->
            runCatching {
                if (route.included) {
                    builder.addRoute(route.address, route.prefix)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    builder.excludeRoute(IpPrefix(InetAddress.getByName(route.address), route.prefix))
                }
            }.onFailure { host.log("OpenVPN: route ${route.address}/${route.prefix} skipped: ${it.message}") }
        }
        builder.setMtu(mtu)
        val tun = builder.establish()
        if (tun == null) {
            fail("Android refused to create the VPN interface")
            send("needok 'OPENTUN' cancel\n")
            return
        }
        tun.use { send("needok 'OPENTUN' ok\n", it.fileDescriptor) }
        localIp = null
        localIpv6 = null
        dnsServers.clear()
        searchDomains.clear()
        routes.clear()
        tunnelUp = true
        firstTunnel.countDown()
    }

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 60L
        const val STOP_TIMEOUT_MILLIS = 3_000L

        // Directives that would run programs, write files, detach or take the
        // management interface; OpenVPN for Android drops them as well.
        val DROPPED = setOf(
            "management", "management-client", "management-hold", "management-query-passwords",
            "management-signal", "management-forget-disconnect", "management-client-auth",
            "up", "down", "route-up", "route-pre-down", "ipchange", "learn-address",
            "tls-verify", "auth-user-pass-verify", "client-connect", "client-disconnect",
            "script-security", "daemon", "log", "log-append", "status", "writepid", "user",
            "group", "chroot", "cd", "syslog", "service",
        )

        fun openVpnConfig(original: String, socketPath: String): String = buildString {
            // Windows-only options found in many provider files.
            append("ignore-unknown-option block-outside-dns register-dns\n")
            var block: String? = null
            for (raw in original.lineSequence()) {
                val line = raw.trim()
                if (block != null) {
                    append(raw).append('\n')
                    if (line == "</$block>") block = null
                    continue
                }
                Regex("^<([a-z0-9-]+)>$").find(line)?.let { block = it.groupValues[1] }
                val words = line.split(Regex("\\s+"))
                val name = words.first()
                when {
                    block == null && name in DROPPED -> append("# ").append(raw).append('\n')
                    // The login comes through the management interface.
                    block == null && name == "auth-user-pass" && words.size > 1 ->
                        append("auth-user-pass\n")
                    else -> append(raw).append('\n')
                }
            }
            append("\n# Pebble\n")
            append("management ").append(socketPath).append(" unix\n")
            append("management-client\n")
            append("management-query-passwords\n")
            append("management-hold\n")
            append("machine-readable-output\n")
            append("allow-recursive-routing\n")
            append("ifconfig-nowarn\n")
            append("setenv IV_GUI_VER \"Pebble\"\n")
        }

        // The management interface's quoting, as OpenVPN for Android does it.
        fun escape(value: String): String {
            if (value.isNotEmpty() && value.none { it.isWhitespace() || it == '"' || it == '\\' || it == '\'' }) {
                return value
            }
            return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
        }

        fun isTunDevice(device: String?): Boolean =
            device != null && (device.startsWith("tun") || device == "(null)" || device == "vpnservice-tun")

        fun String.isIpv4(): Boolean = split('.').let { parts ->
            parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }
        }

        fun ipv4(address: String): Long =
            address.split('.').fold(0L) { acc, part -> (acc shl 8) or (part.toLongOrNull() ?: 0L) }

        fun prefixOfMask(mask: String): Int? {
            if (!mask.isIpv4()) return null
            val inverted = ipv4(mask).inv() and 0xffffffffL
            if (inverted and (inverted + 1) != 0L) return null
            return 32 - java.lang.Long.bitCount(inverted)
        }

        fun network(address: String, prefix: Int): String {
            val mask = if (prefix == 0) 0L else (0xffffffffL shl (32 - prefix)) and 0xffffffffL
            val value = ipv4(address) and mask
            return (3 downTo 0).joinToString(".") { ((value shr (it * 8)) and 0xff).toString() }
        }

        fun splitCidr(value: String): Pair<String, Int> {
            val slash = value.indexOf('/')
            return if (slash < 0) {
                value to 128
            } else {
                value.substring(0, slash) to value.substring(slash + 1).toInt()
            }
        }
    }
}
