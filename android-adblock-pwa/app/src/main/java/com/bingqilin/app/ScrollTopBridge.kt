package com.bingqilin.app

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/**
 * Bingqilin's pages scroll an inner `.app-main` container rather than the
 * WebView's own document, so WebView.canScrollVertically() - what
 * SwipeRefreshLayout checks to decide whether it may intercept a drag -
 * never reflects the real scroll position: it always reads "at the top",
 * so pull-to-refresh would otherwise hijack every upward swipe inside the
 * page, not just ones that start at the real top. The JS injected by
 * PwaWebViewClient reports the actual scroll position back through this.
 *
 * JavascriptInterface callbacks run on a background thread, so this hops
 * back to the main thread before touching any views.
 */
class ScrollTopBridge(private val onChange: (Boolean) -> Unit) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onScrollTopChanged(atTop: Boolean) {
        mainHandler.post { onChange(atTop) }
    }
}
