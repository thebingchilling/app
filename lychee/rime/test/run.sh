#!/bin/sh
# Lychee: build the app's Rime data with the host librime (see
# ../host_librime.sh) and check both schemes against the cases here, the
# way the app uses them (compiled dictionaries only, fresh user folder).
set -eu
here=$(cd "$(dirname "$0")" && pwd)
deployer=$("$here/../host_librime.sh")
librime=$(cd "$(dirname "$deployer")/../.." && pwd)
export LD_LIBRARY_PATH="$librime/build/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
"$here/../build_rime_data.sh" "$work/out"
mkdir -p "$work/user"
cc -O1 -I"$librime/src" -I"$librime/build/src" -o "$work/schema_test" "$here/schema_test.c" \
   -L"$librime/build/lib" -lrime
status=0
"$work/schema_test" "$work/out/rime" "$work/user" lychee_mandarin < "$here/mandarin.tsv" || status=1
"$work/schema_test" "$work/out/rime" "$work/user" lychee_cantonese < "$here/cantonese.tsv" || status=1
exit $status
