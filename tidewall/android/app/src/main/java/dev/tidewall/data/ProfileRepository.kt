package dev.tidewall.data

import android.content.Context
import dev.tidewall.core.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Stores profiles as files in app-private storage: an index.json with the
 * metadata and one content file per profile (.yaml, .conf or .ovpn).
 */
class ProfileRepository(context: Context) {
    private val dir = File(context.filesDir, "profiles").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")
    private val mutex = Mutex()
    private val serializer = ListSerializer(Profile.serializer())

    private val _profiles = MutableStateFlow(loadIndex())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private fun loadIndex(): List<Profile> = runCatching {
        Engine.json.decodeFromString(serializer, indexFile.readText())
    }.getOrDefault(emptyList())

    private fun saveIndex(list: List<Profile>) {
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(Engine.json.encodeToString(serializer, list))
        tmp.renameTo(indexFile)
        _profiles.value = list
    }

    fun get(id: String?): Profile? = id?.let { i -> _profiles.value.firstOrNull { it.id == i } }

    private fun file(p: Profile) = File(dir, "${p.id}.${p.fileExtension}")

    suspend fun content(p: Profile): String = withContext(Dispatchers.IO) { file(p).readText() }

    private suspend fun upsert(p: Profile, text: String?) = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (text != null) file(p).writeText(text)
            val list = _profiles.value.toMutableList()
            val i = list.indexOfFirst { it.id == p.id }
            if (i >= 0) list[i] = p else list.add(p)
            saveIndex(list)
        }
    }

    suspend fun delete(p: Profile) = mutex.withLock {
        withContext(Dispatchers.IO) {
            file(p).delete()
            saveIndex(_profiles.value.filterNot { it.id == p.id })
        }
    }

    suspend fun rename(p: Profile, name: String) = upsert(p.copy(name = name.trim().ifEmpty { p.name }), null)

    suspend fun setUpdateInterval(p: Profile, hours: Int) = upsert(p.copy(updateIntervalHours = hours), null)

    suspend fun setCredentials(p: Profile, user: String, pass: String) =
        upsert((get(p.id) ?: p).copy(username = user.trim(), password = pass), null)

    /** Remembers the proxy chosen in a selector group of a proxy profile. */
    suspend fun setSelected(id: String, group: String, proxy: String) {
        val p = get(id) ?: return
        if (p.selected[group] == proxy) return
        upsert(p.copy(selected = p.selected + (group to proxy)), null)
    }

    private fun newId() = UUID.randomUUID().toString().substring(0, 8)

    private fun clashSummary(yaml: String): String = runCatching {
        val s = Engine.profileStats(yaml)
        buildList {
            if (s.proxies > 0) add("${s.proxies} proxies")
            if (s.providers > 0) add("${s.providers} providers")
            if (s.rules > 0) add("${s.rules} rules")
        }.joinToString(" · ")
    }.getOrDefault("")

    /**
     * Imports pasted text or a file. Detects Clash YAML, share links, .ovpn
     * and WireGuard configs. OpenVPN profiles that need a login are saved with
     * [Profile.missingLogin] set; the UI then asks for it.
     */
    suspend fun importText(
        text: String,
        name: String? = null,
        fileName: String? = null,
    ): Profile = withContext(Dispatchers.IO) {
        val body = text.trimStart('﻿')
        val fallbackName = name?.takeIf { it.isNotBlank() } ?: ContentDetector.nameFromFile(fileName)
        when (ContentDetector.detect(body, fileName)) {
            ContentType.CLASH_YAML -> {
                Engine.validateProfile(body)
                val p = Profile(newId(), fallbackName ?: "Proxy profile", ProfileKind.CLASH, summary = clashSummary(body))
                upsert(p, body)
                p
            }
            ContentType.LINKS -> {
                val decoded = if (ContentDetector.hasLinks(body)) body else ContentDetector.decodeBase64(body) ?: body
                val yaml = Engine.profileFromLinks(decoded)
                val p = Profile(newId(), fallbackName ?: "Imported links", ProfileKind.CLASH, summary = clashSummary(yaml))
                upsert(p, yaml)
                p
            }
            ContentType.OPENVPN -> {
                val info = Engine.inspectOvpn(body)
                val p = Profile(
                    newId(), fallbackName ?: info.name, ProfileKind.OPENVPN,
                    // An inline <auth-user-pass> block carries the login itself.
                    username = info.username,
                    password = info.password,
                    needsLogin = info.needsPassword,
                    summary = "${info.server}:${info.port} ${info.proto.uppercase()}",
                )
                upsert(p, body)
                p
            }
            ContentType.WIREGUARD -> {
                val info = Engine.inspectWireGuard(body)
                val p = Profile(
                    newId(), fallbackName ?: (info.endpoints?.firstOrNull() ?: "WireGuard"), ProfileKind.WIREGUARD,
                    amnezia = info.amnezia,
                    summary = (info.endpoints?.firstOrNull() ?: "") + (info.addresses?.firstOrNull()?.let { " · $it" } ?: ""),
                )
                upsert(p, body)
                p
            }
            ContentType.UNKNOWN -> throw IllegalArgumentException(
                "Not recognised. Supported: Clash/mihomo YAML, share links, .ovpn and WireGuard .conf files.",
            )
        }
    }

    private data class Fetched(val body: String, val info: SubscriptionInfo?, val fileName: String?)

    private fun fetch(url: String): Fetched {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        // Providers pick the Clash format based on the client's User-Agent.
        conn.setRequestProperty("User-Agent", "clash.meta mihomo/${dev.tidewall.libcore.Libcore.MihomoVersion} Tidewall")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("Server answered HTTP $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return Fetched(
                body,
                ContentDetector.parseSubscriptionUserInfo(conn.getHeaderField("subscription-userinfo")),
                ContentDetector.fileNameFromDisposition(conn.getHeaderField("content-disposition")),
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun toClashYaml(body: String): String {
        return when (ContentDetector.detect(body)) {
            ContentType.CLASH_YAML -> body.also { Engine.validateProfile(it) }
            ContentType.LINKS -> Engine.profileFromLinks(
                if (ContentDetector.hasLinks(body)) body else ContentDetector.decodeBase64(body) ?: body,
            )
            else -> throw IllegalArgumentException("The subscription did not return a Clash profile or share links")
        }
    }

    /** Adds a subscription URL as a new auto-updating profile. */
    suspend fun importUrl(url: String, name: String? = null): Profile = withContext(Dispatchers.IO) {
        val f = fetch(url.trim())
        val yaml = toClashYaml(f.body)
        val profileName = name?.takeIf { it.isNotBlank() }
            ?: ContentDetector.nameFromFile(f.fileName)
            ?: runCatching { URL(url).host }.getOrNull()
            ?: "Subscription"
        val p = Profile(
            newId(), profileName, ProfileKind.CLASH, url = url.trim(),
            subscription = f.info, summary = clashSummary(yaml),
        )
        upsert(p, yaml)
        p
    }

    /** Re-downloads a subscription profile. */
    suspend fun update(p: Profile): Profile = withContext(Dispatchers.IO) {
        val url = p.url ?: throw IllegalArgumentException("${p.name} is not a subscription")
        val f = fetch(url)
        val yaml = toClashYaml(f.body)
        val updated = p.copy(updatedAt = System.currentTimeMillis(), subscription = f.info ?: p.subscription, summary = clashSummary(yaml))
        upsert(updated, yaml)
        updated
    }

    /** Saves edited content after validating it with the right engine. */
    suspend fun saveContent(p: Profile, text: String) = withContext(Dispatchers.IO) {
        val summary = when (p.kind) {
            ProfileKind.CLASH -> {
                Engine.validateProfile(text)
                clashSummary(text)
            }
            ProfileKind.OPENVPN -> Engine.inspectOvpn(text).let { "${it.server}:${it.port} ${it.proto.uppercase()}" }
            ProfileKind.WIREGUARD -> Engine.inspectWireGuard(text).let {
                (it.endpoints?.firstOrNull() ?: "") + (it.addresses?.firstOrNull()?.let { a -> " · $a" } ?: "")
            }
        }
        var updated = p.copy(updatedAt = System.currentTimeMillis(), summary = summary)
        if (p.kind == ProfileKind.OPENVPN) {
            val info = Engine.inspectOvpn(text)
            updated = updated.copy(needsLogin = info.needsPassword)
            if (info.username != null) updated = updated.copy(username = info.username, password = info.password)
        }
        upsert(updated, text)
    }

    /** Creates a Proxy-mode copy of an OpenVPN or WireGuard profile. */
    suspend fun convertToProxy(p: Profile): Profile = withContext(Dispatchers.IO) {
        val text = content(p)
        val name = p.name
        val yaml = when (p.kind) {
            ProfileKind.OPENVPN -> Engine.profileFromOvpn(text, name, p.username.orEmpty(), p.password.orEmpty())
            ProfileKind.WIREGUARD -> Engine.profileFromWireGuard(text, name)
            ProfileKind.CLASH -> throw IllegalArgumentException("Already a proxy profile")
        }
        val np = Profile(newId(), "$name (Proxy mode)", ProfileKind.CLASH, summary = clashSummary(yaml))
        upsert(np, yaml)
        np
    }

    /** Adds a single proxy (YAML mapping) to an existing profile, or a new one when [target] is null. */
    suspend fun addProxy(target: Profile?, proxyYaml: String, newName: String): Profile = withContext(Dispatchers.IO) {
        if (target == null) {
            val yaml = Engine.addProxyToProfile("", proxyYaml)
            val p = Profile(newId(), newName, ProfileKind.CLASH, summary = clashSummary(yaml))
            upsert(p, yaml)
            p
        } else {
            require(target.kind == ProfileKind.CLASH) { "Proxies can only be added to proxy profiles" }
            val yaml = Engine.addProxyToProfile(content(target), proxyYaml)
            val p = target.copy(updatedAt = System.currentTimeMillis(), summary = clashSummary(yaml))
            upsert(p, yaml)
            p
        }
    }

    /** Subscription profiles whose auto-update interval has elapsed. */
    fun dueForUpdate(now: Long = System.currentTimeMillis()): List<Profile> = _profiles.value.filter {
        it.url != null && it.updateIntervalHours > 0 && now - it.updatedAt >= it.updateIntervalHours * 3_600_000L
    }
}
