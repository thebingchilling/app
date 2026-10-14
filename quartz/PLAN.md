# Quartz — plan

> Working name. Rename freely (folder, app ID `app.quartz.camera`, strings).

An Android camera app that **looks and feels like Google's Pixel Camera**
(GCam) but runs on the phone's **own camera stack through the public Camera2
API**, so it works on any Android phone. It gives full control over
resolution, frame rate, format and lens in every mode and at every zoom, and it
**never enhances an image**. No HDR merging, no night stacking, no AI, no
beautification, no sharpening, no denoising, no "scene optimiser". What the
sensor saw is what you get.

Status: **plan only, no code yet.** This file is both the design and the
handoff. A code agent starting the project should read all of it, then
`/CLAUDE.md` (commit straight to `main`, no branches, no PRs).

---

## 1. The contract

These are the product rules. Every design choice below follows from them.

1. **Never enhance.** The app never asks the camera for, or applies itself, any
   processing that makes an image look different from the scene: denoising,
   sharpening, local tone mapping or HDR, multi-frame merging, super-resolution,
   AI or scene detection, beauty or face retouching, saturation or contrast
   boosts, simulated bokeh, low-light boost. There is no setting to turn these
   on, because they are not in the app.
2. **Correct the hardware, nothing more.** The steps that turn raw sensor data
   into a faithful picture stay on: black level, demosaic, white balance,
   colour calibration, lens shading, chromatic aberration and (optionally)
   distortion correction, a standard sRGB tone curve. They undo the hardware's
   flaws; they don't improve on the scene. See §3.
3. **Everything the hardware can do, you can choose.** Every resolution, frame
   rate, format, codec and lens the device really supports is selectable, per
   mode and per lens. Options the device can't do are never shown, so nothing
   fails silently.
4. **Be honest about the device.** Phones don't always obey. The app reads
   back what the camera actually did on every frame and shows a
   per-device **Natural report** when the hardware ignored a request (§3.4).
   RAW (DNG) is always offered where the sensor supports it, as the path that
   is guaranteed unprocessed.
5. **Viewfinder aids are never baked in.** Histogram, focus peaking, zebras,
   grid and level only draw over the preview. They never touch saved files.
6. **Private.** No `INTERNET` permission, no analytics, no Google Play
   Services. Location in EXIF only if the user turns it on.

### Non-goals

Panorama and Photo Sphere (stitching), Portrait, Night Sight, Astrophotography,
Top Shot, Motion Photos, Action Pan, motion-blur Long Exposure (multi-frame),
Super Res Zoom, Real Tone, Cinematic video, Video Boost, Night Sight video,
Audio Magic Eraser or speech enhancement, Google Lens, Magic Eraser or Best
Take, and any vendor "Camera Extensions" (HDR, night, bokeh, face retouch,
auto). None of these come back as hidden or optional features.

---

## 2. Engine: Camera2, used directly

**Decision: write our own engine on `android.hardware.camera2`.** No CameraX
for capture, no ported GCam, no fork of an existing app.

| Option | Verdict |
| --- | --- |
| **Camera2 directly** | ✅ Full control of every request key, every stream size, physical lenses, high-speed sessions, RAW, max-resolution mode, 10-bit. The only option that meets contract rules 1 and 3 together. |
| CameraX (+ `Camera2Interop`) | ❌ Good for simple apps, but it chooses stream sizes and combinations for you, doesn't expose constrained high-speed sessions, max-resolution sensor mode or physical-lens streams well, and tends to fall back silently. Our "show only what really works" rule needs the raw API. We still use the **`androidx.camera:camera-viewfinder`** library, which is made for Camera2 users. |
| GCam ports (BSG, Arnova, etc.) | ❌ Proprietary Google code, redistributed without permission, tied to specific SoCs, and the whole point of GCam is HDR+ processing, which we're removing. |
| Fork Open Camera | ❌ GPL-3, Views-based UI from 2013, its own UI idioms. Read it for Camera2 device quirks (it is the best record of them), but don't copy its code if we stay Apache-2.0. |
| Fork Aperture (LineageOS) / GrapheneOS Camera | ❌ Both are CameraX-based, so they have the same limits. Useful references for UI structure, intents, MediaStore and lock-screen handling. |

"Stock engine" therefore means **the phone's own camera HAL and ISP**, driven
through the public API with every optional processing block turned off.

### Reference code (read, borrow carefully)

- Google `android/camera-samples`: Camera2Basic, Camera2Video,
  Camera2SlowMotion (Apache-2.0). Starting point for session and recording code.
- Google `jetpack-camera-app` (Apache-2.0): modern Compose camera UI from
  Google, closest to Pixel Camera's structure.
- AOSP `packages/apps/Camera2` (Apache-2.0): the old Google Camera app.
  Shows how GCam was structured before HDR+.
- LineageOS Aperture (Apache-2.0, check the repo before borrowing),
  GrapheneOS Camera (MIT): intents, secure camera, MediaStore, EXIF.
- Open Camera (GPL-3): **knowledge only.** Manual white balance from colour
  temperature, device quirks, DNG edge cases.

---

## 3. What "natural" means in Camera2 terms

### 3.1 What runs, and what doesn't

| Stage | Where | Quartz | Why |
| --- | --- | --- | --- |
| Black level, defect map from factory calibration | Sensor/ISP | always (can't be turned off) | needed for a valid image |
| Demosaic | ISP | always | needed to turn Bayer data into RGB |
| Auto exposure, auto focus, auto white balance | ISP | on by default, fully manual in Pro | choosing settings, not editing the image |
| Colour correction matrix (sensor → sRGB) | ISP | on (`FAST`) | calibrated colour, not stylised |
| Lens shading correction | ISP | on (`FAST`), Advanced toggle to turn off | otherwise corners go dark or tinted |
| Chromatic aberration correction | ISP | on (`FAST`), same toggle | lens flaw |
| Distortion correction | ISP (API 28+) | on (`FAST`), same toggle | lens flaw, mostly matters on ultrawide |
| Tone curve | ISP | **sRGB preset curve** where supported | standard transfer function. The device default often adds an S-curve. |
| Noise reduction | ISP | **`OFF`** (else `MINIMAL`, else lowest available) | enhancement |
| Edge enhancement (sharpening) | ISP | **`OFF`** | enhancement |
| Hot pixel correction | ISP | **`OFF`** where available | see decision D3 |
| Scene modes, effects, extended scene (bokeh) | ISP | `DISABLED` / `OFF` | enhancement |
| Low-light boost AE mode (API 35) | ISP | never requested | enhancement |
| Auto framing (API 34) | ISP | `OFF` | changes framing for you |
| Camera Extensions (vendor HDR, night, bokeh, beauty) | vendor library | **never used**, not linked | enhancement |
| Ultra HDR JPEG (`JPEG_R`, gain map) | ISP/encoder | not offered | computed HDR rendering |
| Optical image stabilisation | lens hardware | on (physical, doesn't touch pixels) | |
| Electronic video stabilisation | ISP | see decision D1 | warps and crops frames |
| Post-RAW digital gain | ISP | 100 (none) in manual exposure | under auto exposure the HAL owns it |
| JPEG encode | ISP | user picks quality, default 95 | output format |
| Zoom between lenses | ISP | plain crop and lens switch, no fusion requested | |

### 3.2 `NaturalPolicy`: one place that sets every request

Every `CaptureRequest.Builder` (preview, still, video, burst, high-speed) goes
through `NaturalPolicy.apply(builder, characteristics, userPrefs)` before it is
sent. That way the viewfinder matches the saved file. The policy picks the
lowest value the device lists for each key:

```
CONTROL_MODE                      AUTO              (OFF in full manual)
CONTROL_SCENE_MODE                DISABLED
CONTROL_EFFECT_MODE               OFF
CONTROL_EXTENDED_SCENE_MODE       DISABLED          (API 30+)
CONTROL_AE_MODE                   ON / ON_*_FLASH / OFF  — never ON_LOW_LIGHT_BOOST_*
CONTROL_AUTOFRAMING               OFF               (API 34+)
CONTROL_VIDEO_STABILIZATION_MODE  OFF               (unless D1 opt-in)
LENS_OPTICAL_STABILIZATION_MODE   ON if available
NOISE_REDUCTION_MODE              OFF → MINIMAL → FAST   (first one listed)
EDGE_MODE                         OFF → FAST
HOT_PIXEL_MODE                    OFF → FAST
SHADING_MODE                      FAST  (OFF with "lens corrections" off)
COLOR_CORRECTION_ABERRATION_MODE  FAST  (OFF with "lens corrections" off)
DISTORTION_CORRECTION_MODE        FAST  (OFF with "lens corrections" off)  (API 28+)
COLOR_CORRECTION_MODE             FAST  (TRANSFORM_MATRIX / CCT in manual WB)
TONEMAP_MODE                      PRESET_CURVE + TONEMAP_PRESET_CURVE_SRGB
                                  → CONTRAST_CURVE with an sRGB curve (sampled to TONEMAP_MAX_CURVE_POINTS)
                                  → FAST (device default; reported in the Natural report)
STATISTICS_FACE_DETECT_MODE       OFF
CONTROL_POST_RAW_SENSITIVITY_BOOST 100 when AE is off
JPEG_QUALITY                      user setting (default 95)
```

Important detail: `TEMPLATE_STILL_CAPTURE` and `TEMPLATE_RECORD` default to
`HIGH_QUALITY` noise reduction and sharpening on most devices. The policy
**must override them on every request.** Never send a template's defaults
as they are.

`NaturalPolicy` is pure Kotlin over a `CameraCaps` interface, so it is unit
tested against captured characteristics from real devices (§13).

### 3.3 Compliance monitor

`ComplianceMonitor` reads each `TotalCaptureResult` and compares the result keys
(`NOISE_REDUCTION_MODE`, `EDGE_MODE`, `TONEMAP_MODE`, `CONTROL_SCENE_MODE`,
`CONTROL_VIDEO_STABILIZATION_MODE`, `DISTORTION_CORRECTION_MODE`…) with what
the policy asked for. Mismatches go into a per-camera report and, for still
captures, into a small sidecar entry in the app's database keyed by MediaStore
URI. The review screen can then show "noise reduction: requested OFF, device
used FAST" for that photo.

### 3.4 The honest limit

Camera2 tells us what the HAL *says* it did. Some vendors (Samsung, and some
Xiaomi, Oppo and Vivo builds especially) run extra processing inside the ISP
that no public key controls, or ignore `OFF`. We can't fix that without root.
What we do:

- Settings → About this device → **Natural report**: per lens, the modes it
  accepted, the tone curve in use, and whether RAW is available, with a plain
  verdict ("Fully natural", "Device applies light processing it won't disable",
  "Use RAW for guaranteed unprocessed output").
- RAW+JPEG is one tap away in the settings panel.
- Device results go in the quirks table (§12.4) so later builds can warn or
  work around.

---

## 4. Device coverage ("works on all devices")

- **minSdk 26** (Android 8.0). That's Camera2 on every device with modern
  scoped-storage fallbacks, and covers almost every phone in use. **targetSdk /
  compileSdk = latest stable** (36 at the time of writing, matching Lychee).
- Supports every `INFO_SUPPORTED_HARDWARE_LEVEL`: `LEGACY`, `LIMITED`,
  `EXTERNAL`, `FULL`, `LEVEL_3`. Features turn on by **capability**, never by
  brand or model (except quirks).

| Feature | Needs | Fallback |
| --- | --- | --- |
| Preview, JPEG photo, video | any level | — |
| Manual ISO / shutter | `MANUAL_SENSOR` | exposure compensation only |
| Manual focus | `MANUAL_SENSOR`, `LENS_INFO_MINIMUM_FOCUS_DISTANCE > 0` | tap-to-focus only |
| sRGB tone curve | `MANUAL_POST_PROCESSING` + `PRESET_CURVE` or `CONTRAST_CURVE` | device curve, reported |
| RAW / DNG | `RAW` capability | hidden |
| Lens list from a logical camera | `LOGICAL_MULTI_CAMERA` (API 28+) | each listed camera ID is a lens |
| Smooth zoom across lenses | `CONTROL_ZOOM_RATIO_RANGE` (API 30+) | `SCALER_CROP_REGION` on one lens, discrete jumps between IDs |
| Full sensor resolution (50/108/200 MP) | `ULTRA_HIGH_RESOLUTION_SENSOR` (API 31+) | binned sizes only |
| Check a stream combination before opening | `isSessionConfigurationSupported` (API 29+); `CameraDeviceSetup` without opening (API 35+) | try to configure it, catch failure, cache the result |
| 10-bit HLG / HDR10 video | `DYNAMIC_RANGE_TEN_BIT` (API 33+) + encoder support | 8-bit SDR |
| Display P3 stills | `REQUEST_AVAILABLE_COLOR_SPACE_PROFILES` (API 34+) | sRGB |
| Native shutter/ISO priority | `CONTROL_AE_PRIORITY_MODE` (API 36+) | emulated in the app (§7.6) |
| Kelvin white balance | `COLOR_CORRECTION_MODE_CCT` (API 36+) | gains computed from sensor calibration (§7.6) |
| Slow motion | `CONSTRAINED_HIGH_SPEED_VIDEO` | "high frame rate" = normal session at the highest listed fps |
| HEIC stills | `ImageFormat.HEIC` output (API 29+) | JPEG only |
| Unprocessed microphone | `AudioSource.UNPROCESSED` + `PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED` | `CAMCORDER`, reported |

### 4.1 Capability probe

On first launch, and again whenever `Build.FINGERPRINT` changes, a background
probe builds a **`DeviceCaps` database** (JSON in app storage):

1. Every camera ID from `getCameraIdList()`, plus every physical ID behind each
   logical camera. Optionally (Advanced, off by default) it also checks hidden
   IDs `0..63`, which exposes extra lenses on some Xiaomi/Oppo builds.
2. For each camera: facing, focal lengths, sensor size → **35 mm equivalent
   focal length**, aperture, OIS, hardware level, capabilities, AE/AF/AWB
   modes, ISO/exposure/focus ranges, NR/edge/tonemap/hot-pixel/shading modes,
   output sizes per format (`JPEG`, `YUV_420_888`, `RAW_SENSOR`, `PRIVATE`,
   `HEIC`), max-res sizes, minimum frame durations and stall durations per size,
   AE target fps ranges, high-speed sizes and fps ranges, 10-bit profiles,
   colour spaces, zoom ratio range.
3. Video encoders: for each codec (`video/avc`, `video/hevc`, `video/av01`)
   the hardware encoders, profiles (Main10 for HDR), and
   `VideoCapabilities.areSizeAndRateSupported(w, h, fps)`.
4. The **mode matrix**: for each mode × lens × candidate setting, the stream
   set it needs (preview + JPEG, preview + encoder + JPEG snapshot,
   preview + RAW + JPEG, …) checked with `isSessionConfigurationSupported` /
   `CameraDeviceSetup`. On API 26–28 combinations are judged from the mandatory
   stream tables and confirmed lazily: first real use, then cached.
5. Settings → About this device → **Export capability report** (JSON, shared
   by the user through the share sheet) so device problems can be reproduced.
   These dumps are also the unit test fixtures (§13).

The UI reads only from `DeviceCaps`. **If it isn't in `DeviceCaps`, it isn't
on screen.**

---

## 5. UI: the Pixel Camera look, our own assets

### 5.1 Legal line

Copy the **layout, interaction patterns and motion**, not Google's property:

- No Google names, logos or "Pixel" or "Google Camera" anywhere, including
  the store listing ("a camera with a familiar, clean interface" is fine,
  "Google Camera clone" is not).
- Icons: **Material Symbols** (Apache-2.0), the same open icon family Pixel
  Camera draws from. Font: **Google Sans Flex** (released under OFL 1.1 in
  Nov 2025; check the licence file in the download), with Roboto Flex as a
  fallback. Full design-system spec in §5.2. Shutter and recording sounds recorded or synthesised ourselves,
  or `MediaActionSound` (the system sounds).
- Never decompile or lift resources from the GCam APK.
- (Not legal advice. The aim is "recognisably in the same family", like many
  launchers resemble the Pixel Launcher.)

### 5.2 Material design system: icons and style

The whole app is **Material Design 3 Expressive**, the same system Pixel
Camera 10 is built on. That's what makes it look like the same family without
copying Google's own assets. Rule: **if Material 3 has a component, token or
icon for it, use that; only build custom UI where a camera needs it**
(viewfinder, shutter, zoom ruler, mode carousel), and build those from
Material tokens.

**Icons: Material Symbols**

- Use **Material Symbols Rounded**, the rounded style Pixel Camera uses
  (Apache-2.0, from Google Fonts / `google/material-design-icons`). Don't use
  the old `material-icons-extended` Compose artifact: it's the older
  Material Icons set, frozen, and pulls in thousands of icons.
- Ship only the icons we use, as **vector drawables** generated by a script
  (`tools/icons.py`: names listed in `icons.txt` → downloaded SVGs → Android
  XML in `core/ui/src/main/res/drawable/`), each in two variants: outlined
  (`FILL 0`) and filled (`FILL 1`), weight 400, grade 0, optical size 24.
  Selected or "on" states use the filled variant, as Material does.
- The viewfinder uses grade `-25` variants (slightly thinner) for icons on the
  dark scrim, and 20 dp optical size for status chips.
- Starter list: `photo_camera`, `videocam`, `cameraswitch`, `settings`,
  `flash_on/off/auto`, `timer`, `timer_3`, `timer_10`, `aspect_ratio`,
  `grid_on`, `straighten`, `raw_on`, `hdr_on` (10-bit video only), `slow_motion_video`,
  `timelapse`, `shutter_speed`, `iso`, `exposure`, `wb_auto`,
  `wb_incandescent`, `wb_sunny`, `wb_cloudy`, `wb_shade`, `filter_center_focus`,
  `center_focus_weak`, `lens`, `zoom_in`, `pause`, `stop`, `mic`, `mic_off`,
  `location_on`, `photo_library`, `share`, `delete`, `info`, `bar_chart`
  (histogram), `thermostat`, `sd_card`, `lock`.
- App icon: adaptive icon with a **monochrome layer** so it follows themed
  icons on Android 13+ (Material You). Our own design (a lens/quartz shape
  built from `MaterialShapes`), not a Google-like camera glyph.

**Colour**

- **Dynamic colour (Material You)** on Android 12+:
  `dynamicDarkColorScheme` / `dynamicLightColorScheme` from the wallpaper.
  On Android 8–11, a static scheme generated with the Material Theme Builder
  from a quartz-neutral seed colour.
- The **camera screen is always dark** (black background, white icons on a
  translucent `scrim` surface), whatever the system theme, so colour never
  tints how you judge the picture. Dynamic colour there is limited to small
  accents: the selected zoom pill, active mode label, Pro slider thumbs and
  toggles, again like Pixel Camera 10.1. Recording red is a fixed colour (the
  `error` role from a fixed palette), not dynamic.
- Settings, review and full-settings screens follow the system light/dark
  theme with full dynamic colour.

**Type**

- **Google Sans Flex** (OFL 1.1) bundled as a variable font, mapped onto the
  M3 Expressive type scale (Display → Label), with emphasised styles for the
  mode carousel's selected label and the zoom ratio readout. Roboto Flex is
  the fallback. Tabular numerals for the timer, ISO and shutter readouts.

**Shape**

- M3 shape scale (extra-small → extra-large, full). Zoom pills and the
  photo/video toggle use `full` (pills); inactive chips in the settings panel
  are rounded squares and the selected one morphs into a circle, matching
  Pixel Camera 10.1.
- `MaterialShapes` + shape morphing for expressive moments: the shutter morphs
  circle → rounded square while recording, the selected Pro control morphs on
  press, the loading indicator while a session swaps.

**Motion**

- `MotionScheme.expressive()` springs for spatial movement (panel slide,
  carousel, pill selection) and the standard scheme's effects springs for
  fades and colour. No hand-tuned durations except the shutter flash.
- Respect the system "remove animations" setting.

**Components** (Compose Material 3, latest stable; a few Expressive APIs may
still need `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`):

| Where | Material 3 component |
| --- | --- |
| Photo/video toggle | Connected `ToggleButton`s in a `ButtonGroup` |
| Zoom pills, settings-panel chips | `ToggleButton` / `FilterChip` with icon morph |
| Settings panel | `ModalBottomSheet`-style panel (custom height, no drag handle in the viewfinder), `PrimaryTabRow` for General / Pro |
| Pro sliders | `Slider` with inset icon and value indicator; `VerticalSlider` in landscape |
| Full settings screen | `LargeFlexibleTopAppBar`, `ListItem`s grouped in segmented rounded cards, `Switch` with check / close thumb icons (the Pixel Camera 10 toggle style) |
| Video grid (resolution × fps) | `FilterChip` grid in a sheet, disabled chips with supporting text |
| Errors and notices | `Snackbar` (and small chips over the viewfinder for lens-adaptation notices) |
| Session swaps, saving | `LoadingIndicator` (Expressive) |
| Review screen | `HorizontalPager`, `FloatingToolbar` (share, delete, info) |
| Dialogs, permissions | `AlertDialog`, `BasicAlertDialog` |
| Large screens | Window size classes + Material adaptive layouts (`ListDetailPaneScaffold` in settings) |

**Platform style**

- Edge-to-edge everywhere, with the viewfinder going under the status and
  navigation bars; controls inset by `WindowInsets.safeDrawing` and display
  cutouts.
- Predictive back (settings, review, sheets).
- Haptics through `HapticFeedbackConstants` (`CONFIRM`, `CLOCK_TICK`,
  `SEGMENT_TICK` for zoom detents) where the device supports them.
- A small `core/ui` module holds the theme (`QuartzTheme` wrapping
  `MaterialExpressiveTheme`), icon resources, shapes and shared camera
  components, so every screen uses the same tokens.

### 5.3 Viewfinder layout (portrait, phone)

```
┌─────────────────────────────────────┐
│  ⚡A   ⏱    4:3      12MP · JPEG     │  status row: only what's non-default
│                                     │  (tap a chip = jump to that setting)
│                                     │
│                                     │
│            VIEWFINDER               │  tap: focus + exposure point
│        (aspect-ratio framed)        │  pinch: zoom (ruler appears)
│               ◯ ─☀─                  │  tap shows brightness + WB sliders
│                                     │  double-tap: switch to next lens
│                                     │
│        ( .5 )  (1×)  ( 2 )  ( 5 )   │  zoom pills, built from the lenses
│                                     │
│   Long exposure    PHOTO    Interval │  mode carousel (swipe anywhere)
│                                     │
│    [▣ thumb]     (  ◉  )    [⟲ flip] │  last capture · shutter · front/back
│                                     │
│    [⚙]         [ 📷 | 🎥 ]     [ ⓘ ] │  settings · photo/video toggle · lens info
└─────────────────────────────────────┘
```

- **Photo/Video toggle** at the bottom (Pixel Camera 9+ layout). Each side has
  its own carousel:
  - Photo: `Long exposure · Photo · Interval`
  - Video: `Slow motion · Video · Time-lapse`
- **Zoom pills** come from real lenses (§8): one pill per lens at its 35 mm
  ratio relative to the main lens, plus user-added stops. Tapping the active
  pill cycles through that lens's crop stops (e.g. 1× → 1.5× → 2× on main). Pinching shows the **zoom ruler** with lens markers and live ratio.
- **Tap to focus** puts a focus ring there with two sliders, as Pixel Camera
  does: brightness (exposure compensation) and white balance (temperature
  shift). There is no "shadows" slider, because that is local tone mapping.
  Long-press locks AE/AF ("AE/AF lock" chip).
- **Settings panel** (gear, bottom-left) slides up over the lower viewfinder,
  like Pixel Camera, with two tabs:
  - **General**: Flash · Timer (off/3/10 s) · Ratio (4:3 / 16:9 / 1:1 / full)
    · Resolution (chips from `DeviceCaps`, e.g. `12 MP · 50 MP`) · Format
    (`JPEG · RAW+JPEG · RAW · HEIC`) · Grid · Level · More settings →.
  - **Pro**: lens lock · ISO · shutter speed · focus · white balance
    (presets + K) · exposure compensation · metering area · histogram /
    peaking / zebra toggles. A control the camera lacks shows as a disabled
    chip with a short reason ("This lens has fixed focus").
  - In Video, General becomes: resolution · fps · codec · stabilisation (D1) ·
    HDR (10-bit) · audio · bitrate. The **`4K · 60` chip** in the status row
    opens the resolution × fps grid directly (§7.3).
- **Full settings** (separate screen, Material 3 lists): everything in §7, plus
  storage, file naming, shutter sound, volume-key action, long-press shutter
  action, location, Natural report, capability report export, about.
- **Orientation**: the activity stays portrait-locked, as Pixel Camera does.
  Icons, chips and the thumbnail rotate with an `OrientationEventListener`,
  and captures get the right orientation. Tablets and foldables get a
  landscape-native layout (controls on the right) through window size classes.
- **Motion**: shutter press shrinks and flashes the preview edge; the capture
  flies into the thumbnail; the mode carousel slides with spring physics; the
  photo/video morph turns the shutter white→red; the lens switch cross-fades
  over the last preview frame (blurred **in the UI only**, never in files)
  while the new session starts. Haptics on shutter, zoom detents and mode
  changes.
- **Recording UI**: timer chip, pause, video snapshot button, live audio level
  meter, remaining storage time, thermal warning chip.
- **Review**: the thumbnail opens a lightweight in-app viewer for this
  session's captures (swipe, share, delete, info sheet with EXIF and the
  Natural report for that file), with "Open in Gallery". No editing.
- **Accessibility**: TalkBack labels on every control, 48 dp targets, a spoken
  zoom ratio, large-font safe layouts.

---

## 6. Modes

| Mode | What it does | Engine |
| --- | --- | --- |
| **Photo** | Single still. Auto by default, any Pro control can be set manually. | Preview + still stream (JPEG / YUV / RAW / HEIC). `TEMPLATE_STILL_CAPTURE` + policy, with the AF/AE precapture sequence when needed. |
| **Long exposure** | One frame with a long shutter, from 1/2 s up to the sensor's `SENSOR_INFO_EXPOSURE_TIME_RANGE` max (often 1 s on budget phones, 30 s+ on others). Tripod hint, countdown, ISO chosen by the user or "lowest". **One frame, never stacked**, which is what makes it different from Night Sight. | Manual AE (`MANUAL_SENSOR` required, otherwise the mode is hidden). |
| **Interval** | Intervalometer: N stills every T seconds, saved as normal photos (JPEG/DNG). | Photo pipeline on a timer, screen dimmed, wake lock. |
| **Video** | Normal video, every resolution/fps/codec the phone supports. | Preview + `MediaCodec` encoder surface (+ optional snapshot stream). §10. |
| **Slow motion** | 120/240/480/960 fps (what the device lists). Saved at real capture rate with a playback-rate flag, or "conformed" to 30 fps by retiming. No frame interpolation. | Constrained high-speed session. |
| **Time-lapse** | Real frames recorded at a chosen interval and played back at 24/30 fps. No frame blending. | Normal session; a GL pass-through copy renders every Nth frame into the encoder with retimed timestamps. |

Burst: long-pressing the shutter in Photo does Burst (default), Quick video
or nothing, set in settings. A burst is a repeating still request at the
fastest rate that size's stall duration allows; frames are saved one by one,
never merged.

The **"never enhance" rule holds in every mode**: same policy, same
compliance monitor.

---

## 7. Customisation model

### 7.1 Settings are per mode × lens

```
Settings
 ├─ global        (shutter sound, storage, naming, location, grid, …)
 └─ per mode
     └─ per lens (logical "auto" + each physical lens)
          photo:  size, aspect, format, JPEG quality, colour space
          video:  size, fps, codec, profile (SDR/HLG/HDR10), bitrate, bitrate mode,
                  I-frame interval, stabilisation, audio (source, rate, bitrate, channels)
          slowmo: size, fps, output (real rate / conformed 30)
          lapse:  size, capture interval, playback fps
```

Stored with **Proto DataStore** (typed, migratable). Defaults per lens are
computed from `DeviceCaps`: highest binned 4:3 still size, 1080p30 or
4K30 video if supported, HEVC if a hardware encoder exists, else H.264.

### 7.2 Resolution choices

- **Photo**: every `JPEG` (or `HEIC`/`RAW_SENSOR`) output size for that lens,
  grouped by aspect ratio and labelled with megapixels. Ultra-high-res sensors
  get a separate "Full sensor (remosaic)" group from the max-resolution
  configuration map, marked "slower, no burst".
- **Aspect ratio** picks only native output sizes of that aspect. No
  software cropping unless the user picks "crop from full sensor" (a lossless
  geometric crop, never a resample).
- **Custom sizes** are limited to what the device lists. Inventing sizes would
  mean resampling, and resampling is processing.

### 7.3 Frame-rate choices

- The video grid shows **resolution × fps** cells, built from:
  camera output sizes ∩ encoder `areSizeAndRateSupported` ∩
  `minFrameDuration(size) ≤ 1/fps` ∩ a fixed `[fps, fps]` range present in
  `CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES` ∩ the session combo check (preview +
  encoder (+ snapshot)). Typical cells: 24, 25, 30, 48, 50, 60, and 120 where a
  normal session allows it.
- Each cell is ✅ available, or hidden. With "Show unsupported" on, unsupported
  cells appear greyed with the reason ("encoder can't do 8K60", "this lens tops
  out at 30 fps").
- Fixed fps by default (`[fps, fps]`). Option: "Allow lower frame rate in low
  light" uses `[min, fps]`. Off by default, because a dropping frame rate
  quietly changes the footage.
- Slow-motion fps come only from `getHighSpeedVideoFpsRangesFor(size)`.
- Preview fps for Photo: 30, or 60 where supported (setting).

### 7.4 When a lens can't do the current setting

Example: 4K60 is set and the user taps the 5× pill, but the telephoto only does
4K30. A setting decides what happens:

- **Switch lens, adapt settings** (default): change to the nearest supported
  setting for that lens and flash a chip saying "Telephoto: 4K 30".
- **Keep settings, crop on current lens**: stay on the current lens and crop
  digitally.
- **Ask**.

The same rule applies to photo sizes and formats (e.g. no RAW on the
ultrawide).

### 7.5 Zoom customisation

See §8. Users can edit the zoom pills (add/remove stops such as 0.6×, 1.5×,
3×, 10×), set a maximum digital zoom, choose auto lens switching or lens lock,
and pick the crop output (§8.4).

### 7.6 Pro controls

- **ISO**: `SENSOR_SENSITIVITY` within `SENSOR_INFO_SENSITIVITY_RANGE`, with
  `CONTROL_POST_RAW_SENSITIVITY_BOOST` = 100 so the number is honest.
- **Shutter**: `SENSOR_EXPOSURE_TIME` within range; displayed as 1/8000 … 30″.
- **Priority modes**: shutter priority or ISO priority. On API 36+ use
  `CONTROL_AE_PRIORITY_MODE`. Below that, run AE on, read the metered exposure
  from results (EV = log2(t·ISO)), then switch to AE off with the fixed value
  plus a computed partner, updated every few frames.
- **Focus**: `LENS_FOCUS_DISTANCE` (diopters) with distance labels when
  `LENS_INFO_FOCUS_DISTANCE_CALIBRATION` is `CALIBRATED`, otherwise a 0–100
  slider. Focus peaking draws on the preview only.
- **White balance**: AWB presets (`AUTO, INCANDESCENT, FLUORESCENT, DAYLIGHT,
  CLOUDY, SHADE`), plus Kelvin 2000–10000 K and tint. API 36+ uses
  `COLOR_CORRECTION_MODE_CCT`. Below that, compute `COLOR_CORRECTION_GAINS`
  and the transform from the sensor's two reference illuminants
  (`SENSOR_REFERENCE_ILLUMINANT1/2`, `SENSOR_COLOR_TRANSFORM1/2`,
  `SENSOR_FORWARD_MATRIX1/2`), interpolating between them like DNG does.
- **Metering**: `CONTROL_AE_REGIONS` / `CONTROL_AF_REGIONS` from tap points
  (centre-weighted default, spot on tap). Face metering is off by default.
- Viewfinder aids (preview-only, GPU overlay on a separate low-res `YUV`
  analysis stream): histogram (luma/RGB), focus peaking, zebras, level, grid.

---

## 8. Zoom and lenses

### 8.1 Finding lenses

1. Take each back-facing logical camera; if it is `LOGICAL_MULTI_CAMERA`,
   list its physical IDs.
2. For each lens compute the 35 mm equivalent:
   `eq = focal_length × 43.27 / diagonal(SENSOR_INFO_PHYSICAL_SIZE)`
   (use the active array crop, not the full pixel array).
3. Main lens = the default camera's widest-FOV physical lens nearest 23–28 mm
   eq. Ratios = `eq / eq_main`, rounded for labels (0.5×, 1×, 2×, 5×).
4. Front cameras get the same treatment. If there are two front lenses, they
   get a wide/normal toggle.

### 8.2 Driving zoom

- **API 30+, logical camera**: `CONTROL_ZOOM_RATIO` across
  `CONTROL_ZOOM_RATIO_RANGE`. The HAL switches lenses as zoom passes each
  lens's ratio, which is how GCam behaves. We read
  `LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID` from results to show which lens is
  live (ⓘ button and EXIF).
- **Lens lock / API < 30 / OEMs that hide physical lenses**: open the
  physical lens directly (separate ID) or stream it through
  `OutputConfiguration.setPhysicalCameraId`, and zoom with
  `SCALER_CROP_REGION`. Changing lens = session swap with the cross-fade
  from §5.3.
- On API 35+, `getSessionCharacteristics` gives the real zoom range for a
  given session (e.g. 4K60 may limit zoom); the ruler uses it.

### 8.3 Honest zoom labels

The ruler shows real optical points. Beyond the longest lens the label turns
amber ("digital crop"), and at the user's maximum zoom it stops. No
super-resolution, ever.

### 8.4 Crop zoom output (stills)

- **Scale to selected size** (default): the ISP crops and scales to the chosen
  output size. That is what every camera does.
- **Native crop, no upscaling**: capture `YUV_420_888` (or JPEG) at the lens's
  full size, crop to the zoom region in software, encode the crop at its true
  pixel count (e.g. 3× on a 12 MP main = 1.3 MP file). For YUV,
  `YuvImage.compressToJpeg(rect, …)` crops without resampling. For RAW, write
  the full frame; `DngCreator` sets the crop tags itself, so a metadata-only
  crop means patching `DefaultCropOrigin/Size` in the written file
  afterwards (later milestone; v1 saves the full-frame DNG).

Video zoom always uses the ISP crop.

---

## 9. Photo pipeline

```
Shutter ──► [AF trigger if needed] ──► [AE precapture if flash/auto-flash] ──►
  STILL_CAPTURE request (NaturalPolicy, JPEG_ORIENTATION, JPEG_QUALITY, GPS opt.)
      │
      ├─► ImageReader(JPEG/HEIC) ─► write bytes as is ─► EXIF patch ─► MediaStore
      ├─► ImageReader(RAW_SENSOR) ─► DngCreator(characteristics, result) ─► MediaStore
      └─► ImageReader(YUV) (native-crop zoom only) ─► crop ─► JPEG ─► MediaStore
```

- **No ZSL template.** `TEMPLATE_ZERO_SHUTTER_LAG` and reprocessing can apply
  `ZERO_SHUTTER_LAG` noise reduction. Shutter lag is kept low by
  locking AF/AE on press, skipping precapture when flash is off, and keeping
  the session warm.
- JPEG bytes from the HAL are written **byte for byte**, never decoded and
  re-encoded. EXIF is changed with `ExifInterface` (an in-place attribute
  rewrite): make/model, lens (physical ID → "Main 24 mm f/1.7"), real ISO,
  exposure, focal length, 35 mm eq, software "Quartz x.y", optional GPS.
- RAW+JPEG share one request, so they come from the same frame.
- Saves go into a `Pictures/Quartz` / `DCIM/Quartz` collection in MediaStore
  with `IS_PENDING` while writing; naming is `PXL`-style
  `QTZ_yyyyMMdd_HHmmssSSS[.RAW].jpg/.dng` (configurable).
- API 26–28: `WRITE_EXTERNAL_STORAGE` with `maxSdkVersion=28`.

---

## 10. Video pipeline

### 10.1 Normal video

```
Camera2 session:  [Preview SurfaceView]  [MediaCodec input Surface]  [JPEG snapshot reader (optional)]
                                              │
                                     MediaCodec video encoder (H.264 / HEVC / AV1)
                                              │
AudioRecord (UNPROCESSED → CAMCORDER) ─► MediaCodec AAC ─► MediaMuxer (MP4)
```

- **`MediaCodec` + `MediaMuxer`, not `MediaRecorder`.** That gives exact control
  of codec, profile/level, bitrate, bitrate mode (VBR/CBR/CQ where supported),
  I-frame interval, 10-bit, and pause/resume, and avoids `CamcorderProfile`
  limits.
- Request: `TEMPLATE_RECORD` + policy + `CONTROL_AE_TARGET_FPS_RANGE=[fps,fps]`.
- **Timestamps**: frames use `SENSOR_TIMESTAMP`. If
  `SENSOR_INFO_TIMESTAMP_SOURCE` is `REALTIME`, audio and video share a
  clock; if it is `UNKNOWN`, keep a measured offset. Pause shifts the
  presentation-time base.
- **10-bit**: `OutputConfiguration.setDynamicRangeProfile(HLG10 / HDR10)` on
  the encoder surface, HEVC Main10 / AV1 Main10 with the right colour
  standard, transfer and range keys. Labelled as a format choice
  ("HDR · HLG 10-bit"), never on by default.
- **Bitrate presets** per resolution × fps (e.g. 4K60 HEVC: Standard 60,
  High 100, Max = encoder max), plus a custom Mbps field.
- **Audio**: 48 kHz AAC-LC, 128–320 kbps, mono/stereo; source `UNPROCESSED`
  when the device supports it (no AGC, noise suppression or wind filter, which
  is the audio version of the contract). Microphone choice via
  `AudioRecord.setPreferredDevice` (built-in, wired, USB, Bluetooth SCO).
- **Video snapshot**: `TEMPLATE_VIDEO_SNAPSHOT` into the JPEG reader, if the
  combination check passed for that setting.
- **Thermal**: `PowerManager.addThermalStatusListener`. At SEVERE, warn. At
  CRITICAL, stop and finalise the file cleanly. Never lower quality silently.
- **Storage**: estimate remaining minutes from the bitrate; split files at the
  4 GB limit when writing to FAT-formatted SD cards. Write to a pending MediaStore entry with
  `FileDescriptor` → muxer.

### 10.2 Slow motion

`createConstrainedHighSpeedCaptureSession` with exactly preview + encoder
surfaces at a size from `getHighSpeedVideoSizes()`.
`createHighSpeedRequestList` with `[fps, fps]` for recording (preview at
`[30, fps]` before recording, as the API docs recommend). Encoder at capture
fps; the muxer writes either real timing plus a capture-fps tag (players show
it as high-fps) or "conformed" timing (frames spaced at 1/30 s → true slow
motion on any player). Audio is off in conformed mode.

### 10.3 Time-lapse

A normal session at a low fixed fps (e.g. `[15,15]` or the lowest fixed range)
→ `SurfaceTexture` → a GL **pass-through** shader (copy, no colour math)
renders one frame per interval into the encoder's input surface with
timestamps `n × (1/playbackFps)`. No audio. Optional "photo time-lapse" is
the Interval mode.

---

## 11. Output and metadata

| Kind | Formats |
| --- | --- |
| Stills | JPEG (quality 50–100, default 95), HEIC (if the device outputs it natively), DNG, JPEG+DNG |
| Colour | sRGB default; Display P3 option on API 34+ where listed |
| Video | MP4: H.264 High, HEVC Main/Main10, AV1 Main/Main10 (where hardware encoders exist) |
| Audio | AAC-LC 48 kHz |
| Metadata | Full EXIF (no fake values); XMP field `quartz:natural=true/false` + summary of requested/actual modes; video `location` atom only if location is on |

---

## 12. Architecture

### 12.1 Modules

```
quartz/
├─ app/                    Compose UI, navigation, activities, intents, DI wiring
├─ core/ui/                Material 3 Expressive theme, Material Symbols drawables, shared components
├─ core/camera/            Camera2 engine
│    CameraRepository      open/close, device state, error and eviction handling
│    SessionController     builds and swaps sessions (normal, high-speed), combo checks
│    NaturalPolicy         the request rules (§3.2) — pure Kotlin, 100% unit tested
│    ComplianceMonitor     result vs request (§3.3)
│    ZoomController        lens map, ratio ↔ lens, crop regions (§8)
│    ExposureController    AE/AF/AWB, precapture, manual + priority modes, WB math
│    StillCapture          JPEG/HEIC/RAW/YUV readers, DngCreator, native crop
│    VideoRecorder         MediaCodec/MediaMuxer/AudioRecord, pause, snapshot, thermal
│    HighSpeedRecorder, TimelapseRecorder
├─ core/caps/              capability probe, DeviceCaps model + JSON, quirks table
├─ core/media/             MediaStore writer, EXIF/XMP, naming, sidecar DB (Room)
├─ core/settings/          Proto DataStore schema + defaults derived from DeviceCaps
└─ tools/                  natural_check.py (§13.3), caps fixtures importer
```

### 12.2 Threading and state

- One `CameraDispatcher` (single-threaded `HandlerThread` + `asExecutor`) owns
  all Camera2 calls. Commands (switch mode, switch lens, change setting,
  capture, record) go through a **serial actor**, so a fast double-tap can't
  race a session change.
- `CameraState`: a `StateFlow` sealed hierarchy `Closed → Opening → Ready(session,
  mode, lens, settings) → Capturing / Recording → Error(reason, recoverable)`.
  The UI is a pure function of `CameraState` + settings (unidirectional data
  flow; one `ViewModel` per screen).
- Image readers drain on their own threads; disk writes on `Dispatchers.IO`
  with a bounded queue (bursts back off instead of running out of memory).
- Lifecycle: close the camera in `onStop`; reopen in `onStart` with the last
  state. Handle `onDisconnected` (another app took the camera), multi-window,
  folding/unfolding, and system camera privacy toggles (`onError` with
  `ERROR_CAMERA_DISABLED`).

### 12.3 System integration

- Intents: `ACTION_IMAGE_CAPTURE`, `ACTION_VIDEO_CAPTURE` (returns to caller,
  honours `EXTRA_OUTPUT`), `STILL_IMAGE_CAMERA`, `VIDEO_CAMERA`,
  `STILL_IMAGE_CAMERA_SECURE` (lock screen: `showWhenLocked`, review limited
  to that session's captures), so it can be the **default camera** and work
  with double-press power.
- App shortcuts (selfie, video), a Quick Settings tile, volume keys as shutter
  or zoom (setting).
- Permissions: `CAMERA`, `RECORD_AUDIO` (asked on first video), location
  (asked only when turned on). **No `INTERNET`.**

### 12.4 Quirks

`core/caps/Quirks.kt`: a small, documented table keyed by
manufacturer/model/SoC/API, each entry with a reason and the device report
that found it. Examples of the kind we expect: "advertises 4K60 combo but
fails configure → drop", "NR OFF ignored → mark in Natural report", "ZOOM_RATIO
switch flickers → add 1-frame hold", "front camera JPEG orientation wrong →
rotate EXIF flag". Quirks change capability, never add processing.

---

## 13. Testing and verification

### 13.1 Unit (JVM, CI)

- `NaturalPolicy` against **real device fixtures** (`DeviceCaps` JSON dumps
  from §4.1, starting with every phone available to us and growing with user
  reports). It asserts no enhancement key is ever above its minimum listed
  value, that the tonemap fallback chain is right, and that no low-light-boost
  or extension path exists.
- Settings matrix: the video grid, lens-switch adaptation (§7.4), defaults.
- Zoom maths: 35 mm eq, pill ratios, crop regions, active-array conversions.
- WB maths: CCT ↔ gains against DNG SDK reference values.
- A **guard test** that scans the code for banned API use
  (`CameraExtensionCharacteristics`, `CameraExtensionSession`,
  `JPEG_R`, `*_HIGH_QUALITY` noise/edge constants, `ON_LOW_LIGHT_BOOST`) and
  fails the build if it finds any.

### 13.2 Instrumented (real devices)

- Open each lens, preview, take JPEG/RAW, record 5 s at each available grid
  cell, slow motion, time-lapse. Assert files are valid (`MediaExtractor`:
  size, fps, codec, duration) and that `ComplianceMonitor` has no unexpected
  mismatches.
- Device lab: our phones, plus Samsung Remote Test Lab (real devices with
  cameras). Target matrix for v1: Pixel (Tensor), Samsung Galaxy S and A
  series (Exynos and Snapdragon), Xiaomi/Redmi (MediaTek and Snapdragon),
  OnePlus/Oppo, Motorola, Sony Xperia (great Camera2 support), one low-end
  Unisoc phone, one `LEGACY`-level phone, one tablet, one foldable.

### 13.3 "Is it natural?" check

`tools/natural_check.py` (Python, `rawpy` + `numpy` + `opencv`), run on
RAW+JPEG pairs of a test scene (slanted-edge target, grey card, colour
chart, a dark corner):

- **Sharpening**: slanted-edge profile on the JPEG. Overshoot or halo above
  a small threshold means sharpening was applied.
- **Noise reduction**: noise σ on flat grey patches, JPEG vs a neutral DNG
  render at the same exposure. Much lower JPEG noise means NR.
- **Tone**: grey-ramp response vs the sRGB curve (contrast boost and local
  tone mapping show up here).
- **Colour**: chart patch saturation vs the DNG neutral render.
- Run on stock camera output too, as a "processed" baseline.

The result per device goes into its Natural report entry and quirk notes.

### 13.4 Performance budgets

- Cold start to first preview frame: ≤ 800 ms mid-range, ≤ 1.5 s low-end.
- Shutter press to saved 12 MP JPEG: ≤ 600 ms (no flash, AF locked).
- Lens switch with session swap: ≤ 400 ms visible gap (cross-fade covers it).
- Zero dropped encoder frames at 4K30 on capable devices for 10 minutes.
- Measured with Macrobenchmark plus in-app timing logs.

---

## 14. Build, CI, release

- Kotlin 2.3, AGP latest stable, Java 21 toolchain, Compose BOM latest,
  Compose Material 3 (Expressive) + `material3-adaptive`, Material Symbols as
  generated vector drawables, `camera-viewfinder-compose`, DataStore, Room,
  kotlinx.serialization, Coroutines. No Play Services, no Firebase.
- `.github/workflows/build-quartz.yml`, same shape as `build-lychee.yml`:
  on push to `main` under `quartz/**`, run unit tests, lint (including the
  banned-API guard), `assembleDebug` and `assembleRelease`, and upload APKs.
  Release signing comes from `QUARTZ_KEYSTORE_*` secrets.
- App ID `app.quartz.camera`, licence **Apache-2.0** (so Google samples,
  Aperture and Jetpack Camera App code can be borrowed with attribution;
  no GPL code).
- Distribution: signed APKs on GitHub Releases. F-Droid metadata later (the
  app has no proprietary dependencies, so it qualifies).

---

## 15. Milestones

Each one ends with a build on CI and a short check on at least two real
phones (one Pixel, one non-Pixel).

| # | Milestone | Done when |
| --- | --- | --- |
| **M0** | **Probe spike** — standalone screen that runs the capability probe and exports `DeviceCaps` JSON; `NaturalPolicy` v1 with a test preview showing requested vs actual modes. | JSON dumps from ≥ 5 different phones committed as fixtures; policy unit tests green on all of them. |
| **M1** | **Engine core** — open/close, preview in `CameraViewfinder`, JPEG photo through the policy, MediaStore save, EXIF, orientation, lifecycle and errors. | Photo works on every fixture phone; JPEG written byte for byte; compliance monitor live. |
| **M2** | **Pixel-style shell** — `core/ui` theme (dynamic colour, Google Sans Flex, shapes, motion, Material Symbols drawables), viewfinder layout, photo/video toggle, mode carousel, shutter and capture animation, thumbnail + review, tap focus with brightness/WB sliders, settings panel (General), full settings screen, rotation of controls. | Side-by-side with Pixel Camera, feels like the same family. TalkBack pass. |
| **M3** | **Photo complete** — every resolution and aspect, full-sensor mode, RAW / RAW+JPEG / HEIC, flash, timer, grid, level, burst, Pro tab (ISO, shutter, focus, WB incl. Kelvin, priority modes, metering), histogram/peaking/zebra overlays, Long exposure and Interval modes. | Natural check (§13.3) passes on Pixel and Sony; mismatches on others recorded in the Natural report. |
| **M4** | **Video** — MediaCodec/Muxer recorder, resolution × fps grid, codecs, bitrate presets + custom, 10-bit HLG/HDR10, unprocessed audio + mic choice, pause/resume, video snapshot, thermal handling, storage estimate. | 10 min 4K30 with no dropped frames and A/V in sync (clap test) on a capable phone; every grid cell records a valid file on fixture phones. |
| **M5** | **Zoom and lenses** — lens discovery and 35 mm labels, zoom pills and ruler, `ZOOM_RATIO` and lens-lock paths, per-lens settings with adaptation (§7.4), custom zoom stops, native-crop output, front lenses. | Correct pills on Pixel, Samsung, Xiaomi; lens lock works where the OEM exposes lenses; honest fallback where it doesn't. |
| **M6** | **Slow motion + time-lapse** — high-speed session, real-rate and conformed output, GL pass-through time-lapse. | 240 fps file plays correctly in Google Photos and VLC on a supporting phone. |
| **M7** | **System integration** — capture intents, secure lock-screen camera, default camera / double-press power, shortcuts, QS tile, volume keys, location. | Works as the system default camera from the lock screen. |
| **M8** | **Hardening + release** — device lab sweep, quirks table, Natural report UI, capability report export, performance budgets, crash-free sweep, docs (`README.md`, `HANDOFF.md`), signed release. | v1.0 APK on GitHub Releases. |

Rough size: M0–M2 are the riskiest and most UI-heavy; M3 and M4 are the
biggest by code. Work in this order; M5 can start alongside M3 once M1 is in.

---

## 16. Risks

| Risk | Mitigation |
| --- | --- |
| OEM HALs ignore `OFF` or process anyway (Samsung, some Xiaomi/Oppo/Vivo) | Compliance monitor + Natural report, RAW path, quirk notes. Be upfront that "natural" depends on what the phone allows. |
| OEMs hide ultrawide/telephoto from third-party apps | Logical-camera `ZOOM_RATIO` path (some OEMs switch lenses for third-party apps this way even when they hide the physical IDs), optional hidden-ID probe, honest "main lens only on this phone" message. |
| Advertised stream combinations that fail | Verify with `isSessionConfigurationSupported` / `CameraDeviceSetup`, otherwise try once and cache; quirks table. |
| `NR OFF` makes low-light shots very noisy | That is the honest result. Explain it in onboarding and the Long exposure tip ("use a tripod and low ISO"). |
| Similarity to Pixel Camera trade dress | Own name, own assets, open-licensed icons and font, layout inspiration only. |
| Camera2 complexity and fragmentation slow everything down | M0 probe first; fixtures from real phones drive tests; Open Camera's quirk history as a reading list. |
| Thermal limits in 4K60/8K | Thermal listener, clean stop, clear warning. Never lower quality silently. |
| 10-bit preview on non-HDR screens looks flat | Preview uses the SDR-mapped stream where needed. Label HDR as a format, off by default. |

---

## 17. Decisions to confirm with the user

Defaults in **bold** are what this plan assumes.

- **D1 Video stabilisation (EIS).** It warps and crops frames, which isn't
  "enhancing" the picture but is processing. **Offered as an opt-in toggle,
  off by default, labelled "crops and warps frames"**, or never offered.
  (OIS, the physical kind, is always on.)
- **D2 Lens corrections** (shading, chromatic aberration, distortion).
  **On, with an Advanced toggle to turn them off**, or always on, or off by
  default.
- **D3 Hot-pixel correction.** **Off where the device allows**, or treat it as
  a hardware correction and leave it on (`FAST`).
- **D4 Name**: **Quartz** (working), app ID `app.quartz.camera`.
- **D5 minSdk 26**, **Apache-2.0**, **Kotlin + Compose + Camera2**, **no
  Panorama / Photo Sphere**.
