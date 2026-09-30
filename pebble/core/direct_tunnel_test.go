package main

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/metacubex/mihomo/config"
	"github.com/metacubex/mihomo/tunnel/statistic"
)

// The profile lib/common/direct_tunnel.dart writes for a WireGuard/OpenVPN
// file: mihomo must load it (ignoring the x-pebble-direct line) as an empty
// DIRECT profile.
const directTunnelProfile = `# Pebble direct WireGuard tunnel.
x-pebble-direct: {"type":"wireguard","config":"[Interface]\nPrivateKey = x\n\n[Peer]\nEndpoint = \"a b\": 1\n"}
proxies: []
rules:
  - MATCH,DIRECT
`

func TestDirectTunnelProfileLoadsAsEmptyDirectConfig(t *testing.T) {
	path := filepath.Join(t.TempDir(), "profile.yaml")
	if err := os.WriteFile(path, []byte(directTunnelProfile), 0o644); err != nil {
		t.Fatal(err)
	}
	if msg := handleValidateConfig(path); msg != "" {
		t.Fatalf("handleValidateConfig = %q", msg)
	}
	raw, err := config.UnmarshalRawConfig([]byte(directTunnelProfile))
	if err != nil {
		t.Fatal(err)
	}
	if len(raw.Proxy) != 0 || len(raw.Rule) != 1 || raw.Rule[0] != "MATCH,DIRECT" {
		t.Fatalf("proxies %v, rules %v", raw.Proxy, raw.Rule)
	}
	if _, err := config.ParseRawConfig(raw); err != nil {
		t.Fatalf("ParseRawConfig: %v", err)
	}
}

func TestAddDirectTrafficCountsAsProxied(t *testing.T) {
	statistic.DefaultManager.ResetStatistic()
	handleAddDirectTraffic(1000, 3000)
	handleAddDirectTraffic(-5, 0)
	up, down := statistic.DefaultManager.TotalTraffic(true)
	if up != 1000 || down != 3000 {
		t.Fatalf("proxy totals = %d/%d, want 1000/3000", up, down)
	}
	up, down = statistic.DefaultManager.TotalTraffic(false)
	if up != 1000 || down != 3000 {
		t.Fatalf("totals = %d/%d, want 1000/3000", up, down)
	}
}
