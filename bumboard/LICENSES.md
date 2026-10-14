# BumBoard licences

BumBoard is a fork of [fcitx5-android](https://github.com/fcitx5-android/fcitx5-android)
(LGPL-2.1-or-later). Upstream's licence permits a freemium or closed-source app as long as:

1. changes to upstream files (everything in `patches/`) are published under LGPL-2.1+;
2. users can replace the LGPL libraries (the app ships them as separate `.so` files);
3. the app's terms allow reverse engineering for debugging those changes (LGPL-2.1 §6);
4. no GPL code or GPL data is put inside the APK.

BumBoard's own code (`app/`, `core/`, `data/*.py`, `native/`, `scripts/`) is owned by the project
and can be licensed as the owner chooses. **Not legal advice — have the final list reviewed before
a paid launch.**

## In the APK

| Component | Licence | Use |
|---|---|---|
| fcitx5-android, fcitx5, libime, fcitx5-chinese-addons, fcitx5-rime, fcitx5-lua | LGPL-2.1+ | base app and engines |
| librime, librime-lua, librime-octagram | BSD-3-Clause | Rime engine |
| OpenCC (code + data) | Apache-2.0 | Traditional/Simplified conversion |
| fcitx5-android prebuilt libs (boost, glog, leveldb, lua, marisa, yaml-cpp, libuv…) | BSL-1.0 / BSD / MIT | check each when upstream bumps `prebuilt` |
| AndroidX, Material Components, Kotlin, coroutines | Apache-2.0 | UI and runtime |
| Splitties, Arrow, Timber, Flexbox, aboutlibraries | Apache-2.0 | upstream dependencies |

## Dictionary data

| Data | Licence | Notes |
|---|---|---|
| TypeDuck `jyut6ping3_scolar` | CC BY 4.0 | readings, English, register, written-Chinese equivalents. Commercial use allowed; ask TypeDuck to confirm before a paid launch (many sources). |
| rime-cantonese (chars, words, phrase, lettered, essay) | CC BY 4.0 | Cantonese input. `jyut6ping3.maps` is ODbL and is **not** used. |
| CC-CEDICT | CC BY-SA 4.0 | Mandarin readings and English |
| CC-Canto, CC-CEDICT Cantonese readings (Pleco) | CC BY-SA 3.0 | Cantonese words and readings |
| Unihan | Unicode License v3 | per-character fallback |
| jieba `dict.txt` | MIT | Mandarin word weights |

`readings.db` and the generated Rime dictionaries are therefore **CC BY-SA 4.0**: they may be used
commercially, must credit the sources (in-app: `assets/bumboard/data-licenses.txt`) and **must not
be encrypted or DRM-locked**. Paid features can gate *features*, not this data.

## Deliberately not used

| Thing | Why |
|---|---|
| words.hk | non-commercial licence |
| rime-ice | GPL-3; could only ship as a separate optional download |
| rime-prelude / rime-essay / luna-pinyin (LGPL-3) | replaced by BumBoard's own config and Mandarin dictionary |
| libime-jyutping data, upstream jyutping plugin | GPL-3+ data |
| fcitx5-anthy (Japanese) | GPL-2; use Mozc (BSD) when Japanese is added |
| HeliBoard, TypeDuck-Android code | GPL-3 |

## Planned add-ons (separate APKs / processes)

| Component | Licence | Notes |
|---|---|---|
| ML Kit Digital Ink (handwriting) | Google ML Kit terms, closed | in its own APK so the keyboard stays LGPL-clean and F-Droid-buildable |
| hanzi_lookup / Make Me a Hanzi graphics | LGPL-3 / Arphic PL | offline handwriting fallback |
| sherpa-onnx, Silero VAD | Apache-2.0, MIT | voice |
| SenseVoice-Small | FunASR Model Licence 1.1 | attribution + keep model name; wording says "reference and learning purposes" — Whisper (MIT) is the clean alternative |
| JMdict (Japanese), Mozc | CC BY-SA 4.0, BSD-3 | later |
| fcitx5-hangul, libhangul (Korean) | LGPL-2.1 | later |
