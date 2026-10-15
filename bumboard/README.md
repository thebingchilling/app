# BumBoard

*(placeholder name)* — an Android keyboard for people learning **Cantonese and Mandarin**.
Every Chinese candidate shows its **Jyutping / Yale / pinyin** and a short **English meaning**;
long-press a candidate for the full dictionary entry. Type English in **EN→中** mode to get Chinese
words by meaning. Traditional and Simplified, Material 3 design.

Built on [fcitx5-android](https://github.com/fcitx5-android/fcitx5-android) 0.1.3 with
[Rime](https://rime.im) for both Chinese languages.

## Features

✅ = implemented and building in CI. Nothing has been tried on a real device yet.

| | Status |
|---|---|
| Cantonese (Jyutping or Yale input, tones optional or as digits) | ✅ |
| Mandarin (pinyin, ü as v, abbreviations) | ✅ |
| Readings under candidates: Jyutping, Yale (tone marks or numbers), pinyin; optional other language | ✅ |
| One-line English meaning under candidates; full entry on long-press | ✅ |
| EN→粵 / EN→普: type an English word, get Chinese words | ✅ |
| 繁/简 toggle (per language: Cantonese defaults to Traditional, Mandarin to Simplified) | ✅ |
| English typing | ✅ (upstream) |
| Material 3 Expressive settings app (dynamic colour), Material Symbols icons, M3 keyboard theme | ✅ |
| Gboard-style toolbar: pin tools, tools panel with drag to reorder | ✅ |
| Resize from the keyboard, one-handed mode, number row, emoji key, emoji size, two-person skin tones | ✅ |
| Globe key cycles 粵 → 普 → EN | ✅ |
| Voice typing (system recogniser, Cantonese/Mandarin/English; on-device where supported) | ✅ |
| Handwriting (separate add-on APK with ML Kit; candidates show readings and meanings) | ✅ |
| Offline voice model download (sherpa-onnx) | planned |
| Cantonese → Standard Written Chinese converter, Japanese, Korean | later |

## Building

CI builds the APK: `.github/workflows/build-bumboard.yml` (artifact `bumboard-*`).
Locally you need Python 3 with `pip install opencc`, `librime-bin` (for `rime_deployer`),
`extra-cmake-modules`, `gettext`, JDK 17 and the Android SDK/NDK listed in the workflow.

```sh
git submodule update --init --recursive bumboard/upstream/fcitx5-android
python3 bumboard/data/build_data.py        # dictionaries -> bumboard/data/out
bumboard/data/test/run.sh                  # Rime schema tests (host librime)
bumboard/scripts/apply-patches.sh          # patches -> upstream checkout
cd bumboard/upstream/fcitx5-android
BUILD_ABI=arm64-v8a ./gradlew :bumboard-core:test :app:assembleDebug
```

See [HANDOFF.md](HANDOFF.md) for how the project is put together and
[LICENSES.md](LICENSES.md) for every licence.
