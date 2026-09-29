# Pebble

A proxy and VPN client for Android and Windows. Pebble is
[FlClash](https://github.com/chen08209/FlClash)'s Flutter app with its
interface kept as it is, rebranded and trimmed. It runs FlClash's mihomo
(Clash.Meta) engine.

## What it imports

Add a profile from **Profiles → +** (file, URL or QR code):

- **Clash / mihomo YAML** subscriptions and files, as in FlClash.
- **WireGuard and AmneziaWG** `.conf` files, including QR codes exported by
  WireGuard apps. The config becomes a profile with one `wireguard` proxy
  (several `[Peer]` sections go under `peers`).
- **OpenVPN** `.ovpn` files with inline `<ca>` (and `<cert>`/`<key>`,
  `<tls-auth>`, `<tls-crypt>` when present). Each `remote` becomes a proxy,
  grouped under "Proxy" with an "Auto" url-test group.
  - If the file has `auth-user-pass` without inline credentials, Pebble asks
    for a username and password and stores them in the generated profile.
    VPN providers often issue separate OpenVPN (service or manual setup)
    credentials that differ from the website login.
  - When a subscription URL serves the `.ovpn`, updates reuse the saved login.

The converted profile is ordinary mihomo YAML: edit it, add rules, or chain
it behind other proxies like any other profile. WireGuard and OpenVPN run
inside mihomo, so they get FlClash's VPN service, delay tests and IP check.

## Differences from FlClash

- Name, package (`app.pebble.android`) and icon are Pebble's, so it installs
  next to FlClash.
- No Firebase Crashlytics and no update checker.
- The About page credits FlClash and mihomo.

## Building

CI builds on every push to `main` that touches `pebble/**`
(`.github/workflows/build-pebble.yml`) and uploads the APK as the
**Pebble-apk** artifact.

Locally (Linux or macOS host):

```bash
git submodule update --init --recursive   # core/Clash.Meta
tool/fetch_geodata.sh                     # geo databases into assets/data/
flutter pub get
echo '{"APP_ENV":"stable"}' > env.json
flutter build apk --release --split-per-abi \
  --target-platform android-arm64 --dart-define-from-file=env.json
```

Requirements: Flutter 3.47.x, Go 1.26, Rust with the Android targets
(`rustup target add aarch64-linux-android`), Java 17+, Android SDK 36 and
NDK 28.2.13676358. The Go core and the Rust library are built by Dart build
hooks (`plugins/setup`, `plugins/rust_api`) during `flutter build`.

Tests: set `build_assets: false` for both packages under `hooks.user_defines`
in `pubspec.yaml` (so the hooks skip the native build), then `flutter test`.

### Release signing

Set these repository secrets so every CI build is signed with the same key
(Android refuses to update an app signed with a different key):

| Secret | Value |
| --- | --- |
| `PEBBLE_KEYSTORE_BASE64` | `base64 -w0 pebble.p12` (PKCS12 or JKS keystore) |
| `PEBBLE_KEYSTORE_PASSWORD` | keystore password |
| `PEBBLE_KEY_ALIAS` | key alias |
| `PEBBLE_KEY_PASSWORD` | key password |

Without them CI falls back to Tidewall's `TIDEWALL_*` signing secrets, and
without those to a throwaway debug key (a new one per run). Create a key with:

```bash
keytool -genkeypair -storetype PKCS12 -keystore pebble.p12 -alias pebble \
  -keyalg RSA -keysize 4096 -validity 10000
```

## License

GPL-3.0, like FlClash. See [LICENSE](LICENSE).
