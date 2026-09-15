# Bingqilin (Adblock PWA)

An Android app that wraps [Bingqilin](https://thebingchilling.github.io/)
(from `thebingchilling/thebingchilling.github.io`) — a movies/TV/live-TV &
radio PWA — in a chrome-less WebView.

There is no third-party ad-blocking library and no runtime filter-list
download. What the site actually needed blocked was the video player's
popup/popunder ads, so that's what's vendored here as plain Kotlin in
`PwaWebViewClient.kt` and `KnownAdHosts.kt` — no JitPack/jcenter dependency,
nothing fetched at build or run time. See "How the popup blocking works"
below. (An earlier version of this app used
[Edsuns/AdblockAndroid](https://github.com/Edsuns/AdblockAndroid) +
AdGuard's filter lists for general ad/tracker blocking; that pulled in a
JitPack dependency with a native-code sub-module and some now-dead jcenter
transitive dependencies, which was more machinery than the actual
popup-blocking problem needed.)

## Pointing it at a different site

It's currently locked to Bingqilin. To retarget it, edit
`app/src/main/res/values/strings.xml`:

```xml
<string name="pwa_url" translatable="false">https://your-site.example/</string>
<string name="pwa_host" translatable="false">your-site.example</string>
```

- `pwa_url` is the page loaded on launch.
- `pwa_host` is the domain the app is locked to: any link to that host or
  its subdomains opens inside the WebView, anything else (a different
  domain, `mailto:`, `tel:`, etc.) is handed off to the system browser via
  `PwaWebViewClient`.

Also update `applicationId`/`namespace` in `app/build.gradle.kts`,
`app_name` in `strings.xml`, and the launcher icons in `res/mipmap-*`
(currently Bingqilin's own PWA icons, copied from its manifest) before
reusing this for another site.

## How the popup blocking works

- `window.open()` / `target="_blank"` popups are blocked by never enabling
  `setSupportMultipleWindows` / `javaScriptCanOpenWindowsAutomatically` and
  never implementing `WebChromeClient.onCreateWindow` — WebView's default
  behavior for both is to do nothing.
- The remaining pattern on these sites is a script-triggered top-level
  navigation (a "popunder"): the player loads and, without any tap, sends
  the WebView to an ad domain. `PwaWebViewClient.shouldOverrideUrlLoading`
  drops any off-site navigation that doesn't carry
  `WebResourceRequest.hasGesture()`.
- Some sites also lay an invisible ad-network click-catcher over the real
  play button, which *does* produce a genuine gesture. `KnownAdHosts.kt` is
  a small vendored list of popup ad-network domains (propellerads,
  exoclick, popads, etc.) that get dropped even with a gesture.
- A genuine off-site link the user taps (anything else) opens in the system
  browser instead of hijacking this app.

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

- `KnownAdHosts.kt` is a short, hand-picked list — if a new popup network
  shows up in the player, add its domain there.
- `minSdk` is 26 (Android 8.0), so only the adaptive-icon mipmap set is
  needed (no legacy pre-API-26 fallback icons).
- The launcher icon files under `res/mipmap-*` are Bingqilin's own
  `icon-192.png` / `icon-maskable-192.png` from its PWA manifest, and the
  adaptive-icon background color matches its `theme_color` (`#f7ebdd`).
