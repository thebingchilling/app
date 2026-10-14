#!/usr/bin/env python3
"""Download Material Symbols Rounded (Apache-2.0) as Android vector drawables.

For each name in icons.txt writes core/ui/src/main/res/drawable/ms_<name>.xml
(outlined, FILL 0) and ms_<name>_fill.xml (filled, FILL 1); weight 400,
grade 0, 24 dp. The android:tint attribute is stripped so Compose's Icon()
tints them.
"""
import pathlib, re, sys, urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "core/ui/src/main/res/drawable"
BASE = "https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android"

def fetch(url):
    with urllib.request.urlopen(url, timeout=30) as r:
        return r.read().decode()

def main():
    OUT.mkdir(parents=True, exist_ok=True)
    names = [l.strip() for l in (ROOT / "tools/icons.txt").read_text().splitlines()
             if l.strip() and not l.startswith("#")]
    failed = []
    for n in names:
        for suffix, out in (("_24px", f"ms_{n}.xml"), ("_fill1_24px", f"ms_{n}_fill.xml")):
            try:
                xml = fetch(f"{BASE}/{n}/materialsymbolsrounded/{n}{suffix}.xml")
            except Exception as e:
                failed.append(f"{n}{suffix}: {e}")
                continue
            xml = re.sub(r'\s*android:tint="[^"]*"', "", xml)
            (OUT / out).write_text(xml)
    if failed:
        print("failed:\n  " + "\n  ".join(failed), file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
