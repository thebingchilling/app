# Lychee — handoff for the next agent

Read this, then `/CLAUDE.md` (repo rules: commit and push straight to `main`,
no branches, no PRs) and `README.md` (what the keyboard does).

## Goal (confirmed by the user)

A keyboard with only English, Mandarin and Cantonese where every Chinese
candidate shows Yale and pinyin (Yale first) and the English meaning, with
the full meaning on long-press (or wrapped, never cut off). Best quality
everywhere, open source where possible.

User decisions, in order:
- Built on **HeliBoard** (not fcitx5-android any more). The fcitx5 version was
  deleted (last commit with it: `919586c`); nothing was carried over except
  the icon, the Cantonese scheme and the dictionary source files.
- Engine: **librime** for both Chinese languages (best open option; libime
  and libpinyin have no real Cantonese).
- Mandarin: rime-ice's full word lists. Cantonese: TypeDuck, typed in Yale
  by default, Jyutping also works.
- **Voice typing** (SenseVoice via sherpa-onnx) and **ML Kit handwriting**,
  both as **optional downloads** (user asked "optional download for either
  one", then agreed to download rather than bundle). ML Kit cannot be bundled
  (Google only offers downloaded models). hanzi_lookup is the built-in
  handwriting fallback.
- Mandarin sentence model (octagram) as an optional download too.
- App ID `app.lychee.android`, version code 200+ (fcitx5 Lychee was 112).

## Layout

- `app/` — HeliBoard 4.1 (commit `a3716f5` is upstream unmodified). Lychee's
  own code is `app/src/main/java/app/lychee/`:
  - `LycheeKeyboard.kt` — what LatinIME talks to: keys, cursor moves,
    language changes; owns the candidate bar/grid/card, handwriting and voice
    panels (overlays in `strip_container` and `keyboard_view_wrapper`).
  - `chinese/ChineseInput.kt` — letters → Rime, composing text, punctuation;
    `RimeData.kt` (copies the `rime` assets once per install, starts Rime on
    its own thread); `SentenceModel.kt` (writes the octagram patch).
  - `readings/` — `Readings.kt` (SQLite lookup, longest-match fallback),
    `Romanization.kt` (Jyutping→Yale, tone marks), `CandidateLines.kt`
    (which reading matches Rime's comment; short meaning).
  - `ui/` — candidate cells/bar/grid, `WordCard.kt` (long-press),
    `TradSimpDrawable.kt` (the 繁/简 toolbar key).
  - `voice/` — `VoiceEngine.kt` (AudioRecord → Silero VAD → SenseVoice),
    `VoicePanel.kt`, `MicPermissionActivity.kt`.
  - `handwriting/` — `HandwritingPanel.kt` (ink view, ML Kit, fallback),
    `HandwritingModels.kt` (ML Kit downloads), `HanziLookup.kt` (Kotlin port
    of gugray/hanzi_lookup 01f90c3a; data `assets/lychee/mmah.bin`).
  - `downloads/Downloads.kt` — resumable downloads, pinned URLs + SHA-256.
  - `settings/LycheeScreen.kt` — Settings → Lychee (first entry of the
    main settings screen).
  - Upstream files touched (search "Lychee"): `LatinIME.java` (hooks),
    `ToolbarUtils.kt` + `KeyboardIconsSet.kt` (TRAD_SIMP, LANGUAGE_SWITCH,
    HANDWRITING keys), `SubtypeLocaleUtils.kt` (language names),
    `method.xml` (only en_US/GB/CA/AU, zh_CN, yue_HK), `SettingsNavHost.kt`,
    `MainSettingsScreen.kt`, manifest (RECORD_AUDIO, INTERNET), build file.
- `rime/` — Gradle library: JNI bridge `src/main/cpp/rime_jni.cpp` →
  `app.lychee.rime.Rime` (sessions, candidates, options, OpenCC). Links
  fcitx5-android's prebuilt static librime 1.16.1 (submodule `prebuilt/`,
  includes the lua and octagram plugins). `data/` holds Lychee's schemes;
  submodules `rime-ice/`, `typeduck/`, `rime-prelude/`.
  `build_rime_data.sh` compiles the dictionaries **on the build machine**
  with a host librime 1.16.1 (`host_librime.sh` builds it into
  `.host-librime/`) and ships only the binaries (65 MB) — the phone never
  compiles them. `test/run.sh`: scheme cases on host librime.
  `test/host_jni.sh` + `RimeHostTest`: the real JNI bridge on the host.
- `readings/` — `build_readings.py` → `lychee/readings.db` (210k words,
  every sense from TypeDuck, CC-CEDICT, CC-Canto). Licences in its README.
- Gradle task `:app:lycheeData` runs both data builds into generated assets.
- sherpa-onnx is not on Maven: `app/build.gradle.kts` fetches its AAR
  (v1.13.8, pinned SHA-256) into `.cache/`.

## Verified here (2026-10-02)

- `:app:assembleDebug`, `:app:assembleRelease` (arm64) build.
- Unit tests: Romanization, HanziLookup (hanzi_lookup's own samples give the
  same results), RimeHostTest (Yale, Jyutping, 𨋢 outside the BMP,
  Traditional/Simplified, backspace, partial selection, OpenCC) — through
  the real JNI code on host librime.
- Scheme cases: Mandarin 10/10, Cantonese 15/15 (with dictionary sources
  removed, as on the phone).
- `liblychee_rime.so` has no unresolved symbols; all JNI methods exported.
- Download checksums computed from the real files.

## Not verified (no emulator/KVM in the container)

- **Nothing has run on a phone.** In particular: first launch (asset copy
  + Rime start, expected a few seconds), the candidate bar height/layout,
  long-press card, handwriting panel and ML Kit, voice (model load time,
  speed, recognition quality), downloads over a real network, the sentence
  model's effect, toolbar key icons.
- Lua translators (dates, calculator) only exist in the phone's librime (the
  host build has no lua), so they were not tested.

## Known issues / notes

- hanzi_lookup lacks many Cantonese characters (咗 冇 啲 喺 哋 佢 …); the ML
  Kit Hong Kong model is the real fix (download).
- The sentence model URL is RIME-LMDG's rolling "LTS" release; if upstream
  replaces the file, its checksum stops matching and the download fails
  with "checksum does not match" — update `Downloads.kt` (URL/SHA/size).
- HeliBoard's own upstream tests are not run in CI (only `app.lychee.*`
  and `:rime` tests).
- Signing secrets as before: `LYCHEE_KEYSTORE_BASE64`,
  `LYCHEE_KEYSTORE_PASSWORD`, `LYCHEE_KEY_ALIAS`, `LYCHEE_KEY_PASSWORD`
  (falls back to Pebble's, then a throwaway debug key — then every CI APK
  has a different signature, and reinstalling wipes downloaded models).

## Environment gotchas

- The agent proxy rate-limits repo.maven.apache.org: local
  `~/.gradle/init.d/mirror.gradle` puts
  `https://maven-central.storage-download.googleapis.com/maven2/` first.
- No Android SDK in a fresh container: install cmdline-tools to
  `/opt/android-sdk`, then `platforms;android-36 build-tools;36.1.0
  ndk;28.0.13004108 cmake;3.31.6`; `local.properties` → `sdk.dir`.
- Host librime needs the apt packages listed in README; a first build takes
  a few minutes. Run Gradle in the background (a cold build ~5 minutes).
