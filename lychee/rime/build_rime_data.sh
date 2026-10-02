#!/bin/sh
# Lychee: stage Rime's data for the app and compile the dictionaries on the
# build machine, so the phone does not spend minutes compiling them on first
# launch. The result is copied into the APK as the asset folder "rime".
#
# usage: build_rime_data.sh <out_dir>
#
# Needs rime_deployer from the same librime version as the app (1.16.1,
# the version in prebuilt/): host_librime.sh builds it when missing.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
out=$1
deployer=$("$here/host_librime.sh")
export LD_LIBRARY_PATH="$(dirname "$deployer")/../lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"

shared="$out/rime"
rm -rf "$out"
mkdir -p "$shared/opencc" "$shared/lua" "$shared/cn_dicts" "$shared/en_dicts"

# Lychee's configuration and schemes
cp "$here/data/default.yaml" "$here/data/lychee_mandarin.schema.yaml" \
   "$here/data/lychee_cantonese.schema.yaml" "$shared/"

# Shared presets (punctuation and key bindings)
cp "$here/rime-prelude/key_bindings.yaml" "$here/rime-prelude/punctuation.yaml" \
   "$here/rime-prelude/symbols.yaml" "$shared/"

# Mandarin: rime-ice (GPL-3.0)
ice="$here/rime-ice"
cp "$ice/rime_ice.schema.yaml" "$ice/rime_ice.dict.yaml" "$ice/custom_phrase.txt" "$shared/"
cp "$ice"/cn_dicts/8105.dict.yaml "$ice"/cn_dicts/base.dict.yaml "$ice"/cn_dicts/ext.dict.yaml \
   "$ice"/cn_dicts/tencent.dict.yaml "$ice"/cn_dicts/others.dict.yaml "$shared/cn_dicts/"
cp "$ice/en_dicts/cn_en.txt" "$shared/en_dicts/"
for f in date_translator lunar unicode number_translator calc_translator pin_cand_filter long_word_filter; do
    cp "$ice/lua/$f.lua" "$shared/lua/"
done
cp "$ice/lua/lunar.db" "$shared/lua/" 2>/dev/null || true
cp "$ice/opencc/emoji.json" "$ice/opencc/emoji.txt" "$ice/opencc/others.txt" "$shared/opencc/"

# Cantonese: TypeDuck's dictionary (CC BY 4.0)
cp "$here/typeduck/jyut6ping3.dict.yaml" "$here/typeduck/essay.txt" "$shared/"

# OpenCC data for Traditional/Simplified (Apache-2.0)
cp "$here"/prebuilt/opencc/data/*.json "$here"/prebuilt/opencc/data/*.ocd2 "$shared/opencc/"

# Compile: the binaries land in rime/build, where Rime looks for prebuilt data.
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
(cd "$work" && "$deployer" --build "$work" "$shared" "$shared/build" > "$work/deploy.log" 2>&1) || {
    cat "$work/deploy.log"; exit 1
}
for f in lychee_mandarin.schema.yaml lychee_cantonese.schema.yaml rime_ice.table.bin rime_ice.prism.bin \
         jyut6ping3.table.bin jyut6ping3.prism.bin; do
    [ -s "$shared/build/$f" ] || { cat "$work/deploy.log"; echo "missing build/$f" >&2; exit 1; }
done
# The compiled dictionaries are all the phone needs (Rime uses prebuilt
# binaries when their sources are absent): drop the 50 MB of sources.
rm -f "$shared"/cn_dicts/*.dict.yaml "$shared/jyut6ping3.dict.yaml" "$shared/essay.txt"
# Rime writes the user's build info here; the app's own user folder is elsewhere.
rm -f "$shared/user.yaml" "$shared/installation.yaml"
echo "Rime data: $(du -sh "$shared" | cut -f1) in $shared"
