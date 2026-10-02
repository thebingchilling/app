# Lychee 荔枝

An Android keyboard for **English, Mandarin and Cantonese**. Every Chinese
candidate shows its **Cantonese reading (Yale)**, its **Mandarin reading
(pinyin)** and its **English meaning** underneath; long-press a candidate for
everything Lychee knows about it, never cut off.

```
┌──────────────────────┐
│         食飯          │
│ sihk faahn · shí fàn  │
│     have a meal       │
└──────────────────────┘
```

Lychee is [HeliBoard](https://github.com/HeliBorg/HeliBoard) 4.1 (GPL-3.0)
with [Rime](https://github.com/rime/librime) built in for Chinese.
HeliBoard's README is kept as [README.HeliBoard.md](README.HeliBoard.md).

## Languages

- **English**: HeliBoard's own engine with US, UK, Canadian and Australian
  dictionaries (US and UK bundled).
- **Mandarin 普通话**: full pinyin with [rime-ice](https://github.com/iDvel/rime-ice)'s
  complete word lists; abbreviations (`zg` → 中国) and common slips work.
  Simplified by default.
- **Cantonese 廣東話**: [TypeDuck](https://github.com/TypeDuck-HK/schema)'s
  dictionary. Type **Yale** or **Jyutping** without tones (`sihkfaahn`,
  `sikfaan`), or just initials (`sf` → 食飯). Traditional by default.

In Mandarin and Cantonese, space takes the first candidate, Enter types the
letters as they are, and `,.?!` become `，。？！` (not after digits).

## Candidates

- Three lines per candidate: the word, `Yale · pinyin`, a short English meaning.
- **⌄** shows all candidates in a grid.
- **Long-press** any candidate: every Cantonese and Mandarin reading, every
  English sense from TypeDuck, CC-CEDICT and CC-Canto, and the characters one
  by one. "Forget word" removes a word Rime learned.
- Settings → Lychee: turn each line on or off, **wrap meanings** (two lines,
  nothing cut off), Jyutping instead of Yale.

## Toolbar

Pinned to the bar: **繁/简** (Traditional / Simplified for the current Chinese
language) and **✎ handwriting**. In the toolbar: language switch, 🎤 voice,
clipboard, undo/redo and the rest of HeliBoard's keys.

## Optional downloads (Settings → Lychee → Downloads)

None is needed; each is fetched only when you tap Download (Wi-Fi only by
default) and checked against a fixed SHA-256.

| Download | Size | Without it |
|---|---|---|
| Offline voice typing: [SenseVoice](https://github.com/FunAudioLLM/SenseVoice) via [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | 238 MB | 🎤 hands over to the phone's voice typing |
| Handwriting: Hong Kong Chinese / mainland Chinese / English ([ML Kit](https://developers.google.com/ml-kit/vision/digital-ink-recognition)) | ~20 MB each | Chinese handwriting uses the built-in [hanzi_lookup](https://github.com/gugray/hanzi_lookup) port (about 9,500 characters, not all Cantonese ones) |
| Mandarin sentence model: [RIME-LMDG](https://github.com/amzxyz/RIME-LMDG) (`wanxiang-lts-zh-hans.gram`) | 409 MB | rime-ice's word lists alone |

Voice recognises Mandarin, Cantonese and English automatically (also mixed),
or only the keyboard's language (setting). Its output follows 繁/简.

Lychee has the internet permission only for these downloads; typed text is
never sent anywhere. ML Kit, once a handwriting model is installed, sends
Google its usual anonymous usage data.

## Building

See [HANDOFF.md](HANDOFF.md). In short (Linux): Android SDK 36, NDK
28.0.13004108, CMake 3.31.6, Java 21, Python 3, and for the build-time Rime
deploy `cmake ninja-build libboost-dev libboost-regex-dev libboost-locale-dev
libgoogle-glog-dev libleveldb-dev libmarisa-dev libopencc-dev libyaml-cpp-dev`.

    git submodule update --init --depth 1 lychee/rime/   # from the repo root
    ./gradlew :app:assembleRelease

GitHub Actions builds a signed APK on every push to `lychee/` (workflow
*Build Lychee*, artifact `Lychee-apk`).

## Licences

Lychee is GPL-3.0 (HeliBoard's licence). Data and libraries: see
[readings/README.md](readings/README.md), [handwriting/](handwriting/) and
the credits in Settings → Lychee.
