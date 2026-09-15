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
        // Reports the real scroll position of whatever the page's actual
        // scrolling element is (see ScrollTopBridge) - the WebView's own
        // document never scrolls on this site, so its native scroll
        // position is useless. `scroll` doesn't bubble, but a capturing
        // listener on window still sees it fire on any descendant, which
        // is what lets this work without hardcoding a container per page
        // (the movies/TV page scrolls a nested `.view` panel, not the
        // `.app-main` wrapper the Tools page scrolls directly).
        const val SCROLL_TRACKER_JS = """
            (function() {
              if (!window.BQNative) return;
              function scrollTopOf(target) {
                if (target === document || target === window) {
                  var el = document.scrollingElement || document.documentElement;
                  return el.scrollTop;
                }
                return target.scrollTop;
              }
              window.addEventListener('scroll', function(e) {
                window.BQNative.onScrollTopChanged(scrollTopOf(e.target) <= 0);
              }, { capture: true, passive: true });
              window.BQNative.onScrollTopChanged(true);
            })();
        """
    }
}
