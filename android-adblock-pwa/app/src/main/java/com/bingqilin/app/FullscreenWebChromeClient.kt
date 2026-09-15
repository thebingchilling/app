package com.bingqilin.app

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * WebView has no built-in support for the HTML5 Fullscreen API (what the
 * player's own fullscreen button calls) - without these two callbacks
 * implemented, a page's `element.requestFullscreen()` silently does
 * nothing. This adds the fullscreen video/element as a full-window overlay
 * on top of everything (including [normalContent]) and hides the system
 * bars for the duration, for a genuinely immersive fullscreen.
 *
 * Hiding/showing the system bars changes the WebView's own
 * env(safe-area-inset-*) values, and showing them back is an animated
 * reveal, not instant. If [normalContent] (the WebView) is made visible
 * again before that reveal finishes, Chromium can render a frame with a
 * mid-transition inset value, which the site's sticky top bar reacts to
 * via its own padding - visible as it getting pushed down right after
 * exiting fullscreen. So on exit, revealing [normalContent] is deferred
 * until the reveal animation actually ends (with a timeout fallback for
 * any device/API level where no animation callback fires at all).
 */
class FullscreenWebChromeClient(
    private val activity: Activity,
    private val normalContent: View,
    private val onFullscreenChanged: (Boolean) -> Unit,
) : WebChromeClient() {

    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingReveal: Runnable? = null

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
        cancelPendingReveal()
        customView = view
        customViewCallback = callback

        fullscreenContainer.addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        (activity.window.decorView as FrameLayout).addView(fullscreenContainer)
        // Hide normalContent before touching system bars: it's the WebView,
        // and it's not visible to react to anything while the bars hide.
        normalContent.visibility = View.GONE
        hideSystemBars()
        onFullscreenChanged(true)
    }

    override fun onHideCustomView() {
        if (customView == null) return

        (activity.window.decorView as FrameLayout).removeView(fullscreenContainer)
        fullscreenContainer.removeAllViews()
        showSystemBarsThenReveal()

        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null
        onFullscreenChanged(false)
    }

    /** Called when the user presses back while fullscreen, instead of navigating WebView history. */
    fun exitFullscreen() {
        onHideCustomView()
    }

    private fun hideSystemBars() {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun showSystemBarsThenReveal() {
        val window = activity.window
        val decor = window.decorView

        cancelPendingReveal()
        val reveal = Runnable {
            ViewCompat.setWindowInsetsAnimationCallback(decor, null)
            WindowCompat.setDecorFitsSystemWindows(window, true)
            normalContent.visibility = View.VISIBLE
            pendingReveal = null
        }
        pendingReveal = reveal
        // Fallback in case no insets animation ever fires (older API levels,
        // or a device that just snaps bars in without one) - never leave
        // the WebView hidden indefinitely.
        mainHandler.postDelayed(reveal, REVEAL_FALLBACK_MS)

        ViewCompat.setWindowInsetsAnimationCallback(
            decor,
            object : WindowInsetsAnimationCompat.Callback(WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>,
                ) = insets

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.systemBars() != 0) {
                        mainHandler.removeCallbacks(reveal)
                        reveal.run()
                    }
                }
            },
        )
        WindowInsetsControllerCompat(window, decor).show(WindowInsetsCompat.Type.systemBars())
    }

    private fun cancelPendingReveal() {
        pendingReveal?.let { mainHandler.removeCallbacks(it) }
        pendingReveal = null
        ViewCompat.setWindowInsetsAnimationCallback(activity.window.decorView, null)
    }

    private companion object {
        const val REVEAL_FALLBACK_MS = 400L
    }
}
