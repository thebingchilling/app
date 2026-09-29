# Pebble — handoff for the next agent

Read this first, then `/CLAUDE.md` (repo rules: commit and push straight to
`main`, no branches, no PRs).

## Goal (confirmed by the user)

**Pebble** = FlClash's Android app with its Flutter UI kept **1:1** (the user
wants it to look, feel and scroll exactly like FlClash), rebranded, running
"their own engine". Plan A was chosen over polishing the Kotlin `tidewall/` app.

User decisions:
- Name **Pebble**; custom icon that does **not** suggest a VPN (idea: three
  stacked rounded stones, flat, with an Android 13+ monochrome/themed layer).
  No shield/key/padlock. Do not use FlClash's name or icon.
- Build the whole app in one go (not phased).
- Not answered explicitly, so defaults were taken: new folder `pebble/`
  (`tidewall/` stays as it is); drop desktop-only features (hotkeys, tray,
  system proxy, Windows loopback, desktop helper), Firebase crash reporting,
  and FlClash's app-update checker.
- The user interrupts long **foreground** commands. Run builds with
  `run_in_background` and keep the user posted in short lines.

## Engine decision (tell the user if you change it)

FlClash's core (`core/`, mihomo fork `core/Clash.Meta`, submodule at
`70f0570`) already has **WireGuard/AmneziaWG and OpenVPN outbounds**. So
WireGuard `.conf` and OpenVPN `.ovpn` files become mihomo profiles, running
through FlClash's own VPN service, UI, delay tests and IP check. No separate
"direct" engine is needed. The user has not been told this detail yet; mention
it when reporting.

Port the converters from Tidewall's Go code into Dart (or add a core method):
- `tidewall/core/wireguard.go` → `ProfileFromWireGuard` (+ `parseWireGuard`)
- `tidewall/core/ovpn.go` → `ovpnToProxy` / `ProfileFromOvpn`, including
  inline `<auth-user-pass>` credentials
- `tidewall/core/convert.go` → `profileFromProxies` (Proxy select + Auto
  url-test groups, `MATCH,Proxy`)
- Detection: `tidewall/android/.../data/ContentDetector.kt`
  (`looksLikeOvpn`, `looksLikeWireGuard`)

Hook them into FlClash's add-profile paths (file, URL, QR, clipboard/link):
look in `lib/providers/action.dart` (`profilesActionProvider`:
`addProfileFormFile`, `addProfileFormURL`) and `lib/views/profiles/add.dart`.
An `.ovpn` with bare `auth-user-pass` needs a **username/password dialog**
(user's earlier complaint). Explain in it that providers often issue separate
OpenVPN/service credentials. Store the creds in the generated YAML.

## Done so far (this commit)

- FlClash (commit of 2026-09-17, `chen08209/FlClash` main) copied into
  `pebble/`, without `.git`, desktop platform folders (`windows/`, `linux/`,
  `macos/`), `snapshots/`, or FlClash's agent config files (`AGENTS.md`,
  `CLAUDE.md`, `.agents`, `.claude`, `.codex`, `.gemini`, `.github`...).
- Engine source: root `.gitmodules` submodule `pebble/core/Clash.Meta` →
  `https://github.com/chen08209/Clash.Meta.git` @ `70f0570` (branch FlClash).
- Geo databases (`assets/data/*`, 44 MB) are git-ignored. Fetch them with
  `pebble/tool/fetch_geodata.sh` (locally and in CI, before building).
- Android build files:
  - Firebase removed (plugins, deps, `google-services.json`).
  - `GlobalState.setCrashlytics`/`didCrashOnPreviousExecution` are no-ops.
  - `applicationId = "app.pebble.android"` (Kotlin packages stay
    `com.follow.clash`: the channel names use `Components.PACKAGE_NAME`, not
    the app ID, so only the ID was changed).
  - Release signing reads the env vars `PEBBLE_KEYSTORE`,
    `PEBBLE_KEYSTORE_PASSWORD`, `PEBBLE_KEY_ALIAS`, `PEBBLE_KEY_PASSWORD`.
    Without them it falls back to the debug key, with no `.dev` suffix.
- **Not yet built even once.**

## Remaining work, in order

1. **First build.** `cd pebble && flutter pub get && dart setup.dart android
   --arch arm64` (read `setup.dart`; FlClash CI uses Flutter 3.47.1, Go
   1.26.x, NDK r28c, Rust with Android targets). Fix what breaks after the
   Firebase removal: grep Dart for `crashlytics` (`lib/bootstrap.dart`,
   `lib/common/boot_guard.dart`, `lib/views/application_setting.dart`, models)
   and hide the toggle/tip. Do not hand-edit generated files; run
   `dart run build_runner build` after model changes.
2. **Rebrand the UI text.**
   - `lib/common/constant.dart`: `appName`, `repository`; keep `packageName`,
     it is the channel prefix.
   - `appName` in `arb/*.arb`, then regenerate l10n (`lib/l10n`).
   - `android/common/src/main/res/values/strings.xml`.
   - The About page (`lib/views/about.dart`): credit FlClash (GPL-3.0) and
     mihomo; remove FlClash links/Telegram and the avatars.
   - `README.md` rewritten for Pebble.
3. **Icon.** Replace the launcher icons (`android/app/src/main/res/mipmap-*`,
   adaptive icon XML), `assets/images/icon.png`, and the notification small
   icon.
4. **Remove the update checker** (grep `autoCheckUpdate`, `checkUpdate`,
   `repository`, `update_dialog`). Hide the setting.
5. **Desktop-only features.** They are already platform-gated on Android. Just
   make sure nothing Android-visible references them. Optionally delete
   `services/helper` (Rust desktop helper) if the build hook allows it.
6. **WireGuard/OpenVPN import** as described above, plus Dart tests
   (`flutter test`).
7. **CI**: `.github/workflows/build-pebble.yml`.
   - Triggers: push to `main`, paths `pebble/**` + the workflow file, and
     `workflow_dispatch`.
   - Checkout with `submodules: recursive`.
   - Setup: Flutter 3.47.1, Go, Java 17+, Android SDK/NDK r28c, Rust + Android
     targets.
   - Run `tool/fetch_geodata.sh`; restore the keystore from a
     `PEBBLE_KEYSTORE_BASE64` secret if present.
   - Build `dart setup.dart android --arch arm64` (or `flutter build apk
     --split-per-abi`), then upload the APK artifact `Pebble-apk`.
   - Model it on `.github/workflows/build-tidewall.yml` and FlClash's
     `build.yaml` android job. Document the secrets in `pebble/README.md`.
8. Add Pebble to the Apps list in `/CLAUDE.md`, push, and watch the CI run
   until it is green. Tell the user where the APK is: they must uninstall other
   builds signed with a different key.

## Environment gotchas (seen in this session)

- The agent proxy returned **HTTP 429 from repo.maven.apache.org**. The local
  fix is `~/.gradle/init.d/mirror.gradle`, swapping Maven Central for
  `https://maven-central.storage-download.googleapis.com/maven2/`. Not
  committed; recreate it if needed.
- Go: use one toolchain (`GOTOOLCHAIN=local` with Go ≥1.26 on `PATH`); mixing
  auto-downloaded toolchains broke a gomobile build with "could not import
  crypto/sha256".
- There is no KVM, so no emulator: UI can't be checked visually here.

## Related uncommitted-then-committed Tidewall fix

Also in this commit, `tidewall/` has a small OpenVPN fix: set
`disableClientCert` when the `.ovpn` has no `<cert>`/`<key>`. OpenVPN 3
otherwise reports "Profiles using an Android keystore certificate are not
supported". The test is in `ContentDetectorTest.ovpnClientCertDetection`. It
was **not built locally** before pushing; the Tidewall CI run on this push
builds and tests it. Check that run and fix it if it is red.

## Pending question: a Windows version

The user asked whether the same app (the same engine as FlClash desktop) would
work well on Windows, and wants a plan before any work. It is **not decided**.
Until it is:
- do **not** delete `services/helper` (the Rust Windows service helper),
  the desktop code paths in `lib/`, or `tool/`;
- the `windows/` platform folder was left out of this copy; restore it from
  FlClash at the same commit if Windows is approved.
If approved, the plan is: restore `windows/` and rebrand it (exe name, icon,
installer via Inno Setup in `distribute_options.yaml`); keep the desktop
features for Windows only (tray, hotkeys, system proxy, TUN through the helper
service, loopback); build with `dart setup.dart windows` on a `windows-2022`
runner (GCC + Inno Setup), with its own CI job. WireGuard/OpenVPN import comes
for free because it is Dart + mihomo.
