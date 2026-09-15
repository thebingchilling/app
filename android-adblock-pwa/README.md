# Bingqilin (Adblock PWA)

An Android app that wraps [Bingqilin](https://thebingchilling.github.io/)
(from `thebingchilling/thebingchilling.github.io`) — a movies/TV/live-TV &
radio PWA — in a chrome-less WebView.

There is no third-party ad-blocking library, no ad-domain list, and no
runtime filter-list download. `PwaWebViewClient` just refuses to navigate
anywhere off `pwa_host` — popups, popunders, and any other outbound link
alike never leave the app. See "How navigation is locked down" below. (Two
earlier iterations tried harder to be a real browser: first pulling in
[Edsuns/AdblockAndroid](https://github.com/Edsuns/AdblockAndroid) + AdGuard's
filter lists for general ad/tracker blocking — which pulled in a JitPack
dependency with a native-code sub-module and dead jcenter transitives, and
in fact failed to build — then a gesture + known-ad-domain heuristic that
still let genuine outbound links through. Since nothing on this site needs
to leave it, blocking everything off-site outright is both simpler and more
robust than maintaining either a filter list or an ad-domain list.)

## Pointing it at a different site

It's currently locked to Bingqilin. To retarget it, edit
`app/src/main/res/values/strings.xml`:

```xml
<string name="pwa_url" translatable="false">https://your-site.example/</string>
<string name="pwa_host" translatable="false">your-site.example</string>
```

- `pwa_url` is the page loaded on launch.
- `pwa_host` is the only domain the app will ever navigate to: any link to
  that host or its subdomains loads inside the WebView, anything else
  (a different domain, `mailto:`, `tel:`, etc.) is silently dropped by
  `PwaWebViewClient` — there's no fallback to the system browser.

Also update `applicationId`/`namespace` in `app/build.gradle.kts`,
`app_name` in `strings.xml`, and the launcher icons in `res/mipmap-*`
(currently Bingqilin's own PWA icons, copied from its manifest) before
reusing this for another site.

## How navigation is locked down

- `window.open()` / `target="_blank"` popups are blocked by never enabling
  `setSupportMultipleWindows` / `javaScriptCanOpenWindowsAutomatically` and
  never implementing `WebChromeClient.onCreateWindow` — WebView's default
  behavior for both is to do nothing.
- Every other **main-frame** navigation goes through
  `PwaWebViewClient.shouldOverrideUrlLoading`, which checks the target host
  against `pwa_host` and its subdomains. A match loads in the WebView
  (`return false`); anything else is dropped (`return true`, and nothing
  further happens) — no distinction between a popunder redirect and a link
  the user actually tapped, since neither has anywhere to go.
- Sub-frame navigations (`!request.isForMainFrame`) are always let through
  regardless of host. The video player is a cross-origin iframe
  (`#playerFrame` on the movies/TV page loads from a separate embed/CDN
  domain) - locking those down the same way as top-level navigation would
  break the player itself, not just off-site link-outs. `CookieManager`
  also has third-party cookies explicitly enabled for the same reason:
  WebView blocks them by default, which can break an embed that relies on
  its own session/token cookies.

## UI behavior

- **Pull-to-refresh**: the WebView sits in a `SwipeRefreshLayout`; pulling
  down reloads the current page. The spinner stops on `onPageFinished`.
  Bingqilin's pages scroll a nested container rather than the WebView's own
  document - and which container varies by page (the Tools page scrolls
  `.app-main` directly, the movies/TV page scrolls a `.view` panel nested
  inside it) - so `WebView.canScrollVertically()`, what `SwipeRefreshLayout`
  checks by default to decide if it may intercept a drag, always reads "at
  the top" regardless of where the page actually is, and would otherwise
  hijack upward swipes anywhere on the page. `PwaWebViewClient` injects a
  script on every page load that adds a capturing `scroll` listener on
  `window` - `scroll` doesn't bubble, but a capturing listener still sees
  it fire on whichever descendant actually scrolled, so this needs no
  per-page container selector - and reports that element's position through
  `ScrollTopBridge` (`window.BQNative`). `swipeRefresh.isEnabled` is only
  kept `true` while the last-scrolled element is genuinely at its top.
- **Back**: a real `OnBackPressedCallback` (not a `KeyEvent` override, which
  doesn't reliably fire for gesture-nav swipe-back on modern Android) exits
  fullscreen first if the player is fullscreen, otherwise goes back through
  the WebView's own history, and is only enabled when fullscreen or
  `webView.canGoBack()` — otherwise back falls through to closing the app.
  `enableOnBackInvokedCallback="true"` in the manifest gets the predictive
  back animation on Android 13+.
- **Player fullscreen**: plain `WebView` has no built-in support for the
  HTML5 Fullscreen API the player's fullscreen button calls -
  `element.requestFullscreen()` silently no-ops without a `WebChromeClient`
  implementing `onShowCustomView`/`onHideCustomView`. `FullscreenWebChromeClient`
  adds the fullscreen view as a full-window overlay above everything
  (including the `SwipeRefreshLayout`) and hides the system bars for the
  duration via `WindowInsetsControllerCompat`.
- No progress bar — the pull-to-refresh spinner is the only loading
  indicator, shown only when the user asked for a reload.
- **Splash screen**: uses `androidx.core:core-splashscreen` so it follows
  the system light/dark theme like the rest of the app. `MainActivity`'s
  manifest theme is `Theme.AdblockPwa.Splash` (background = `@color/background`,
  icon = `@mipmap/ic_launcher`), which `installSplashScreen()` swaps for
  `Theme.AdblockPwa.NoActionBar` once the activity is ready.
  `values-night/colors.xml` gives `background` a dark value that matches
  Bingqilin's own dark-theme surface color, so light/dark mode looks
  consistent between the splash, the native chrome, and the site itself.

## Building

Requires the Android SDK (command-line tools + platform 34). With that on
your `PATH`/`ANDROID_HOME`:

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

A GitHub Actions workflow (`.github/workflows/build-adblock-pwa-apk.yml`)
builds the debug APK on every push that touches this folder and uploads it
as a build artifact, since GitHub-hosted runners already have the Android
SDK installed.

## Notes / things to tune per-site

- If the site itself ever needs a legitimate outbound link to work (e.g. a
  "share" button), it'll be silently dropped along with everything else —
  there's no allowlist for that, by design.
- `minSdk` is 26 (Android 8.0), so only the adaptive-icon mipmap set is
  needed (no legacy pre-API-26 fallback icons).
- The launcher icon files under `res/mipmap-*` are Bingqilin's own
  `icon-192.png` / `icon-maskable-192.png` from its PWA manifest, and the
  adaptive-icon background color matches its `theme_color` (`#f7ebdd`).
  The maskable PNG is full-bleed to its own edges (per the W3C maskable-icon
  spec), but Android's adaptive-icon mask only guarantees the inner ~66dp of
  the 108dp canvas survives, so `drawable/ic_launcher_foreground_inset.xml`
  insets it by 16.7% on each side before it's used as the foreground layer —
  without that inset it renders zoomed-in/cropped on most launchers.
