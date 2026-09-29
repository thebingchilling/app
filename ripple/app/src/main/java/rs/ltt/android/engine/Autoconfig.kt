/*
 * Copyright 2026 Ripple contributors
 * Licensed under the Apache License, Version 2.0
 */
package rs.ltt.android.engine

import android.util.Xml
import java.io.StringReader
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.xmlpull.v1.XmlPullParser

/** Incoming (IMAP or POP3) and outgoing (SMTP) server settings for an email address. */
data class MailSettings(
    val protocol: String,
    val incomingHost: String,
    val incomingPort: Int,
    val incomingSecurity: String,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpSecurity: String,
    val username: String,
    /** true when the settings came from a lookup rather than a guess */
    val discovered: Boolean,
) {
    val oauthProvider: OAuthProvider?
        get() = OAuthProvider.forHost(incomingHost)
}

/**
 * Finds mail server settings for an address the way Thunderbird does: built-in list, Thunderbird's
 * ISP database, the provider's own autoconfig file, the database entry of the MX host's domain,
 * and finally a guess.
 */
object Autoconfig {

    private val LOGGER = LoggerFactory.getLogger(Autoconfig::class.java)

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    private const val SSL = "SSL_TLS_REQUIRED"
    private const val STARTTLS = "STARTTLS_REQUIRED"
    private const val NONE = "NONE"

    private val GMAIL = listOf("gmail.com", "googlemail.com")
    private val MICROSOFT = listOf(
        "outlook.com", "hotmail.com", "live.com", "msn.com", "hotmail.co.uk", "hotmail.fr",
        "hotmail.de", "hotmail.it", "hotmail.es", "outlook.de", "outlook.fr", "outlook.es", "live.co.uk",
    )

    @JvmStatic
    fun builtIn(email: String): MailSettings? {
        val domain = email.substringAfterLast('@').lowercase()
        return when {
            domain in GMAIL -> google(email)
            domain in MICROSOFT -> microsoft(email)
            domain == "yahoo.com" || domain.startsWith("yahoo.") || domain == "ymail.com" ->
                MailSettings("imap", "imap.mail.yahoo.com", 993, SSL, "smtp.mail.yahoo.com", 465, SSL, email, true)
            domain in listOf("icloud.com", "me.com", "mac.com") ->
                MailSettings("imap", "imap.mail.me.com", 993, SSL, "smtp.mail.me.com", 587, STARTTLS, email.substringBefore('@'), true)
            domain == "aol.com" ->
                MailSettings("imap", "imap.aol.com", 993, SSL, "smtp.aol.com", 465, SSL, email, true)
            else -> null
        }
    }

    @JvmStatic
    fun google(email: String) =
        MailSettings("imap", "imap.gmail.com", 993, SSL, "smtp.gmail.com", 465, SSL, email, true)

    @JvmStatic
    fun microsoft(email: String) =
        MailSettings("imap", "outlook.office365.com", 993, SSL, "smtp.office365.com", 587, STARTTLS, email, true)

    /** Blocking. Never returns null: falls back to a guess based on the domain. */
    @JvmStatic
    fun discover(email: String): MailSettings {
        builtIn(email)?.let { return it }
        val domain = email.substringAfterLast('@').lowercase()
        val candidates = listOf(
            "https://autoconfig.thunderbird.net/v1.1/$domain",
            "https://autoconfig.$domain/mail/config-v1.1.xml?emailaddress=$email",
            "https://$domain/.well-known/autoconfig/mail/config-v1.1.xml?emailaddress=$email",
        )
        for (url in candidates) {
            fetch(url)?.let { xml -> parse(xml, email)?.let { return it } }
        }
        mxDomain(domain)?.let { mx ->
            if (mx != domain) {
                if (mx == "google.com" || mx == "googlemail.com") return google(email)
                if (mx == "outlook.com") return microsoft(email)
                fetch("https://autoconfig.thunderbird.net/v1.1/$mx")?.let { xml ->
                    parse(xml, email)?.let { return it }
                }
            }
        }
        return MailSettings("imap", "imap.$domain", 993, SSL, "smtp.$domain", 465, SSL, email, false)
    }

    private fun fetch(url: String): String? =
        try {
            http.newCall(Request.Builder().url(url.toHttpUrl()).build()).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: Exception) {
            LOGGER.debug("autoconfig lookup {} failed: {}", url, e.message)
            null
        }

    /** Registered domain of the primary MX host, looked up with DNS over HTTPS. */
    private fun mxDomain(domain: String): String? {
        val json = fetch("https://dns.google/resolve?name=$domain&type=MX") ?: return null
        return try {
            val answers = JSONObject(json).optJSONArray("Answer") ?: return null
            val records = (0 until answers.length())
                .map { answers.getJSONObject(it).getString("data").trim() }
                .mapNotNull {
                    val parts = it.split(Regex("\\s+"))
                    if (parts.size == 2) (parts[0].toIntOrNull() ?: 0) to parts[1].trimEnd('.') else null
                }
                .sortedBy { it.first }
            val host = records.firstOrNull()?.second?.lowercase() ?: return null
            val labels = host.split('.')
            if (labels.size >= 3 && labels[labels.size - 2].length <= 3 && labels.last().length == 2) {
                labels.takeLast(3).joinToString(".")
            } else {
                labels.takeLast(2).joinToString(".")
            }
        } catch (e: Exception) {
            null
        }
    }

    private class Server(
        var type: String = "",
        var host: String = "",
        var port: Int = 0,
        var socketType: String = "",
        var username: String = "",
    )

    @JvmStatic
    fun parse(xml: String, email: String): MailSettings? {
        val incoming = ArrayList<Server>()
        val outgoing = ArrayList<Server>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))
            var current: Server? = null
            var isIncoming = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "incomingServer" -> {
                            current = Server(type = parser.getAttributeValue(null, "type") ?: "")
                            isIncoming = true
                        }
                        "outgoingServer" -> {
                            current = Server(type = parser.getAttributeValue(null, "type") ?: "")
                            isIncoming = false
                        }
                        "hostname" -> current?.host = parser.nextText().trim()
                        "port" -> current?.port = parser.nextText().trim().toIntOrNull() ?: 0
                        "socketType" -> current?.socketType = parser.nextText().trim()
                        "username" -> current?.username = parser.nextText().trim()
                    }
                } else if (event == XmlPullParser.END_TAG &&
                    (parser.name == "incomingServer" || parser.name == "outgoingServer")
                ) {
                    current?.let { if (isIncoming) incoming.add(it) else outgoing.add(it) }
                    current = null
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            LOGGER.debug("Unable to parse autoconfig", e)
            return null
        }
        val inServer = incoming.firstOrNull { it.type == "imap" && it.socketType != "plain" }
            ?: incoming.firstOrNull { it.type == "pop3" && it.socketType != "plain" }
            ?: return null
        val outServer = outgoing.firstOrNull { it.type == "smtp" && it.socketType != "plain" } ?: return null
        return MailSettings(
            inServer.type,
            substitute(inServer.host, email),
            inServer.port,
            security(inServer.socketType),
            substitute(outServer.host, email),
            outServer.port,
            security(outServer.socketType),
            substitute(inServer.username.ifEmpty { "%EMAILADDRESS%" }, email),
            true,
        )
    }

    private fun security(socketType: String) =
        when (socketType.uppercase()) {
            "SSL" -> SSL
            "STARTTLS" -> STARTTLS
            else -> NONE
        }

    private fun substitute(value: String, email: String): String =
        value.replace("%EMAILADDRESS%", email)
            .replace("%EMAILLOCALPART%", email.substringBefore('@'))
            .replace("%EMAILDOMAIN%", email.substringAfterLast('@'))
}
