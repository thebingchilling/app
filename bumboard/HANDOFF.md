# BumBoard — handoff

Read this before changing anything. Then [README.md](README.md) and [LICENSES.md](LICENSES.md).

## Goal (from the user)

A keyboard for Chinese learners: under every candidate the romanisation (Jyutping and/or Yale for
Cantonese, pinyin for Mandarin) and an English meaning, with the full entry on long-press; an
English→Chinese mode (type the meaning, get Chinese words); English typing; Traditional and
Simplified; handwriting and voice; a full, current Material 3 (Expressive) UI with Material Symbols;
Gboard-level features (movable toolbar, resize, number row, emoji options, haptics/sound…).
Later: Cantonese → Standard Written Chinese converter, Japanese, Korean.
Name "BumBoard" is a placeholder. Monetisation: none for now (freemium later), so licences matter.

User decisions so far:
- base on **fcitx5-android** (LGPL — allows a paid app later; HeliBoard is GPL-3)
- **no billing** in the app initially
- project lives in `bumboard/` of the shared `thebingchilling/app` repo; push straight to `main`
  (another project, Quartz, lives in `quartz/`)
- builds happen in **GitHub Actions**, not locally (no Android SDK in the dev container)

Defaults chosen without an explicit answer (easy to change): Cantonese shown in Jyutping by default
(Yale or both in settings); Cantonese typed in Jyutping (Yale input in settings); app ID
`app.bumboard.android`.

## Layout

```
bumboard/
  upstream/fcitx5-android   submodule, pinned to release 0.1.3 — never edited in git
  patches/                  the only changes to upstream (applied by scripts/apply-patches.sh)
  app/                      BumBoard's Android code + resources (package app.bumboard),
                            added to upstream's app module as extra debug/release source sets
  core/                     pure Kotlin (no Android): romanisation, readings.db access, Annotator
  native/bumboard.cmake     builds fcitx5-rime into the app, installs Rime data
  data/                     build_data.py -> out/rime-data (Rime) + out/assets/bumboard/readings.db
    rime/                   hand-written Rime schemas and shared config
    test/                   host-librime harness + cases.txt
    sources.json            every input pinned by commit or sha256
  scripts/                  apply-patches.sh, save-patch.sh
```

### How BumBoard hooks into upstream (keep these small)

| Patch | What |
|---|---|
| 0001-build-integration | `settings.gradle.kts` includes `:bumboard-core`; `app/build.gradle.kts` appends one block (app id, name, icon, `rime` CMake target, source sets, OpenCC symlink for Rime, core dependency); `app/src/main/cpp/CMakeLists.txt` includes `native/bumboard.cmake` |
| 0002-ui-hooks | `CandidateItemUi` → `AnnotatedCandidateView`; `KawaiiBarComponent.HEIGHT` and expanded cells use `BumBoard.barHeightDp`; `BaseInputView.showCandidateActionMenu` → `WordCard`; `ButtonsBarUi` → `BumToolbar` |
| 0003-plugin-signature-check | `DataManager.detectPlugins` only loads plugins signed with the app's key |

Resources in `app/res` override upstream resources with the same name (build-type source sets have
priority over `main`), so restyling/icon swaps need no patches.

### Runtime flow

- Rime is one fcitx input method (`rime`). BumBoard's schemas (`data/rime`): `bum_yue` (Jyutping),
  `bum_yue_yale`, `bum_cmn`, `bum_en_yue`, `bum_en_cmn`. fcitx5-rime puts the schema **name** in the
  IME sub-mode; `BumBoard.modeOf` maps names → `InputMode`. Schema switching and the 繁/简 option go
  through fcitx5-rime's actions (`fcitx-rime-im` menu, `fcitx-rime-<schema>-<option>`), see
  `toolbar/LanguageControls.kt`.
- Rime's `spelling_hints` put each candidate's reading in its comment ("nei5 hou2", "ni hao").
  `core/Annotator` uses it to pick the right reading/meaning from `readings.db`; the comment itself
  is never shown.
- `readings.db` (≈46 MB) ships as an APK asset and is copied to `filesDir/bumboard` once per app
  update, on a background thread (`BumBoard.openDictionary`).
- Rime data is precompiled on the build machine (`rime_deployer`, host librime 1.10) into
  `rime-data/build`; if the device's librime rejects a binary it rebuilds it (slower first start).

## Updating things

- **Upstream fcitx5-android**: bump the submodule to a new tag, run `scripts/apply-patches.sh`.
  If a patch fails, fix the files by hand in the checkout and rewrite it with
  `scripts/save-patch.sh <patch> -- <files>`. Re-check `native/bumboard.cmake` against
  `plugin/rime/src/main/cpp/CMakeLists.txt` (Rime_VERSION, dependency list).
- **Dictionaries**: bump a commit/url + sha256 in `data/sources.json`; CC-CEDICT is a dated snapshot
  in `data/snapshots/`. Run `build_data.py` and `test/run.sh`.
- **Rime schemas**: edit `data/rime/*.yaml`, add a case to `test/cases.txt`.

## Verified (2026-10-14)

- `build_data.py` builds everything; Rime cases 29/29 on host librime.
- `core` tests 18/18 on the JVM, including against the real readings.db.
- Android build: see the latest run of `.github/workflows/build-bumboard.yml`.

## Next

1. Material 3 Expressive: Compose settings (romanisation, meaning, Yale input, theme), onboarding,
   Material Symbols icons replacing upstream drawables by name, M3 key shapes.
2. Gboard-style: movable toolbar + tools panel, in-keyboard resize, one-handed, number row, emoji
   key/language key show/hide/compact, emoji size and per-person skin tones, delete-swipe.
3. Handwriting (ML Kit add-on APK + offline fallback) and voice (sherpa-onnx, downloadable model).
