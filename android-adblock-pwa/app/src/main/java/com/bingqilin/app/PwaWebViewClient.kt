package com.bingqilin.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.edsuns.adfilter.AdFilter

/**
 * Locks navigation to a single site: requests to [allowedHost] (or its
 * subdomains) load inside the WebView, everything else is handed off to
 * the system browser. All sub-resource requests are run through [adFilter]
 * so ads/trackers never load in the first place.
 */
class PwaWebViewClient(
    private val context: Context,
    private val adFilter: AdFilter,
    private val allowedHost: String,
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val result = adFilter.shouldIntercept(view, request)
        return result.resourceResponse
    }

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val uri = request.url
        if (isOnSite(uri)) {
            return false
        }
        return openExternally(uri)
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
        super.onPageStarted(view, url, favicon)
        adFilter.performScript(view, url)
    }

    private fun isOnSite(uri: Uri): Boolean {
        val host = uri.host ?: return false
        return host.equals(allowedHost, ignoreCase = true) ||
            host.endsWith(".$allowedHost", ignoreCase = true)
    }

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
