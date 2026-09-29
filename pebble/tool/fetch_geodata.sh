#!/usr/bin/env bash
# Downloads the geo databases bundled into the app (assets/data/).
set -euo pipefail
cd "$(dirname "$0")/../assets/data"
base=https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest
curl -fsSL -o GEOIP.metadb "$base/geoip.metadb"
curl -fsSL -o ASN.mmdb "$base/GeoLite2-ASN.mmdb"
curl -fsSL -o GEOIP.dat "$base/geoip.dat"
curl -fsSL -o GEOSITE.dat "$base/geosite.dat"
