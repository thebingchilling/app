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
    private val onPageFinished: () -> Unit,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val host = request.url.host
        return host == null || !isOnSite(host)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        view.evaluateJavascript(SCROLL_TRACKER_JS, null)
        onPageFinished.invoke()
    }

    private fun isOnSite(host: String): Boolean =
        host.equals(allowedHost, ignoreCase = true) || host.endsWith(".$allowedHost", ignoreCase = true)

    private companion object {
        // Reports the real scroll position of the page's actual scrolling
        // element (see ScrollTopBridge) - the WebView's own document never
        // scrolls on this site, so its native scroll position is useless.
        const val SCROLL_TRACKER_JS = """
            (function() {
              var el = document.querySelector('main.app-main')
                || document.querySelector('main')
                || document.scrollingElement
                || document.documentElement;
              if (!el || !window.BQNative) return;
              function report() { window.BQNative.onScrollTopChanged(el.scrollTop <= 0); }
              el.addEventListener('scroll', report, { passive: true });
              report();
            })();
        """
    }
}
