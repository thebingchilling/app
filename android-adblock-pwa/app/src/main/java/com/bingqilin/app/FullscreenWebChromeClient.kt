package com.bingqilin.app

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.FrameLayout

/**
 * WebView has no built-in support for the HTML5 Fullscreen API (what the
 * player's own fullscreen button calls) - without these two callbacks
 * implemented, a page's `element.requestFullscreen()` silently does
 * nothing. This adds the fullscreen video/element as an overlay covering
 * the app's whole content area (on top of [normalContent]).
 *
 * This deliberately does NOT try to also hide the system status/nav bars
 * or make the window edge-to-edge for the duration: doing that means
 * toggling the window's inset-fitting, which makes the WebView's own
 * env(safe-area-inset-*) values change (and animate, since hiding/showing
 * system bars is itself an animated transition) - the site's sticky top
 * bar reacts to that via its own padding, which read as it visibly
 * jumping/getting pushed down around the fullscreen transition. The video
 * still fills the whole normal content area either way; only the thin
 * system bar strip stays visible, which is a fine trade for not having
 * insets fluctuate under the WebView at all.
 */
class FullscreenWebChromeClient(
    private val activity: Activity,
    private val normalContent: View,
    private val onFullscreenChanged: (Boolean) -> Unit,
) : WebChromeClient() {

    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null

    private val fullscreenContainer: FrameLayout by lazy {
        FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.BLACK)
        }
    }

    val isFullscreen: Boolean get() = customView != null

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback

        fullscreenContainer.addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        (activity.window.decorView as FrameLayout).addView(fullscreenContainer)
        normalContent.visibility = View.GONE
        onFullscreenChanged(true)
    }

    override fun onHideCustomView() {
        if (customView == null) return

        (activity.window.decorView as FrameLayout).removeView(fullscreenContainer)
        fullscreenContainer.removeAllViews()
        normalContent.visibility = View.VISIBLE

        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null
        onFullscreenChanged(false)
    }

    /** Called when the user presses back while fullscreen, instead of navigating WebView history. */
    fun exitFullscreen() {
        onHideCustomView()
    }
}
