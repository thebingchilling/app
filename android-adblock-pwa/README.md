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
- Every other navigation goes through `PwaWebViewClient.shouldOverrideUrlLoading`,
  which checks the target host against `pwa_host` and its subdomains. A
  match loads in the WebView (`return false`); anything else is dropped
  (`return true`, and nothing further happens) — no distinction between a
  popunder redirect and a link the user actually tapped, since neither has
  anywhere to go.

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
