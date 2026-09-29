# Longoipo

Rebuilds the v2RayTun Android app (universal APK) as **Longoipo**: a different package
(`com.longoipo.app`) and name, with Shadowsocks **rc4-md5** support added through a small in-app
loopback bridge. The vendor's UI, importers and Go core (Xray) are untouched.

For personal use. The vendor APK and the patched build are proprietary: keep them out of this repo
and never publish them. **This repo is currently public: make it private before you upload an APK to a
Release, otherwise the patched APK is public too.**

Background, analysis and a per-update checklist: [`RUNBOOK.md`](RUNBOOK.md).

## How it works

1. `bridge/` is a tiny Java library (compiled to smali and committed under `bridge/smali/`).
   `Rc4Bridge.rewrite(json)` looks for Shadowsocks outbounds with `method: "rc4-md5"` and points them
   at a loopback listener with `method: "none"`. The listener speaks plain Shadowsocks to the core and
   rc4-md5 Shadowsocks (TCP and UDP) to the real server.
2. `patcher/patch.py` decodes the APK with apktool, adds the bridge, hooks the two calls that hand the
   JSON config to the Go core (`CoreController.startLoop`, `Libv2ray.measureOutboundDelay`), renames the
   package and labels, rebuilds, aligns, signs (v2 + v3) and verifies.
3. `.github/workflows/longoipo-patch.yml` runs the patcher when you publish a Release that has the
   universal APK attached, and uploads `Longoipo-<tag>.apk` to that Release.

## One-time setup

Create a signing key once and keep it. The same key on every build means each new Longoipo installs
over the previous one and keeps its data.

```bash
keytool -genkeypair -keystore longoipo.p12 -storetype PKCS12 -alias longoipo \
        -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 longoipo.p12        # copy the output for the first secret below
```

Add these repository secrets (Settings -> Secrets and variables -> Actions):

| Secret | Value |
|---|---|
| `LONGOIPO_KEYSTORE_B64` | the base64 text from above |
| `LONGOIPO_KEYSTORE_PASSWORD` | the keystore password |
| `LONGOIPO_KEY_ALIAS` | `longoipo` (or the alias you chose) |
| `LONGOIPO_KEY_PASSWORD` | key password (optional; defaults to the keystore password) |

Never commit the keystore. `.gitignore` already excludes `*.p12`, `*.jks` and `*.apk`.

## Updating to a new v2RayTun release

1. Make the repo private (see the note at the top).
2. Create a GitHub Release (any tag, for example `5.25.82`) and attach the **universal** APK. It must
   be the only `.apk` asset. Files over 25 MB cannot go through the web upload of repo files, but
   Release assets allow up to 2 GB.
3. Publish it. The workflow patches the APK and adds `Longoipo-<tag>.apk` to the same Release.
   To re-run on an existing release use Actions -> Longoipo patch -> Run workflow with the tag.
4. Install the APK. Because the package and signer differ from the original, it installs next to
   v2RayTun; the very first time you may need to allow installs from unknown sources.
5. Check the run log: the patcher prints the hooked call sites and the rename counts. If it stops with
   `hook anchor not found` the new release changed something; follow the checklist in
   [`RUNBOOK.md`](RUNBOOK.md) section 8.

## Run it locally

```bash
export KS_PASS=your-keystore-password
python3 longoipo/patcher/patch.py v2RayTun_universal.apk out/Longoipo.apk \
    --keystore longoipo.p12 --alias longoipo
```

Needs Python 3, JDK 11+ and network access (downloads apktool 2.11.1 and apksig 8.5.2, both pinned by
SHA-256). It uses `zipalign` when Android build-tools are installed and a built-in aligner otherwise.

## Tests

```bash
bash longoipo/bridge/run_tests.sh
```

Unit tests check RC4/MD5 against OpenSSL-generated vectors and the config rewrite. The end-to-end test
runs the real bridge against an independently written rc4-md5 Shadowsocks server, over TCP (1 MiB, domain
and IPv4 headers, half-close, parallel connections) and UDP. The same script runs in the workflow before
patching.

After editing anything under `bridge/src`, run `python3 longoipo/bridge/make_smali.py` and commit the
regenerated `bridge/smali/`.

## Limits

- Only nodes whose outbound is plain TCP Shadowsocks are bridged. rc4-md5 nodes with TLS, WebSocket or
  other transports are left unchanged (logged as a warning in logcat).
- The bridge listens on `127.0.0.1` on a random port without authentication, so another app on the same
  phone could in principle use it to reach that server.
- rc4-md5 itself has no integrity protection and is weak against active probing.
- Encrypted-link imports (`//crypt/...`) and everything else the vendor core does are unchanged.
- Not tested on a real device. Please check: import an rc4-md5 node, connect, browse, do a UDP test
  (DNS or a call), run the delay test.
