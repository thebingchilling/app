# Adblock PWA

A minimal Android app that wraps **one** website in a chrome-less WebView
and blocks ads/trackers in it using [Edsuns/AdblockAndroid](https://github.com/Edsuns/AdblockAndroid),
loaded with AdGuard's own filter subscriptions:

- AdGuard Base filter
- AdGuard Mobile Ads filter
- AdGuard Tracking Protection filter

These are downloaded automatically on first launch (see `App.kt`) — no
settings screen, no filter picker, nothing for the end user to configure.

## Pointing it at your site

Edit `app/src/main/res/values/strings.xml`:

```xml
<string name="pwa_url" translatable="false">https://your-site.example/</string>
<string name="pwa_host" translatable="false">your-site.example</string>
```

- `pwa_url` is the page loaded on launch.
- `pwa_host` is the domain the app is locked to: any link to that host or
  its subdomains opens inside the WebView, anything else (a different
  domain, `mailto:`, `tel:`, etc.) is handed off to the system browser via
  `PwaWebViewClient`.

Also update `applicationId`/`namespace` in `app/build.gradle.kts` and
`app_name` in `strings.xml` before shipping it as its own app.

## How the ad blocking works

- `App.kt` creates the `AdFilter` engine and, on first run only
  (`!adFilter.hasInstallation`), registers + enables + downloads the three
  AdGuard subscriptions above.
- `PwaWebViewClient.shouldInterceptRequest` runs every sub-resource request
  through `adFilter.shouldIntercept(...)` and returns a blocked/empty
  response when a filter rule matches. Main-frame navigations are never
  blocked by design (the library's own rule).
- `onPageStarted` calls `adFilter.performScript(...)`, which injects the
  element-hiding / cosmetic-filter CSS+JS for the page.

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

- If your PWA relies on a Service Worker for offline caching, requests it
  makes are *not* routed through `shouldInterceptRequest` by default on
  older WebView versions — only tested on recent WebView (Chromium)
  releases where SW fetches do pass through the same interception path.
- `minSdk` is 26 (Android 8.0) to keep the launcher icon as a plain vector
  adaptive icon with no raster fallback assets required.
