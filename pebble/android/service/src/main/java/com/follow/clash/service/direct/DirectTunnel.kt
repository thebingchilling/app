package com.follow.clash.service.direct

import android.content.Context
import android.net.VpnService
import com.google.gson.Gson
import java.io.File

/**
 * A WireGuard, AmneziaWG or OpenVPN file that runs on its own engine instead of mihomo. The app
 * saves it as one JSON line under [KEY] in the profile (lib/common/direct_tunnel.dart).
 */
data class DirectTunnel(
    val type: String,
    val config: String,
    val username: String? = null,
    val password: String? = null,
) {
    companion object {
        private const val KEY = "x-pebble-direct:"
        private val gson = Gson()

        /** The direct tunnel of profile [profileId], or null for a Clash profile. */
        fun read(context: Context, profileId: Int?): DirectTunnel? {
            if (profileId == null) return null
            val file = File(context.filesDir, "profiles/$profileId.yaml")
            if (!file.isFile) return null
            val line = file.bufferedReader().useLines { lines ->
                lines.firstOrNull { it.startsWith(KEY) }
            } ?: return null
            return runCatching {
                gson.fromJson(line.substring(KEY.length).trim(), DirectTunnel::class.java)
            }.getOrNull()?.takeIf { it.type.isNotEmpty() && it.config.isNotEmpty() }
        }
    }
}

/** What an engine needs from Pebble's VpnService. */
interface TunnelHost {
    val context: Context

    /**
     * A builder with the session name and FlClash's per-app VPN settings applied. When FlClash's
     * per-app list is off, the tunnel file's own [included]/[excluded] apps are used instead.
     */
    fun newBuilder(
        included: Set<String> = emptySet(),
        excluded: Set<String> = emptySet(),
    ): VpnService.Builder

    /** Keeps [fd] out of the VPN; false when the system refused. */
    fun protect(fd: Int): Boolean

    fun log(message: String)

    /** The engine stopped on its own (server gone, login rejected); the VPN should stop. */
    fun onEngineStopped(reason: String)
}

interface DirectEngine {
    /** Connects and brings the VPN interface up; throws when that fails. */
    fun start()

    fun stop()

    /** Bytes sent and received through the tunnel since [start]. */
    fun traffic(): Pair<Long, Long>

    companion object {
        fun create(tunnel: DirectTunnel, host: TunnelHost): DirectEngine = when (tunnel.type) {
            "wireguard" -> WireGuardEngine(host, OfficialWireGuard, tunnel.config)
            "amneziawg" -> WireGuardEngine(host, AmneziaWg, tunnel.config)
            "openvpn" -> OpenVpnEngine(host, tunnel)
            else -> error("Unknown tunnel type ${tunnel.type}")
        }
    }
}
