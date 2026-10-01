# Lychee's dictionaries

## Readings and meanings (`build_dict.py`)

Gradle runs `build_dict.py` and bundles the result as the asset
`lychee/dict.db` (SQLite): for every word, its Jyutping, pinyin and English.
The app converts Jyutping to Yale and numbered pinyin to tone marks
(`app/src/main/java/app/lychee/dict/Romanization.kt`). The script ends with
spot checks (食飯, 吃饭, 香港…) and fails the build if they don't hold.

| Source | What for | Licence |
|---|---|---|
| [`typeduck/`](https://github.com/TypeDuck-HK/schema) (submodule) | Cantonese words, Jyutping, frequencies, English; also the Rime dictionary | CC BY 4.0 |
| `sources/cedict_1_0_ts_utf-8_mdbg.txt.gz` ([CC-CEDICT](https://www.mdbg.net/chinese/dictionary?page=cc-cedict), 2026-09-30) | Pinyin, English, Simplified forms | CC BY-SA 4.0 |
| `sources/cccanto-170202.zip` ([CC-Canto](https://cantonese.org/), © Pleco Inc.) | Cantonese words and English | CC BY-SA 3.0 |
| `sources/cccedict-canto-readings-150923.zip` (cantonese.org, © Pleco Inc.) | Jyutping for CC-CEDICT words | CC BY-SA 3.0 |
| `sources/opencc-TS*.txt.gz` ([OpenCC](https://github.com/BYVoid/OpenCC)) | Traditional → Simplified | Apache-2.0 (`opencc-LICENSE`) |
| [`rime-luna-pinyin/`](https://github.com/rime/rime-luna-pinyin) (submodule) | Which pinyin of a character is most common | LGPL-3.0 |

Because of the CC BY-SA sources, the generated `dict.db` is CC BY-SA 4.0.

## English spelling variants (`english/`)

`variants.tsv` lists US → UK/Canadian/Australian spellings (`replace` = 1
when the US spelling is wrong there, 0 when both are words, like check /
cheque). `build_spell.py` turns fcitx's US spell dictionary into
`en_GB/en_CA/en_AU_dict.fscd` during the CMake configure step.
`make_variants.py` regenerates the list from a built
[ESDB/SCOWL](https://github.com/en-wl/wordlist) checkout.

> Copyright 2000-2026 by Kevin Atkinson
>
> Permission to use, copy, modify, distribute, and sell any part of the
> English Speller Database (ESDB, previously known as SCOWLv2), or word lists
> created from it, is hereby granted without fee, provided that the above
> copyright notice appears in all copies and that both the above copyright
> notice and this notice appear in supporting documentation. Kevin Atkinson
> makes no representations about the suitability of this database for any
> purpose. It is provided "as is" without express or implied warranty.
