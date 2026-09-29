# Longoipo

Rebuilds the v2RayTun Android app (universal APK) as **Longoipo**: a different package
(`com.longoipo.app`) and name, with Shadowsocks **rc4-md5** support added through a small in-app
loopback bridge. The vendor's UI, importers and Go core (Xray) are untouched.

For personal use. The vendor APK and the patched build are proprietary: keep them out of this repo
(`.gitignore` blocks `*.apk`, `*.p12`, `*.jks`). There is no CI workflow: builds are made by running the
patcher by hand, or by asking a code agent to do it (see "Ask an agent" below).

Background, analysis and a per-update checklist: [`RUNBOOK.md`](RUNBOOK.md).

## Signing key

Every Longoipo build must be signed with the same key, otherwise Android will not install a new build over
the previous one (you would have to uninstall first and lose the app's data).

| | |
|---|---|
| keystore file | `longoipo.p12` (PKCS12). **Not stored in this repo**; keep your copy safe |
| alias | `longoipo` |
| password | `RLord8bgdRZXtv7Twg0utKjc` (same for keystore and key) |
| certificate SHA-256 | `30:12:07:79:56:ED:AB:A8:2F:57:61:D1:9E:C4:1D:53:9C:60:21:DF:78:74:95:25:00:99:8E:F6:58:81:D4:82` |

The password is written here on purpose. On its own it is useless: signing needs the `.p12` file too, so
never commit that file. If it is ever lost, generate a new key (below); the next build will then need an
uninstall-and-reinstall once.

Create a new key:

```bash
keytool -genkeypair -keystore longoipo.p12 -storetype PKCS12 -alias longoipo \
        -keyalg RSA -keysize 3072 -validity 36500 -dname "CN=Longoipo, O=Personal"
```

## How it works

1. `bridge/` is a tiny Java library (compiled to smali and committed under `bridge/smali/`).
   `Rc4Bridge.rewrite(json)` finds Shadowsocks outbounds with `method: "rc4-md5"` and points them at a
   loopback listener with `method: "none"`. The listener speaks plain Shadowsocks to the core and rc4-md5
   Shadowsocks (TCP and UDP) to the real server.
2. `patcher/patch.py` decodes the APK with apktool, adds the bridge, hooks the two calls that hand the JSON
   config to the Go core (`CoreController.startLoop`, `Libv2ray.measureOutboundDelay`), renames the package and
   labels, rebuilds, aligns, signs (v2 + v3) and verifies.

## Patch a new v2RayTun release

You need Python 3, JDK 11+ and network access (the patcher downloads apktool 2.11.1 and apksig 8.5.2, both
pinned by SHA-256). Get the **universal** APK of the new release, then:

```bash
export KS_PASS='RLord8bgdRZXtv7Twg0utKjc'
python3 longoipo/patcher/patch.py v2RayTun_universal.apk out/Longoipo.apk \
    --abis arm64-v8a --keystore longoipo.p12 --alias longoipo
```

- `--abis arm64-v8a` keeps only the 64-bit ARM core (nearly all modern phones). The result is about 29 MB
  instead of 60 MB. Leave the option out to keep all three architectures (arm64-v8a, armeabi-v7a, x86_64), or
  pass e.g. `--abis armeabi-v7a` for an old 32-bit phone.
- `zipalign` from the Android build-tools is used if installed; otherwise a built-in aligner is used.
- Options: `--package` (default `com.longoipo.app`), `--label` (default `Longoipo`), `--work DIR`, `--keep-work`.

The patcher prints the hooked call sites and the rename counts. If it stops with `hook anchor not found` or
another error, the new release changed something: follow the checklist in [`RUNBOOK.md`](RUNBOOK.md) section 8.
Install the result with the usual "install unknown apps" permission; it installs next to v2RayTun because the
package and signer differ.

## Ask an agent

Give a code agent the new universal APK (for example as a Google Drive link) and this instruction:

> Read longoipo/README.md and longoipo/RUNBOOK.md in thebingchilling/app. Patch the attached v2RayTun
> universal APK with longoipo/patcher/patch.py using the signing key from the README (I will supply
> longoipo.p12), arm64-v8a only. Run longoipo/bridge/run_tests.sh first. If a hook anchor is missing, re-do the
> RUNBOOK section 8 checklist, fix the patcher, and tell me what changed. Do not commit any APK or key file.

## Tests

```bash
bash longoipo/bridge/run_tests.sh
```

Unit tests check RC4/MD5 against OpenSSL-generated vectors and the config rewrite. The end-to-end test runs the
real bridge against an independently written rc4-md5 Shadowsocks server, over TCP (1 MiB, domain and IPv4
headers, half-close, parallel connections) and UDP.

After editing anything under `bridge/src`, run `python3 longoipo/bridge/make_smali.py` and commit the
regenerated `bridge/smali/`.

## Limits

- Only nodes whose outbound is plain TCP Shadowsocks are bridged. rc4-md5 nodes with TLS, WebSocket or other
  transports are left unchanged (logged as a warning in logcat).
- The bridge listens on `127.0.0.1` on a random port without authentication, so another app on the same phone
  could in principle use it to reach that server.
- rc4-md5 itself has no integrity protection and is weak against active probing.
- Encrypted-link imports (`//crypt/...`) and everything else the vendor core does are unchanged.
- Not tested on a real device. Check: import an rc4-md5 node, connect, browse, do a UDP test (DNS or a call), run
  the delay test.
