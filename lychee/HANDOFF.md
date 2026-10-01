# Lychee — handoff for the next agent

Read this, then `/CLAUDE.md` (repo rules: commit and push straight to `main`,
no branches, no PRs) and `README.md` (what the keyboard does).

## Goal (confirmed by the user)

A Gboard-like keyboard with only English, Mandarin and Cantonese, where every
Chinese candidate shows **Yale and pinyin** (always both, Yale first) and the
**English meaning** underneath, plus quick buttons for switching language
and Traditional/Simplified.

User decisions:
- Not a Gboard fork (closed source). Base: fcitx5-android (option B over
  forking TypeDuck-Android, which is a stale Trime fork).
- Name Lychee 荔枝, app ID `app.lychee.android`, lychee icon.
- Cantonese is typed in **Yale** by default; Jyutping spellings also work.
- Mandarin: **Pinyin + handwriting** only. Cantonese: Jyutping/Yale +
  handwriting. Handwriting: **Google ML Kit** (accepted: one-time model
  download from Google, closed-source library).
- English: **spelling variants only** (US/UK/CA/AU), no other layouts.
- Built and released "like Pebble": folder in this repo, GitHub Actions APK.

## Layout (changes from upstream)

Upstream fcitx5-android `e6199a28` is commit `86dceff`, unmodified except
for the dropped plugins. Everything after that is Lychee.

- `app/src/main/cpp/rime/` — fcitx5-rime built into the app (upstream ships
  it as a plugin APK; `plugin/` and `lib/plugin-base` are gone).
  `lychee_cantonese.schema.yaml` is the Cantonese scheme: TypeDuck's
  `jyut6ping3` dictionary, Yale spellings derived by spelling algebra,
  `simplification` switch (OpenCC `hk2s`), Jyutping of the match as comment.
  `test/run.sh` deploys it with the host's librime and checks `cases.tsv`.
- `dictionary/` — `build_dict.py` (readings + meanings DB, run by the Gradle
  task `lycheeDictionary` into a generated asset), `english/` (spelling
  variants, run at CMake configure time), sources and submodules. See its
  README for licences.
- `app/src/main/java/app/lychee/` — `dict/LycheeDictionary.kt` (asset copy +
  SQLite lookup, longest-match fallback for phrases), `dict/Romanization.kt`
  (Jyutping→Yale, pinyin tone marks; unit tests in `app/src/test`),
  `handwriting/` (ML Kit wrapper, ink view).
- Upstream files touched (search for "Lychee"):
  `input/candidates/CandidateItemUi.kt` (3-line cells),
  `input/bar/KawaiiBarComponent.kt` (taller bar, 繁/简 and handwriting
  buttons), `input/bar/ui/idle/ButtonsBarUi.kt`,
  `input/handwriting/HandwritingWindow.kt` (new), expanded candidate
  windows (height), `core/Fcitx.kt` (first run enables `keyboard-us`,
  `pinyin`, `rime`), `data/prefs/AppPrefs.kt` (Lychee prefs; toolbar shown by
  default), settings route/screen, `FcitxApplication.kt`,
  `androidkeyboard.cpp` (`keyboard-gb/ca/au` input methods),
  `lib/fcitx5/src/main/cpp/lychee/spell-custom-dict.cpp` (upstream file with
  the English dictionary taking the language, swapped in by CMake),
  build-logic signing (`SIGN_KEY_KEY_PWD`, debug-key fallback).

## Verified here (2026-10-01)

- `:app:assembleDebug` and `:app:assembleRelease` (arm64, R8 on) build.
- Lychee unit tests (`--tests 'app.lychee.*'`) pass.
- Cantonese scheme on librime 1.10 (Ubuntu): 15/15 cases — Jyutping, toneless
  Yale, Yale with tone "h", ch/j/y initials, syllabic m, initials only.
- Dictionary spot checks (食飯, 吃饭→吃飯, 你好, 行, 唔該, 香港, 电脑).
- UK/CA/AU spell dictionaries are generated and the patched source is the
  one compiled.

## Not verified

- **Nothing has run on a phone** (no emulator/KVM in this container): the
  candidate layout and bar height, first-run IM list, the 繁/简 button's
  state for Rime, handwriting (model download, recognition), first Rime
  deploy time on device (155k-entry dictionary).
- The app still contains upstream's plugin UI code paths (unused).

## Known issues / notes

- `ThemeSerializationTest.version2` fails upstream too (test expects theme
  version 2.0, code is at 2.1); CI only runs `app.lychee.*` tests.
- Without signing secrets CI signs with a throwaway debug key, so each CI
  APK has a different signature (uninstall before installing a new one).
  Secrets: `LYCHEE_KEYSTORE_BASE64`, `LYCHEE_KEYSTORE_PASSWORD`,
  `LYCHEE_KEY_ALIAS`, `LYCHEE_KEY_PASSWORD` (falls back to Pebble's).
- ML Kit sends Google its usual anonymous usage logs.

## Environment gotchas

- The agent proxy answers HTTP 429 for repo.maven.apache.org (and the
  Gradle plugin portal, which redirects there). Local fix (not committed):
  `~/.gradle/init.d/mirror.gradle` putting
  `https://maven-central.storage-download.googleapis.com/maven2/` first and
  the plugin portal last.
- Toolchain: Java 21, SDK 36, build-tools 36.1.0, NDK 28.0.13004108,
  CMake 3.31.6, apt `extra-cmake-modules gettext` (+ `librime-dev` for the
  scheme test). Submodules must be fetched recursively (libime needs kenlm).
- Run Gradle in the background; a cold build takes ~3 minutes here.
