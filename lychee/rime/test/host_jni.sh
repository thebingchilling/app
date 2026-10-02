#!/bin/sh
# Lychee: build the JNI bridge (src/main/cpp/rime_jni.cpp) for the build
# machine against the host librime, and the app's Rime data, so the JVM test
# app.lychee.rime.RimeHostTest can drive real Rime through the same code.
# Prints the folder to pass as LYCHEE_HOST_RIME (library + data).
set -eu
here=$(cd "$(dirname "$0")" && pwd)
out=${1:-$here/../build/host-rime}
deployer=$("$here/../host_librime.sh")
librime=$(cd "$(dirname "$deployer")/../.." && pwd)
java_home=${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}
mkdir -p "$out"
c++ -std=c++17 -O1 -shared -fPIC -o "$out/liblychee_rime.so" "$here/../src/main/cpp/rime_jni.cpp" \
    -I"$java_home/include" -I"$java_home/include/linux" -I"$librime/src" -I"$librime/build/src" \
    -L"$librime/build/lib" -lrime -Wl,-rpath,"$librime/build/lib" >&2
"$here/../build_rime_data.sh" "$out/data" >&2
echo "$out"
