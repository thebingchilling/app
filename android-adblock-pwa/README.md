# Bingqilin (Adblock PWA)

A deliberately minimal Android app that wraps
[Bingqilin](https://thebingchilling.github.io/) (from
`thebingchilling/thebingchilling.github.io`) — a movies/TV/live-TV & radio
PWA — in a chrome-less WebView. It does exactly two things: locks
navigation to that one site, and blocks popups. That's it — no
pull-to-refresh, no fullscreen handling, no splash screen. Earlier
iterations of this app had all of those, plus a third-party ad-blocking
library before that; each one added its own class of bug (WebView/insets
interactions, scroll-container quirks, fullscreen-transition glitches,
dead upstream dependencies) for a feature that wasn't the actual point.
What's here now is what's left after removing everything that wasn't
"lock to one site" or "block popups."

## Pointing it at a different site

It's currently locked to Bingqilin. To retarget it, edit
`app/src/main/res/values/strings.xml`:

```xml
<string name="pwa_url" translatable="false">https://your-site.example/</string>
<string name="pwa_host" translatable="false">your-site.example</string>
```

- `pwa_url` is the page loaded on launch.
- `pwa_host` is the only domain the app will ever navigate to at the
  top level: any link to that host or its subdomains loads inside the
  WebView, anything else (a different domain, `mailto:`, `tel:`, etc.) is
  silently dropped by `PwaWebViewClient` — there's no fallback to the
  system browser.

Also update `applicationId`/`namespace` in `app/build.gradle.kts`,
`app_name` in `strings.xml`, and the launcher icons in `res/mipmap-*`
(currently Bingqilin's own PWA icons, copied from its manifest) before
reusing this for another site.

## How it works

The whole app is two small classes:

- **`PwaWebViewClient.shouldOverrideUrlLoading`** checks every **main-frame**
  navigation's target host against `pwa_host` and its subdomains. A match
  loads in the WebView (`return false`); anything else is dropped
  (`return true`) — no distinction between a popunder redirect and a link
  the user actually tapped, since neither has anywhere to go.
- **Sub-frame navigations** (`!request.isForMainFrame`) are always let
  through regardless of host. The video player is a cross-origin iframe
  (`#playerFrame` on the movies/TV page loads from a separate embed/CDN
  domain) — locking those down the same way as top-level navigation would
  break the player itself, not just off-site link-outs.
- **`window.open()`/`target="_blank"` popups** are blocked by never
  enabling `setSupportMultipleWindows`/`javaScriptCanOpenWindowsAutomatically`
  and never implementing `WebChromeClient.onCreateWindow` — WebView's
  default behavior for both is to do nothing. No custom `WebChromeClient`
  needed at all.
- **`CookieManager`** has third-party cookies explicitly enabled, since
  WebView blocks them by default and the cross-origin player embed depends
  on its own session/token cookies to work.
- **Back** is a plain `OnBackPressedCallback`: go back through the
  WebView's history if there is any, otherwise disable itself and fall
  through to the system default (closing the app).

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

- If the site itself ever needs a legitimate outbound link to work at the
  top level (e.g. a "share" button that isn't just a same-origin path),
  it'll be silently dropped along with everything else — there's no
  allowlist for that, by design.
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
- The site's own player fullscreen button calls `requestFullscreen()` on
  its player iframe (fixed on the website side, in
  `thebingchilling/thebingchilling.github.io`), which without a custom
  `WebChromeClient` implementing `onShowCustomView`/`onHideCustomView`
  will silently no-op in this app. That support was cut for simplicity —
  see the git history on this file if it's worth re-adding later.
