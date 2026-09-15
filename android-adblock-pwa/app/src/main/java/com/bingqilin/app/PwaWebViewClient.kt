package com.bingqilin.app

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Locks top-level navigation to a single site: requests to [allowedHost]
 * (or its subdomains) load in the WebView, everything else - popups,
 * popunders, and genuine outbound links alike - is dropped silently.
 *
 * Only applies to the main frame; sub-frame navigations (e.g. a video
 * player embed loading from a different domain) are always allowed
 * through, since blocking those breaks the page's own embedded content
 * rather than stopping anything.
 */
class PwaWebViewClient(
    private val allowedHost: String,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        if (!request.isForMainFrame) {
            return false
        }
        val host = request.url.host
        return host == null || !isOnSite(host)
    }

    private fun isOnSite(host: String): Boolean =
        host.equals(allowedHost, ignoreCase = true) || host.endsWith(".$allowedHost", ignoreCase = true)
}
