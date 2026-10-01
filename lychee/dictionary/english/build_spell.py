#!/usr/bin/env python3
"""Make fcitx spell dictionaries for UK, Canadian and Australian English
from fcitx's US one (en_dict.fscd) and variants.tsv.

fscd format: "FSCD0000", uint32 word count, then per word a uint16 weight
and the NUL-terminated word (all little endian).
"""
import os
import struct
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MAGIC = b"FSCD0000"


def read_fscd(path):
    data = open(path, "rb").read()
    if data[:8] != MAGIC:
        sys.exit(f"{path}: not an fscd file")
    (count,) = struct.unpack_from("<I", data, 8)
    words, i = {}, 12
    for _ in range(count):
        (weight,) = struct.unpack_from("<H", data, i)
        end = data.index(b"\0", i + 2)
        words[data[i + 2:end].decode()] = weight
        i = end + 1
    return words


def write_fscd(path, words):
    with open(path, "wb") as f:
        f.write(MAGIC + struct.pack("<I", len(words)))
        for word in sorted(words):
            f.write(struct.pack("<H", words[word]) + word.encode() + b"\0")


def main():
    us_path, out_dir = sys.argv[1], sys.argv[2]
    us = read_fscd(us_path)
    regions = {}
    with open(os.path.join(HERE, "variants.tsv"), encoding="utf-8") as f:
        for line in f:
            if line.startswith("#"):
                continue
            region, us_word, variant, replace = line.rstrip("\n").split("\t")
            regions.setdefault(region, []).append((us_word, variant, replace == "1"))
    os.makedirs(out_dir, exist_ok=True)
    for region, pairs in regions.items():
        words = dict(us)
        for us_word, variant, replace in pairs:
            weight = us.get(us_word)
            if weight is None:
                continue
            if replace:
                # colour takes color's place
                words[variant] = max(words.get(variant, 0), weight)
                words.pop(us_word, None)
            elif variant not in words:
                # both are words there (check / cheque): don't let the
                # variant outrank the common one
                words[variant] = weight // 4
        write_fscd(os.path.join(out_dir, f"en_{region}_dict.fscd"), words)
        print(f"en_{region}: {len(words)} words")


if __name__ == "__main__":
    main()
