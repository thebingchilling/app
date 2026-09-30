# Pebble — handoff for the next agent

Read this, then `/CLAUDE.md` (repo rules: commit and push straight to `main`,
no branches, no PRs) and `README.md`.

## Goal (confirmed by the user)

All of FlClash (v0.8.98, its own mihomo engine) plus importing and using
WireGuard/AmneziaWG `.conf` and OpenVPN `.ovpn` **in FlClash's engine**.
Engine fixes are allowed; separate engines are not (a version with the
official WireGuard library, amneziawg-android and ics-openvpn was built and
dropped at the user's request — it is in git history at `d5c738d`).

User decisions:
- Name Pebble, app ID `app.pebble.android`, stones icon, no Crashlytics, no
  update checker, Windows names renamed.
- DNS default changed to one that works outside China (the user left the
  choice to the agent).
- Android first; Windows later. No share-link import for now.
- Never copy code from the old Tidewall app.
- The user interrupts long foreground commands: run builds in the background.

## Layout

- `lib/common/vpn_import.dart` — `.conf`/`.ovpn` → mihomo profile; hooked in
  `Profile.saveFile/update` and `ProfilesAction` (file, URL, QR; raw
  WireGuard QR codes accepted). `lib/widgets/ovpn_login_dialog.dart` asks for
  the OpenVPN login.
- `core/hub.go` `validateProxies` — import builds every proxy, so a proxy the
  engine rejects fails the import instead of the whole profile silently
  falling back to an empty config when applied.
- `core/patches/` — engine patches (tls-auth digest, compression framing),
  applied by `plugins/setup/setup_hooks/lib/src/go_builder.dart` and in CI.
  Re-check they apply when bumping `core/Clash.Meta`.
- `lib/models/clash_config.dart` — DNS defaults; `legacyFlClashDns` migrates
  untouched old settings.

## Why it "connected but did not work" (found 2026-09-30)

Reproduced end to end: FlClash's own core (init → setupConfig →
startListener, TUN from the runtime config) in a network namespace standing
in for the phone, runtime configs made by the app's own `convertVpnConfig` +
`makeRealProfileTask`, local WireGuard / AmneziaWG / three OpenVPN servers
(UDP tls-crypt + login, TCP tls-auth SHA512 + comp-lzo, tls-crypt-v2 +
lz4-v2) reached by hostname, and a web server reachable only through the
tunnels. With FlClash's stock DNS (doh.pub/AliDNS/223.5.5.5 unreachable)
every connection failed with `resolve endpoint domain` / `connect OpenVPN
server: dns resolve failed`, while the VPN showed as connected. With the new
defaults all five passed, by IP and by name. The harness scripts are not in
the repo; the description above is enough to rebuild them.

## Not verified

- On a real phone (no emulator/KVM here) and against real provider servers
  (this container blocks outgoing UDP and non-HTTPS TCP).
- If WireGuard is blocked by DPI where the user is, no client change helps;
  AmneziaWG is the answer there.

## Environment gotchas

- The agent proxy answers HTTP 429 for repo.maven.apache.org. Local fix (not
  committed): `~/.gradle/init.d/mirror.gradle` rewriting Maven Central URLs to
  `https://maven-central.storage-download.googleapis.com/maven2/`.
- Toolchain used: Flutter 3.47.1, Go 1.26.8 (`GOTOOLCHAIN=local`), Java 21,
  NDK 28.2.13676358.
