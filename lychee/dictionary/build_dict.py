#!/usr/bin/env python3
"""Build Lychee's lookup database: Cantonese reading, Mandarin pinyin and
English meaning for every word the keyboard can offer.

Sources (all in this folder, see README.md for licences):
  typeduck/jyut6ping3.dict.yaml        Cantonese words + Jyutping + weights
  typeduck/jyut6ping3_scolar.dict.yaml TypeDuck dictionary (English meanings)
  sources/cedict_*.txt.gz              CC-CEDICT (pinyin + English)
  sources/cccanto-*.zip                CC-Canto (Cantonese words + English)
  sources/cccedict-canto-readings-*.zip Jyutping for CC-CEDICT words
  sources/opencc-TS*.txt.gz            OpenCC Traditional -> Simplified
  luna_pinyin.dict.yaml (--luna)       Mandarin reading frequencies

Output: one SQLite file with
  entry(word PRIMARY KEY, jyutping, pinyin, english)
  alias(word PRIMARY KEY, target)   simplified/variant form -> entry word
Readings are numbered ("sik6 faan6", "chi1 fan4"); several readings of one
word are separated by "/", most common first. The app converts them to Yale
and tone-marked pinyin for display.
"""

import argparse
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

MAX_READINGS = 4
MAX_ENGLISH = 200


def is_han(ch):
    cp = ord(ch)
    return (0x3400 <= cp <= 0x9FFF or 0xF900 <= cp <= 0xFAFF or
            0x20000 <= cp <= 0x3134F or cp == 0x3007)


def has_han(word):
    return any(is_han(c) for c in word)


def single_source(pattern):
    names = sorted(f for f in os.listdir(SOURCES) if re.fullmatch(pattern, f))
    if len(names) != 1:
        sys.exit(f"expected exactly one source matching {pattern}, found {names}")
    return os.path.join(SOURCES, names[0])


def zip_lines(path):
    with zipfile.ZipFile(path) as z:
        (name,) = [n for n in z.namelist() if n.endswith(".txt")]
        with z.open(name) as f:
            yield from io.TextIOWrapper(f, encoding="utf-8")


def rime_body(path):
    """Lines after the YAML header ('...') of a Rime dict."""
    with open(path, encoding="utf-8") as f:
        started = False
        for line in f:
            line = line.rstrip("\n")
            if not started:
                started = line == "..."
                continue
            if line and not line.startswith("#"):
                yield line


def parse_weight(text):
    text = text.strip()
    if not text:
        return None
    try:
        return float(text[:-1]) if text.endswith("%") else float(text)
    except ValueError:
        return None


# --- pinyin / jyutping normalisation ---------------------------------------

def norm_pinyin(pinyin):
    """CC-CEDICT pinyin -> lower case, ü, numbered tones, 5 for neutral."""
    out = []
    for syl in pinyin.split():
        syl = syl.replace("u:", "ü").replace("v", "ü")
        if not re.fullmatch(r"[A-Za-zü]+[1-5]", syl):
            # letters, "xx5" placeholders, punctuation: not a reading
            if re.fullmatch(r"[A-Za-zü]+", syl):
                syl += "5"
            else:
                return None
        out.append(syl.lower())
    return " ".join(out) if out else None


JYUTPING_SYL = re.compile(r"(?:[bpmfdtnlgkhwzcsj]|ng|gw|kw)?(?:[aeiou]+|yu)?(?:[iuptkmn]|ng)?[1-6]")


def norm_jyutping(jp):
    jp = jp.strip().lower()
    sylls = jp.split()
    if not sylls or not all(JYUTPING_SYL.fullmatch(s) for s in sylls):
        return None
    return " ".join(sylls)


def split_jyutping(code):
    """'aa1pin3' -> 'aa1 pin3' (TypeDuck writes syllables without spaces)."""
    return " ".join(re.findall(r"[a-z]+[1-6]", code))


# --- English clean-up --------------------------------------------------------

SKIP_SENSE = re.compile(
    r"^(CL:|Taiwan pr\.|also pr\.|also written|old variant of|"
    r"variant of|archaic variant of|see [^ ]+$|see also)", re.I)


def cedict_senses(defs):
    senses = []
    for s in defs:
        s = re.sub(r"\[[^\]]*\]", "", s)           # pinyin in references
        s = re.sub(r"[㐀-鿿\U00020000-\U0003134f|]+", "", s) if s.startswith(("variant of", "see ")) else s
        s = s.strip(" ;")
        if s and not SKIP_SENSE.match(s):
            senses.append(s)
    if not senses:  # nothing but "variant of X": keep the first as is
        senses = [re.sub(r"\[[^\]]*\]", "", defs[0]).strip()] if defs else []
    return senses


def join_english(senses):
    seen, out = set(), []
    for s in senses:
        key = s.lower()
        if s and key not in seen:
            seen.add(key)
            out.append(s)
    text = "; ".join(out)
    if len(text) > MAX_ENGLISH:
        cut = text.rfind("; ", 0, MAX_ENGLISH)
        text = text[:cut] if cut > 20 else text[:MAX_ENGLISH].rstrip() + "…"
    return text


# --- OpenCC (Traditional -> Simplified) -------------------------------------

class OpenCC:
    def __init__(self):
        self.chars = {}
        self.phrases = {}
        for name, table in (("TSCharacters", self.chars), ("TSPhrases", self.phrases)):
            with gzip.open(os.path.join(SOURCES, f"opencc-{name}.txt.gz"), "rt", encoding="utf-8") as f:
                for line in f:
                    parts = line.rstrip("\n").split("\t")
                    if len(parts) == 2:
                        table[parts[0]] = parts[1].split()[0]
        self.longest = max(map(len, self.phrases))

    def t2s(self, text):
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
    entries = []  # (trad, simp, pinyin, senses)
    with gzip.open(single_source(r"cedict_.*\.txt\.gz"), "rt", encoding="utf-8") as f:
        for line in f:
            if line.startswith("#"):
                continue
            m = CEDICT_LINE.match(line)
            if not m:
                continue
            trad, simp, pinyin, defs = m.groups()
            pinyin = norm_pinyin(pinyin)
            if pinyin and has_han(trad):
                entries.append((trad, simp, pinyin, cedict_senses(defs.split("/"))))
    return entries


def load_canto(pattern):
    entries = []  # (trad, simp, jyutping, senses)
    for line in zip_lines(single_source(pattern)):
        if line.startswith("#"):
            continue
        m = CANTO_LINE.match(line)
        if not m:
            continue
        trad, simp, _pinyin, jp, defs = m.groups()
        jp = norm_jyutping(jp)
        if jp and has_han(trad):
            senses = [s.strip() for s in defs.split("/") if s.strip()] if defs else []
            entries.append((trad, simp, jp, senses))
    return entries


def load_typeduck(folder):
    readings = defaultdict(list)  # word -> [(weight, jyutping)]
    for line in rime_body(os.path.join(folder, "jyut6ping3.dict.yaml")):
        parts = line.split("\t")
        if len(parts) < 2 or not has_han(parts[0]):
            continue
        jp = norm_jyutping(parts[1])
        if not jp:
            continue
        weight = parse_weight(parts[2]) if len(parts) > 2 else None
        readings[parts[0]].append((100.0 if weight is None else weight, jp))

    english = defaultdict(list)       # word -> [english]
    scolar_readings = defaultdict(list)
    canonical = {}                    # variant word -> canonical word
    for line in rime_body(os.path.join(folder, "jyut6ping3_scolar.dict.yaml")):
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
        if cols[16]:
            english[word].append(cols[16].strip())
    return readings, english, scolar_readings, canonical


def load_luna(path):
    """char -> {toneless pinyin: weight}."""
    weights = defaultdict(dict)
    for line in rime_body(path):
        parts = line.split("\t")
        if len(parts) >= 2 and len(parts[0]) == 1 and " " not in parts[1]:
            weights[parts[0]][parts[1]] = parse_weight(parts[2]) if len(parts) > 2 else 100.0
    return weights


# --- merge -------------------------------------------------------------------

def unique(seq):
    seen, out = set(), []
    for x in seq:
        if x not in seen:
            seen.add(x)
            out.append(x)
    return out


def build(typeduck, luna_path, out_path):
    occ = OpenCC()
    cedict = load_cedict()
    cccanto = load_canto(r"cccanto-.*\.zip")
    canto_readings = load_canto(r"cccedict-canto-readings-.*\.zip")
    td_readings, td_english, td_scolar_readings, td_canonical = load_typeduck(typeduck)
    luna = load_luna(luna_path)

    pinyin = defaultdict(list)     # trad -> [pinyin]
    english_cedict = defaultdict(list)
    jyut_extra = defaultdict(list)  # trad -> [jyutping] from CC-Canto / readings
    english_canto = defaultdict(list)
    simp_of = {}                    # trad -> simp from the dictionaries

    for trad, simp, py, senses in cedict:
        pinyin[trad].append(py)
        english_cedict[trad].extend(senses)
        simp_of.setdefault(trad, simp)
    for trad, simp, jp, senses in cccanto:
        jyut_extra[trad].append(jp)
        english_canto[trad].extend(senses)
        simp_of.setdefault(trad, simp)
    for trad, simp, jp, _ in canto_readings:
        jyut_extra[trad].append(jp)
        simp_of.setdefault(trad, simp)

    # Single characters: most common reading first.
    def rank_pinyin(ch, readings):
        w = luna.get(ch, {})
        return sorted(unique(readings), key=lambda r: -w.get(r.rstrip("12345").replace("ü", "v"), 0.0))

    char_pinyin = {}
    for ch in [w for w in pinyin if len(w) == 1]:
        pinyin[ch] = rank_pinyin(ch, pinyin[ch])
        char_pinyin[ch] = pinyin[ch][0]
    for trad, simp in simp_of.items():  # simplified characters too
        if len(trad) == 1 and trad in char_pinyin:
            char_pinyin.setdefault(simp, char_pinyin[trad])
    char_jyut = {}
    for word, rs in td_readings.items():
        rs.sort(key=lambda r: -r[0])
        if len(word) == 1:
            char_jyut[word] = rs[0][1]

    def compose(word, table):
        parts = [table.get(ch) for ch in word]
        return " ".join(parts) if all(parts) else None

    words = unique([*td_readings, *td_english, *pinyin, *jyut_extra])
    rows = []
    for word in words:
        if not has_han(word):
            continue
        # Cantonese: TypeDuck by weight (drop 0% readings when better exist),
        # then CC-Canto / CC-CEDICT readings, then character by character.
        td = td_readings.get(word, [])
        jps = [jp for w, jp in td if w > 0] or [jp for _, jp in td]
        jps = unique(jps + td_scolar_readings.get(word, [])[:1] + jyut_extra.get(word, []))
        if not jps:
            c = compose(word, char_jyut) or compose(td_canonical.get(word, ""), char_jyut)
            jps = [c] if c else []
        pys = unique(pinyin.get(word, []))
        if not pys:
            canon = td_canonical.get(word)
            pys = unique(pinyin.get(canon, [])) if canon else []
        if not pys:
            c = compose(word, char_pinyin) or compose(occ.t2s(word), char_pinyin)
            pys = [c] if c else []
        eng = td_english.get(word) or []
        if not eng and word in td_canonical:
            eng = td_english.get(td_canonical[word], [])
        eng = eng or english_cedict.get(word) or english_canto.get(word) or []
        if not (jps or pys or eng):
            continue
        rows.append((word, "/".join(jps[:MAX_READINGS]), "/".join(pys[:MAX_READINGS]), join_english(eng)))

    # Simplified (and variant) spellings point at the most common
    # Traditional word, e.g. 吃饭 -> 吃飯 rather than 喫飯.
    def score(word):
        td = td_readings.get(word)
        return (max(w for w, _ in td) if td else -1.0, word in pinyin, len(english_cedict.get(word, [])))

    entry_words = {r[0] for r in rows}
    alias_choices = defaultdict(set)
    for word in entry_words:
        for simp in {simp_of.get(word), occ.t2s(word)}:
            if simp and simp != word and simp not in entry_words:
                alias_choices[simp].add(word)
    aliases = {simp: max(sorted(words), key=score) for simp, words in alias_choices.items()}

    if os.path.exists(out_path):
        os.remove(out_path)
    db = sqlite3.connect(out_path)
    db.executescript("""
        PRAGMA page_size = 4096;
        CREATE TABLE entry (word TEXT PRIMARY KEY, jyutping TEXT NOT NULL,
                            pinyin TEXT NOT NULL, english TEXT NOT NULL) WITHOUT ROWID;
        CREATE TABLE alias (word TEXT PRIMARY KEY, target TEXT NOT NULL) WITHOUT ROWID;
    """)
    db.executemany("INSERT INTO entry VALUES (?, ?, ?, ?)", sorted(rows))
    db.executemany("INSERT INTO alias VALUES (?, ?)", sorted(aliases.items()))
    db.commit()
    db.execute("VACUUM")
    db.close()
    return len(rows), len(aliases)


def lookup(db, word):
    row = db.execute("SELECT * FROM entry WHERE word = ?", (word,)).fetchone()
    if row is None:
        alias = db.execute("SELECT target FROM alias WHERE word = ?", (word,)).fetchone()
        if alias:
            row = db.execute("SELECT * FROM entry WHERE word = ?", alias).fetchone()
    return row


def check(out_path):
    """Spot checks the app relies on; fails the build if the data is off."""
    db = sqlite3.connect(out_path)
    expect = {
        "食飯": ("sik6 faan6", "shi2 fan4", "meal"),
        "吃饭": ("hek3 faan6", "chi1 fan4", "eat"),
        "你好": ("nei5 hou2", "ni3 hao3", "hello"),
        "行": ("hang4", "xing2", ""),
        "唔該": ("m4 goi1", "", "thank"),
        "香港": ("hoeng1 gong2", "xiang1 gang3", "Hong Kong"),
        "电脑": ("din6 nou5", "dian4 nao3", "computer"),
    }
    bad = []
    for word, (jp, py, en) in expect.items():
        row = lookup(db, word)
        if row is None:
            bad.append(f"{word}: missing")
            continue
        _, jps, pys, eng = row
        if jp and jp not in jps.split("/"):
            bad.append(f"{word}: jyutping {jps!r} lacks {jp!r}")
        if py and py not in pys.split("/"):
            bad.append(f"{word}: pinyin {pys!r} lacks {py!r}")
        if en.lower() not in eng.lower():
            bad.append(f"{word}: english {eng!r} lacks {en!r}")
    if bad:
        sys.exit("dictionary check failed:\n  " + "\n  ".join(bad))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--typeduck", default=os.path.join(HERE, "typeduck"))
    ap.add_argument("--luna", required=True, help="path to luna_pinyin.dict.yaml")
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    tmp = args.out + ".tmp"
    n, a = build(args.typeduck, args.luna, tmp)
    check(tmp)
    os.replace(tmp, args.out)
    print(f"lychee dictionary: {n} entries, {a} aliases, {os.path.getsize(args.out) // 1024} KiB")


if __name__ == "__main__":
    main()
