#!/usr/bin/env python3
"""Build Lychee's readings-and-meanings database (SQLite).

For every Chinese word the keyboard can offer: its Cantonese readings
(Jyutping), its Mandarin readings (pinyin) and every English sense from each
source, so the long-press card can show the full meaning. Readings are
numbered ("sik6 faan6", "shi2 fan4"), most common first; the app shows them
as Yale and tone-marked pinyin.

Sources (see README.md for licences):
  ../rime/typeduck/jyut6ping3.dict.yaml        Cantonese words, Jyutping, weights
  ../rime/typeduck/jyut6ping3_scolar.dict.yaml TypeDuck dictionary (English, part of speech)
  ../rime/typeduck/luna_pinyin.dict.yaml       how common each pinyin of a character is
  sources/cedict_*.txt.gz                      CC-CEDICT (pinyin, English, Simplified forms)
  sources/cccanto-*.zip                        CC-Canto (Cantonese words, English)
  sources/cccedict-canto-readings-*.zip        Jyutping for CC-CEDICT words
  sources/opencc-TS*.txt.gz                    OpenCC Traditional -> Simplified

Tables:
  entry(word, jyutping, pinyin, typeduck, cedict, canto)
      typeduck: one sense per line, "part of speech<TAB>English"
      cedict, canto: one sense per line
  alias(word, target)  Simplified or variant spelling -> entry word

usage: build_readings.py <output.db>
"""

import csv
import gzip
import io
import os
import re
import sqlite3
import sys
import zipfile
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCES = os.path.join(HERE, "sources")
TYPEDUCK = os.path.join(HERE, "..", "rime", "typeduck")
MAX_READINGS = 6


def is_han(ch):
    cp = ord(ch)
    return (0x3400 <= cp <= 0x9FFF or 0xF900 <= cp <= 0xFAFF
            or 0x20000 <= cp <= 0x3134F or cp == 0x3007)


def has_han(word):
    return any(is_han(c) for c in word)


def unique(seq):
    seen, out = set(), []
    for x in seq:
        if x and x not in seen:
            seen.add(x)
            out.append(x)
    return out


def source(pattern):
    names = sorted(f for f in os.listdir(SOURCES) if re.fullmatch(pattern, f))
    if len(names) != 1:
        sys.exit(f"expected one source matching {pattern}, found {names}")
    return os.path.join(SOURCES, names[0])


def zip_lines(path):
    with zipfile.ZipFile(path) as z:
        (name,) = [n for n in z.namelist() if n.endswith(".txt")]
        with z.open(name) as f:
            yield from io.TextIOWrapper(f, encoding="utf-8")


def rime_body(path):
    """Entry lines of a Rime dictionary (after the YAML header)."""
    with open(path, encoding="utf-8") as f:
        started = False
        for line in f:
            line = line.rstrip("\n")
            if not started:
                started = line == "..."
            elif line and not line.startswith("#"):
                yield line


def weight(text):
    text = text.strip()
    try:
        return float(text[:-1]) if text.endswith("%") else float(text)
    except ValueError:
        return None


# --- readings ----------------------------------------------------------------

def norm_pinyin(text):
    """CC-CEDICT pinyin -> lower case, ü, numbered tones (5 = neutral)."""
    out = []
    for syl in text.split():
        syl = syl.replace("u:", "ü").replace("v", "ü")
        if re.fullmatch(r"[A-Za-zü]+", syl):
            syl += "5"
        if not re.fullmatch(r"[A-Za-zü]+[1-5]", syl):
            return None  # punctuation, "xx" placeholders: not a reading
        out.append(syl.lower())
    return " ".join(out) or None


JYUTPING = re.compile(r"(?:[bpmfdtnlgkhwzcsj]|ng|gw|kw)?(?:[aeiou]+|yu)?(?:[iuptkmn]|ng)?[1-6]")


def norm_jyutping(text):
    sylls = text.strip().lower().split()
    if sylls and all(JYUTPING.fullmatch(s) for s in sylls):
        return " ".join(sylls)
    return None


def split_jyutping(code):
    """TypeDuck writes syllables together: 'aa1pin3' -> 'aa1 pin3'."""
    return " ".join(re.findall(r"[a-z]+[1-6]", code))


# --- English -----------------------------------------------------------------

def clean_sense(sense):
    """'see 食飯|食饭[shi2 fan4]' -> 'see 食飯'; CL:個|个[ge4] -> 'CL: 個'."""
    sense = re.sub(r"([^\s|\[\]]+)\|[^\s\[\]]+\[[^\]]*\]", r"\1", sense)  # trad|simp[pinyin]
    sense = re.sub(r"([㐀-鿿\U00020000-\U0003134f]+)\[[^\]]*\]", r"\1", sense)  # word[pinyin]
    sense = re.sub(r"^CL:", "CL: ", sense)
    return sense.strip(" ;")


# --- OpenCC Traditional -> Simplified ----------------------------------------

class TraditionalToSimplified:
    def __init__(self):
        self.chars, self.phrases = {}, {}
        for name, table in (("TSCharacters", self.chars), ("TSPhrases", self.phrases)):
            with gzip.open(os.path.join(SOURCES, f"opencc-{name}.txt.gz"), "rt", encoding="utf-8") as f:
                for line in f:
                    parts = line.rstrip("\n").split("\t")
                    if len(parts) == 2:
                        table[parts[0]] = parts[1].split()[0]
        self.longest = max(map(len, self.phrases))

    def __call__(self, text):
        out, i = [], 0
        while i < len(text):
            for n in range(min(self.longest, len(text) - i), 1, -1):
                hit = self.phrases.get(text[i:i + n])
                if hit:
                    out.append(hit)
                    i += n
                    break
            else:
                out.append(self.chars.get(text[i], text[i]))
                i += 1
        return "".join(out)


# --- sources -----------------------------------------------------------------

CEDICT_LINE = re.compile(r"^(\S+) (\S+) \[([^\]]*)\] /(.*)/\s*$")
CANTO_LINE = re.compile(r"^(\S+) (\S+) \[([^\]]*)\] \{([^}]*)\}(?: /(.*)/)?")


def load_cedict():
    for line in gzip.open(source(r"cedict_.*\.txt\.gz"), "rt", encoding="utf-8"):
        m = CEDICT_LINE.match(line)
        if not m or line.startswith("#"):
            continue
        trad, simp, pinyin, defs = m.groups()
        pinyin = norm_pinyin(pinyin)
        if pinyin and has_han(trad):
            yield trad, simp, pinyin, [clean_sense(s) for s in defs.split("/") if s.strip()]


def load_canto(pattern):
    for line in zip_lines(source(pattern)):
        m = CANTO_LINE.match(line)
        if not m or line.startswith("#"):
            continue
        trad, simp, _, jp, defs = m.groups()
        jp = norm_jyutping(jp)
        if jp and has_han(trad):
            yield trad, simp, jp, [clean_sense(s) for s in (defs or "").split("/") if s.strip()]


def load_typeduck():
    readings = defaultdict(list)  # word -> [(weight, jyutping)]
    for line in rime_body(os.path.join(TYPEDUCK, "jyut6ping3.dict.yaml")):
        parts = line.split("\t")
        if len(parts) < 2 or not has_han(parts[0]):
            continue
        jp = norm_jyutping(parts[1])
        if jp:
            w = weight(parts[2]) if len(parts) > 2 else None
            readings[parts[0]].append((100.0 if w is None else w, jp))

    senses = defaultdict(list)   # word -> ["pos\tEnglish"]
    scolar_readings = defaultdict(list)
    canonical = {}               # variant spelling -> usual spelling
    for line in rime_body(os.path.join(TYPEDUCK, "jyut6ping3_scolar.dict.yaml")):
        code, sep, word = line.rpartition("\t")
        if not sep or not has_han(word):
            continue
        cols = next(csv.reader([code]))  # meanings may be quoted ("a, b")
        if len(cols) < 17:
            continue
        jp = norm_jyutping(split_jyutping(cols[0]))
        if jp:
            scolar_readings[word].append(jp)
        if cols[3] and cols[3] != word:
            canonical.setdefault(word, cols[3])
        english = cols[16].strip()
        if english:
            pos = cols[10].replace("|", ", ")
            senses[word].append(f"{pos}\t{english}")
    return readings, senses, scolar_readings, canonical


def load_luna():
    """character -> {toneless pinyin: weight} (how common each reading is)."""
    weights = defaultdict(dict)
    for line in rime_body(os.path.join(TYPEDUCK, "luna_pinyin.dict.yaml")):
        parts = line.split("\t")
        if len(parts) >= 2 and len(parts[0]) == 1 and " " not in parts[1]:
            w = weight(parts[2]) if len(parts) > 2 else None
            weights[parts[0]][parts[1]] = 100.0 if w is None else w
    return weights


# --- merge -------------------------------------------------------------------

def build(out_path):
    t2s = TraditionalToSimplified()
    td_readings, td_senses, td_scolar_readings, td_canonical = load_typeduck()
    luna = load_luna()

    pinyin = defaultdict(list)
    cedict = defaultdict(list)
    canto = defaultdict(list)
    jyut_more = defaultdict(list)
    simp_of = {}
    for trad, simp, py, senses in load_cedict():
        pinyin[trad].append(py)
        cedict[trad].extend(senses)
        simp_of.setdefault(trad, simp)
    for trad, simp, jp, senses in load_canto(r"cccanto-.*\.zip"):
        jyut_more[trad].append(jp)
        canto[trad].extend(senses)
        simp_of.setdefault(trad, simp)
    for trad, simp, jp, _ in load_canto(r"cccedict-canto-readings-.*\.zip"):
        jyut_more[trad].append(jp)
        simp_of.setdefault(trad, simp)

    # Characters: most common Mandarin reading first (luna_pinyin's weights).
    for ch in [w for w in pinyin if len(w) == 1]:
        w = luna.get(ch, {})
        pinyin[ch] = sorted(unique(pinyin[ch]), key=lambda r: -w.get(r.rstrip("12345").replace("ü", "v"), 0.0))
    char_pinyin = {ch: rs[0] for ch, rs in pinyin.items() if len(ch) == 1}
    for trad, simp in simp_of.items():
        if len(trad) == 1 and trad in char_pinyin:
            char_pinyin.setdefault(simp, char_pinyin[trad])
    char_jyut = {}
    for word, rs in td_readings.items():
        rs.sort(key=lambda r: -r[0])
        if len(word) == 1:
            char_jyut[word] = rs[0][1]

    def compose(word, table):
        parts = [table.get(ch) for ch in word]
        return " ".join(parts) if word and all(parts) else None

    rows = []
    for word in unique([*td_readings, *td_senses, *pinyin, *jyut_more]):
        if not has_han(word):
            continue
        # Cantonese: TypeDuck by weight (0% readings only when nothing else), then
        # CC-Canto and CC-CEDICT readings, then character by character.
        td = td_readings.get(word, [])
        jps = [jp for w, jp in td if w > 0] or [jp for _, jp in td]
        jps = unique(jps + td_scolar_readings.get(word, []) + jyut_more.get(word, []))
        if not jps:
            jps = unique([compose(word, char_jyut) or compose(td_canonical.get(word, ""), char_jyut)])
        pys = unique(pinyin.get(word, []) or pinyin.get(td_canonical.get(word, ""), []))
        if not pys:
            pys = unique([compose(word, char_pinyin) or compose(t2s(word), char_pinyin)])
        tds = unique(td_senses.get(word, []) or td_senses.get(td_canonical.get(word, ""), []))
        ced = unique(cedict.get(word, []))
        can = unique(canto.get(word, []))
        if not (jps or pys or tds or ced or can):
            continue
        rows.append((word, "/".join(jps[:MAX_READINGS]), "/".join(pys[:MAX_READINGS]),
                     "\n".join(tds), "\n".join(ced), "\n".join(can)))

    # Simplified and variant spellings point at the most common Traditional
    # word, e.g. 吃饭 -> 吃飯 rather than 喫飯.
    def score(word):
        td = td_readings.get(word)
        return (max(w for w, _ in td) if td else -1.0, word in pinyin, len(cedict.get(word, [])))

    words = {r[0] for r in rows}
    choices = defaultdict(set)
    for word in words:
        for simp in {simp_of.get(word), t2s(word)}:
            if simp and simp != word and simp not in words:
                choices[simp].add(word)
    aliases = {simp: max(sorted(ws), key=score) for simp, ws in choices.items()}

    if os.path.exists(out_path):
        os.remove(out_path)
    db = sqlite3.connect(out_path)
    db.executescript("""
        PRAGMA page_size = 4096;
        CREATE TABLE entry (word TEXT PRIMARY KEY, jyutping TEXT NOT NULL, pinyin TEXT NOT NULL,
                            typeduck TEXT NOT NULL, cedict TEXT NOT NULL, canto TEXT NOT NULL) WITHOUT ROWID;
        CREATE TABLE alias (word TEXT PRIMARY KEY, target TEXT NOT NULL) WITHOUT ROWID;
        CREATE TABLE android_metadata (locale TEXT);
        INSERT INTO android_metadata VALUES ('en_US');
    """)
    db.executemany("INSERT INTO entry VALUES (?, ?, ?, ?, ?, ?)", sorted(rows))
    db.executemany("INSERT INTO alias VALUES (?, ?)", sorted(aliases.items()))
    db.commit()
    db.execute("VACUUM")
    db.close()
    return len(rows), len(aliases)


def lookup(db, word):
    row = db.execute("SELECT * FROM entry WHERE word = ?", (word,)).fetchone()
    if row is None:
        target = db.execute("SELECT target FROM alias WHERE word = ?", (word,)).fetchone()
        if target:
            row = db.execute("SELECT * FROM entry WHERE word = ?", target).fetchone()
    return row


def check(out_path):
    """Spot checks the app relies on; the build fails if one does not hold."""
    db = sqlite3.connect(out_path)
    expect = {  # word: (a Jyutping, a pinyin, text in some English sense)
        "食飯": ("sik6 faan6", "shi2 fan4", "meal"),
        "吃饭": ("hek3 faan6", "chi1 fan4", "eat"),
        "你好": ("nei5 hou2", "ni3 hao3", "hello"),
        "行": ("hang4", "xing2", "walk"),
        "唔該": ("m4 goi1", "", "thank"),
        "香港": ("hoeng1 gong2", "xiang1 gang3", "Hong Kong"),
        "电脑": ("din6 nou5", "dian4 nao3", "computer"),
        "银行": ("ngan4 hong4", "yin2 hang2", "bank"),
    }
    bad = []
    for word, (jp, py, en) in expect.items():
        row = lookup(db, word)
        if row is None:
            bad.append(f"{word}: missing")
            continue
        _, jps, pys, tds, ced, can = row
        if jp and jp not in jps.split("/"):
            bad.append(f"{word}: Jyutping {jps!r} lacks {jp!r}")
        if py and py not in pys.split("/"):
            bad.append(f"{word}: pinyin {pys!r} lacks {py!r}")
        if en.lower() not in (tds + ced + can).lower():
            bad.append(f"{word}: English lacks {en!r}")
    if bad:
        sys.exit("readings check failed:\n  " + "\n  ".join(bad))


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    out = sys.argv[1]
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    entries, aliases = build(out)
    check(out)
    print(f"readings: {entries} words, {aliases} other spellings, {os.path.getsize(out) // 1024} KiB")


if __name__ == "__main__":
    main()
