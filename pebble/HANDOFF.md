# Pebble — handoff for the next agent

Read this, then `/CLAUDE.md` (repo rules: commit and push straight to `main`,
no branches, no PRs) and `README.md` (how direct tunnels work).

## Goal (confirmed by the user)

Stock FlClash (v0.8.98, its own mihomo engine, unpatched) plus **direct
WireGuard, AmneziaWG and OpenVPN** on their own official engines. An earlier
Pebble ran WireGuard/OpenVPN inside mihomo and "connected but didn't work";
it was deleted and Pebble restarted from FlClash.

User decisions:
- Name Pebble, app ID `app.pebble.android`, stones icon, no Crashlytics, no
  update checker, Windows names renamed (`Pebble.exe`, `PebbleHelperService`).
- **Android first.** Windows direct engines come after Android works on the
  user's phone (plan: official wireguard-windows/amneziawg-windows
  `tunnel.dll` and official `openvpn.exe` 2.x, run by the helper service).
- Share-link import and a non-Chinese DNS default: **not now**, maybe after
  everything works.
- Never copy code from the old Tidewall app.
- The user interrupts long foreground commands: run builds in the background.

## Layout

- `core/Clash.Meta` — FlClash's engine submodule @ `70f0570`, unmodified.
  `core/hub.go`/`lib.go` add only `addDirectTraffic` (direct-tunnel bytes into
  mihomo's statistics).
- `lib/common/direct_tunnel.dart` — detection, the `x-pebble-direct` profile
  format, OpenVPN login. Hooked in `Profile.saveFile/update` and
  `ProfilesAction` (file, URL, QR; raw WireGuard QR codes accepted).
- `VpnOptions.profileId` (Dart and Kotlin) tells the service which profile to
  read. `ServiceStateHost.resolveVpnOptions` forces the VPN service for direct
  tunnels.
- `android/service/.../direct/` — `DirectTunnel`, `WireGuardEngine`
  (official library via reflection on `GoBackend`'s private natives;
  AmneziaWG via its public `org.amnezia.awg.GoBackend`), `OpenVpnEngine`
  (management-interface client modelled on ics-openvpn's
  `OpenVpnManagementThread`/`OpenVPNService`).
- `android/amneziawg/` — builds amneziawg-android's `libwg-go` as
  `libawg-go.so` (the official library already ships `libwg-go.so`).
- `android/openvpn/` — builds ics-openvpn's `libopenvpn.so` +
  `libovpnexec.so` with its own CMakeLists (OpenVPN 3 skipped via its
  "skeleton" path check).
- `android/vendor/` — submodules: amneziawg-android @ v3.1.4, ics-openvpn @
  v0.7.65 (nested: openvpn, openssl, lz4, fmt only).

## Verified here (2026-09-30)

- `flutter build apk --release` (arm64) builds.
- Go tests (`core/direct_tunnel_test.go`) and Dart tests
  (`test/common/direct_tunnel_test.dart`, plus FlClash's suite).
- OpenVPN: the vendored OpenVPN compiled for Linux with `TARGET_ANDROID`,
  driven by a Python mirror of `OpenVpnEngine`'s management answers against
  OpenVPN 2.6.19 servers in a network namespace with a real tun device: UDP +
  tls-crypt + login (password with space and quote), TCP + tls-auth SHA512 +
  comp-lzo + net30 (+ dropped `up` script), tls-crypt-v2 + pushed lz4-v2,
  `auth-user-pass <file>`, wrong password → "login rejected". HTTP went
  through the tunnel in each. Found and fixed: OpenVPN reports a
  placeholder default route after OPENTUN, which must be ignored.
- WireGuard/AmneziaWG: the official parsers (the exact AAR / vendored
  sources) produced the UAPI config for wireguard-go / amneziawg-go on Linux;
  handshake and HTTP through the tunnel both passed.

## Not verified

- On a real phone (no emulator/KVM here): the Android `VpnService` glue,
  per-app rules, reconnects, Doze, always-on VPN.
- Real provider servers: this container blocks outgoing UDP and non-HTTPS
  TCP.

## Environment gotchas

- The agent proxy answers HTTP 429 for repo.maven.apache.org. Local fix (not
  committed): `~/.gradle/init.d/mirror.gradle` rewriting Maven Central URLs to
  `https://maven-central.storage-download.googleapis.com/maven2/`.
- Toolchain used: Flutter 3.47.1, Go 1.26.8, Java 21, NDK 28.2.13676358,
  CMake 3.22.1. `GOTOOLCHAIN=local` breaks amneziawg-android's Makefile; its
  CMake wrapper sets `GOTOOLCHAIN=auto` for that step.
