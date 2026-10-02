#!/bin/sh
# Lychee: print the path of a host rime_deployer from librime 1.16.1 (the
# version of the app's prebuilt librime), building it first if needed.
# Debian/Ubuntu packages: cmake ninja-build libboost-dev libboost-regex-dev
# libboost-locale-dev libgoogle-glog-dev libleveldb-dev libmarisa-dev
# libopencc-dev libyaml-cpp-dev
set -eu
version=1.16.1
here=$(cd "$(dirname "$0")" && pwd)
cache=${LYCHEE_HOST_LIBRIME:-$here/.host-librime}
bin="$cache/build/bin/rime_deployer"
if [ ! -x "$bin" ]; then
    {
        rm -rf "$cache"
        git clone -q --depth 1 --branch "$version" https://github.com/rime/librime "$cache"
        cmake -S "$cache" -B "$cache/build" -G Ninja -DCMAKE_BUILD_TYPE=Release \
            -DBUILD_TEST=OFF -DBUILD_STATIC=OFF -DENABLE_LOGGING=OFF
        ninja -C "$cache/build"
    } >&2
fi
echo "$bin"
