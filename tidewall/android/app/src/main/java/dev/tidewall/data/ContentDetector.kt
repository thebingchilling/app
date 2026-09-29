package dev.tidewall.data

import java.util.Base64

/** What a piece of imported text is. */
enum class ContentType { CLASH_YAML, LINKS, OPENVPN, WIREGUARD, UNKNOWN }

/** Pure detection and parsing helpers, kept free of Android APIs for unit tests. */
object ContentDetector {
    private val linkRegex = Regex(
        "^(vless|vmess|ss|ssr|trojan|hysteria|hysteria2|hy2|tuic|anytls|socks5?|https?|wireguard|wg|mierus?)://",
        RegexOption.IGNORE_CASE,
    )

    fun detect(text: String, fileName: String? = null): ContentType {
        val name = fileName?.lowercase().orEmpty()
        val body = text.trimStart('﻿').trim()
        if (body.isEmpty()) return ContentType.UNKNOWN

        if (name.endsWith(".ovpn") || looksLikeOvpn(body)) return ContentType.OPENVPN
        if (looksLikeWireGuard(body)) return ContentType.WIREGUARD
        if (looksLikeClash(body)) return ContentType.CLASH_YAML
        if (hasLinks(body)) return ContentType.LINKS
        decodeBase64(body)?.let { if (hasLinks(it)) return ContentType.LINKS }
        return ContentType.UNKNOWN
    }

    private fun lines(text: String) = text.lineSequence().map { it.trim() }

    fun looksLikeOvpn(text: String): Boolean {
        val ls = lines(text).toList()
        val hasRemote = ls.any { it.startsWith("remote ") }
        return hasRemote && (ls.any { it == "client" || it.startsWith("dev tun") } || text.contains("<ca>"))
    }

    /**
     * True when a .ovpn carries a client certificate (inline <cert>/<key>,
     * <pkcs12>, or cert/key/pkcs12 directives). Without one the server
     * authenticates by username/password only; OpenVPN 3 would otherwise
     * look for the certificate in the Android keystore.
     */
    fun ovpnHasClientCert(text: String): Boolean = lines(text).any { l ->
        val d = l.substringBefore(' ').substringBefore('\t').lowercase()
        d in setOf("cert", "key", "pkcs12", "<cert>", "<key>", "<pkcs12>")
    }

    fun looksLikeWireGuard(text: String): Boolean =
        lines(text).any { it.equals("[Interface]", ignoreCase = true) } &&
            lines(text).any { it.replace(" ", "").startsWith("PrivateKey=", ignoreCase = true) }

    fun looksLikeClash(text: String): Boolean =
        text.lineSequence().any {
            it.startsWith("proxies:") || it.startsWith("proxy-providers:") || it.startsWith("proxy-groups:")
        }

    fun hasLinks(text: String): Boolean = lines(text).any { linkRegex.containsMatchIn(it) }

    fun decodeBase64(text: String): String? {
        val compact = text.filterNot { it.isWhitespace() }
        if (compact.length < 8 || !compact.all { it.isLetterOrDigit() || it in "+/=-_" }) return null
        val decoders = listOf(Base64.getDecoder(), Base64.getUrlDecoder())
        for (d in decoders) {
            val padded = compact.padEnd((compact.length + 3) / 4 * 4, '=')
            val bytes = runCatching { d.decode(padded) }.getOrNull() ?: continue
            return String(bytes, Charsets.UTF_8)
        }
        return null
    }

    /** Parses `upload=1; download=2; total=3; expire=4`. */
    fun parseSubscriptionUserInfo(header: String?): SubscriptionInfo? {
        if (header.isNullOrBlank()) return null
        val map = header.split(';').mapNotNull {
            val (k, v) = it.split('=', limit = 2).takeIf { p -> p.size == 2 } ?: return@mapNotNull null
            k.trim().lowercase() to (v.trim().toDoubleOrNull()?.toLong() ?: return@mapNotNull null)
        }.toMap()
        if (map.isEmpty()) return null
        return SubscriptionInfo(
            upload = map["upload"] ?: 0,
            download = map["download"] ?: 0,
            total = map["total"] ?: 0,
            expire = map["expire"] ?: 0,
        )
    }

    /** Extracts a filename from a Content-Disposition header, if any. */
    fun fileNameFromDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        Regex("filename\\*=(?:UTF-8'')?([^;]+)", RegexOption.IGNORE_CASE).find(header)?.let {
            return java.net.URLDecoder.decode(it.groupValues[1].trim('"', ' '), "UTF-8")
        }
        Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(header)?.let {
            return it.groupValues[1].trim()
        }
        return null
    }

    /** A readable profile name from a file name: strips the extension. */
    fun nameFromFile(fileName: String?): String? =
        fileName?.substringAfterLast('/')?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }

    /** Pulls the subscription URL out of clash://install-config?url=...&name=... */
    fun installConfigUrl(uri: String): Pair<String, String?>? {
        val q = uri.substringAfter('?', "")
        if (q.isEmpty()) return null
        val params = q.split('&').mapNotNull {
            val (k, v) = it.split('=', limit = 2).takeIf { p -> p.size == 2 } ?: return@mapNotNull null
            k to java.net.URLDecoder.decode(v, "UTF-8")
        }.toMap()
        val url = params["url"] ?: return null
        return url to params["name"]
    }
}
