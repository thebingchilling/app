# Quartz — handoff

Read this, then `PLAN.md` (the design, with *(built: …)* notes where the code
differs). Quartz lives in `quartz/` in a repo that holds other, unrelated
projects; CI for it is `.github/workflows/build-quartz.yml` and only runs on
changes under `quartz/`.

## User decisions

- Pixel Camera's UI, the phone's own camera engine (Camera2), works on every
  phone, full control of resolution/frame rate/lens in every mode, **never
  any enhancement**. Material icons and Material 3 Expressive style.
- Builds happen on GitHub Actions, not locally ("we push to github actions
  to build").
- Plan defaults taken (§17), not yet confirmed by the user: D1 electronic
  stabilisation opt-in (off); D2 lens corrections on with a switch; D3 hot
  pixel correction off; name "Quartz"; minSdk 26; Apache-2.0; no panorama.

## Stack

AGP 9.4.1 (built-in Kotlin), Kotlin 2.3.20, Gradle 9.8.1, compileSdk/
targetSdk 37, minSdk 26, Compose BOM 2026.09.00 with material3
1.5.0-beta01 (needed for the Expressive components; requires compileSdk 37
and AGP ≥ 9.1). No Play Services, no internet permission.

## How it fits together

1. `CapabilityProbe` reads every camera's characteristics (and physical
   lenses behind logical cameras) plus every video encoder's size × rate
   support into `DeviceCaps`, cached per `Build.FINGERPRINT`.
2. `LensMap` turns that into lenses with 35 mm equivalents and zoom ratios;
   `ZoomMath` maps a zoom ratio to a camera + CONTROL_ZOOM_RATIO or a crop.
3. `SpecBuilder` (app) combines settings (per mode × lens key), the lens set
   and the committed zoom into a `SessionSpec`, using `Resolver` to adapt
   saved choices to what that camera can do.
4. `CameraViewModel` configures `CameraEngine` when the spec or the preview
   surface changes (the SurfaceView's buffer is set to `spec.previewSize`
   first), and pushes `Controls` (zoom, manual exposure, focus, WB, metering).
5. `CameraEngine` builds every request through `RequestApplier.natural`
   (from `NaturalPolicy`), captures stills (JPEG/HEIC bytes written as-is,
   DNG via DngCreator, YUV for native-crop zoom), records with MediaCodec +
   MediaMuxer (persistent input surface, so the session doesn't change when
   recording starts), and runs slow motion in a constrained high-speed
   session and time-lapse through a GL pass-through copy.
6. `ComplianceMonitor` reads processing modes back from results; the Natural
   report combines that with what each camera lists.

## Verified (CI, 2026-10-14)

- All modules compile; app debug/release APKs build (check the latest run).
- Unit tests: NaturalPolicy against fixtures (lowest enhancement value, never
  an unlisted value, sRGB curve, lens-correction preference, stabilisation
  opt-in, no low-light boost), lens discovery and zoom (logical camera with
  ZOOM_RATIO, separate camera IDs, legacy), metering/rotation maths, video
  grid, slow-motion cells, nearest-cell adaptation, bitrates, JSON round
  trip, settings Resolver, and a guard that fails the build if source uses
  enhancement APIs (Camera Extensions, JPEG_R, HIGH_QUALITY NR/edge, ZSL,
  low-light boost, HDR/night scene modes, bokeh).
- Fixtures in `core/caps/src/test/.../Fixtures.kt` are **synthetic**.

## Not verified — needs a real phone

Nothing has run on a device yet. In rough order of risk:

1. Session setup on real HALs: persistent encoder surface priming
   (`Recorder` + `MediaCodec.setInputSurface` before the session is built),
   10-bit output configurations, physical-lens streams, full-sensor mode.
2. Recording: A/V sync (sensor timestamp source REALTIME vs monotonic audio),
   pause/resume timeline, slow-motion conform timing, time-lapse EGL path.
3. Stills: AF lock/precapture state waits, RAW+JPEG pairing, native-crop
   YUV→JPEG orientation, HEIC.
4. Gestures and layout on different screen sizes; tablets/foldables
   (landscape layout from the plan is not built yet).
5. Real `DeviceCaps` dumps: Settings → Advanced → Export capability report.
   Add them as test fixtures (plan §13.1) and fix whatever they show.
6. `tools/natural_check.py` on RAW+JPEG pairs from real phones (§13.3).

## Not built yet (from the plan)

- Landscape-native layout for tablets/foldables (§5.3).
- Combination checking with `isSessionConfigurationSupported` before
  showing grid cells (§4.1 step 4). Today the grid comes from the static
  tables, and the engine falls back by dropping optional streams
  (analysis, snapshot) if a session fails.
- "Ask" lens-switch policy; absolute Kelvin below API 36; 4 GB file split
  on FAT cards; thermal listener (§10.1); quirks table (§12.4);
  performance benchmarks (§13.4); F-Droid metadata.

## Pitfalls

- `CaptureRequest` templates default to HIGH_QUALITY noise reduction and
  sharpening. Every request must go through `RequestApplier.natural`.
- Smart casts don't work on properties from other modules (e.g.
  `ZoomTarget.zoomRatio` in core:caps used from core:camera): copy to a local.
- In Compose, `AnimatedVisibility` inside a `Box` that sits in a `Column`
  resolves to `ColumnScope.AnimatedVisibility` and fails; wrap it in its own
  composable (see `PanelHost`).
- CI logs: compile errors are the lines containing ` e: `.
