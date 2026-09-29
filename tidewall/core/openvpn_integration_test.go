//go:build integration

package libcore

import (
	"bufio"
	"context"
	"fmt"
	"io"
	"net"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/metacubex/mihomo/adapter"
	C "github.com/metacubex/mihomo/constant"
	"go.yaml.in/yaml/v3"
)

// Run through testdata/openvpn-e2e.sh, which starts real OpenVPN servers.
func TestOpenVPNIntegration(t *testing.T) {
	dir := os.Getenv("TIDEWALL_OVPN_DIR")
	if dir == "" {
		t.Skip("run via testdata/openvpn-e2e.sh")
	}
	cases := []struct {
		name, file, user, pass, target string
	}{
		{"udp tls-auth user/pass", "udp-client.ovpn", "alice", "s3cret", "10.88.0.1:18080"},
		{"tcp tls-crypt", "tcp-client.ovpn", "", "", "10.89.0.1:18080"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			text, err := os.ReadFile(filepath.Join(dir, tc.file))
			if err != nil {
				t.Fatal(err)
			}
			// Same path as the app: .ovpn -> Proxy-mode profile YAML -> mihomo.
			profile, err := ProfileFromOvpn(string(text), "e2e", tc.user, tc.pass)
			if err != nil {
				t.Fatal(err)
			}
			var doc struct {
				Proxies []map[string]any `yaml:"proxies"`
			}
			if err := yaml.Unmarshal([]byte(profile), &doc); err != nil {
				t.Fatal(err)
			}
			proxy, err := adapter.ParseProxy(doc.Proxies[0])
			if err != nil {
				t.Fatal(err)
			}
			host, port, _ := net.SplitHostPort(tc.target)
			var p uint16
			fmt.Sscan(port, &p)

			ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
			defer cancel()
			conn, err := proxy.DialContext(ctx, &C.Metadata{NetWork: C.TCP, Host: host, DstPort: p})
			if err != nil {
				t.Fatalf("dial through OpenVPN: %v", err)
			}
			defer conn.Close()
			_ = conn.SetDeadline(time.Now().Add(15 * time.Second))
			if _, err := io.WriteString(conn, "GET /index.html HTTP/1.0\r\nHost: "+host+"\r\n\r\n"); err != nil {
				t.Fatal(err)
			}
			body, err := io.ReadAll(bufio.NewReader(conn))
			if err != nil && len(body) == 0 {
				t.Fatal(err)
			}
			if !strings.Contains(string(body), "tidewall-openvpn-ok") {
				t.Fatalf("unexpected response: %q", body)
			}
			t.Logf("fetched through %s: %d bytes", tc.name, len(body))
		})
	}
}
