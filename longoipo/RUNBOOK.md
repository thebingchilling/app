# Runbook: rc4-md5 support + "Longoipo" rebrand for v2RayTun (Android)

Audience: a future agent repeating this for a new v2RayTun release.
Baseline analysed: `v2RayTun_universal.apk`, version 5.25.81 (versionCode 81) only.
Nothing here comes from any other build.

## 0. Status (read first)

| Item | State |
|---|---|
| Analysis of 5.25.81 (sections 3-4) | Done, verified |
| rc4-md5 bridge (`bridge/`), patcher (`patcher/patch.py`), rename step, workflow (`.github/workflows/longoipo-patch.yml`) | **Built** (section 10) |
| Bridge vs an independent rc4-md5 server, TCP and UDP; crypto vs OpenSSL vectors | **Passing** (section 10) |
| Patcher end to end on the 5.25.81 universal APK (hooks, rename, build, sign, verify) | **Passing** with a throwaway key (section 10) |
| Xray accepts `method: "none"` for Shadowsocks over TCP and UDP | **Unverified** (build of Xray was denied by the sandbox classifier) |
| GitHub Action run on GitHub | **Never run** (YAML parses; needs the secrets and a private repo) |
| Behaviour on a real device | **Never tested**; there is no device or emulator in the sandbox |

Do not claim the patched app works until it has been run on a device.

## 1. Goal and constraints from the user

- Make Shadowsocks `rc4-md5` nodes work in this exact app (its UI and its handling of subscriptions and Clash-derived configs). No other client app is acceptable.
- Rebrand the rebuilt app: new package name and every visible label = "Longoipo".
- Output: ONE installable APK, produced by a GitHub Action when a new universal APK is attached to a Release.
- Personal use. Never publish the vendor APK, decompiled code, or patched builds. The repo `thebingchilling/app` is currently public; APKs go only into Release assets, and only after the repo is made private.

## 2. Session lessons (environment quirks)

- Vendor repo `LXST-CODE/v2RayTun`: `api.github.com` and `github.com/.../releases` return 403 ("GitHub access ... not enabled"). `add_repo` with read access only allows git clone/fetch; release assets are not reachable. The repo has docs only (README, RELEASES.md), no source, no binaries.
- Claude file uploads are capped at 30 MB. The universal APK is 59.7 MB, so the user supplied it as a Google Drive link. This worked:
  `curl -L -o universal.apk "https://drive.usercontent.google.com/download?id=<FILE_ID>&export=download&confirm=t"`
  A Cloudflare-protected file host failed (403 "Just a moment...").
- Auto-mode classifier denials seen: probing many hosts ("Exfil Scouting"), reading the proxy status URL ("Containment Escape"), attaching a repo with push access the first time ("Permission Grant"; approved when the user asked again), building Xray from source with `go install` ("Code from External"). Do not retry a denied action another way. Explain and ask.
- Tools that WERE reachable: Maven Central, Google Maven (`https://maven.google.com`), PyPI, Bitbucket downloads. Local: Java, Maven, Python 3.11 (cryptography, PyYAML), `keytool`, Go, openssl. NOT installed: apktool, jadx, aapt, zipalign, apksigner.
- Repo policies: `thebingchilling/chat` = never commit/push (sandbox), except `CLAUDE.md` when explicitly asked. `thebingchilling/app` = always commit and push straight to `main`, no branches, no PRs, one folder per project.
- Push access: `add_repo` with `access: "push"` clones to `/home/user/app` (a read-only clone lives at `/home/user/thebingchilling/app`; do not use it). Run `register_repo_root` after cloning.

## 3. Analysis procedure (exact steps)

Work in the scratchpad, never in a repo.

1. Get the APK; `sha256sum` it. Baseline: 59,698,839 bytes, SHA-256 `f940f5b15d3d728ccd907099088e280389e166a309d978f21a767db2e1bdbb0a`.
2. apktool 2.11.1: `curl -L -o apktool.jar https://bitbucket.org/iBotPeaches/apktool/downloads/apktool_2.11.1.jar` then `java -jar apktool.jar d -f -o uni_out universal.apk`.
3. jadx 1.5.6 is not a single download here. Resolve it from Maven with a POM that lists `io.github.skylot:jadx-cli:1.5.6`, `io.github.skylot:jadx-dex-input:1.5.6`, `org.slf4j:slf4j-simple:2.0.13` and adds `<repository><id>google</id><url>https://maven.google.com</url></repository>` (r8, smali and aapt2-proto live on Google Maven). Run `mvn -B -U -q dependency:copy-dependencies -DoutputDirectory=jadxlib`.
   Run: `java -Xmx4g -cp "jadxlib/*" jadx.cli.JadxCLI -d uni_jadx --no-res --threads-count 4 universal.apk`. Exit code 3 with about 180 errors is normal (5.25.81: 181).
4. Signature: apksig 8.5.2 from Google Maven (`com.android.tools.build:apksig`), plus a ~15-line `Verify.java` using `ApkVerifier.Builder(file).build().verify()`; print `isVerifiedUsingV1/V2/V3Scheme` and the signer certificate SHA-256.
5. Inspect native libs: `unzip -l universal.apk | grep '\.so$'`; for Go build info run `strings -n 8 lib/arm64-v8a/libgojni.so | grep -E '^(go1\.|dep\s+github.com/xtls)'`.

## 4. Verified findings for 5.25.81

- Package `com.v2raytun.android`, minSdk 24, targetSdk 36, three dex files. Signed with APK Signature Scheme v2 only; signer "DATABRIDGES TECHNOLOGIES LTD" (RU); certificate SHA-256 `5a7e0281f350f16895955be1864e96b6ca4e4d5e3a581508648db7626e8d2dcb`. Not Play-signed, so it can never update over a Play install.
- Native libs for arm64-v8a, armeabi-v7a, x86_64, each with `libgojni.so` (about 34-38 MB), plus barcode, image and mmkv libs.
- Core = stock Xray-core `v1.260327.1` (pseudo-version commit `94ffd50060f1`, 2026-06-01), Go 1.26.3, gomobile bind (`path gobind/gobind`). AmneziaWG is also embedded (`AmnesiaWGVersion = "v0.2.16"`).
- gomobile bindings in Java (package `libv2ray`, `go`): `Libv2ray` statics (`initCoreEnv`, `measureOutboundDelay`, `measureAwgOutboundDelay`, `probe`, `probeString`, `checkVersionX/A`, `fetchTlsCertSha256`, `fetchQuicCertSha256`, `geosourseEntriesAt`, `geosourseTagsAt`, `reconcileBrowserDialer`, `newCoreController`, `newAwgController`), `CoreController` (`startLoop(String,int)`, `stopLoop`, `queryStats`, `queryAllOutboundTrafficStats`, `measureDelay`, `registerProcessFinder`), `AwgController` (`startLoop(String,long)`). These names are bound to the `.so` and cannot be renamed by R8.
- The app builds Xray JSON in Kotlin and passes it to `startLoop(json, tunFd)`. No YAML or Clash parser exists in the dex. How the TUN fd is used inside the core was NOT determined.
- `probeString()` decodes `//crypt/` and `//crypt3/` payloads inside the native core (encrypted-link import). This cannot be replicated without the vendor core.
- Hook call sites (smali; class names below are R8-renamed and WILL change, find by API signature instead):
  - `smali_classes2/q/r.smali:459`  `invoke-virtual {v0, v4, p0}, Llibv2ray/CoreController;->startLoop(Ljava/lang/String;I)V`, with `v4` the config string (from `ConfigResult->getContent()`).
  - `smali_classes2/com/v2raytun/android/service/CoreTestService.smali:437`  `invoke-static {p0, p1}, Llibv2ray/Libv2ray;->measureOutboundDelay(Ljava/lang/String;Ljava/lang/String;)J`, with `p0` the config string.
  - `CoreVpnService.smali:4395`  `AwgController->startLoop(String,J)` (AmneziaWG; do not touch).
- Shadowsocks UI cipher list (`res/values/arrays.xml`, array `ss_securitys`): aes-256-gcm, aes-128-gcm, chacha20-poly1305, chacha20-ietf-poly1305, xchacha20-poly1305, xchacha20-ietf-poly1305, none, plain, 2022-blake3-aes-128-gcm, 2022-blake3-aes-256-gcm, 2022-blake3-chacha20-poly1305. No rc4-md5 anywhere in dex or resources (only TLS suite names in okhttp).
- The `ss://` importer copies the method string with no validation (`setMethod(...)` in the parser). Only the manual-edit spinner is limited to the array above. So an imported rc4-md5 node reaches the core unchanged; the core rejects it.
- No tamper/integrity checks in app-authored code: no signature reads, installer checks, Play Integrity, SafetyNet, debugger, Xposed, frida or root checks; no dynamic dex loading. `Runtime.exec` appears only near the logcat viewer (not confirmed). Play Services and Firebase libraries are bundled; their own checks were not analysed.
- Obfuscation: R8 renaming. Manifest components keep readable names; other app code is in short root packages (`q`, `s`, `t`, `u`, `w`, `m`...). Strings are plaintext; no packer.
- Package-name references to handle when renaming:
  - Manifest: `<permission>`/`<uses-permission>` `com.v2raytun.android.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; provider authorities `com.v2raytun.android.androidx-startup`, `.cache`, `.mlkitinitprovider`, `.com.squareup.picasso`; actions `com.v2raytun.android.action.widget.click`, `.action.activity`.
  - Smali `const-string` literals: 119x `"com.v2raytun.android"` (includes `addDisallowedApplication`, the VPN self-exclusion; if left unchanged after a package rename the app's own traffic loops into its tunnel), 5x `...action.activity`, 4x `...action.widget.click`, 3x `...action.service`, 1x `"com.v2raytun.android:bg"` (process name).

## 5. Design (chosen) and why

Rejected: patching or replacing the Go core (vendor wrapper source is closed; `//crypt`, AWG, tun and process-finder features would be lost or must be reverse engineered); a separate proxy app (user ruled out); a server-side relay (needs a host the user controls).

Chosen: a loopback bridge inside the app, written in Java (JDK and Android APIs only) and injected as smali.

- `rewrite(String json)` parses the config. For each outbound with `protocol == "shadowsocks"` and `settings.servers[i].method` equal (case-insensitive) to `rc4-md5`: start or reuse a bridge for (address, port, password) on `127.0.0.1:<random port>`, then change that server to `address 127.0.0.1`, `port <bridge port>`, `method "none"`. Keep the outbound tag so stats still work. If `streamSettings` is anything but plain TCP, skip that node and log a warning.
- Bridge = transcoder between plain Shadowsocks (loopback, `none`) and rc4-md5 Shadowsocks (upstream). Both use the same address header (`ATYP 1|3|4`, address, 2-byte port), so no SOCKS parsing is needed.
  - Key: `EVP_BytesToKey(MD5, no salt, 1 round)` of the password, 16 bytes.
  - TCP: send a random 16-byte IV, then RC4 with key `MD5(key || iv)` over the whole stream (address header + data). Server's reply starts with its own 16-byte IV; decrypt likewise.
  - UDP: same port number as TCP. Per packet, a fresh 16-byte IV plus RC4 over `[address header + payload]`; strip and decrypt in the other direction; one upstream UDP socket per client flow.
- Weakness to tell the user: loopback listener with no authentication (another app on the phone could use it). Optional hardening: AEAD on the loopback side.
- Hooks: insert before each relevant call, using the String register of the call:
  `invoke-static {vX}, Lcom/<bridgepkg>/Bridge;->rewrite(Ljava/lang/String;)Ljava/lang/String;` then `move-result-object vX`. If the register number is above 15 use `invoke-static/range`. The patcher must locate the calls by the fixed gomobile signatures (`CoreController;->startLoop(Ljava/lang/String;I)V`, `Libv2ray;->measureOutboundDelay(Ljava/lang/String;Ljava/lang/String;)J`), never by R8 names, and must fail with a clear message if a call is missing or the register is reused after the call.
- Bridge classes: write Java, compile with `--release 8`, convert to dex with d8 (`com.android.tools.r8.D8` in the R8 jar), then to smali with baksmali (bundled inside the apktool jar). Use a package that cannot collide with the app.

## 6. Rename rules ("Longoipo")

- New package suggestion: `com.longoipo.app` (any unique value works). It installs beside the original because it also has a different signer.
- Keep class files in place. Only the application ID changes.
- In `AndroidManifest.xml`: set `package`; replace dotted `com.v2raytun.android...` in attribute values (permissions, authorities, actions, process) EXCEPT values that resolve to an existing class (`smali*/com/v2raytun/android/.../X.smali`), which must stay.
- In smali: replace dotted `const-string` literals with the same "does it resolve to a class file" test. Leave slash-form descriptors (`Lcom/v2raytun/android/...;`) untouched.
- Labels: replace visible text `v2RayTun` (not inside URLs or package names) in `res/values*/strings.xml` for every locale, including `app_name`. Launcher icon is unchanged unless the user asks.
- Risks: Firebase/Play Services may not match the new package (expect harmless failures; unverified); Play in-app update will not apply.

## 7. Build, sign, release

1. Patcher (Python 3 + Java 11+): `apktool d` -> add bridge smali -> apply hooks -> rename -> `apktool b`.
2. Align and sign. `resources.arsc` must be stored uncompressed and 4-byte aligned; if the manifest has `extractNativeLibs="false"`, `.so` files need page alignment (`zipalign -p 4`). Use `zipalign` and `apksigner` from the runner's Android build-tools. Sign with v2+v3 (apksig 8.5.2 also works).
3. Keystore: the user generates it once (`keytool -genkeypair ...`) and stores it as GitHub Secrets (base64 keystore, store password, alias, key password). Never generate or store it in the repo. Same key on every build = updates install over each other.
4. Workflow in `.github/workflows/`: trigger `release: published` (and `workflow_dispatch`), NOT `push`. Steps: download the universal APK asset, run the patcher, sign, verify, upload `Longoipo-<version>.apk` to the same release. A 60 MB APK exceeds GitHub's web upload limit for repo files, so it must be a Release asset.
5. Tests to run in the sandbox before shipping: bridge vs a local rc4-md5 server (TCP and UDP); RC4 and key derivation cross-checked against OpenSSL; real Xray at commit `94ffd50060f1` with the rewritten config (requires the user to allow building/running Xray, or supply a binary); the patcher end to end with signature verification. Then the user tests on a device: import an rc4-md5 node, connect, browse, do a UDP check (DNS or a call), run the delay test; collect logcat.

## 8. Re-check on every new release (checklist)

1. New APK: hash, version, signer certificate (has the signing key changed?), ABIs.
2. Core version: `strings` on `libgojni.so`; note the Xray version. Does it now natively support rc4-md5 or other legacy ciphers? If yes, the bridge may be unnecessary.
3. Are the gomobile signatures unchanged (`startLoop(String,I)`, `measureOutboundDelay(String,String)`)? Find the call sites again with `grep -rn 'Llibv2ray/CoreController;->startLoop\|Llibv2ray/Libv2ray;->measureOutboundDelay' smali*` and inspect the register use around each.
4. Does `ss_securitys` (or the importer) now validate methods, or has `rc4-md5` appeared?
5. Package-name references: repeat the manifest and smali greps in section 4; new providers, permissions, actions or process names must be covered by the rename rules.
6. New tamper checks: repeat the API grep for signatures, installer, integrity, debugger, root, frida (outside androidx/kotlin/okhttp/com.google).
7. jadx error count roughly the same as before (about 180); a big jump means new obfuscation or a new packer.
8. Did the config JSON change shape (new outbound fields, TUN inbound handling)? Diff a dumped sample config against the previous release.
9. Does `probeString` / `//crypt` behaviour still live in the native core only?

## 9. Known unknowns

- Whether Xray at this commit accepts `method: "none"` for Shadowsocks over TCP and UDP (the app's own cipher list offers `none` and `plain`, which suggests yes). Fallback design: rewrite to a SOCKS outbound and implement SOCKS5 CONNECT and UDP ASSOCIATE in the bridge.
- How the vendor core consumes the TUN fd and the `tun` inbound; whether UDP over a SOCKS or SS outbound behaves as expected through it.
- Effect of the new package on Firebase and Play Services; whether the app misbehaves after a rename beyond the strings listed.
- Throughput: RC4 and MD5 should be cheap and the extra loopback hop small; no measurement exists.

## 10. Build results (2026-09-29)

Everything below was run in the sandbox against `v2RayTun_universal.apk` 5.25.81.

- **Bridge** (`longoipo/bridge/src`, package `com.longoipo.rc4`): `Rc4Bridge.rewrite(json)` plus a TCP/UDP transcoder. `bridge/run_tests.sh` passes: 16 unit checks (EVP_BytesToKey and a 32-byte rc4-md5 keystream generated with OpenSSL, the classic RC4 vector, rewrite cases including untouched AEAD, TLS and WebSocket nodes) and an end-to-end run against a separately written Python rc4-md5 server (TCP 1 MiB, domain header, half-close, 8 parallel connections, UDP single and multi-packet flow).
- **Smali**: `bridge/make_smali.py` (javac --release 8 -> D8 9.1.31 -> baksmali 3.0.9) generated `bridge/smali/` (10 files). The output re-assembles with smali.
- **Patcher** (`patcher/patch.py`, about 1 minute): found and hooked `smali_classes2/q/r.smali:459` (`startLoop`, register `v4`) and `CoreTestService.smali:437` (`measureOutboundDelay`, register `p0`); rename counts manifest 11, smali 132 (=119+5+4+3+1), res 2, labels 10. The rebuilt APK (60.3 MB) verifies with apksig (v2 and v3), has package `com.longoipo.app`, `app_name` "Longoipo", the bridge in the dex, and all three `libgojni.so`. In `CoreVpnService.d` the register passed to `addDisallowedApplication` now loads `"com.longoipo.app"`. No old-package literal remains in smali; the manifest keeps only names that resolve to real classes; the native core does not contain the old package string.
- **Signing**: a throwaway PKCS12 key was used for the test only; the real key must be created by the owner and stored as secrets (see README).
- **Speed**: RC4 alone ran at about 288 MB/s on a 4-core server JVM, per-stream setup 1.2 microseconds. A phone will be slower; no phone measurement exists.
- **Not done / open**: Xray `none` check, an actual GitHub Action run, the device test, and hardening the loopback listener (no auth).
- **Rename notes**: `res/xml/shortcuts.xml` keeps `targetClass` (real classes) and gets the new `targetPackage`; `activity_settings.xml` keeps its fragment class name; broadcast actions and provider authorities are renamed consistently in the manifest and in smali.
