# Lychee's readings and meanings

`build_readings.py` builds the SQLite database shown under every Chinese
candidate and on the long-press card: Cantonese readings (Jyutping, shown as
Yale), Mandarin readings (pinyin) and every English sense from each source.
Gradle runs it (task `lycheeData`) into the asset `lychee/readings.db`; it
ends with spot checks (食飯, 吃饭, 行, 银行…) and fails the build if one
does not hold.

| Source | What for | Licence |
|---|---|---|
| [TypeDuck](https://github.com/TypeDuck-HK/schema) (`../rime/typeduck`) | Cantonese words, Jyutping, frequencies, English, part of speech | CC BY 4.0 |
| `sources/cedict_1_0_ts_utf-8_mdbg.txt.gz` ([CC-CEDICT](https://www.mdbg.net/chinese/dictionary?page=cc-cedict)) | Pinyin, English, Simplified forms | CC BY-SA 4.0 |
| `sources/cccanto-170202.zip` ([CC-Canto](https://cantonese.org/), © Pleco Inc.) | Cantonese words and English | CC BY-SA 3.0 |
| `sources/cccedict-canto-readings-150923.zip` (cantonese.org, © Pleco Inc.) | Jyutping for CC-CEDICT words | CC BY-SA 3.0 |
| `sources/opencc-TS*.txt.gz` ([OpenCC](https://github.com/BYVoid/OpenCC)) | Traditional → Simplified | Apache-2.0 (`sources/opencc-LICENSE`) |
| `luna_pinyin.dict.yaml` (in `../rime/typeduck`, from [rime-luna-pinyin](https://github.com/rime/rime-luna-pinyin)) | Which pinyin of a character is most common | LGPL-3.0 |

Because of the CC BY-SA sources, the generated database is CC BY-SA 4.0.
