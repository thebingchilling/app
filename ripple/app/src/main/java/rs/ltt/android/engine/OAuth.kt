/*
 * Copyright 2026 Ripple contributors
 * Licensed under the Apache License, Version 2.0
 */
package rs.ltt.android.engine

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.fsck.k9.mail.AuthenticationFailedException
import com.fsck.k9.mail.oauth.OAuth2TokenProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.TokenResponse
import org.json.JSONObject
import org.slf4j.LoggerFactory
import rs.ltt.android.BuildConfig
import rs.ltt.android.database.AppDatabase

/** OAuth 2.0 providers Ripple can sign in to. Client IDs come from the build (see README). */
enum class OAuthProvider(
    val id: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val scopes: List<String>,
) {
    GOOGLE(
        "google",
        "https://accounts.google.com/o/oauth2/v2/auth",
        "https://oauth2.googleapis.com/token",
        listOf("https://mail.google.com/", "openid", "email"),
    ),
    MICROSOFT(
        "microsoft",
        "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
        "https://login.microsoftonline.com/common/oauth2/v2.0/token",
        listOf(
            "https://outlook.office.com/IMAP.AccessAsUser.All",
            "https://outlook.office.com/POP.AccessAsUser.All",
            "https://outlook.office.com/SMTP.Send",
            "offline_access",
            "openid",
            "email",
            "profile",
        ),
    ),
    ;

    val clientId: String
        get() =
            when (this) {
                GOOGLE -> BuildConfig.GOOGLE_CLIENT_ID
                MICROSOFT -> BuildConfig.MICROSOFT_CLIENT_ID
            }

    val isConfigured: Boolean
        get() = clientId.isNotBlank()

    /**
     * Google only accepts the reversed client id as scheme for installed apps; Microsoft accepts
     * any custom scheme registered for the app.
     */
    val redirectUri: Uri
        get() =
            when (this) {
                GOOGLE -> Uri.parse("${googleRedirectScheme(clientId)}:/oauth2redirect")
                MICROSOFT -> Uri.parse("app.ripple.mail://oauth2redirect")
            }

    val serviceConfiguration: AuthorizationServiceConfiguration
        get() =
            AuthorizationServiceConfiguration(
                Uri.parse(authorizationEndpoint),
                Uri.parse(tokenEndpoint),
            )

    companion object {
        @JvmStatic
        fun of(id: String?): OAuthProvider? = entries.firstOrNull { it.id == id }

        /** Which provider handles mail for the given IMAP host, if any. */
        @JvmStatic
        fun forHost(host: String?): OAuthProvider? {
            val h = host?.lowercase() ?: return null
            return when {
                h == "imap.gmail.com" || h == "imap.googlemail.com" || h == "pop.gmail.com" -> GOOGLE
                h == "outlook.office365.com" || h == "imap-mail.outlook.com" ||
                    h == "pop-mail.outlook.com" || h == "outlook.live.com" -> MICROSOFT
                else -> null
            }
        }

        @JvmStatic
        fun googleRedirectScheme(clientId: String): String {
            val prefix = clientId.removeSuffix(".apps.googleusercontent.com")
            return "com.googleusercontent.apps.$prefix"
        }

        /** Email addresses found in an OpenID Connect id_token. */
        @JvmStatic
        fun emailsFromIdToken(idToken: String?): Set<String> {
            if (idToken == null) return emptySet()
            val parts = idToken.split('.')
            if (parts.size < 2) return emptySet()
            return try {
                val payload =
                    String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
                val json = JSONObject(payload)
                listOf("email", "preferred_username", "upn")
                    .mapNotNull { json.optString(it).takeIf { v -> v.contains('@') } }
                    .toSet()
            } catch (e: Exception) {
                emptySet()
            }
        }
    }
}

/** Supplies access tokens to the IMAP/POP3/SMTP code, refreshing them with the stored AuthState. */
class AppAuthTokenProvider(
    context: Context,
    private val credentialsId: Long,
) : OAuth2TokenProvider {

    private val context = context.applicationContext
    private val accountDao = AppDatabase.getInstance(this.context).accountDao()

    private fun loadState(): AuthState {
        val json = accountDao.getOAuthState(credentialsId)
            ?: throw AuthenticationFailedException("Not signed in. Please sign in again.")
        return AuthState.jsonDeserialize(json)
    }

    private fun saveState(state: AuthState) {
        accountDao.setOAuthState(credentialsId, state.jsonSerializeString())
    }

    override val usernames: Set<String>
        get() = OAuthProvider.emailsFromIdToken(loadState().idToken)

    @Synchronized
    override fun getToken(timeoutMillis: Long): String {
        val state = loadState()
        val current = state.accessToken
        if (current != null && !state.needsTokenRefresh) {
            return current
        }
        val request =
            state.createTokenRefreshRequest()
        val latch = CountDownLatch(1)
        var response: TokenResponse? = null
        var exception: AuthorizationException? = null
        val service = AuthorizationService(context)
        try {
            service.performTokenRequest(request) { r, e ->
                response = r
                exception = e
                latch.countDown()
            }
            if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                throw AuthenticationFailedException("Timed out refreshing the sign-in token")
            }
        } finally {
            service.dispose()
        }
        state.update(response, exception)
        saveState(state)
        exception?.let {
            LOGGER.warn("Token refresh failed", it)
            if (it.type == AuthorizationException.TYPE_OAUTH_TOKEN_ERROR) {
                throw AuthenticationFailedException(
                    "Sign-in expired. Remove and add the account again.",
                    it,
                    it.errorDescription ?: it.error,
                )
            }
            throw AuthenticationFailedException("Unable to refresh the sign-in token", it)
        }
        return state.accessToken ?: throw AuthenticationFailedException("No access token")
    }

    @Synchronized
    override fun invalidateToken() {
        try {
            val state = loadState()
            state.needsTokenRefresh = true
            saveState(state)
        } catch (e: AuthenticationFailedException) {
            LOGGER.debug("Nothing to invalidate", e)
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(AppAuthTokenProvider::class.java)
    }
}

/** Hands out one access token; used while checking a freshly signed-in account. */
class StaticTokenProvider(private val accessToken: String, override val usernames: Set<String>) : OAuth2TokenProvider {
    override fun getToken(timeoutMillis: Long): String = accessToken

    override fun invalidateToken() = Unit
}
