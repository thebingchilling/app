package dev.tidewall.data

import kotlinx.serialization.Serializable

/** Which engine runs a profile. */
@Serializable
enum class ProfileKind(val label: String, val engine: String) {
    /** mihomo proxy profile: VLESS, VMess, Trojan, Shadowsocks, OpenVPN, WireGuard... */
    CLASH("Proxy", "mihomo"),

    /** WireGuard / AmneziaWG .conf run directly on the VPN interface. */
    WIREGUARD("WireGuard", "Direct WireGuard"),

    /** .ovpn run by the official OpenVPN 3 core. */
    OPENVPN("OpenVPN", "Direct OpenVPN"),
}

/** Traffic quota reported by a subscription's `subscription-userinfo` header. */
@Serializable
data class SubscriptionInfo(
    val upload: Long = 0,
    val download: Long = 0,
    val total: Long = 0,
    /** Unix seconds, 0 when unknown. */
    val expire: Long = 0,
)

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val kind: ProfileKind,
    /** Subscription URL for CLASH profiles that update from the network. */
    val url: String? = null,
    /** Hours between automatic updates; 0 disables auto-update. */
    val updateIntervalHours: Int = 24,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val subscription: SubscriptionInfo? = null,
    /** AmneziaWG obfuscation parameters present (WIREGUARD). */
    val amnezia: Boolean = false,
    /** OpenVPN credentials, for servers that use auth-user-pass. */
    val username: String? = null,
    val password: String? = null,
    /** The OpenVPN server asks for a username and password (auth-user-pass). */
    val needsLogin: Boolean = false,
    /** Proxy selector choices (group -> proxy), restored whenever the profile is loaded. */
    val selected: Map<String, String> = emptyMap(),
    /** Short summary shown under the name, e.g. "12 proxies" or the server. */
    val summary: String = "",
) {
    val badge: String
        get() = when (kind) {
            ProfileKind.CLASH -> if (url != null) "Subscription" else "Proxy"
            ProfileKind.WIREGUARD -> if (amnezia) "AmneziaWG" else "WireGuard"
            ProfileKind.OPENVPN -> "OpenVPN"
        }

    /** An OpenVPN profile that cannot connect until the user enters a login. */
    val missingLogin: Boolean get() = kind == ProfileKind.OPENVPN && needsLogin && username.isNullOrBlank()

    val fileExtension: String
        get() = when (kind) {
            ProfileKind.CLASH -> "yaml"
            ProfileKind.WIREGUARD -> "conf"
            ProfileKind.OPENVPN -> "ovpn"
        }
}
