# Pebble

A proxy and VPN client for Android. Pebble is
[FlClash](https://github.com/chen08209/FlClash) v0.8.98, including its
mihomo (Clash.Meta) engine, rebranded, that can also **import WireGuard,
AmneziaWG and OpenVPN files**. They run in FlClash's own engine like any
other proxy.

## What it imports

Add a profile from **Profiles → +** (file, URL or QR code):

- **Clash / mihomo YAML** subscriptions and files, as in FlClash.
- **WireGuard and AmneziaWG** `.conf` files, including QR codes exported by
  WireGuard apps. The config becomes a profile with one `wireguard` proxy
  (AmneziaWG's `Jc`/`S1`/`H1`… go into `amnezia-wg-option`; several
  `[Peer]` sections go under `peers`).
- **OpenVPN** `.ovpn` files with an inline `<ca>` (and `<cert>`/`<key>`,
  `<tls-auth>`, `<tls-crypt>`, `<tls-crypt-v2>` when present). Each `remote`
  becomes an `openvpn` proxy, grouped under "Proxy" with an "Auto" group.
  - A file with `auth-user-pass` and no inline login asks for a username and
    password once. VPN providers often issue separate OpenVPN (service)
    credentials that differ from the website login. A URL that serves an
    `.ovpn` keeps the saved login when it updates.
  - Supported: `dev tun`, UDP or TCP, AES-GCM, AES-CBC and
    ChaCha20-Poly1305, `tls-auth` with any `auth` digest, `tls-crypt`,
    `tls-crypt-v2`, `comp-lzo` and `compress` (stub, lz4, lz4-v2). Files that
    need something else (`dev tap`, BF-CBC only, PKCS#12, no inline `<ca>`)
    are refused on import with the reason.

Every proxy is built once when the profile is added, so a problem shows up
as an error on import instead of a profile that connects and carries
nothing.

## Differences from FlClash

- Name, package (`app.pebble.android`) and icon are Pebble's, so it installs
  next to FlClash.
- No Firebase Crashlytics and no update checker; the About page credits
  FlClash and mihomo.
- **Default DNS**: the phone's own resolver plus Cloudflare and Google (by IP,
  so they need no lookup first) instead of FlClash's China-only servers
  (doh.pub, AliDNS). With those unreachable, mihomo cannot even resolve a VPN
  server's name: the VPN "connects" and nothing loads. Installs that never
  changed the DNS settings switch automatically.
- The engine carries two patches (`core/patches/`, applied by the Go build
  hook): OpenVPN `tls-auth` uses the file's `auth` digest (NordVPN,
  Surfshark use SHA512; the engine always used SHA1), and OpenVPN
  compression framing (`comp-lzo`, `compress`, pushed or not) is understood.
- Importing `.conf`/`.ovpn` (above).

## Building

CI builds on every push to `main` that touches `pebble/**`
(`.github/workflows/build-pebble.yml`) and uploads the APK as the
**Pebble-apk** artifact.

Locally (Linux host):

```bash
git submodule update --init pebble/core/Clash.Meta
cd pebble
flutter pub get
echo '{"APP_ENV":"stable"}' > env.json
flutter build apk --release --split-per-abi \
  --target-platform android-arm64 --dart-define-from-file=env.json
```

The Go build hook applies `core/patches/*.patch` to `core/Clash.Meta`
(already-applied patches are skipped). To work on the engine by hand:
`for p in core/patches/*.patch; do git -C core/Clash.Meta apply "$PWD/$p"; done`.

Requirements: Flutter 3.47.x, Go 1.26, Rust with `aarch64-linux-android`,
Java 17+, Android SDK 36 and NDK 28.2.13676358.

Release signing: set `PEBBLE_KEYSTORE` (path), `PEBBLE_KEYSTORE_PASSWORD`,
`PEBBLE_KEY_ALIAS` and `PEBBLE_KEY_PASSWORD`; CI reads them from the
`PEBBLE_KEYSTORE_BASE64`/`…` repository secrets. Without them the APK is
signed with a throwaway key and cannot update a previous install.
