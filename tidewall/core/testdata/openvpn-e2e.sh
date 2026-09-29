#!/usr/bin/env bash
# End-to-end test of Proxy-mode OpenVPN (mihomo's openvpn outbound, fed by
# Tidewall's .ovpn converter) against real OpenVPN 2.6 servers.
#
# Needs root, /dev/net/tun, openvpn, openssl, python3 and Go. Linux only.
#   sudo core/testdata/openvpn-e2e.sh
# Set KEEP=1 to keep the work dir. SERVE=1 skips the Go test and keeps the
# servers running (work dir in /tmp/tidewall-ovpn-dir) until killed, for
# testing other clients; REMOTE sets the server address in client profiles.
set -euo pipefail
cd "$(dirname "$0")/.."
CORE="$(pwd)"
W="$(mktemp -d /tmp/tidewall-ovpn.XXXXXX)"
cleanup() {
  for p in "$W"/*.pid; do [[ -f "$p" ]] && kill "$(cat "$p")" 2>/dev/null || true; done
  [[ "${KEEP:-}" == 1 ]] && echo "work dir: $W" || rm -rf "$W"
}
trap cleanup EXIT
cd "$W"

# --- PKI ----------------------------------------------------------------------
openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -days 2 \
  -subj "/CN=Tidewall Test CA" -keyout ca.key -out ca.crt 2>/dev/null
issue() { # name eku
  openssl req -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -subj "/CN=$1" \
    -keyout "$1.key" -out "$1.csr" 2>/dev/null
  printf "basicConstraints=CA:FALSE\nkeyUsage=digitalSignature,keyAgreement\nextendedKeyUsage=%s\n" "$2" > "$1.ext"
  openssl x509 -req -in "$1.csr" -CA ca.crt -CAkey ca.key -CAcreateserial -days 2 \
    -extfile "$1.ext" -out "$1.crt" 2>/dev/null
}
issue server serverAuth
issue client clientAuth
openvpn --genkey secret ta.key
openvpn --genkey secret tc.key

# Username/password check for the UDP server.
cat > check.sh <<'SH'
#!/bin/sh
[ "$(sed -n 1p "$1")" = "alice" ] && [ "$(sed -n 2p "$1")" = "s3cret" ]
SH
chmod +x check.sh

# --- Servers --------------------------------------------------------------------
common="ca ca.crt
cert server.crt
key server.key
dh none
topology subnet
keepalive 10 60
data-ciphers AES-256-GCM:CHACHA20-POLY1305:AES-128-GCM
auth SHA256
verb 3"

cat > udp.conf <<C
port 11940
proto udp
dev tw-udp
dev-type tun
server 10.88.0.0 255.255.255.0
tls-auth ta.key 0
script-security 2
auth-user-pass-verify $W/check.sh via-file
username-as-common-name
$common
C

cat > tcp.conf <<C
port 11941
proto tcp-server
dev tw-tcp
dev-type tun
server 10.89.0.0 255.255.255.0
tls-crypt tc.key
$common
C

openvpn --config udp.conf --daemon --writepid udp.pid --log udp.log --cd "$W"
openvpn --config tcp.conf --daemon --writepid tcp.pid --log tcp.log --cd "$W"

# Something to reach through the tunnels.
mkdir web && echo "tidewall-openvpn-ok" > web/index.html
python3 -m http.server 18080 --bind 0.0.0.0 --directory web >web.log 2>&1 &
echo $! > web.pid

for i in $(seq 1 30); do
  if grep -q "Initialization Sequence Completed" udp.log && grep -q "Initialization Sequence Completed" tcp.log; then break; fi
  sleep 0.5
done
grep -q "Initialization Sequence Completed" udp.log || { cat udp.log; exit 1; }
grep -q "Initialization Sequence Completed" tcp.log || { cat tcp.log; exit 1; }

# --- Client profiles, as a user would receive them -----------------------------
block() { echo "<$1>"; cat "$2"; echo "</$1>"; }
{
  echo "client"; echo "dev tun"; echo "proto udp"; echo "remote ${REMOTE:-127.0.0.1} 11940"
  echo "nobind"; echo "remote-cert-tls server"; echo "auth SHA256"; echo "auth-user-pass"
  echo "data-ciphers AES-256-GCM:CHACHA20-POLY1305"; echo "key-direction 1"; echo "keepalive 10 60"
  block ca ca.crt; block cert client.crt; block key client.key; block tls-auth ta.key
} > udp-client.ovpn
{
  echo "client"; echo "dev tun"; echo "proto tcp-client"; echo "remote ${REMOTE:-127.0.0.1} 11941"
  echo "nobind"; echo "remote-cert-tls server"; echo "auth SHA256"; echo "cipher AES-256-GCM"
  block ca ca.crt; block cert client.crt; block key client.key; block tls-crypt tc.key
} > tcp-client.ovpn

if [[ "${SERVE:-}" == 1 ]]; then
  echo "$W" > /tmp/tidewall-ovpn-dir
  echo "servers ready in $W"
  wait "$(cat web.pid)"
  exit 0
fi

cd "$CORE"
TIDEWALL_OVPN_DIR="$W" go test -count=1 -tags with_gvisor,integration -run 'OpenVPN' -v . 2>&1 \
  | grep -Ev 'level=(info|debug)'
