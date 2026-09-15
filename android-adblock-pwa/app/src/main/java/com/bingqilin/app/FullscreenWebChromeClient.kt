package com.bingqilin.app

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * WebView has no built-in support for the HTML5 Fullscreen API (what the
 * player's own fullscreen button calls) - without these two callbacks
 * implemented, a page's `element.requestFullscreen()` silently does
 * nothing. This adds the fullscreen video/element as a full-window overlay
 * on top of everything (including [normalContent]) and hides the system
 * bars for the duration.
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
        // Toggle the window's inset-fitting before touching normalContent's
        // visibility: it's the WebView underneath, and if it's made visible
        // again while the window is still mid-transition, Chromium can latch
        // onto a stale env(safe-area-inset-*) value and not reflow until
        // something else forces a layout pass (looks like the page's own
        // top bar getting pushed down after exiting fullscreen).
        setSystemBarsHidden(true)
        normalContent.visibility = View.GONE
        onFullscreenChanged(true)
    }

    override fun onHideCustomView() {
        if (customView == null) return

        (activity.window.decorView as FrameLayout).removeView(fullscreenContainer)
        fullscreenContainer.removeAllViews()
        setSystemBarsHidden(false)
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

    private fun setSystemBarsHidden(hidden: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, !hidden)
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        if (hidden) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
