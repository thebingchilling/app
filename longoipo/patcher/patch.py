#!/usr/bin/env python3
"""Patch a v2RayTun universal APK: rc4-md5 support (loopback bridge) + rebrand + re-sign.

    python3 patch.py INPUT.apk OUTPUT.apk --keystore KS --alias ALIAS
        [--package com.longoipo.app] [--label Longoipo] [--work DIR] [--keep-work]

Passwords are read from the environment: KS_PASS (keystore) and KEY_PASS (key, defaults to KS_PASS).

What it does (see longoipo/RUNBOOK.md for the reasoning):
  1. apktool decode.
  2. Copy the precompiled bridge smali (longoipo/bridge/smali) into a new smali_classesN folder.
  3. Hook the two gomobile call sites that hand a JSON config to the Go core
     (CoreController.startLoop and Libv2ray.measureOutboundDelay) so the config passes through
     Rc4Bridge.rewrite() first. Sites are found by their fixed native signatures, never by R8 names.
  4. Rename the application ID and every visible label. Dotted strings starting with the old package
     are rewritten unless they name a class that really exists in the dex (those must stay).
  5. apktool build, zip-align, sign (v2 + v3), then verify the result.
The script stops with a clear error if any expected anchor is missing, instead of writing a broken APK.
"""
import argparse
import hashlib
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
BRIDGE_SMALI = HERE.parent / "bridge" / "smali"

OLD_PKG = "com.v2raytun.android"
BRIDGE_CLASS = "Lcom/longoipo/rc4/Rc4Bridge;"
BRIDGE_REWRITE = BRIDGE_CLASS + "->rewrite(Ljava/lang/String;)Ljava/lang/String;"

APKTOOL_URL = "https://bitbucket.org/iBotPeaches/apktool/downloads/apktool_2.11.1.jar"
APKTOOL_SHA256 = "56d59c524fc764263ba8d345754d8daf55b1887818b15cd3b594f555d249e2db"
APKSIG_URL = "https://maven.google.com/com/android/tools/build/apksig/8.5.2/apksig-8.5.2.jar"
APKSIG_SHA256 = "c4f6fdf2148490296f422a7eb37647ea11d1c3b02f1f2349c33ce257f3c29b1f"

# (call signature, index of the String argument counted over the invoke's register list)
HOOK_TARGETS = [
    ("Llibv2ray/CoreController;->startLoop(Ljava/lang/String;I)V", 1),
    ("Llibv2ray/Libv2ray;->measureOutboundDelay(Ljava/lang/String;Ljava/lang/String;)J", 0),
]

INVOKE_RE = re.compile(r"^(\s*)invoke-(virtual|static|direct|interface|super)(/range)?\s+\{([^}]*)\},\s*(\S+)\s*$")
OLD_TOKEN_RE = re.compile(re.escape(OLD_PKG) + r"[A-Za-z0-9_.$:\-]*")
RELATIVE_NAME_RE = re.compile(r'(android:(?:name|targetActivity|parentActivityName|backupAgent|'
                              r'manageSpaceActivity|zygotePreloadName|appComponentFactory)=")\.')
LABEL_RE = re.compile(r"(?<![\w./:@-])v2ray ?tun(?![\w.])", re.IGNORECASE)


class PatchError(Exception):
    pass


def log(msg):
    print(msg, flush=True)


def run(cmd, **kw):
    log("+ " + " ".join(str(c) for c in cmd))
    r = subprocess.run([str(c) for c in cmd], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, **kw)
    if r.returncode != 0:
        tail = "\n".join(r.stdout.splitlines()[-25:])
        raise PatchError(f"command failed ({r.returncode}): {cmd[0]}\n{tail}")
    return r.stdout


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def fetch(url, dest, expected_sha):
    if dest.exists() and sha256(dest) == expected_sha:
        return dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    log(f"downloading {url}")
    with urllib.request.urlopen(url, timeout=120) as r, open(dest, "wb") as f:
        shutil.copyfileobj(r, f)
    got = sha256(dest)
    if got != expected_sha:
        dest.unlink()
        raise PatchError(f"checksum mismatch for {url}: got {got}, expected {expected_sha}")
    return dest


# --------------------------------------------------------------------------- tools

def ensure_tools(tools_dir, work):
    apktool = fetch(APKTOOL_URL, tools_dir / "apktool-2.11.1.jar", APKTOOL_SHA256)
    apksig = fetch(APKSIG_URL, tools_dir / "apksig-8.5.2.jar", APKSIG_SHA256)
    sigdir = work / "sigtool"
    sigdir.mkdir(parents=True, exist_ok=True)
    run(["javac", "-cp", apksig, "-d", sigdir, HERE / "SigTool.java"])
    return apktool, apksig, sigdir


def find_zipalign():
    found = shutil.which("zipalign")
    if found:
        return found
    for var in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        root = os.environ.get(var)
        if root and (Path(root) / "build-tools").is_dir():
            for bt in sorted((Path(root) / "build-tools").iterdir(), reverse=True):
                if (bt / "zipalign").exists():
                    return str(bt / "zipalign")
    return None


# --------------------------------------------------------------------------- smali helpers

def smali_dirs(src):
    return sorted(p for p in src.iterdir() if p.is_dir() and re.fullmatch(r"smali(_classes\d+)?", p.name))


def class_names(src):
    names = set()
    for d in smali_dirs(src):
        for f in d.rglob("*.smali"):
            names.add(".".join(f.relative_to(d).with_suffix("").parts))
    return names


def expand_registers(text, is_range):
    text = text.strip()
    if not is_range:
        return [r.strip() for r in text.split(",") if r.strip()]
    m = re.fullmatch(r"([vp])(\d+)\s*\.\.\s*([vp])(\d+)", text)
    if not m or m.group(1) != m.group(3):
        raise PatchError(f"cannot parse register range: {text}")
    return [f"{m.group(1)}{n}" for n in range(int(m.group(2)), int(m.group(4)) + 1)]


def next_smali_dir(src):
    top = 1
    for d in smali_dirs(src):
        m = re.fullmatch(r"smali_classes(\d+)", d.name)
        top = max(top, int(m.group(1)) if m else 1)
    return src / f"smali_classes{top + 1}"


# --------------------------------------------------------------------------- steps

def inject_bridge(src):
    if not (BRIDGE_SMALI / "com" / "longoipo" / "rc4" / "Rc4Bridge.smali").exists():
        raise PatchError(f"bridge smali missing in {BRIDGE_SMALI}; run bridge/make_smali.py")
    dest = next_smali_dir(src)
    shutil.copytree(BRIDGE_SMALI, dest)
    log(f"bridge smali -> {dest.name}")
    return dest


def keep_abis(src, abis):
    """Drop native libraries of every ABI not listed (shrinks the APK, e.g. arm64-v8a only)."""
    libdir = src / "lib"
    present = sorted(p.name for p in libdir.iterdir() if p.is_dir()) if libdir.is_dir() else []
    missing = [a for a in abis if a not in present]
    if missing:
        raise PatchError(f"requested ABI(s) {missing} not in the APK (has {present})")
    for name in present:
        if name not in abis:
            shutil.rmtree(libdir / name)
    log(f"kept ABIs {abis} (removed {[n for n in present if n not in abis]})")


def apply_hooks(src, bridge_dir):
    patched = {sig: [] for sig, _ in HOOK_TARGETS}
    for d in smali_dirs(src):
        if d == bridge_dir:
            continue
        for f in d.rglob("*.smali"):
            text = f.read_text(encoding="utf-8")
            if "Llibv2ray/" not in text:
                continue
            lines = text.split("\n")
            out = []
            changed = False
            for n, line in enumerate(lines, 1):
                m = INVOKE_RE.match(line)
                if m:
                    for sig, idx in HOOK_TARGETS:
                        if m.group(5) == sig:
                            regs = expand_registers(m.group(4), bool(m.group(3)))
                            if idx >= len(regs):
                                raise PatchError(f"{f}:{n}: expected argument {idx} in {line.strip()}")
                            reg = regs[idx]
                            out.append(f"{m.group(1)}invoke-static/range {{{reg} .. {reg}}}, {BRIDGE_REWRITE}")
                            out.append("")
                            out.append(f"{m.group(1)}move-result-object {reg}")
                            out.append("")
                            patched[sig].append(f"{f.relative_to(src)}:{n} ({reg})")
                            changed = True
                out.append(line)
            if changed:
                f.write_text("\n".join(out), encoding="utf-8")
    for sig, sites in patched.items():
        if not sites:
            raise PatchError(f"hook anchor not found: {sig}\n"
                             "The app no longer calls this gomobile method. Re-run the analysis in RUNBOOK.md section 8.")
        if len(sites) > 4:
            log(f"WARNING: {len(sites)} call sites for {sig}; expected 1-2, check the release")
        for s in sites:
            log(f"hooked {sig.split('->')[1].split('(')[0]} at {s}")
    return patched


def rename_token(token, classes, new_pkg):
    """Rewrite one dotted token that starts with the old package, unless it names a real class."""
    base = token.split(":")[0]
    if base in classes:
        return token
    return new_pkg + token[len(OLD_PKG):]


def rewrite_text(text, classes, new_pkg, quoted_only):
    count = 0

    def sub(m):
        nonlocal count
        tok = m.group(1) if quoted_only else m.group(0)
        new = rename_token(tok, classes, new_pkg)
        if new != tok:
            count += 1
        return f'"{new}"' if quoted_only else new

    pattern = re.compile(r'"(' + OLD_TOKEN_RE.pattern + r')"') if quoted_only else OLD_TOKEN_RE
    return pattern.sub(sub, text), count


def rename_package(src, bridge_dir, new_pkg, label):
    classes = class_names(src)
    report = {"manifest": 0, "smali": 0, "res": 0, "labels": 0}

    manifest = src / "AndroidManifest.xml"
    text = manifest.read_text(encoding="utf-8")
    text = RELATIVE_NAME_RE.sub(lambda m: m.group(1) + OLD_PKG + ".", text)  # expand relative class names first
    text, report["manifest"] = rewrite_text(text, classes, new_pkg, quoted_only=False)
    manifest.write_text(text, encoding="utf-8")

    for d in smali_dirs(src):
        if d == bridge_dir:
            continue
        for f in d.rglob("*.smali"):
            t = f.read_text(encoding="utf-8")
            if OLD_PKG not in t:
                continue
            t, n = rewrite_text(t, classes, new_pkg, quoted_only=True)
            if n:
                f.write_text(t, encoding="utf-8")
                report["smali"] += n

    for f in (src / "res").rglob("*.xml"):
        t = f.read_text(encoding="utf-8")
        if OLD_PKG in t:
            t2, n = rewrite_text(t, classes, new_pkg, quoted_only=False)
            if n:
                f.write_text(t2, encoding="utf-8")
                report["res"] += n

    for f in (src / "res").glob("values*/strings.xml"):
        lines = f.read_text(encoding="utf-8").split("\n")
        for i, line in enumerate(lines):
            if "://" not in line and LABEL_RE.search(line):
                lines[i], n = LABEL_RE.subn(label, line)
                report["labels"] += n
        f.write_text("\n".join(lines), encoding="utf-8")

    if report["manifest"] == 0 or report["labels"] == 0:
        raise PatchError(f"rename found nothing to change ({report}); check the app for a new package name or label")
    log(f"rename: {report}")
    return classes


def py_zipalign(src_apk, dst_apk, so_align):
    """Fallback for zipalign: pad stored entries so their data starts on a 4-byte (or so_align for .so) boundary."""
    with zipfile.ZipFile(src_apk) as zin, open(dst_apk, "wb") as fout:
        with zipfile.ZipFile(fout, "w") as zout:
            for zi in zin.infolist():
                data = zin.read(zi)
                ni = zipfile.ZipInfo(zi.filename, zi.date_time)
                ni.compress_type = zi.compress_type
                ni.external_attr = zi.external_attr
                ni.create_system = zi.create_system
                if zi.compress_type == zipfile.ZIP_STORED:
                    align = so_align if zi.filename.endswith(".so") else 4
                    hdr = 30 + len(ni.filename.encode("utf-8"))
                    pad = (-(fout.tell() + hdr)) % align
                    if pad:
                        if pad < 4:
                            pad += align
                        ni.extra = struct.pack("<HH", 0xD935, pad - 4) + b"\0" * (pad - 4)
                zout.writestr(ni, data)


def build_sign_verify(apktool, sigdir, apksig, src, work, out_apk, keystore, alias, extractnative_false):
    unsigned = work / "unsigned.apk"
    run(["java", "-jar", apktool, "b", "-o", unsigned, src])
    aligned = work / "aligned.apk"
    za = find_zipalign()
    if za:
        run([za, "-f", "-p", "4", unsigned, aligned])
    else:
        log("zipalign not found; using built-in aligner")
        py_zipalign(unsigned, aligned, 16384 if extractnative_false else 4)
    cp = f"{sigdir}{os.pathsep}{apksig}"
    out = run(["java", "-cp", cp, "SigTool", "sign", aligned, out_apk, keystore, alias])
    log(out.strip())
    out = run(["java", "-cp", cp, "SigTool", "verify", out_apk])
    log(out.strip())
    if "verified=true" not in out:
        raise PatchError("signature verification failed")


def verify_output(apktool, out_apk, work, classes, new_pkg, label):
    """Structural checks on the final APK (decodes it again with apktool)."""
    with zipfile.ZipFile(out_apk) as z:
        dex_bytes = b"".join(z.read(n) for n in z.namelist() if re.fullmatch(r"classes\d*\.dex", n))
        native = [n for n in z.namelist() if n.endswith("libgojni.so")]
    if b"Lcom/longoipo/rc4/Rc4Bridge;" not in dex_bytes:
        raise PatchError("verify: bridge class not found in the dex files")
    if not native:
        raise PatchError("verify: libgojni.so missing from the APK")
    vdir = work / "verify"
    run(["java", "-jar", apktool, "d", "-f", "--no-src", "-o", vdir, out_apk])
    manifest = (vdir / "AndroidManifest.xml").read_text(encoding="utf-8")
    if f'package="{new_pkg}"' not in manifest:
        raise PatchError("verify: manifest package was not renamed")
    leftovers = sorted({t for t in OLD_TOKEN_RE.findall(manifest) if t.split(":")[0] not in classes})
    if leftovers:
        raise PatchError(f"verify: old-package names left in the manifest that are not classes: {leftovers[:5]}")
    for auth in re.findall(r'authorities="([^"]+)"', manifest):
        if not auth.startswith(new_pkg):
            raise PatchError(f"verify: provider authority not renamed: {auth}")
    app_name = re.search(r'<string name="app_name">([^<]*)</string>',
                         (vdir / "res" / "values" / "strings.xml").read_text(encoding="utf-8"))
    if not app_name or app_name.group(1) != label:
        raise PatchError(f"verify: app_name is {app_name.group(1) if app_name else None!r}, expected {label!r}")
    log(f"verify: ok (package {new_pkg}, label {label}, bridge in dex, {len(native)} native core libs)")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("input")
    ap.add_argument("output")
    ap.add_argument("--keystore", required=True)
    ap.add_argument("--alias", required=True)
    ap.add_argument("--package", default="com.longoipo.app")
    ap.add_argument("--label", default="Longoipo")
    ap.add_argument("--abis", help="comma-separated ABIs to keep, e.g. arm64-v8a (default: keep all)")
    ap.add_argument("--work", help="working directory (default: temp dir)")
    ap.add_argument("--tools", default=str(HERE / ".tools"), help="cache dir for downloaded tools")
    ap.add_argument("--keep-work", action="store_true")
    args = ap.parse_args()

    if not os.environ.get("KS_PASS"):
        raise PatchError("set KS_PASS (and optionally KEY_PASS) in the environment")
    if not re.fullmatch(r"[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+", args.package):
        raise PatchError(f"invalid package name: {args.package}")
    if args.package == OLD_PKG:
        raise PatchError("new package must differ from the original")

    work = Path(args.work) if args.work else Path(tempfile.mkdtemp(prefix="longoipo-patch-"))
    work.mkdir(parents=True, exist_ok=True)
    Path(args.output).parent.mkdir(parents=True, exist_ok=True)
    try:
        apktool, apksig, sigdir = ensure_tools(Path(args.tools), work)
        src = work / "src"
        shutil.rmtree(src, ignore_errors=True)
        log(f"input sha256 {sha256(args.input)}")
        run(["java", "-jar", apktool, "d", "-f", "-o", src, args.input])
        yml = (src / "apktool.yml").read_text(encoding="utf-8")
        version = re.search(r"versionName: (.+)", yml)
        log(f"decoded {args.input} (versionName {version.group(1).strip() if version else '?'})")
        m = (src / "AndroidManifest.xml").read_text(encoding="utf-8")
        if f'package="{OLD_PKG}"' not in m:
            raise PatchError(f"unexpected package in manifest; this patcher targets {OLD_PKG}")

        if args.abis:
            keep_abis(src, [a.strip() for a in args.abis.split(",") if a.strip()])
        bridge_dir = inject_bridge(src)
        apply_hooks(src, bridge_dir)
        classes = rename_package(src, bridge_dir, args.package, args.label)
        build_sign_verify(apktool, sigdir, apksig, src, work, Path(args.output), args.keystore, args.alias,
                          'extractNativeLibs="false"' in m)
        verify_output(apktool, Path(args.output), work, classes, args.package, args.label)
        log(f"output sha256 {sha256(args.output)}")
        log(f"DONE: {args.output}")
    finally:
        if not args.keep_work and not args.work:
            shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    try:
        main()
    except PatchError as e:
        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(1)
