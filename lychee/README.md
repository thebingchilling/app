# Lychee 荔枝

An Android keyboard for **English, Mandarin and Cantonese**. Every Chinese
candidate shows its **Cantonese reading (Yale)**, its **Mandarin reading
(pinyin)** and its **English meaning** in small text underneath:

```
┌──────────────────────┐
│         食飯          │
│ sihk faahn · shí fàn  │
│     have a meal       │
└──────────────────────┘
```

Lychee is [fcitx5-android](https://github.com/fcitx5-android/fcitx5-android)
(LGPL-2.1+) with the Rime engine built in, TypeDuck's Cantonese dictionary,
and Lychee's own reading/meaning dictionary. Upstream's README is kept as
[README.fcitx5-android.md](README.fcitx5-android.md).

## Languages

Enabled on first run, in this order (tap 🌐 to cycle, long-press to pick):

- **English**: US by default; *English (UK)*, *(Canada)* and *(Australia)*
  can be enabled instead in Settings → Input methods. They differ only in
  spelling suggestions (colour, organise, travelled…).
- **Mandarin (Pinyin)**: fcitx's pinyin engine (libime).
- **Cantonese**: Rime with [TypeDuck](https://github.com/TypeDuck-HK/schema)'s
  dictionary. Type **Yale** or **Jyutping** without tones (`sihkfaahn`,
  `sikfaan`, `sik faan`), or just initials (`sf` → 食飯).

## Toolbar

The toolbar above the keys shows:

- **繁 / 简**: switches the current Chinese input method between Traditional
  and Simplified output.
- **✎ Handwriting**: write characters with your finger (Google ML Kit, on
  device; the model is downloaded once, about 20 MB per language: Hong Kong
  characters in Cantonese, mainland characters in Mandarin, English otherwise).
- Undo, redo, cursor keys, clipboard, more.

## Settings

Settings → **Readings & meanings** turns the Yale, pinyin and English lines
on or off. With all three off the candidate bar goes back to its normal
height.

## Building

See [HANDOFF.md](HANDOFF.md). In short: Android SDK 36, NDK 28.0.13004108,
CMake 3.31.6, `extra-cmake-modules`, `gettext` and `python3`, then

    git submodule update --init --recursive lychee/   # from the repo root
    BUILD_ABI=arm64-v8a ./gradlew :app:assembleRelease

GitHub Actions builds a signed APK on every push to `lychee/`
(workflow *Build Lychee*, artifact `Lychee-apk`).

## Credits

See [dictionary/README.md](dictionary/README.md) for the dictionaries and
their licences; Settings → About → Open source licences lists everything.
