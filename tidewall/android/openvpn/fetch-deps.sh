#!/usr/bin/env bash
# Downloads the pinned OpenVPN 3 sources and its dependencies into deps/.
# Run automatically by Gradle before the native build; safe to re-run.
set -euo pipefail
cd "$(dirname "$0")"
DEPS="deps"
mkdir -p "$DEPS"

OPENVPN3_TAG="release/3.11.7"
ASIO_TAG="asio-1-24-0"   # the version OpenVPN 3 pins in deps/vcpkg-ports/asio
MBEDTLS_VER="3.6.7"
LZ4_TAG="v1.10.0"
FMT_TAG="12.2.0"
XXHASH_TAG="v0.8.3"

stamp="$DEPS/.stamp-$( (echo "$OPENVPN3_TAG $ASIO_TAG $MBEDTLS_VER $LZ4_TAG $FMT_TAG $XXHASH_TAG"; cat patches/*.patch) | sha1sum | cut -c1-12)"
if [[ -f "$stamp" ]]; then
  exit 0
fi
rm -rf "$DEPS"/*

clone() { # repo tag dir
  git -c advice.detachedHead=false clone -q --depth 1 --branch "$2" "https://github.com/$1.git" "$DEPS/$3"
}

clone OpenVPN/openvpn3 "$OPENVPN3_TAG" openvpn3
clone chriskohlhoff/asio "$ASIO_TAG" asio
clone lz4/lz4 "$LZ4_TAG" lz4
clone fmtlib/fmt "$FMT_TAG" fmt
clone Cyan4973/xxHash "$XXHASH_TAG" xxhash

# mbed TLS release tarballs ship the generated sources (git checkouts need Python tooling).
curl -fsSL "https://github.com/Mbed-TLS/mbedtls/releases/download/mbedtls-${MBEDTLS_VER}/mbedtls-${MBEDTLS_VER}.tar.bz2" \
  | tar -xj -C "$DEPS"
mv "$DEPS/mbedtls-${MBEDTLS_VER}" "$DEPS/mbedtls"

# OpenVPN 3 expects its patched asio.
for p in "$DEPS"/openvpn3/deps/asio/patches/*.patch; do
  git -C "$DEPS/asio" apply "$(pwd)/$p"
done

# Tidewall patches on top of the pinned OpenVPN 3 release.
for p in patches/openvpn3-*.patch; do
  git -C "$DEPS/openvpn3" apply "$(pwd)/$p"
done

touch "$stamp"
echo "OpenVPN 3 dependencies ready in $DEPS"
