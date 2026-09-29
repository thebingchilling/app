package dev.tidewall.autoconnect

import dev.tidewall.data.AppSettings

enum class NetworkType { WIFI, CELLULAR, ETHERNET, NONE }

/** The physical (non-VPN) network the phone is on. */
data class CurrentNetwork(val type: NetworkType, val ssid: String? = null)

enum class AutoAction { CONNECT, DISCONNECT, NONE }

/** Pure auto-connect decision logic (unit tested). */
object AutoConnectRules {
    fun cleanSsid(raw: String?): String? {
        val s = raw?.trim()?.removeSurrounding("\"") ?: return null
        return s.takeUnless { it.isEmpty() || it == "<unknown ssid>" }
    }

    fun decide(network: CurrentNetwork, s: AppSettings): AutoAction = when (network.type) {
        NetworkType.WIFI -> {
            val trusted = network.ssid != null && network.ssid in s.trustedSsids
            when {
                trusted -> if (s.disconnectOnTrusted) AutoAction.DISCONNECT else AutoAction.NONE
                s.connectOnUntrustedWifi -> AutoAction.CONNECT
                else -> AutoAction.NONE
            }
        }
        NetworkType.CELLULAR -> if (s.connectOnMobile) AutoAction.CONNECT else AutoAction.NONE
        NetworkType.ETHERNET, NetworkType.NONE -> AutoAction.NONE
    }
}
