# Facet — plan

> Working name. Private repo, personal use only; never shared or published.

**Goal:** take Google's official Pixel Camera app (GCam), edit it directly,
and make it install and run as **a normal camera on any reasonably modern
Android phone**: the Pixel Camera UI, photos and video. Google's HDR+
processing isn't needed. Custom features come later.

Status: plan only.

---

## 1. Approach

Decompile the APK, edit it (manifest, resources, smali, and our own added
Java/Kotlin code where that's simpler), rebuild, sign, install.

Since the repo is private and never shared, it can hold what's needed to
rebuild:

- **The original APK:** pinned by version and SHA-256 and stored as a
  release asset in this private repo, because GitHub limits files in git to
  100 MB.
- **Our changes as patch files:** unified diffs against the decompiled tree
  (`patches/*.patch`), plus any whole new files we add. Keeping changes as
  patches, rather than committing the full decompiled tree, keeps the repo
  small, shows exactly what we changed, and makes moving to a newer GCam
  version a matter of re-applying and fixing patches.
- **A build script** that runs: download the original → decompile → apply
  patches → add our code → rebuild → zipalign → sign → APK.

Editing workflow: `./facet.sh work` decompiles into `work/` (gitignored) with
patches applied. Edit there freely, then `./facet.sh save` writes the
changes back into `patches/`. CI runs the same script and uploads the APK as
a build artifact. That's fine because the repo is private.

**Tools:**
- **Decompile and rebuild:** apktool.
- **Merge split APKs into one:** APKEditor. Pixel Camera ships as an app
  bundle with per-ABI and per-density splits.
- **Read the code:** jadx, which decompiles to Java for understanding; edits
  happen in smali.
- **Sign:** zipalign and apksigner, with our own key stored as a repo secret.
- **Add our own code:** a small Gradle module whose classes are compiled to
  smali (dex → baksmali) and dropped into the tree.

---

## 2. Scope: "essentially a camera"

**In:**
- The viewfinder, with the Pixel Camera UI and its controls.
- Photo, with a plain capture path: the camera's own JPEG, or a single
  frame.
- Video, using standard Camera2 recording.
- Front/back switch and zoom, with lens switching where the phone exposes
  its lenses.
- Flash, timer, grid, aspect ratio, settings, saving to the gallery.

**Out:** the HDR+ multi-frame pipeline and the modes built on it (Night
Sight, Astrophotography, Portrait, Super Res Zoom, Top Shot, Long Exposure,
Action Pan), plus anything that needs Tensor or Google servers (Video Boost,
Night Sight video, Magic Editor). These are **hidden**, not left broken.

"Any phone" here means arm64 (Pixel Camera ships no 32-bit code), the
minimum Android version of the chosen base APK, and a working Camera2 back
camera. Without HDR+ there's no need for RAW streaming or per-sensor tuning,
so cheap phones are just as supported as flagships.

---

## 3. Choosing the base version

The deciding question is now **"which version still has a capture path that
doesn't go through HDR+?"** Older GCam (7.x/8.x) had an HDR+ on/off setting
and a non-HDR+ capture path, which is why ports could offer "HDR+ off".
Recent Pixel Camera (9.x/10.x) always routes stills through HDR+ on Pixels,
and is more tied to Tensor and recent Android.

M0 checks the newest version first:
1. If it has a usable non-HDR+ still path, use it: newest UI, least work
   later.
2. If not, either replace its capture call with our own simple Camera2 JPEG
   capture (§4.4), or use the newest version that still has the path.

Whichever is less code wins. This is decision D1.

---

## 4. What stops it running on other phones, and the edits

### 4.1 Install

| Blocker | Edit |
| --- | --- |
| Split APKs | Merge base + arm64 + density splits into one APK (APKEditor) before decompiling |
| Same package name as Google's app (clashes with a stock install, Play would "update" it) | Rename the package, its provider authorities and its permissions so it installs next to anything |
| Manifest requires Pixel-only shared libraries / system features | Mark them `required="false"` or remove them; guard their uses (§4.5) |
| minSdk / targetSdk | Keep as the base sets them. Lowering minSdk is possible only if M0 finds no newer-API calls on the paths we keep |
| Signature | Re-signed with our key. Any self-check of Google's signature is found and removed (§4.5) |

### 4.2 Device gating at launch

Pixel Camera works out which Pixel it's on (model, board, Pixel feature
flags, system properties) and loads that **Pixel's device profile**: camera
IDs, sensor info, which modes exist and HAL options. On another phone that
lookup fails or returns nonsense.

**Edit:** replace the profile lookup with a **generic profile** built by our
code at startup from Camera2:
- which cameras exist;
- their facing and focal lengths;
- supported sizes and frame rates;
- flash, autofocus and zoom range;
- whether a logical multi-camera with `CONTROL_ZOOM_RATIO` exists.

The profile marks every HDR+-based mode as unsupported. We don't spoof a
specific Pixel model: it changes behaviour unpredictably.

### 4.3 Camera and HAL assumptions

| Assumption in the app | Edit |
| --- | --- |
| Fixed camera IDs and lens layout per Pixel | Use the IDs and lenses from the generic profile |
| Fixed stream sizes/formats (Pixel RAW sizes, specific YUV/JPEG sizes, ZSL buffers) | Choose from the phone's stream configuration map. No RAW and no ZSL ring buffer, since there's no HDR+ |
| Google vendor tags (`com.google.*`) in requests and results | Check that a tag exists before using it; otherwise skip it. Never crash on a missing key |
| Pixel HAL video features (EIS hints, HDR video tags) | Standard Camera2 video stabilisation where available; hide the rest |
| Sensor/sound/haptics tuned per Pixel | Read from the phone, or fall back to defaults |

### 4.4 Stills without HDR+

The point where the app turns the shutter press into an HDR+ burst is
replaced with one of these, in order of preference (M0 decides):

1. **The app's own non-HDR+ path**, if the base version has one: force it on
   and remove the HDR+ setting.
2. **Our capture code:** one `TEMPLATE_STILL_CAPTURE` request to a JPEG
   stream at the chosen size, with AF/AE precapture when needed. The
   resulting bytes go into the app's normal save-and-thumbnail flow, so the
   UI, the thumbnail animation and the gallery hand-off are unchanged.

Option 2 is a small, self-contained piece of our own code, called from one
patched spot. It's also what later custom features (manual controls,
resolution choice) will build on.

### 4.5 Missing Google pieces

| Dependency | Edit |
| --- | --- |
| Pixel-only libraries, feature flags, other Pixel system apps | Guard each call; the feature is hidden or does nothing |
| HDR+ / Tensor native libraries (still loaded at startup?) | Skip loading them if nothing we keep needs them; drop them from the APK to save size |
| Google Photos hand-off, Lens, account features | Open the system gallery; hide Lens |
| Integrity or signature self-checks, if any | Remove |
| Telemetry and crash upload to Google | Remove |

### 4.6 Per-phone quirks

Without HDR+, quirks should be rare and small (a stream size the HAL
rejects, a missing autofocus mode). They're handled by falling back to the
next-best supported value in our code. A small `quirks.json` keyed by
manufacturer/model is added only if a real phone needs it.

---

## 5. Repo layout

```
facet/
├─ base.json          Pinned base: version, download (private release asset), SHA-256
├─ patches/           Unified diffs against the decompiled tree, one per change, numbered
├─ java/              Our own code (generic profile, simple capture, guards) → dex → smali
├─ facet.sh           work | save | build: decompile, apply, edit, rebuild, sign
├─ docs/
│   ├─ NOTES.md       What each part of the app does, found while reading it
│   └─ DEVICES.md     Phones tested, what works
└─ .github/workflows/build-facet.yml
```

`work/` and any decompiled output are gitignored. The tree is rebuilt from
the base APK and `patches/`, so it never needs committing.

---

## 6. CI

On every push:
1. Download the pinned APK (private release asset) and check its SHA-256.
2. Decompile, apply the patches (fail if one doesn't apply), add our code,
   rebuild, sign with the key from secrets.
3. Upload `facet.apk` as an artifact.
4. **Emulator smoke test:** an Android emulator with its virtual camera
   installs the APK and launches it. It checks the viewfinder opens,
   switches to the front camera, takes a photo (and checks a JPEG lands in
   the gallery), records 3 s of video, and fails on any crash in logcat.
   Screenshots go into the artifact.

The emulator stands in for "a phone that isn't a Pixel", so every push is
tested against the thing that matters most here.

---

## 7. Testing on real phones

Install the CI artifact. Minimum spread: one Samsung (Exynos or Snapdragon),
one Xiaomi/Redmi (MediaTek), one budget phone (Unisoc or Helio), and one
Pixel (to make sure nothing broke there). Each row in `docs/DEVICES.md`
covers: opens, photo, video, front camera, lens switching, crashes.

---

## 8. Milestones

| # | Milestone | Done when |
| --- | --- | --- |
| **M0** | **Survey.** Get the newest Pixel Camera (and one older GCam for comparison); merge splits; decompile; find the device-profile lookup, camera-ID/stream setup, the shutter → HDR+ hand-off, the native libraries loaded at startup, and any self-checks. Write `docs/NOTES.md`. Pick the base (D1). Set up `facet.sh`, the private release asset and CI (rebuild unmodified, sign, install on the emulator). | An unmodified rebuild installs on the emulator; notes cover every spot §4 needs. |
| **M1** | **Installs and opens.** Package rename, manifest fixes, device gate, generic profile, Google-service guards, telemetry removed. | Viewfinder opens on the emulator and on a non-Pixel phone. |
| **M2** | **Takes photos and video.** Non-HDR+ still path (§4.4), standard video, vendor-tag guards, HDR+ modes hidden, HDR+ libraries not loaded. | Smoke test passes: photo and video saved, front camera works, no crashes. |
| **M3** | **Lenses, flash and settings.** Lens switching from the generic profile, flash, timer, aspect ratios, settings screens with removed features cleaned out. | Every remaining control works on the emulator and two real non-Pixel phones. |
| **M4** | **Hardening.** Real-phone matrix (§7), quirk fallbacks, APK size trimmed (drop unused native libraries and assets). | Works on every phone in the matrix. |
| later | **Custom features** (resolution and frame-rate choice, manual controls, etc.) on top of our capture code. | |

---

## 9. Risks

| Risk | Mitigation |
| --- | --- |
| The newest version has no non-HDR+ path and its capture code is deeply tied to HDR+ | Our own capture code (§4.4 option 2) behind one patched call; or an older base |
| Obfuscated code changes between versions | Pin one base version; update deliberately; patches are small and documented in `NOTES.md` |
| The app needs newer Android than some phones run | The base version decides the floor; an older base lowers it |
| Pixel HAL assumptions hidden in many places | Fail-safe guards around vendor tags and camera IDs; emulator smoke test on every push catches crashes |
| Google's terms forbid modifying the app | Private repo, personal use, never distributed |

---

## 10. Decisions for you

- **D1 Base version:** newest Pixel Camera if it can capture without HDR+
  cleanly (my lean), otherwise the newest older version that can. M0 reports.
- **D2 Name:** "Facet" is a placeholder.
