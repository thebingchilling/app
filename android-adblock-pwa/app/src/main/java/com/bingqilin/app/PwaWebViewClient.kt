package com.bingqilin.app

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Locks navigation to a single site: requests to [allowedHost] (or its
 * subdomains) load in the WebView, everything else - popups, popunders,
 * and genuine outbound links alike - is dropped silently. No domain list
 * to maintain: anything off-site simply never leaves the app.
 */
class PwaWebViewClient(
    private val allowedHost: String,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val host = request.url.host
        return host == null || !isOnSite(host)
    }

    private fun isOnSite(host: String): Boolean =
        host.equals(allowedHost, ignoreCase = true) || host.endsWith(".$allowedHost", ignoreCase = true)
}
