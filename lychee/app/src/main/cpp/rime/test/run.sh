#!/bin/sh
# Lychee: deploy the Cantonese scheme with the build machine's librime
# (Debian/Ubuntu: apt install librime-dev) and check cases.tsv.
set -e
here=$(cd "$(dirname "$0")" && pwd)
rime=$here/..
lychee=$rime/../../../../..
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/shared" "$work/user"
cp "$rime/default.yaml" "$rime/lychee_cantonese.schema.yaml" \
   "$rime/rime-prelude/key_bindings.yaml" "$rime/rime-prelude/punctuation.yaml" \
   "$rime/rime-prelude/symbols.yaml" \
   "$lychee/dictionary/typeduck/jyut6ping3.dict.yaml" "$lychee/dictionary/typeduck/essay.txt" \
   "$work/shared/"
cp -r "$lychee/lib/fcitx5/src/main/cpp/prebuilt/opencc/data" "$work/shared/opencc"
cc -O1 -o "$work/schema_test" "$here/schema_test.c" -lrime
"$work/schema_test" "$work/shared" "$work/user" < "$here/cases.tsv"
