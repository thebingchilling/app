# Pebble

A proxy and VPN client for Android. Pebble is
[FlClash](https://github.com/chen08209/FlClash) v0.8.98 with its interface
and its mihomo (Clash.Meta) engine unchanged, rebranded, plus **direct
WireGuard, AmneziaWG and OpenVPN tunnels** that run on their own official
engines instead of inside mihomo.

## What it imports

Add a profile from **Profiles → +** (file, URL or QR code):

| File | Runs on |
|---|---|
| Clash / mihomo YAML (files and subscriptions) | mihomo, exactly as in FlClash |
| WireGuard `.conf` (also QR codes from WireGuard apps) | the official WireGuard library, [`com.wireguard.android:tunnel`](https://git.zx2c4.com/wireguard-android) 1.0.20260102 |
| AmneziaWG `.conf` (`Jc`, `Jmin`, `S1`, `H1`… in `[Interface]`) | Amnezia's own engine, [amneziawg-android](https://github.com/amnezia-vpn/amneziawg-android) 3.1.4 |
| OpenVPN `.ovpn` | OpenVPN 2 as built by [OpenVPN for Android](https://github.com/schwabe/ics-openvpn) (ics-openvpn) v0.7.65 |

- A `.ovpn` with `auth-user-pass` and no inline login asks for a username and
  password on import. VPN providers often issue separate OpenVPN (service)
  credentials that differ from the website login. A URL that serves an
  `.ovpn` keeps the saved login when it updates.
- `dev tap` servers are refused on import: Android VPNs only carry IP traffic.

## How direct tunnels work

A WireGuard/AmneziaWG/OpenVPN file is saved as a small Clash profile that
keeps the original file word for word on one `x-pebble-direct:` line
(`lib/common/direct_tunnel.dart`). mihomo ignores that key and loads an empty
profile that sends everything DIRECT, so FlClash's pages keep working.

When you connect, FlClash's `VpnService` reads that line
(`android/service/.../direct/`) and, instead of handing the VPN to mihomo,
builds the interface from the tunnel's own addresses, DNS, routes and MTU and
starts its engine:

- **WireGuard / AmneziaWG** (`WireGuardEngine.kt`): does what the libraries'
  own `GoBackend` does, parsing with their config parser and passing the
  interface to their Go engine, whose sockets are kept out of the VPN.
- **OpenVPN** (`OpenVpnEngine.kt`): starts `libovpnexec.so` like OpenVPN for
  Android and answers OpenVPN's management interface: login, socket
  protection, the tunnel's addresses/routes/DNS, and the interface itself.

FlClash's per-app VPN setting, "allow bypass", the notification, Quick
Settings tile and always-on VPN apply to direct tunnels too. Their traffic is
counted in FlClash's statistics (Dashboard, notification). A direct tunnel
always uses the VPN service, even with FlClash's VPN switch off.

## Differences from FlClash

- Name, package (`app.pebble.android`) and icon are Pebble's, so it installs
  next to FlClash.
- No Firebase Crashlytics and no update checker; the About page credits
  FlClash and mihomo.
- The direct tunnels above. Everything else, including DNS defaults, is
  FlClash's.

## Building

CI builds on every push to `main` that touches `pebble/**`
(`.github/workflows/build-pebble.yml`) and uploads the APK as the
**Pebble-apk** artifact.

Locally (Linux host):

```bash
git submodule update --init pebble/core/Clash.Meta \
  pebble/android/vendor/amneziawg-android pebble/android/vendor/ics-openvpn
git -C pebble/android/vendor/ics-openvpn submodule update --init \
  main/src/main/cpp/openvpn main/src/main/cpp/openssl \
  main/src/main/cpp/lz4 main/src/main/cpp/fmt
cd pebble
flutter pub get
echo '{"APP_ENV":"stable"}' > env.json
flutter build apk --release --split-per-abi \
  --target-platform android-arm64 --dart-define-from-file=env.json
```

Requirements: Flutter 3.47.x, Go 1.26 (plus network access: amneziawg-android's
Makefile fetches its own Go), Rust with `aarch64-linux-android`, Java 17+,
Android SDK 36, NDK 28.2.13676358 and CMake 3.22.1.

Release signing: set `PEBBLE_KEYSTORE` (path), `PEBBLE_KEYSTORE_PASSWORD`,
`PEBBLE_KEY_ALIAS` and `PEBBLE_KEY_PASSWORD`; CI reads them from the
`PEBBLE_KEYSTORE_BASE64`/`…` repository secrets. Without them the APK is
signed with a throwaway key and cannot update a previous install.

## Licenses

FlClash is GPL-3.0. The WireGuard and AmneziaWG libraries are Apache-2.0.
OpenVPN and OpenVPN for Android are GPL-2.0.
