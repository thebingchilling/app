# Tidewall

A Material 3 VPN client for Android with three engines in one app:

| Engine | Runs | Protocols |
|---|---|---|
| **Proxy mode** — [mihomo](https://github.com/MetaCubeX/mihomo) v1.19.31 (Clash.Meta core) | Clash/mihomo profiles, subscriptions, share links | VLESS (incl. Reality), VMess, Trojan, Shadowsocks (**all ciphers, including `rc4-md5`**), Hysteria/Hysteria2, TUIC, AnyTLS, SOCKS/HTTP, plus OpenVPN and WireGuard/AmneziaWG as outbounds, with rules and proxy groups |
| **Direct WireGuard** — AmneziaWG userspace engine | WireGuard / AmneziaWG `.conf` files | WireGuard, AmneziaWG obfuscation (Jc/Jmin/Jmax, S1–S4, H1–H4, I1–I5) |
| **Direct OpenVPN** — the official [OpenVPN 3 core](https://github.com/OpenVPN/openvpn3) 3.11.7 (mbed TLS) | `.ovpn` files | OpenVPN over UDP/TCP, tls-auth, tls-crypt, tls-crypt-v2, certificates and/or username/password |

Android allows one VPN at a time, so exactly one engine runs. OpenVPN and
WireGuard profiles can also be copied into Proxy mode (profile menu → *Copy as
Proxy-mode profile*) to combine them with rules or chain them behind other
proxies.

## Features

- **Profiles:** Clash subscription URLs (auto-update, traffic/expiry from
  `subscription-userinfo`), pasted YAML or share links, QR codes, `.yaml` /
  `.ovpn` / `.conf` files (also via "Open with" and `clash://install-config`
  links), and a Shadowsocks editor with every cipher mihomo supports.
- **Proxies:** proxy groups with selection, latency tests per group or all,
  Rule / Global / Direct routing.
- **Connections** and **Logs** screens, live traffic in the notification.
- **Per-app VPN** (only selected apps, or all except selected), bypass LAN,
  IPv6, TUN stack (gVisor/System/Mixed), MTU, DNS override (fake-ip), sniffing.
- **Auto-connect:** connect on untrusted Wi-Fi and/or mobile data, disconnect on
  trusted Wi-Fi networks.
- Quick Settings tile, connect on boot, Android always-on VPN / kill switch.
- Material 3 with dynamic color (Android 12+), light/dark, phone and tablet
  layouts.

Legacy ciphers (`rc4-md5`, other stream ciphers, OpenVPN BF-CBC) are supported
because some servers still need them, but they are weak: the app says so where
you pick them.

## Get the APK

The **Build Tidewall APK** GitHub Actions workflow runs on every push to
`main` that touches `tidewall/` (or manually via *Run workflow*). Download
`Tidewall-apk` from the run's **Artifacts**: `app-release.apk` is the one to
install (arm64 phones, Android 8+).

### Signing key

Android only installs an update over an existing install when both are signed
with the same key. Without a key configured, CI signs with a throwaway debug
key that changes every run, so **each CI build must be uninstalled before
installing the next one** (this loses profiles and settings).

To get stable, updatable builds, create a key once and add it as repository
secrets (Settings → Secrets and variables → Actions):

```bash
keytool -genkeypair -keystore tidewall.p12 -storetype PKCS12 -alias tidewall \
        -keyalg RSA -keysize 3072 -validity 36500 -dname "CN=Tidewall, O=Personal"
base64 -w0 tidewall.p12   # value for TIDEWALL_KEYSTORE_BASE64
```

| Secret | Value |
|---|---|
| `TIDEWALL_KEYSTORE_BASE64` | output of the `base64` command |
| `TIDEWALL_KEYSTORE_PASSWORD` | the keystore password |
| `TIDEWALL_KEY_ALIAS` | `tidewall` |
| `TIDEWALL_KEY_PASSWORD` | the key password (same as the keystore password for PKCS12) |

Never commit the `.p12` file.

## Layout

```
tidewall/
├─ core/                 Go engine: mihomo + AmneziaWG, exported with gomobile
│  ├─ proxy.go           Proxy mode: profile overrides, TUN from the VpnService fd
│  ├─ api.go             proxy groups, selection, latency, traffic, connections
│  ├─ wireguard.go       Direct WireGuard: .conf parser, UAPI, fd-backed TUN
│  ├─ ovpn.go            .ovpn → mihomo openvpn outbound converter
│  ├─ convert.go         share links → profile, add proxy, profile stats
│  ├─ build.sh           builds android/app/libs/libcore.aar
│  └─ testdata/openvpn-e2e.sh   end-to-end test against real OpenVPN servers
└─ android/              Gradle project (AGP 9, Kotlin, Jetpack Compose)
   ├─ app/               the app: VPN service, profiles, UI
   └─ openvpn/           OpenVPN 3 core built with the NDK + SWIG JNI bindings
      ├─ fetch-deps.sh   downloads pinned OpenVPN 3, mbed TLS, asio, lz4, fmt, xxHash
      └─ patches/        backports applied on top of the OpenVPN 3 release
```

## Build it yourself

Needs JDK 21, Go 1.26+, SWIG 4, and the Android SDK with platform 37,
build-tools 37.0.0, NDK 28.2.13676358 and CMake 3.31.6.

```bash
export ANDROID_HOME=~/Android/Sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/28.2.13676358

cd tidewall/core && ./build.sh                # Go engine → android/app/libs/libcore.aar
cd ../android && ./gradlew assembleRelease    # APK in app/build/outputs/apk/release/
```

The first Gradle build downloads the OpenVPN 3 sources and dependencies into
`android/openvpn/deps/`. To build for more ABIs, set `tidewall.abis` in
`android/gradle.properties` (e.g. `arm64-v8a,armeabi-v7a`) and run
`ABIS=android/arm64,android/arm ./build.sh`.

## Tests

```bash
cd tidewall/core
go test -tags cmfa,with_gvisor,no_tailscale,no_zerotier,no_easytier ./...
sudo testdata/openvpn-e2e.sh        # Linux, needs root + openvpn + openssl

cd ../android && ./gradlew testDebugUnitTest
```

The Go tests include a real WireGuard and AmneziaWG handshake between two
tunnels, an `rc4-md5` Shadowsocks round trip, and `.ovpn` / WireGuard / share
link conversions validated by mihomo's own parser. `openvpn-e2e.sh` starts
OpenVPN 2.6 servers (UDP + tls-auth + username/password, TCP + tls-crypt) and
fetches a page through them with Proxy-mode OpenVPN. The Kotlin tests cover
import detection, LAN-bypass route maths and the auto-connect rules.

## License

GPL-3.0 (mihomo is GPL-3.0; OpenVPN 3 is used under MPL-2.0; AmneziaWG and
WireGuard are MIT). Tidewall is not affiliated with any of these projects.
