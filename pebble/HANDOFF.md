# Pebble — handoff for the next agent

Read this first, then `/CLAUDE.md` (repo rules: commit and push straight to
`main`, no branches, no PRs).

## Goal (confirmed by the user)

**Pebble** = FlClash's app with its Flutter UI kept **1:1** (look, feel and
scrolling exactly like FlClash), rebranded, for **Android and Windows**.

User decisions:
- Name **Pebble**; icon of three stacked stones (no shield/key/padlock), with
  an Android 13+ monochrome layer. Never use FlClash's name or icon.
- Drop Firebase crash reporting and FlClash's update checker. On Android the
  desktop-only features stay platform-gated off; on Windows keep them (tray,
  hotkeys, system proxy, loopback, TUN via the helper service).
- The user interrupts long **foreground** commands. Run builds with
  `run_in_background` and keep the user posted in short lines.

## State (2026-09-30)

Source: FlClash `main` at `c7be702` (2026-09-17); engine submodule
`pebble/core/Clash.Meta` → `chen08209/Clash.Meta` @ `70f0570` (branch FlClash).

Done:
- **Android build works** locally (`flutter build apk --release
  --split-per-abi --target-platform android-arm64`): `app.pebble.android`,
  label "Pebble". Version `1.0.0`; CI passes `--build-number=<run number>`
  so every CI APK updates the previous one.
- `flutter test` (1862 tests) and `flutter analyze` pass with
  `build_assets: false` (see README).
- Rebrand: `appName`, ARB strings, Android strings/notification, Windows
  exe/core/helper/pipe/lock names (`Pebble.exe`, `PebbleCore`,
  `PebbleHelperService`, `\\.\pipe\PebbleCore_*`), new Inno Setup `app_id`,
  `pebble://` scheme added (clash/clashmeta/flclash kept for "import to"
  buttons). Kotlin packages and the Dart package name stay `com.follow.clash`
  / `fl_clash` (channel names depend on them).
- Icons: `assets_source/images/app/generate.py` (Pillow + cairosvg) writes the
  Android launcher/adaptive/monochrome/TV/banner/notification icons,
  `assets/images/icon.png` and `icon.ico`. Tray icons and
  `windows/runner/resources/app_icon.ico` come from
  `dart run tool/generate_status_icons.dart` (needs `rsvg-convert`) using
  `assets_source/images/icon/status_*.svg`.
- Crashlytics toggle/tip and the update checker (setting, startup check,
  About entry) removed; the model fields stay so saved configs still load.
  About page credits FlClash (GPL-3.0) and mihomo; avatars removed.
- **WireGuard/AmneziaWG and OpenVPN import** (`lib/common/vpn_import.dart`,
  tests in `test/common/vpn_import_test.dart`), ported from Tidewall's Go
  converters. Hooked into `Profile.saveFile` so file, URL and QR imports all
  convert; QR codes with raw WireGuard configs are accepted. `.ovpn` with bare
  `auth-user-pass` shows `OvpnLoginDialog` (explains provider service
  credentials); subscription updates reuse the saved login. Generated YAML
  was checked with mihomo's `config.Parse`.
- `windows/` restored from FlClash `c7be702` and rebranded.
- CI: `.github/workflows/build-pebble.yml` — `android` job (tests + APK
  artifact **Pebble-apk**) and `windows` job (`dart setup.dart windows`,
  artifact **Pebble-windows**: installer + portable zip).

## Audit (2026-09-30)

The user reported that nothing connects and some servers cannot be added.
Tested end to end in the container with Pebble's own core (`handleValidateConfig`
→ `handleSetupConfig` → delay test → HTTP through the mixed port), profiles
made by `vpn_import.dart` + `makeRealProfileTask`, against local servers:
wireguard-go (+dnsmasq), OpenVPN 2.6 in six setups (tls-crypt + login,
tls-auth + `auth SHA512` + comp-lzo over TCP, no `auth` + `compress lz4-v2`,
tls-crypt-v2, `comp-lzo no`, `compress` stub) and a Shadowsocks server for
share links. Found and fixed:

- **Whole profile silently dead**: `validateConfig` only parsed YAML; one
  proxy mihomo rejects makes `applyConfig` fall back to an empty config (every
  delay "Timeout"). It now builds every proxy (`validateProxies`), so import
  shows the error.
- **OpenVPN `tls-auth` always used HMAC-SHA1**; OpenVPN uses the `auth`
  digest (NordVPN/Surfshark use SHA512). Engine patch in `core/patches/`.
- **OpenVPN `compress`** (stub/lz4/lz4-v2, and pushed `compress`/`comp-lzo`)
  was unsupported, so data never flowed; same patch. `comp-lzo no` now
  keeps its framing byte as OpenVPN does.
- **Missing `auth`** defaulted to SHA256 in mihomo, SHA1 in OpenVPN: the
  converter writes `auth: SHA1`. It also offers OpenVPN 2.6's default
  data-ciphers and refuses tap/BF-CBC/PKCS#12 on import.
- **Share links / base64 subscriptions** could not be added: the core
  converts them (`core/share_links.go`, mihomo's `common/convert`) and the
  URL dialog, QR picker and scan page accept them.
- **DNS defaults** were FlClash's China-only resolvers; now system + public
  DoH (`legacyFlClashDns` migrates untouched old settings).

Engine patches: `core/patches/*.patch`, applied by `GoBuilder.applyEnginePatches`
(skips applied ones) and explicitly in CI before the Go tests. When bumping
the Clash.Meta submodule, re-check that they still apply.

## Open items

- Watch the CI run of the latest push; the **Windows job has never run
  before** (no Windows machine here), so fix whatever it reports.
- Signing: **no signing secrets are set** (checked in the CI log), so every
  CI APK has a new throwaway key and cannot update the previous install. The
  user has to add `PEBBLE_KEYSTORE_*` (see README); the repo is public, so
  never commit a key or upload one as an artifact.
- Not checked on a device (no KVM/emulator here).
- The Windows installer is not code-signed (SmartScreen "unknown publisher");
  TUN mode asks for admin once to install the helper service.

## Environment gotchas

- The agent proxy returns **HTTP 429 from repo.maven.apache.org**. Local fix:
  `~/.gradle/init.d/mirror.gradle` putting
  `https://maven-central.storage-download.googleapis.com/maven2/` first in
  `pluginManagement` (with `gradlePluginPortal()` and `google()` after it) and
  in every project's `buildscript`/`repositories`. Not committed.
- Toolchain used here: Flutter 3.47.1, Go 1.26.8 (`GOTOOLCHAIN=local`), Rust
  stable + `aarch64-linux-android`, Java 21, SDK 35/36, NDK 28.2.13676358.
