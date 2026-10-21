# Chinese Learning Keyboard: Project Plan

An Android keyboard for learners of Chinese, built with Jetpack Compose and Material 3. It is a closed-source fork of FlorisBoard (Apache-2.0), with AOSP LatinIME's English engine and librime for Chinese. It should be good enough to be a user's **default, everyday keyboard**, not only a learning tool.

**Long-term scope**

- **Input:** Mandarin (Hanyu Pinyin, Zhuyin/Bopomofo) and Cantonese (Jyutping, Yale).
- **Candidates:** each shows its reading(s) and a short English gloss.
- **Reverse lookup:** type English to get Chinese candidates.
- **Scripts:** Traditional and Simplified.
- **On-device ML:** handwriting, voice input, and Cantonese → Standard Written Chinese translation.
- **Extras:** emoji, fonts and stickers.
- **Later languages:** Korean and Japanese.
- **Business model:** freemium, with a one-time Pro unlock.

> This is not legal advice. Have a lawyer review the licensing notes before the app charges money.

---

## 0. Foundation decisions (decided)

### 0.1 Base: FlorisBoard fork + LatinIME engine + librime

OpenBoard and HeliBoard are GPL-3.0, so they are ruled out (the app is closed source). Bare AOSP LatinIME was evaluated next: it is minimal, but it lacks a lot and crashes on Android 16. The chosen base combines three permissively licensed projects, each the best open option for its part:

| Part | Project | License | Role |
|---|---|---|---|
| **Keyboard shell** | **FlorisBoard** | Apache-2.0 | Input method service, Compose keyboard rendering, layouts, themes, emoji, clipboard, gestures. Kotlin + Jetpack Compose, works on current Android. |
| **English suggestions** | **AOSP LatinIME native engine** (`libjni_latinime` + dictionary format) | Apache-2.0 | Suggestions, autocorrect, personal dictionary. FlorisBoard's current releases have no word suggestions or spell checking. Only the C++ engine and its dictionary format are used, not LatinIME's UI. |
| **Chinese engine** | **librime** (Rime) | BSD-3 | Pinyin, jyutping, zhuyin, fuzzy matching, user word learning, multi-syllable lattice, OpenCC conversion, mixed English. Our own Android front end (Trime is GPL and fcitx5-android is LGPL, so both are off-limits). |

**Rejected alternatives:** AnySoftKeyboard (Apache-2.0, but heavy legacy View-based code), Simple Keyboard by rkkr (Apache-2.0, minimal but no suggestions and little activity), FUTO Keyboard (non-commercial license).

**Apache-2.0 obligations (FlorisBoard, LatinIME):** keep the LICENSE and NOTICE files and the original copyright headers, mark modified files, and list both on the in-app attribution screen. Publishing our code is **not** required. FlorisBoard's **name and logo are not covered by the license**, so the fork is fully rebranded.

**Fork policy: fork once and diverge.** We strip and change too much to keep merging upstream. Specific upstream fixes are pulled in by hand when needed.

**Before relying on FlorisBoard:** check every bundled asset (dictionaries, emoji data, icons, fonts) for Apache-compatible licenses, and verify Android 16 behaviour on its current main branch (it is still labelled beta).

**LatinIME gaps:** Google's gesture library was never open-sourced, so swipe typing comes from FlorisBoard's own (basic) implementation, tuned later. Check each LatinIME `.dict` file's license; building English word lists from openly licensed frequency data is the safe route.

### 0.2 Step 1: refine the fork before adding anything

The first step is **not** adding features. It is making the forked keyboard look, feel and behave exactly as intended:

1. **Fork and rebrand:** new app name, package ID (`applicationId`), icon, and strings. Remove FlorisBoard branding, its add-ons store links and update channels.
2. **Strip:** remove features and settings that don't fit the bare, minimal direction.
3. **Fix and polish:** layout, key sizing and spacing, key feedback (haptics, sound, popups), theming with Material 3 / Material You, candidate bar look, settings app structure, and anything broken on Android 16.
4. **Measure:** input latency, cold start time, memory, crash-free behaviour, before and after changes.
5. **Record decisions** in `docs/keyboard-behaviour-spec.md` (how every key, gesture and setting should behave). This becomes the reference for later work.

Only after this step do new engines and features go in (LatinIME engine, librime, learning layer).

**OpenBoard-like behaviour:** where we want a behaviour OpenBoard has (for example space-bar cursor swipe or delete swipe), it is written into the behaviour spec and implemented from scratch. Never copy or translate code, layout XML, translations or icons from OpenBoard or HeliBoard (GPL).

### 0.3 Closed source and private repositories

- **The app is closed source.** The `app` repository (and `chat`) is **private**.
- GitHub "public with no license" was rejected. It is legally "all rights reserved", but copying is trivial, and pursuing copies in other countries is impractical.
- Source-available licenses (FSL, BSL, PolyForm) were rejected too. They don't stop cloners outside the reach of enforcement.

**Public footprint** (a separate public repository, for example `<app-name>-community`):

- issue tracker and feedback;
- privacy policy, changelog, attribution / third-party licenses list;
- the **compiled dictionary data**, which must be shared anyway under CC-CEDICT and CC-Canto share-alike terms;
- optionally `tools/dict-pipeline`, which helps the learner community and gives competitors little.

**Trust without source code:**

- no INTERNET permission in the keyboard (anyone can verify it in the APK);
- a plain-language data safety statement;
- optionally, a third-party security audit later.

**Protection against competitors** is mainly not about hidden code:

- a **registered trademark** on the app name and logo, the most effective tool against confusingly similar clones on Google Play;
- the Cantonese → Standard Chinese **model and its training data** stay private;
- data quality, polish and shipping speed.

### 0.4 Dependency license policy

Because the app is closed source, every dependency must allow closed-source distribution.

| Allowed | Conditions |
|---|---|
| Apache-2.0, MIT, BSD, ISC, Unicode License, zlib | Keep notices; list on the attribution screen |
| SIL OFL 1.1 (fonts) | Bundle only with the app, never sold standalone; rename subsets of fonts with a Reserved Font Name |
| CC BY / CC BY-SA **data** | Attribution; derived data files are published under the same license. Code is unaffected. |
| Proprietary but free SDKs (e.g. ML Kit, Play Billing) | Follow their terms |
| **LGPL** | **Data tables only** (e.g. libchewing / libhangul tables). Never link LGPL code, because LGPL's relinking requirement is impractical on Android. Zhuyin and Hangul logic are written in-house. |
| **GPL / AGPL** | **Never**, in code or build-time code copied into the app |
| CC BY-ND | Only if shipped completely unmodified (e.g. no font subsetting) |
| CC BY-NC, "free for personal use" | **Never** (the app is commercial) |

Data licenses remain a bigger legal risk than code licenses. See [section 4](#4-data-sources-and-licenses) and [section 7](#7-emoji-and-fonts).

---

## 1. Architecture (Gradle multi-module)

This is the **target** structure. The fork starts from FlorisBoard's own module layout, and we move towards this structure gradually after step 1 (section 0.2), rather than restructuring on day one.

```
:app                 Settings/onboarding/store UI (Compose, Material 3), entry point
:ime                 InputMethodService, InputConnection handling, event pipeline
:keyboard-ui         Compose keyboard surface, layouts, candidate bar, themes
:engine-api          Interfaces: InputEngine, Candidate, Composition, Reading
:engine-latin        AOSP LatinIME native engine via JNI: suggestions, autocorrect, personal dictionary
:engine-chinese      librime front end (JNI) + Rime schemas: Pinyin, Zhuyin, Jyutping, Yale, mixed English
:engine-korean       (later) Hangul composition + hanja
:engine-japanese     (later) kana-kanji conversion
:dict                Dictionary storage and query layer (readings, glosses, frequency)
:convert             Traditional/Simplified (OpenCC)
:emoji               Emoji picker, CLDR keyword search, skin tones, recents, style selection
:fonts               Bundled font catalogue, user import, fallback chain, glyph coverage check
:stickers            (later) Local sticker packs, sent through the commit-content API
:billing             Play Billing wrapper + entitlement state
:feature-handwriting Dynamic feature module (model downloaded on demand)
:feature-voice       Dynamic feature module
:feature-translate   Dynamic feature module (Cantonese → Standard Written Chinese)
tools/dict-pipeline  Build-time scripts (Kotlin/Python); never shipped in the app
```

### Engine contract (sketch)

```kotlin
interface InputEngine {
    fun onKey(event: KeyEvent): EngineResult  // update composition
    fun candidates(page: Int): List<Candidate>
    fun commit(candidate: Candidate): String
    fun reset()
}

data class Candidate(
    val text: String,                         // in the user's chosen script
    val traditional: String,
    val simplified: String,
    val readings: Map<Romanization, String>,  // PINYIN, JYUTPING, YALE, ZHUYIN
    val gloss: String?,                       // short English descriptor
    val entryId: Long,                        // for the full dictionary card
    val source: Source,                       // DICT, USER, PREDICTION, ENGLISH_LOOKUP, ...
)
```

Every language engine returns the same `Candidate` type. That way one candidate bar can show readings and glosses for any language, including Korean hanja and Japanese later.

---

## 2. Compose and Material 3 inside a keyboard

FlorisBoard already hosts Compose inside its input method service and renders the keyboard in Compose. The points below are what to check, profile and tighten during step 1, not things to build from zero.

- **Hosting Compose in the input method service.** Return a `ComposeView` from `onCreateInputView()`. An IME window has no Activity, so set `ViewTreeLifecycleOwner`, `ViewModelStoreOwner` and `SavedStateRegistryOwner` on the window's decor view by hand. Without them, Compose crashes.
- **Key rendering.** Profile FlorisBoard's key rendering first. If it is a bottleneck, move to drawing all keys on one `Canvas` with a single `pointerInput` handler for multi-touch, key repeat and swipe gestures. Do not use one composable per key: recomposing about 40 key composables on every shift or layout change is where Compose keyboards get laggy. The candidate bar, toolbar, emoji panel, dictionary popup and settings can all be ordinary composables.
- **Avoiding recomposition on every keystroke.** Keep engine state in a `StateFlow` and use stable or immutable data classes. Wrap the candidate list in `derivedStateOf` and show it in a `LazyRow` with keys.
- **Theming.**
  - Material You key colours come from `dynamicColorScheme()`.
  - Also offer custom light, dark and high-contrast themes. Custom themes are a natural Pro feature.
  - The settings app uses the full Material 3 component set, including the Expressive components.
- **Insets and size.** Handle navigation bar insets, floating and one-handed modes, and user-adjustable key height.
- **Memory.** The IME process runs alongside whatever app the user is typing in, and Android kills it aggressively.
  - Load dictionaries with mmap.
  - Load ML models lazily.
  - Release models in `onFinishInputView` or after an idle timeout.

---

## 3. Chinese input engine design

### 3.1 Engine: librime

Every phonetic scheme reduces to the same steps: segment the input into syllables, build a lattice, then rank dictionary matches by frequency, context and user history. **librime already does all of this**, so we do not write our own decoder. Our work is:

- an Android front end: JNI bridge, feeding Rime's composition and candidates into the candidate bar;
- Rime **schemas** (one per input scheme) and **dictionaries** generated by our pipeline (section 4);
- the learning layer on top: readings, glosses, dictionary card (section 3.3).

What Rime gives us, configured through schemas:

- **Segmentation.** Ambiguous strings are split by building a syllable lattice (`xian` could be xian or xi'an). Initial-only abbreviations are supported (`nh` → 你好), and tones are optional.
- **Ranking.** Combine word frequency, a small bigram model for next-word prediction, and a learned user dictionary.
- **Fuzzy matching.** Optional toggles such as z/zh, n/l and an/ang. Southern Chinese and Cantonese speakers will want these.

### 3.2 Schemes

| Scheme | Notes |
|---|---|
| **Hanyu Pinyin** | Main Mandarin engine. Accepts `v` or `ü`, tones as digits 1–5, or no tones. |
| **Zhuyin / Bopomofo** | Each Zhuyin syllable maps one-to-one to a pinyin syllable. Its own key layout (standard Dachen), with a Rime schema that maps Zhuyin onto the same dictionary as pinyin. No separate dictionary needed. |
| **Jyutping** | Main Cantonese engine. Accepts tones 1–6 or no tones. |
| **Yale (Cantonese)** | Converted from Jyutping by rule, for both display and input. Display can use diacritics plus *h*; input uses digits or no tones. |

### 3.3 Candidate bar display (the learning part)

- **Learning mode toggle.** Daily users won't want readings and glosses on every candidate. A one-tap **Learning mode** toggle on the toolbar switches between a clean, Gboard-style candidate bar (off) and the full learning display below (on). The settings choose what Learning mode shows.
- **Readings.** A small ruby line above each candidate shows its reading(s). The user picks which: pinyin, jyutping, Yale or Zhuyin. They can also show **Mandarin and Cantonese readings together**, which is a key selling point.
- **Gloss.** A short English gloss sits under each candidate, truncated to fit.
- **Tone colours.** Optional colouring by tone, like Pleco.
- **Dictionary card.** Long-pressing a candidate opens a card with all readings, all senses, both scripts, example words and a "save to study list" button.

### 3.4 Typing English to get Chinese

Many strings are valid in both languages (`an`, `men`, `hen`, `pin`), so English lookup needs an explicit trigger:

- an "EN→中" chip or toggle on the candidate bar, or
- a prefix key (for example, starting with `'` or long-pressing space).

The lookup uses a SQLite FTS5 index over the English glosses. Results are ranked by headword frequency and by whether the match is the entry's first sense, so typing "water" shows 水 before 水泥.

### 3.5 Mixed English and Chinese

Mixing languages mid-sentence is normal, especially in Hong Kong ("我今日好 busy"). Two mechanisms:

- **Mixed candidates:** Rime schemas can mix English words into Chinese candidates, so typing `busy` in pinyin or jyutping mode also offers "busy". The same mechanism helps the English → Chinese lookup.
- **Fast switching:** long-press or swipe on the space bar, or a globe key, to switch between the English layout (LatinIME engine) and Chinese layouts (Rime).

### 3.6 Traditional / Simplified

- Both forms are stored for each entry (CC-CEDICT already has them), and the keyboard outputs whichever the user chooses.
- Conversion that needs context goes through **OpenCC** (Apache-2.0), which librime integrates directly. This covers phrase-level conversion and regional vocabulary, for example 软件 → 軟體 in Taiwan or 軟件 in Hong Kong. Profiles: `s2hk`, `s2tw`, `s2twp`, `t2s`.
- Settings: output script (Simplified / Traditional-HK / Traditional-TW), plus a separate script setting for candidate display.

---

## 4. Data sources and licenses

| Purpose | Source | License / caveat |
|---|---|---|
| Mandarin readings + English glosses | **CC-CEDICT** | CC BY-SA 4.0. The compiled dictionary file must also be CC BY-SA and credited. The app code is unaffected. |
| Cantonese readings for CEDICT words | **CC-Canto** / CC-CEDICT Cantonese readings | CC BY-SA |
| Cantonese words + frequencies | **rime-cantonese** | Believed CC BY 4.0, but its license file could not be loaded during research. **Confirm before use.** Good word frequencies for ranking. |
| Ready-made Rime schemas (starting point) | `rime-luna-pinyin`, `rime-bopomofo` | LGPL-3.0 (verified). Allowed as **data only** (policy 0.4); replaced over time by schemas and dictionaries from our pipeline. |
| Per-character readings | **Unihan** (kMandarin, kCantonese, kHanyuPinyin) | Unicode License (permissive) |
| Cantonese colloquial glosses | words.hk | **Check the license.** It has historically restricted commercial use. Don't assume it can ship in a paid app. |
| Mandarin frequencies | SUBTLEX-CH, Jun Da | Check each one's terms |
| Trad/Simp conversion | **OpenCC** | Apache-2.0 |
| Keyboard shell | **FlorisBoard** | Apache-2.0 (verified). Audit bundled assets separately. |
| English engine | **AOSP LatinIME** native engine | Apache-2.0 |
| Chinese engine | **librime** + build deps (Boost, LevelDB, yaml-cpp, marisa, glog) | BSD-3 (verified); deps BSL-1.0 / BSD / MIT |
| Emoji names and keywords | **Unicode CLDR annotations**, `emoji-test.txt` | Unicode License v3 (permissive) |
| Stroke order (optional) | Make Me a Hanzi | Arphic / LGPL mix; check before use |
| Japanese (later) | **Mozc** (engine), **JMdict** (glosses) | BSD-3; CC BY-SA |
| Korean (later) | Hangul composition is algorithmic; hanja tables from libhangul | LGPL for the data tables |

**Build pipeline**

1. Raw sources go into `tools/dict-pipeline`.
2. The pipeline normalises readings, merges entries, attaches frequencies, and derives Yale and Zhuyin.
3. The output is **Rime dictionaries** (`*.dict.yaml`, compiled by librime) for input, plus **SQLite** for entries, glosses and the English full-text index used by the learning layer. Both are keyed by the same entry IDs.
4. The result ships as a versioned asset, roughly 15–40 MB for Chinese.

Every source must be recorded in `LICENSES/data.md` and credited on an in-app attribution screen.

---

## 5. On-device ML features

All ML runs on the device. Each feature ships as an **on-demand dynamic feature module or Play Asset Delivery pack**, so the base APK stays small and only users who want a feature download it.

### 5.1 Handwriting

- **Quickest option: Google ML Kit Digital Ink Recognition.** It is free, runs on-device, and supports Chinese, Japanese and Korean. It is a proprietary library, which is fine now that the app is closed source.
- **Open option:** Zinnia (BSD) with open stroke models, or a custom small CNN/transformer exported to ONNX or TFLite.
- **Avoid CASIA-HWDB** for training a commercial model. It is licensed for research only.
- **UI:** a handwriting pad replaces the key area and feeds the same candidate bar, so handwritten characters also get readings and glosses. This is very useful for learners who can't pronounce a character.

### 5.2 Voice

- **Recommended:** **sherpa-onnx** (Apache-2.0) as the on-device speech runtime, running **SenseVoice-Small**. That model covers Mandarin, Cantonese, English, Japanese and Korean, which is the whole roadmap. Check its model license terms for commercial use.
- **Alternative:** Whisper through whisper.cpp. The small Whisper models are weak at Cantonese.
- **Free fallback:** Android's system `SpeechRecognizer`. It uses the network, so it is not private.

### 5.3 Cantonese → Standard Written Chinese

1. **Rule-based (ships first, tiny).**
   - A word substitution table: 佢→他, 嘅→的, 唔→不, 喺→在, 冇→沒有, 咗→了, 啲→些, 睇→看.
   - Common grammar reorderings: 佢高過我 → 他比我高; 我畀本書你 → 我給你一本書.
2. **Model-based (Pro).**
   - A small seq2seq or LLM model (about 0.5B parameters or less, quantised), fine-tuned on parallel Cantonese/Standard Chinese data.
   - It runs on ONNX Runtime or llama.cpp.
   - If public parallel data is thin, generate synthetic training pairs with a larger model and have native speakers check a sample.
- **UX:** type or speak Cantonese, tap "→ 書面語", see a preview with the changes highlighted, then commit. Showing *what changed* is itself a learning feature.

---

## 6. Learning features

- **Study list** built from words the user saved or looked up, with Anki export (CSV/APKG) and a simple spaced-repetition review screen in the app.
- **Learning mode,** which briefly shows readings above the text just committed.
- **Lookup history** and a character breakdown (radicals and components).
- **Optional:** tone drills that use the voice model.

---

## 7. Emoji and fonts

### 7.1 How keyboards send emoji

**A keyboard sends emoji as Unicode characters, not images.** Picking 😀 sends the code point U+1F600, and the receiving app draws it with *its own* emoji font. So:

- A custom emoji style only changes how emoji look **inside the keyboard** (picker, candidates, previews). The recipient still sees their own phone's emoji.
- The setting should be labelled something like "Emoji style in keyboard" and state clearly that recipients won't see it.
- Sending custom artwork is a different feature: **stickers**. Stickers go out as images through the commit-content API, and only in apps that accept images (see 7.6).

### 7.2 Emoji sets

| Set | License | Notes |
|---|---|---|
| **Noto Color Emoji** (Google) | SIL OFL 1.1 | Safest default. Matches most Android phones, and updates keep pace with Unicode. |
| **Twemoji** (community fork, jdecked/twemoji) | Graphics CC BY 4.0, code MIT | Requires credit. Twitter/X stopped updating the original. |
| **OpenMoji** | CC BY-SA 4.0 | Requires credit. Changes to the artwork must also be released as CC BY-SA. Bundling it unchanged is fine. |
| **Fluent Emoji** (Microsoft) | MIT | 3D, flat and high-contrast styles. Check that it covers the latest Unicode version. |
| **Blobmoji** | Apache-2.0 / OFL | The retro Android "blob" style; community maintained. |
| JoyPixels | Proprietary | The free tier excludes this kind of use. Needs a paid license. |
| **Apple, Samsung, WhatsApp, Facebook emoji** | **Proprietary: do not ship** | Copyrighted artwork. Apps offering "iOS emoji" are regularly taken down from Play. |

**Emoji data**

- **Unicode CLDR annotations** give emoji names and search keywords in `zh`, `zh_Hant`, `yue`, `yue_Hans`, `ja`, `ko` and English. Users can search by typing 開心, 笑, 웃음 or "smile", and the keywords double as vocabulary for learners.
- `emoji-test.txt` and `emoji-sequences.txt` provide the ordering, groups, skin-tone variants and ZWJ sequences.

### 7.3 Fonts

Fonts can be used in three ways, and the rules differ.

#### a) Fonts for the keyboard's own display (key labels, candidates, dictionary card)

| Font | Style | License |
|---|---|---|
| **Noto Sans / Serif CJK** (same design as Source Han) | Sans (Hei), Serif (Song/Ming) | OFL 1.1 |
| **LXGW WenKai 霞鶩文楷** (plus the TC and HK variants) | Kai, handwriting-like | OFL 1.1 |
| **Chiron Hei HK / Chiron Sung HK** | Hong Kong character standard | OFL 1.1 |
| **Iansui 芫荽** | Rounded, friendly | OFL 1.1 |
| **Klee One, Zen Maru Gothic** | Japanese (kanji + kana) | OFL 1.1 |
| **Ma Shan Zheng, Zhi Mang Xing, Long Cang, Liu Jian Mao Cao, ZCOOL fonts** | Calligraphy / brush (Google Fonts) | OFL 1.1 |
| **TW-Kai 全字庫正楷體** | Taiwan's official Kai style for teaching | Taiwan Open Government Data License (treated as compatible with CC BY 4.0); requires credit |
| Edukai 教育部標準楷書 | Taiwan's Ministry of Education standard Kai | CC BY-ND 3.0 TW. **ND means no modification, so it can't be subset.** Ship it whole or skip it. |

Kai-style fonts matter for learners: they are closest to handwriting, so they suit the handwriting pad and study cards.

**Do not ship** (often assumed free, but none are):

- 方正 (Founder), 汉仪 (Hanyi), 华康 (DynaComware) fonts: commercial licenses, actively enforced.
- 微软雅黑 (Microsoft YaHei), 苹方 (PingFang), Hiragino: they come with an operating system and can't be redistributed.
- Anything marked "free for personal use" (most of DaFont, many Chinese download sites): that excludes a paid app.

#### b) Fancy text styles (𝓯𝓪𝓷𝓬𝔂, 𝕕𝕠𝕦𝕓𝕝𝕖, ⓒⓘⓡⓒⓛⓔ)

These are not fonts. They are Unicode mathematical and enclosed letter characters, so **no license is involved**. Two limits:

- They exist only for Latin letters and digits. There is no Chinese equivalent.
- Screen readers read them badly, and search engines don't index them. Show a short accessibility warning.

#### c) Changing the font in other apps

A keyboard can't do this; the receiving app always decides the font. Never advertise anything that implies otherwise.

### 7.4 OFL rules to watch

1. **Bundling an OFL font inside a paid app is allowed.** OFL also sits fine next to GPL code, because the fonts ship as separate files rather than being compiled into the code.
2. **An OFL font can't be sold by itself.** A separate "Font pack: LXGW WenKai, $0.99" purchase is a grey area, because it arguably sells the font alone. **Safer:** make the fonts free, or include them in the general Pro unlock alongside other features.
3. **Reserved Font Names.** Some OFL fonts reserve their name; Source Han, for example, reserves "Source".
   - **Subsetting a font to shrink it counts as modifying it,** and a modified font may not keep a reserved name.
   - Either rename the subset, or use a version without a reserved name. Noto CJK is commonly used for this, but check the license header of the exact file.
   - CJK fonts are 15–20 MB each, so expect to subset them or download them on demand.
4. **Include the license file and credit the font** on the in-app attribution screen, the same as dictionary data.

### 7.5 User-imported fonts and emoji

Users can be allowed to add their own files, and the risk is low *if imports stay on the user's device*. No setup removes legal risk entirely.

**What keeps the risk low**

- **Local only.** The user picks a file with Android's file picker (Storage Access Framework). The app copies it into private storage and uses it only on that phone. It is never uploaded, synced or shared. Legally this resembles a font viewer or media player: the user is responsible for having rights to their own files.
- **No hosting and no gallery.** Hosting user uploads, or letting users share packs with each other, turns the app into a platform. That brings a copyright (DMCA) agent, a takedown process, and moderation and reporting tools under Google Play's user-generated content policy. Avoid this unless it becomes a deliberate product decision.
- **No encouragement of infringement.** Never market, document or show screenshots of "import iOS emoji" or "use Apple fonts", and never link to sites offering them. Encouraging it creates liability even when users do the importing, and it is what gets keyboard apps pulled from Play.
- **Terms of service.** One line: users must have the rights to any content they import, and imported content stays on their device.

**Technical safety (font files are an attack surface)**

- Treat imported files as untrusted. Font parsers have a long history of security bugs.
- Use Android's built-in font loading (`Typeface.Builder`, the system font engine). Do not bundle a separate parser.
- Cap file size (for example 30–40 MB for CJK fonts) and check file types (`.ttf`, `.otf`, `.ttc`).
- Wrap loading in try/catch, and fall back to the default font if loading fails or glyphs are missing.
- Check glyph coverage: if a user's font has no Chinese characters, fall back for those characters instead of showing empty boxes.

### 7.6 User emoji and stickers

- **Custom emoji style:** users import a colour emoji font file. It affects display inside the keyboard only (see 7.1). Android 13+ handles the modern colour font format (COLRv1); the bitmap format (CBDT) works on all versions.
- **Stickers:** users build sticker packs from their own images or photos, sent through the commit-content API. This is usually what users mean by "add my own emoji". It is low-risk under the same local-only rules, and a good Pro feature.
- **GIF search** through third-party services (GIPHY and similar) means accepting their API terms and adding network access. That undoes the no-internet-permission privacy point (section 8). Leave it out, or make it a clearly labelled optional feature.

### 7.7 Assets plan

- Ship **Noto Color Emoji** and **Noto CJK** (or LXGW WenKai) as the defaults.
- Offer the other emoji sets and fonts as on-demand downloads to keep the APK small.
- Record every font and emoji set in `LICENSES/data.md` and on the attribution screen.

---

## 8. Freemium design

### Mechanics

- Google Play Billing Library with a single **non-consumable "Pro" purchase**. A subscription can come later if cloud features ever appear.
- The entitlement is cached locally, so the keyboard never blocks waiting on the network, and it is re-checked in the background.
- **The keyboard can ship without the INTERNET permission:**
  - Billing goes through the Play Store app.
  - Model, font and emoji downloads go through Play Asset Delivery.
  - Promote this on the store page; it is a strong trust signal for a keyboard.

### Suggested split

| Free | Pro |
|---|---|
| **Everything in the daily-driver bar (section 9)** | |
| Pinyin, Zhuyin, Jyutping | Handwriting |
| Trad/Simp output | Voice input |
| One reading + short gloss on candidates | Cantonese → Standard Chinese translation |
| Basic dictionary card | Dual readings (Mandarin + Cantonese together), Yale, tone colours |
| English keyboard | English → Chinese search *(or keep it free as the hook)* |
| Emoji picker with Noto + multilingual emoji search | Other emoji styles (Twemoji, OpenMoji, Fluent, Blobmoji) |
| One or two bundled fonts | Full font catalogue, importing your own fonts |
| Fancy text styles | Custom sticker packs |
| | Study list, Anki export, SRS review |
| | Custom themes |

Guidelines:

- **Core typing is never paywalled.** The free tier must be a complete everyday keyboard; if it feels limited, people won't keep it as their default and will never see Pro.
- **English→Chinese search could stay free,** since it's the feature people will show to others. The ML features cost the most to build, so charge for those.
- **Fonts belong in the Pro bundle, not a standalone purchase** (OFL rule, section 7.4).

### Piracy and protection

Closed source stops people legally republishing the app. It does not stop piracy: Android apps can be decompiled, and tools exist that patch billing checks. The aim is to make cracking tedious, not impossible, and to spend most effort on value for paying users.

- **R8 obfuscation and shrinking** in release builds.
- **No single `isPro()` check.** The entitlement is checked in several places and inside feature modules, so one patch doesn't unlock everything.
- **Play Integrity API** to detect modified apps and installs from outside Google Play.
- **Server-side purchase verification (optional, strongest).** Pro models (handwriting, voice, translation) would only be downloadable from our server after verification.
  - Trade-off: this needs network access, which conflicts with the "no INTERNET permission" selling point.
  - Compromise: network access lives only in the settings / download component, never in the typing path, and is declared clearly.
  - **Open decision.**
- **Trademark enforcement** through Google Play's IP complaint process for clones.
- **Accept some piracy.** People who pirate keyboards rarely would have paid.

---

## 9. Daily-driver quality bar

The keyboard should be good enough to be a user's default. These are what get a keyboard uninstalled when missing, and all of them are **free**:

| Area | Requirements |
|---|---|
| **Reliability** | No crashes (an input method crash leaves the user without a keyboard). Opt-in local crash log the user can export, no internet needed. |
| **Speed** | Low input latency, fast cold start, haptics that feel right. |
| **English** | Good suggestions, autocorrect that one backspace undoes, personal dictionary (LatinIME engine). |
| **Lock screen** | Works before first unlock after a reboot (Direct Boot, `directBootAware`). |
| **Privacy** | Never learns from password fields or incognito mode; option to clear learned words. |
| **Autofill** | Password-manager and autofill suggestions inline in the suggestion strip (Android 11+). |
| **Editing** | Clipboard history, space-bar cursor control, swipe to delete, undo. |
| **Swipe typing** | FlorisBoard's version is basic; tune it, or replace the decoder later. |
| **Mixed languages** | Mixed English/Chinese candidates and fast language switching (section 3.5). |
| **Accessibility** | TalkBack support, large text, high-contrast themes. |
| **Layout** | One-handed and floating modes, adjustable height, landscape, split layout for tablets and foldables. |
| **System spell checker** | Optional: underline misspellings in other apps, reusing the LatinIME engine. |

---

## 10. Development workflow and viewing live changes

Development happens in two places:

- **Cloud sessions (Claude Code on the web / app):** edits code, builds, runs unit and screenshot tests, and pushes to a branch. The cloud container cannot run an Android emulator (no hardware virtualisation) and cannot reach the developer's phone or computer.
- **The developer's computer:** Android Studio, a phone or emulator, and live viewing of changes.

### Recommended loop: Claude Code on your own computer

Run Claude Code in a terminal or the Claude Desktop app **on the same computer as Android Studio**, in the local clone. Edits land directly in the project Android Studio has open, so:

- **Run** (or Apply Changes) installs the keyboard on a USB/Wi-Fi-connected phone or emulator in seconds.
- **Compose Live Edit** updates many Compose UI changes on the running device without reinstalling (best for the settings app; the keyboard window may need Apply Changes or a reinstall).
- **Compose Previews** show keyboard pieces (keys, candidate bar, themes) inside Android Studio without a device.
- `claude remote-control` (or the Desktop app) lets the same local session be driven from the Claude app on a phone.

### Alternative loop: cloud session + local pull

1. The cloud session pushes to `claude/...` branches.
2. Locally: `git pull`, then Run from Android Studio.
3. Optional: a **GitHub Actions** workflow builds a debug APK on every push and attaches it as an artifact, to install on a phone without Android Studio.

### Screenshot tests

Screenshot tests (Roborazzi or Paparazzi) render keyboard and settings composables to PNG on the JVM, with no emulator. Cloud sessions use them to show visual changes and catch regressions; they also run in CI.

---

## 11. Roadmap

| Phase | Scope |
|---|---|
| **0. Fork and refine (step 1)** | Fork FlorisBoard into this repo. Build it, confirm it works on Android 16. Rebrand (name, package ID, icon, strings). Strip unwanted features and settings. Fix and polish layout, key feel, theming and the settings app, with **no new features** (section 0.2). Write `docs/keyboard-behaviour-spec.md`. Set up CI (debug APK artifact), R8, screenshot tests, and `LICENSES/` with the attribution screen. Audit FlorisBoard's bundled assets. |
| **1. English foundation** | Connect the LatinIME native engine: suggestions, autocorrect with backspace-undo, personal dictionary. |
| **2. Chinese engine** | Build librime for Android (NDK). JNI front end into the candidate bar. Pinyin schema first, then jyutping, Yale and zhuyin. Mixed English candidates, fast language switching. Trad/Simp through OpenCC. |
| **3. Learning layer** | Dictionary pipeline (CC-CEDICT, CC-Canto, frequencies) producing Rime dictionaries + SQLite. Readings and glosses on candidates, dual readings, Learning mode toggle, dictionary card, English → Chinese search, study list. |
| **4. Daily-driver polish + beta** | Work through the daily-driver bar (section 9). Emoji panel with CLDR multilingual search, bundled fonts. Billing, Pro gating, onboarding. Play Console closed testing, data safety form, privacy policy. |
| **5. Handwriting** | Dynamic feature module. |
| **6. Voice** | sherpa-onnx + SenseVoice. |
| **7. Cantonese → Standard Written Chinese** | Rule-based first, then the model. Extra emoji styles, font catalogue, font import and sticker packs fit around here. |
| **8. Korean** | The easiest addition. Algorithmic Hangul composition, 2-set layout, optional hanja candidates with readings and glosses. |
| **9. Japanese** | The hardest. Kana-kanji conversion needs a real converter; embedding Mozc is the realistic route, but it is large. Romaji and flick layouts, JMdict glosses. |

---

## 12. Main risks

1. **Typing latency with Compose.** Measure from day one, starting with FlorisBoard as forked. A single-Canvas key surface is the fallback if rendering is the bottleneck.
2. **Data, font and emoji licensing.** Track every asset in `LICENSES/data.md` and credit each one in the app. Never ship proprietary emoji or fonts.
3. **App size.** Compress dictionaries, subset fonts, and deliver models, fonts and emoji sets on demand.
4. **IME process memory.** Load models lazily and unload them aggressively.
5. **Quality of Cantonese data and models.** Budget time for native speakers to evaluate it.
6. **Play policy for keyboards.** Clear privacy disclosures are required, and having no network access makes this much easier. Keep user imports local to avoid user-generated content obligations.
7. **Untrusted user files.** Use only Android's built-in font loading, with size and type limits and a fallback font.
8. **License contamination.** A single GPL file, or code copied from OpenBoard, HeliBoard or Trime, would undermine closed source. Follow the dependency policy (0.4) and check licenses on every new dependency and asset.
9. **Piracy.** Make cracking tedious with obfuscation, scattered entitlement checks and Play Integrity, then accept the rest (section 8).
10. **FlorisBoard is still beta.** Its internals may fight our changes. Step 1 doubles as an evaluation: if the fork can't be brought to the desired quality in reasonable time, revisit the base before adding engines.
11. **Diverging from upstream.** We fork once and diverge, so upstream fixes don't arrive automatically. Watch upstream for security and Android-compatibility fixes and port them by hand.
12. **librime build complexity.** Building it for Android pulls in Boost and other C++ dependencies. Expect a few days of build work; Trime and fcitx5-android show it is achievable (reference their build approach only, not their code).
