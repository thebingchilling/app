# Quartz

A camera for Android that looks and works like Pixel Camera, runs on any
phone's own camera through the public Camera2 API, and **never enhances an
image**. No HDR merging, no night stacking, no AI, no beautification, no
sharpening, no noise reduction. What the sensor saw is what you get.

- **Everything the hardware can do, you can choose:** every photo size
  (including full-sensor 50/108/200 MP modes), every resolution × frame rate
  the camera and its encoders both support, H.264/HEVC/AV1, HDR (HLG/HDR10
  10-bit), bitrate, RAW (DNG), HEIC, per mode and per lens.
- **Real lenses, honest zoom:** zoom buttons come from the phone's lenses
  and their 35 mm focal lengths; pinch shows a ruler with the lenses marked
  and turns amber past the longest one. Lock to one lens, add your own zoom
  stops, or save zoomed photos at their true cropped pixel count.
- **Modes:** Photo, Long exposure (one frame, never stacked), Interval,
  Video, Slow motion (no invented frames), Time-lapse (no blended frames).
- **Pro controls:** ISO, shutter, focus, white balance, exposure
  compensation, AE/AWB lock, shutter or ISO priority, with histogram, focus
  peaking and zebras that only ever draw on the viewfinder.
- **Natural report:** Quartz reads back what the camera actually did on
  every frame and tells you, per lens, whether it's fully natural or the
  phone applies processing it won't switch off (then RAW is the answer).
- **Material 3 Expressive:** Material Symbols, Google Sans Flex, Material You
  colour, the Pixel Camera layout (photo/video toggle, mode carousel, zoom
  pills, settings panel with a Pro tab).
- **Private:** no internet permission, no analytics, no Google Play Services.

## Build

CI builds every push (`.github/workflows/build-quartz.yml`): unit tests,
debug and release APKs (artifact `quartz-apks`), lint.

Locally: JDK 21, Android SDK platform 37 (`platforms;android-37.0`), then
`./gradlew :app:assembleDebug`. Release signing uses `QUARTZ_KEYSTORE_PATH`,
`QUARTZ_KEYSTORE_PASSWORD`, `QUARTZ_KEY_ALIAS`, `QUARTZ_KEY_PASSWORD` (CI:
the `QUARTZ_KEYSTORE_BASE64` secret plus the others); without them the
release APK is signed with the debug key.

## Layout

```
app/            Compose UI, ViewModel, SpecBuilder (settings + lens + zoom → session), activity, tile
core/ui/        Material 3 Expressive theme, Material Symbols drawables, shared components
core/caps/      DeviceCaps + probe, NaturalPolicy, lenses/zoom/metering maths, option grids
core/settings/  typed DataStore settings per mode × lens, Resolver
core/media/     MediaStore (DCIM/Quartz), EXIF without re-encoding
core/camera/    Camera2 engine, MediaCodec recording, time-lapse GL copy, analysis overlays
tools/          icons.py (Material Symbols → drawables), natural_check.py (JPEG vs RAW)
```

`PLAN.md` is the design; `HANDOFF.md` is the state of the code.

## Licences

Quartz: Apache-2.0 (`LICENSE`). Material Symbols: Apache-2.0. Google Sans
Flex: SIL Open Font License 1.1 (`core/ui/src/main/assets/licenses`).
