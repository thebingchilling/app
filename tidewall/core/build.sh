#!/usr/bin/env bash
# Builds the Go engine (mihomo + AmneziaWG) into an Android library that the
# app links against: ../android/app/libs/libcore.aar
#
# Needs: Go, ANDROID_HOME, ANDROID_NDK_HOME. gomobile/gobind are installed on
# first run. Set ABIS to e.g. "android/arm64,android/arm" to build more ABIs.
set -euo pipefail
cd "$(dirname "$0")"

ABIS="${ABIS:-android/arm64}"
# with_gvisor: mihomo's gVisor TUN stack and userspace WireGuard/OpenVPN netstacks.
# no_*: drop the Tailscale/ZeroTier/EasyTier outbounds, which add ~20 MB.
TAGS="${TAGS:-with_gvisor,no_tailscale,no_zerotier,no_easytier}"
OUT="../android/app/libs"
export PATH="$PATH:$(go env GOPATH)/bin"

if ! command -v gomobile >/dev/null || ! command -v gobind >/dev/null; then
  go install golang.org/x/mobile/cmd/gomobile@v0.0.0-20260908204917-8b95e45f8d3e
  go install golang.org/x/mobile/cmd/gobind@v0.0.0-20260908204917-8b95e45f8d3e
fi

mkdir -p "$OUT"
gomobile bind \
  -target="$ABIS" \
  -androidapi 26 \
  -tags "$TAGS" \
  -javapkg=dev.tidewall \
  -trimpath \
  -ldflags="-s -w -buildid=" \
  -o "$OUT/libcore.aar" \
  .
rm -f "$OUT/libcore-sources.jar"
echo "Built $OUT/libcore.aar"
