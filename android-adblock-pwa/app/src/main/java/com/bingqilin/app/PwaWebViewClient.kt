package com.bingqilin.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Locks navigation to a single site and swallows the popup/popunder
 * redirects that free video players like to fire off:
 *
 * - Requests to [allowedHost] (or its subdomains) always load in the WebView.
 * - An off-site navigation that was NOT initiated by a real user tap
 *   ([WebResourceRequest.hasGesture]) is dropped silently - this is the
 *   classic "player loads, ad tab pops open on its own" pattern.
 * - An off-site navigation to a [isKnownAdHost] domain is dropped even if it
 *   does carry a gesture, since these sites also hide an invisible
 *   ad-network overlay on top of the real play button.
 * - Anything else off-site (a genuine link the user tapped) opens in the
 *   system browser instead of hijacking this app.
 */
class PwaWebViewClient(
    private val context: Context,
    private val allowedHost: String,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val uri = request.url
        val host = uri.host

        if (host != null && isOnSite(host)) {
            return false
        }
        if (host == null || !request.hasGesture() || isKnownAdHost(host)) {
            // Not a real navigation the user asked for - swallow it.
            return true
        }
        return openExternally(uri)
    }

    private fun isOnSite(host: String): Boolean =
        host.equals(allowedHost, ignoreCase = true) || host.endsWith(".$allowedHost", ignoreCase = true)

    private fun openExternally(uri: Uri): Boolean {
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (e: ActivityNotFoundException) {
            // No app can handle this scheme (e.g. an unsupported intent:// link) - ignore it.
            true
        }
    }
}
