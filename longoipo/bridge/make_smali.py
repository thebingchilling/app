#!/usr/bin/env python3
"""Regenerate longoipo/bridge/smali from the Java sources in longoipo/bridge/src.

Needs: JDK 11+, Maven, Python 3 and network access (Maven Central + maven.google.com).
Pipeline: javac (--release 8) -> D8 -> baksmali -> smali/. Run this after editing the Java sources
and commit the resulting smali/ folder; the patcher only copies that folder, it never compiles Java.

Usage: python3 make_smali.py [--work DIR]
"""
import argparse
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
R8 = "9.1.31"
SMALI = "3.0.9"
ORG_JSON = "20240303"

POM = f"""<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
<groupId>l</groupId><artifactId>l</artifactId><version>1</version>
<repositories><repository><id>google</id><url>https://maven.google.com</url></repository></repositories>
<dependencies>
<dependency><groupId>com.android.tools</groupId><artifactId>r8</artifactId><version>{R8}</version></dependency>
<dependency><groupId>com.android.tools.smali</groupId><artifactId>smali-baksmali</artifactId><version>{SMALI}</version></dependency>
<dependency><groupId>org.json</groupId><artifactId>json</artifactId><version>{ORG_JSON}</version></dependency>
</dependencies></project>
"""


def run(cmd, **kw):
    print("+", " ".join(cmd))
    subprocess.run(cmd, check=True, **kw)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--work", help="working directory (default: a temp dir)")
    args = ap.parse_args()
    work = args.work or tempfile.mkdtemp(prefix="longoipo-smali-")
    os.makedirs(work, exist_ok=True)

    with open(os.path.join(work, "pom.xml"), "w") as f:
        f.write(POM)
    lib = os.path.join(work, "lib")
    run(["mvn", "-B", "-q", "-f", os.path.join(work, "pom.xml"), "dependency:copy-dependencies",
         f"-DoutputDirectory={lib}"])
    json_jar = os.path.join(lib, f"json-{ORG_JSON}.jar")
    r8_jar = os.path.join(lib, f"r8-{R8}.jar")

    classes = os.path.join(work, "classes")
    shutil.rmtree(classes, ignore_errors=True)
    os.makedirs(classes)
    sources = [os.path.join(dp, f) for dp, _, fs in os.walk(os.path.join(HERE, "src")) for f in fs if f.endswith(".java")]
    run(["javac", "--release", "8", "-cp", json_jar, "-d", classes] + sources)

    class_files = [os.path.join(dp, f) for dp, _, fs in os.walk(classes) for f in fs if f.endswith(".class")]
    dexdir = os.path.join(work, "dex")
    shutil.rmtree(dexdir, ignore_errors=True)
    os.makedirs(dexdir)
    # org.json comes from the Android framework at run time, so it is only a classpath entry here.
    run(["java", "-cp", r8_jar, "com.android.tools.r8.D8", "--release", "--min-api", "24", "--no-desugaring",
         "--classpath", json_jar, "--output", dexdir] + class_files)

    out = os.path.join(HERE, "smali")
    shutil.rmtree(out, ignore_errors=True)
    run(["java", "-cp", os.path.join(lib, "*"), "com.android.tools.smali.baksmali.Main", "d",
         os.path.join(dexdir, "classes.dex"), "-o", out])
    n = sum(1 for _, _, fs in os.walk(out) for f in fs if f.endswith(".smali"))
    print(f"wrote {n} smali files to {out}")
    if n == 0:
        sys.exit("no smali produced")


if __name__ == "__main__":
    main()
