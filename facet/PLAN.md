# Facet — plan

> Working name. Never "Google Camera" or "Pixel Camera" in the name, icon or
> listing: those are Google's trademarks.

**Goal:** take Google's official Pixel Camera app (GCam) and patch it so it
installs and runs on **any** reasonably modern Android phone, not just one
model. That is the opposite of today's ports, which are each hand-tuned for one
phone. Custom features come later, once the universal base works.

Status: plan only.

---

## 1. How this can be done without distributing Google's app

Pixel Camera is closed-source Google software. Copying its code into a public
repo, or publishing modified APKs, would be redistributing Google's
copyrighted work. That is how GCam ports have always circulated, and it's the
part that gets projects taken down. So Facet uses the **ReVanced model**:

- **The repo holds only our own work:** patch definitions, our injected
  compatibility code, device data and tools. No Google code, decompiled
  sources, smali or APKs are ever committed. Notes on the original are written
  in our own words.
- **Users patch their own copy.** Each user gets Pixel Camera themselves
  (Play Store download on any phone, or a mirror) and applies Facet's
  patches on their phone with ReVanced Manager, or on a PC with ReVanced CLI.
- **No patched APKs are published.** CI checks that patches apply. It
  uploads a patched APK only if the repo is private (decision D2).

ReVanced's patcher, CLI and Manager are open source (GPL-3.0). Building on
them means our patches are GPL-3.0 too. That's fine, and it gives us three
things for free:
- **Fingerprinting:** patches find obfuscated methods by their shape, so they
  survive Google's renaming between releases.
- **Extensions:** our own Kotlin/Java code compiled into a dex that patches
  can call.
- **On-device patching and re-signing.**

This is not legal advice. Patching for personal use on hardware you own is
the user's call. Google's terms still don't allow modifying its apps, and
GitHub could still act on a DMCA notice even without any Google code in the
repo.

---

## 2. What "all devices" can honestly mean

Pixel Camera is arm64-only, needs a recent Android version (set by the chosen
base version, §3), and its best features need the camera to stream RAW
frames. So the support levels are:

| Level | Phone | What works |
| --- | --- | --- |
| **A** | arm64, Camera2 `FULL` or `LEVEL_3`, RAW capability, enough RAM | Everything that doesn't need Google hardware: HDR+, Night Sight, Portrait, Super Res Zoom, video |
| **B** | arm64, `LIMITED`, RAW present but slow or unreliable | Viewfinder, photo with reduced HDR+ (fewer frames, lower ZSL rate), video |
| **C** | arm64, no RAW or `LEGACY` | App runs; photos and video without HDR+ (single-frame path) instead of crashing |
| — | 32-bit only, or below the base version's minimum Android | Not supported |

The defining difference from existing ports: **the same patched app works on
every A/B/C phone with no per-device config file.** Calibration comes from
the phone itself at runtime (§4.4). Per-device overrides are optional extras,
not requirements.

Features that need Google's Tensor chip or servers are hidden on other
phones: Video Boost, Night Sight video, Magic Editor-style features, anything
using the Tensor TPU.

---

## 3. Choosing the base version

This is the first real decision, and M0 measures it. The options:

- **Latest Pixel Camera (10.x, 2026):** newest UI and features, but the most
  Pixel coupling (Tensor-only paths, newest Android only, possibly tighter
  links with other Pixel system apps), and the least community knowledge to
  lean on.
- **An older GCam (8.x or 9.x):** the generation most community ports are
  built on. Its HDR+ runs on plain GPU/CPU, it has fewer Pixel-only
  dependencies and works on older Android versions.

**Recommendation:** M0 decompiles both, *locally and never committed*, and
lists each one's blockers (§4). Start with whichever has fewer hard blockers,
probably the newest version that is known to port well. Because the patches
use fingerprints, moving to a newer base later means re-checking each patch,
not starting over.

---

## 4. What blocks Pixel Camera on other phones, and the fix for each

Each blocker becomes one or more patches, named after what they do.

### 4.1 Install

| Blocker | Patch |
| --- | --- |
| Same package name as Google's app (clashes with a stock install; Play updates would replace ours) | `rename-package`: new package and authorities, so it installs next to anything |
| Manifest requires Pixel-only shared libraries or system features | `relax-manifest`: mark them `required="false"`, then guard every use at runtime (§4.6) |
| Split APKs (per-ABI, per-density bundles) | Build the patcher input from the arm64 split. ReVanced handles merging; we document the inputs |
| Google's signature | Re-signed by the patcher with the user's key (both Manager and CLI do this) |

### 4.2 Launch-time device gating

Pixel Camera decides what it is from the device it's running on: model and
board names, Pixel feature flags, system properties. It then loads a
**per-Pixel device profile** (camera IDs, sensor names, supported modes,
tuning). On an unknown phone that lookup fails, or it picks wrong values.

- `device-gate`: patch the identity checks so the app accepts any phone, and
  never spoof a specific Pixel globally (that changes behaviour unpredictably).
- `generic-profile`: replace the per-Pixel profile lookup with a profile our
  extension builds at runtime from Camera2 (§4.3, §4.4). This is the core of
  "universal".

### 4.3 Camera layout and HAL assumptions

| Assumption baked into the app | Universal replacement (our extension code) |
| --- | --- |
| Fixed camera IDs (back = 0, front = 1, specific logical/physical IDs per Pixel) | Enumerate cameras from CameraManager; work out lenses by 35 mm focal length; lens switching via logical cameras and CONTROL_ZOOM_RATIO where the phone supports them |
| Fixed stream sizes and formats (specific RAW size, RAW10, ZSL at preview rate) | Choose from the phone's stream configuration map; RAW_SENSOR, RAW10 or RAW16 as available; reduce ZSL rate and buffer depth on Level B |
| Google vendor tags (`com.google.*`) for HAL features (EIS, HDR+ hints, sensor modes) | Check whether each tag exists before reading or writing it; fall back to standard Camera2 keys or skip the feature |
| Pixel HAL video features (stabilisation, HDR video) | Standard Camera2 stabilisation modes; hide what the HAL can't do |
| Front-camera and flash behaviour tuned per Pixel | Read from the phone's characteristics |

### 4.4 HDR+ calibration: why ports are per-device, and the universal answer

HDR+ merges a burst of RAW frames. To do that well it needs to know the
sensor:
- **Noise model:** how noise grows with brightness and ISO.
- **Black level and white level.**
- **Colour matrices**, from sensor colour to standard colour.
- **Lens shading:** corner falloff.
- **Raw format:** the bayer pattern and how the data is packed.

On Pixels these come from Google's tuning. Port makers copy them from a
similar Pixel or hand-tune them for one phone (the "libs" and "noise model"
settings). That is why each port fits one device.

Camera2 already exposes almost all of this, per phone and often per frame:

| HDR+ input | Where Facet gets it |
| --- | --- |
| Noise model | `SENSOR_NOISE_PROFILE` in each capture result (per channel, per frame). If it's missing, a one-time **calibration capture** (§4.5) |
| Black level | `SENSOR_DYNAMIC_BLACK_LEVEL` per frame, else `SENSOR_BLACK_LEVEL_PATTERN` |
| White level | `SENSOR_DYNAMIC_WHITE_LEVEL`, else `SENSOR_INFO_WHITE_LEVEL` |
| Colour | `SENSOR_COLOR_TRANSFORM1/2`, `SENSOR_FORWARD_MATRIX1/2`, `SENSOR_CALIBRATION_TRANSFORM1/2`, reference illuminants |
| Lens shading | `STATISTICS_LENS_SHADING_CORRECTION_MAP` (request it with `STATISTICS_LENS_SHADING_MAP_MODE_ON`) |
| Bayer layout | `SENSOR_INFO_COLOR_FILTER_ARRANGEMENT`, the active array, and the plane's row stride |

The `hdrplus-calibration` patch intercepts the point where the app hands
sensor data to its HDR+ library and fills those values from our extension
instead of the Pixel tables. Finding that boundary is the main reverse
engineering task in M0 and M3. If the native library keeps its tuning
internally, the fallback is to patch the tables it loads, rather than the
native code.

### 4.5 Calibration fallback

Some phones report missing or wrong noise profiles. For those, a guided
calibration (about 1 minute, phone on a table) captures dark frames (lens
covered) and flat frames at several ISOs. It fits the noise model and black
level and saves them as a local device profile. Users can choose to share it
by exporting a file, which can then be added to the repo's profile database.

### 4.6 Missing Google pieces

| Dependency | Handling |
| --- | --- |
| Pixel-only shared libraries, feature flags, other Pixel system apps | Guard every call site; the feature is unavailable with a message, not a crash |
| Google Photos hand-off, Lens, Google account features | Open the system gallery instead; hide Lens |
| Tensor TPU / Google-only acceleration | Force the GPU/CPU paths where they exist; hide TPU-only modes |
| Integrity or signature self-checks, if any are found | Neutralise them (`skip-self-check`); none are assumed until M0 finds them |
| Telemetry, crash upload to Google | Disable (`no-telemetry`): a patched build shouldn't phone home |

### 4.7 Per-chipset quirks

Snapdragon, Exynos, MediaTek, Tensor and Unisoc HALs differ (RAW support,
ZSL behaviour, vendor tags, timestamps). Quirks live in a small,
human-readable `profiles/` database keyed by SoC and model. Each entry is a
data change (e.g. "ZSL depth 4", "use RAW16"), never a fork of the patches.

---

## 5. Project layout

```
facet/
├─ patches/            ReVanced patch definitions (Kotlin), one file per patch in §4
├─ extension/          Our injected code (Kotlin → dex): runtime device profile,
│                      Camera2 calibration reader, calibration wizard, compat report screen
├─ profiles/           Optional per-SoC/per-device overrides (JSON), community-contributed
├─ tools/
│   ├─ fetch-apk.md    How users get the base APK and check its SHA-256 (no download links to mirrors in code)
│   ├─ decompile.sh    Local-only jadx/apktool run into an ignored folder, for writing patches
│   └─ triage.py       Turns a logcat into a short "what crashed and which patch is involved"
├─ docs/
│   ├─ BLOCKERS.md     Our own notes on each blocker per base version (no Google code)
│   └─ DEVICES.md      Tested phones, level A/B/C, status
└─ .github/workflows/  Build patches + extension, unit tests, patch-apply check (§6)
```

`.gitignore` blocks `*.apk`, `*.apkm`, `*.xapk`, `decompiled/`, `smali/` so
Google's code can't be committed by accident. A CI check also fails the
build if any such file appears.

---

## 6. CI

- **Every push:** build the patch bundle (`.rvp`) and the extension, run unit
  tests (profile builder, calibration fitting, quirk lookup), lint.
- **Patch-apply check:** CI downloads the base APK from a URL stored as a
  repository secret (the user's own copy, in private storage), checks its
  SHA-256, applies every patch with ReVanced CLI and fails if any
  fingerprint misses. The patched APK is not uploaded unless the repo is
  private (D2).
- **Release:** a GitHub release with the patch bundle. Users add the repo as
  a patch source in ReVanced Manager and patch on their phone.
- **Emulator smoke test:** an Android emulator with its virtual camera
  installs the patched APK, launches it, opens the viewfinder, takes a
  photo, and checks logcat for crashes. This proves Level C basics only;
  HDR+ needs real hardware.

---

## 7. Testing on real phones

Nothing about HDR+ quality can be checked without hardware. Minimum matrix
for "works everywhere":

| Chip | Example phones | Expected level |
| --- | --- | --- |
| Snapdragon 8-series | Galaxy S (Snapdragon), OnePlus, Xiaomi flagships | A |
| Snapdragon 6/7-series | Galaxy A, Redmi Note, Moto G | A/B |
| Exynos | Galaxy S (Exynos), Galaxy A5x | A/B |
| MediaTek Dimensity / Helio | Redmi, Realme, Oppo, Tecno | B/C (MediaTek RAW is often limited) |
| Tensor (Pixel) | any Pixel 6+ | A (check that we didn't break it) |
| Unisoc | budget phones | C |

Samsung's Remote Test Lab has real Galaxy phones; other models need owners.
Each test fills a row in `docs/DEVICES.md` using the in-app compat report
(level, what's on, calibration source, crashes).

Quality check: the same scene shot with stock camera, an existing per-device
port (where one exists) and Facet. The universal calibration should be close
to the hand-tuned port. If it isn't, that's what `profiles/` is for.

---

## 8. Milestones

| # | Milestone | Done when |
| --- | --- | --- |
| **M0** | **Survey.** Get both candidate base versions; decompile locally; list every blocker per version in `docs/BLOCKERS.md`; find the HDR+ calibration boundary and the device-profile lookup; choose the base (D1). Set up the repo, `.gitignore` guard, ReVanced patch template, CI skeleton. | Blocker list complete for the chosen version; first patch (rename) applies in CI. |
| **M1** | **Installs and opens everywhere.** `rename-package`, `relax-manifest`, `device-gate`, telemetry off, Google-service guards. | Viewfinder opens on the emulator and on one Snapdragon phone, with no Pixel profile. |
| **M2** | **Basic capture everywhere (Level C).** Runtime camera enumeration, stream-size selection, vendor-tag guards, single-frame photo and standard video. | Photo and video work on the emulator plus at least one phone per chip family; no crashes in the smoke test. |
| **M3** | **Universal HDR+ (Level A/B).** `generic-profile` and `hdrplus-calibration` driven from Camera2 metadata; RAW format and stride handling; ZSL depth and rate per level; Night Sight. | HDR+ and Night Sight work on Snapdragon and Exynos phones with no config file; quality close to a hand-tuned port. |
| **M4** | **Lenses and modes.** Ultrawide/telephoto via enumeration and zoom ratio; Portrait (segmentation on GPU/CPU); Super Res Zoom; Panorama; hide Tensor-only modes cleanly. | Each mode works or is hidden, on every phone in the matrix. |
| **M5** | **Calibration wizard and compat report.** Dark/flat-frame noise model fit; exportable profiles; `profiles/` database; `triage.py`. | A MediaTek phone with a bad noise profile gets usable HDR+ after calibration. |
| **M6** | **Hardening and releases.** Device matrix sweep; GitHub releases of the patch bundle; ReVanced Manager source instructions; retarget check against the next Pixel Camera release. | Patch bundle v1.0 released; it applies to the newest base version or the gap is documented. |
| later | **Custom features** on top of the universal base (manual controls, resolution/frame-rate choice, etc.) | |

M0 is the risky step. If the HDR+ library turns out to take its calibration
only from built-in Pixel tables that can't be swapped in, "no per-device
config" becomes "the patches pick the closest Pixel profile and adjust it".
That still works on every phone, but quality varies more. The milestone
reports which of the two applies.

---

## 9. Risks

| Risk | Mitigation |
| --- | --- |
| Google changes obfuscation and internals every release | Fingerprint-based patches; pin a base version; retarget deliberately, not on every release |
| HDR+ tuning lives inside native code that can't be re-parameterised | Fallback above (nearest Pixel profile plus runtime adjustment); document the quality trade-off |
| Many cheap phones can't stream RAW well | Level B/C behaviour: fewer frames, lower rate, or single-frame; never crash |
| Takedown (DMCA) despite no Google code in the repo | Keep the repo free of Google code; private repo option (D2); keep releases to the patch bundle |
| Newer Pixel Camera depends more on Tensor and Pixel system apps | Base-version choice (D1); hide those features |
| Users install patched APKs from strangers | Docs say: patch your own copy with Manager; we never publish APKs |

---

## 10. Decisions for you

- **D1 Base version:** newest Pixel Camera vs the older generation most ports
  use. M0 compares them; my lean is the newest that ports cleanly.
- **D2 Repo visibility:** keep the repo private (then CI can also upload
  patched APKs for you to install), or public with patch bundles only.
- **D3 Patching route:** ReVanced Manager on the phone (recommended, no PC
  needed), ReVanced CLI on a PC, or both.
- **D4 Name:** "Facet" is a placeholder.
