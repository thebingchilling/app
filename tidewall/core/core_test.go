package libcore

import (
	"context"
	"crypto/ecdh"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"encoding/pem"
	"io"
	"math/big"
	"net"
	"net/netip"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/metacubex/mihomo/adapter"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/constant/features"
	"github.com/metacubex/mihomo/listener/inbound"
	"go.yaml.in/yaml/v3"
	"golang.org/x/sys/unix"
)

func ssLink(method, password, host string, port int, name string) string {
	user := base64.RawURLEncoding.EncodeToString([]byte(method + ":" + password))
	return "ss://" + user + "@" + net.JoinHostPort(host, strconv.Itoa(port)) + "#" + name
}

func TestProfileFromLinks(t *testing.T) {
	links := strings.Join([]string{
		ssLink("rc4-md5", "secret", "ss.example.com", 8388, "Legacy SS"),
		ssLink("aes-256-gcm", "secret", "ss2.example.com", 8389, "Modern SS"),
		"vless://b831381d-6324-4d53-ad4f-8cda48b30811@vless.example.com:443?encryption=none&security=reality&sni=www.microsoft.com&fp=chrome&pbk=Z84J2IelR9ch3k8VtlVhhs5ycBUlXA7wHBWcBLjtCkM&sid=6ba85179e30d4fc2&type=tcp&flow=xtls-rprx-vision#Reality",
		"trojan://password@trojan.example.com:443?sni=trojan.example.com#Trojan",
		ssLink("rc4-md5", "secret", "ss.example.com", 8388, "Legacy SS"), // duplicate name
	}, "\n")
	profile, err := ProfileFromLinks(links)
	if err != nil {
		t.Fatal(err)
	}
	if err := ValidateProfile(profile); err != nil {
		t.Fatalf("generated profile is invalid: %v\n%s", err, profile)
	}
	var doc struct {
		Proxies []map[string]any `yaml:"proxies"`
		Groups  []map[string]any `yaml:"proxy-groups"`
	}
	if err := yaml.Unmarshal([]byte(profile), &doc); err != nil {
		t.Fatal(err)
	}
	if len(doc.Proxies) != 5 {
		t.Fatalf("want 5 proxies, got %d", len(doc.Proxies))
	}
	if doc.Proxies[0]["cipher"] != "rc4-md5" {
		t.Fatalf("rc4-md5 cipher lost: %v", doc.Proxies[0])
	}
	names := map[any]bool{}
	for _, p := range doc.Proxies {
		if names[p["name"]] {
			t.Fatalf("duplicate proxy name %v", p["name"])
		}
		names[p["name"]] = true
	}
	if doc.Groups[0]["name"] != "Proxy" || doc.Groups[1]["name"] != "Auto" {
		t.Fatalf("unexpected groups: %v", doc.Groups)
	}
}

func TestProfileFromLinksEmpty(t *testing.T) {
	if _, err := ProfileFromLinks("not a link"); err == nil {
		t.Fatal("expected an error for text without links")
	}
}

func TestAddProxyToProfile(t *testing.T) {
	ss := "name: Manual SS\ntype: ss\nserver: 1.2.3.4\nport: 8388\ncipher: rc4-md5\npassword: pw\nudp: true\n"
	profile, err := AddProxyToProfile("", ss)
	if err != nil {
		t.Fatal(err)
	}
	if err := ValidateProfile(profile); err != nil {
		t.Fatal(err)
	}
	second := strings.Replace(ss, "Manual SS", "Second", 1)
	profile, err = AddProxyToProfile(profile, second)
	if err != nil {
		t.Fatal(err)
	}
	if err := ValidateProfile(profile); err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(profile, "Second") {
		t.Fatal("second proxy missing")
	}
	if _, err := AddProxyToProfile("", "name: bad\ntype: ss\nserver: x\nport: 1\ncipher: nope\npassword: p\n"); err == nil {
		t.Fatal("invalid cipher accepted")
	}
}

func TestBuildRawConfigOverrides(t *testing.T) {
	if !features.WithGVisor {
		t.Skip("run with -tags with_gvisor (see build.sh)")
	}
	profile := `
mixed-port: 7890
allow-lan: true
external-controller: 0.0.0.0:9090
mode: global
proxies: []
rules: ["MATCH,DIRECT"]
`
	raw, err := buildRawConfig([]byte(profile), 42, proxyOptions{Mode: "rule", IPv6: true, Stack: "mixed", MTU: 1500})
	if err != nil {
		t.Fatal(err)
	}
	if raw.MixedPort != 0 || raw.AllowLan || raw.ExternalController != "" {
		t.Fatal("local listeners were not stripped")
	}
	if raw.Mode.String() != "rule" {
		t.Fatalf("mode override ignored: %s", raw.Mode)
	}
	if !raw.Tun.Enable || raw.Tun.FileDescriptor != 42 || raw.Tun.MTU != 1500 || raw.Tun.Stack != C.TunMixed {
		t.Fatalf("tun not configured: %+v", raw.Tun)
	}
	if !raw.DNS.Enable || raw.DNS.EnhancedMode != C.DNSFakeIP {
		t.Fatal("default DNS not applied")
	}
	if len(raw.Tun.Inet6Address) != 1 {
		t.Fatal("IPv6 tun address missing")
	}
	cfg, err := parseRaw(raw)
	if err != nil {
		t.Fatal(err)
	}
	if got := cfg.General.Tun.Inet4Address[0].String(); got != TunAddress4+"/30" {
		t.Fatalf("tun address %s does not match the VpnService address", got)
	}
	if _, err := buildRawConfig([]byte(profile), 1, proxyOptions{Stack: "bogus"}); err == nil {
		t.Fatal("bogus stack accepted")
	}
}

// --- OpenVPN -----------------------------------------------------------------

func testPEM(t *testing.T) (caPEM, certPEM, keyPEM string) {
	t.Helper()
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	tmpl := &x509.Certificate{
		SerialNumber:          big.NewInt(1),
		Subject:               pkix.Name{CommonName: "Tidewall Test CA"},
		NotBefore:             time.Now().Add(-time.Hour),
		NotAfter:              time.Now().Add(time.Hour),
		IsCA:                  true,
		BasicConstraintsValid: true,
		KeyUsage:              x509.KeyUsageCertSign | x509.KeyUsageDigitalSignature,
	}
	der, err := x509.CreateCertificate(rand.Reader, tmpl, tmpl, &key.PublicKey, key)
	if err != nil {
		t.Fatal(err)
	}
	kder, _ := x509.MarshalPKCS8PrivateKey(key)
	c := string(pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der}))
	k := string(pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: kder}))
	return c, c, k
}

func staticKey() string {
	b := make([]byte, 256)
	_, _ = rand.Read(b)
	h := hex.EncodeToString(b)
	var sb strings.Builder
	sb.WriteString("-----BEGIN OpenVPN Static key V1-----\n")
	for i := 0; i < len(h); i += 32 {
		sb.WriteString(h[i:i+32] + "\n")
	}
	sb.WriteString("-----END OpenVPN Static key V1-----\n")
	return sb.String()
}

func sampleOvpn(t *testing.T, extra string) string {
	ca, cert, key := testPEM(t)
	return `# sample
client
dev tun
proto udp
remote vpn.example.com 1194
resolv-retry infinite
nobind
cipher AES-256-GCM
data-ciphers AES-256-GCM:CHACHA20-POLY1305
auth SHA256
key-direction 1
keepalive 10 60
` + extra + `
<ca>
` + ca + `</ca>
<cert>
` + cert + `</cert>
<key>
` + key + `</key>
<tls-auth>
` + staticKey() + `</tls-auth>
`
}

func TestInspectOvpn(t *testing.T) {
	js, err := InspectOvpn(sampleOvpn(t, "auth-user-pass\nfragment 1300\nremote backup.example.com 443 tcp"))
	if err != nil {
		t.Fatal(err)
	}
	var info ovpnInfo
	if err := json.Unmarshal([]byte(js), &info); err != nil {
		t.Fatal(err)
	}
	if info.Server != "vpn.example.com" || info.Port != 1194 || info.Proto != "udp" {
		t.Fatalf("wrong endpoint: %+v", info)
	}
	if !info.NeedsPassword {
		t.Fatal("auth-user-pass not detected")
	}
	if len(info.Unsupported) != 1 || info.Unsupported[0] != "fragment" {
		t.Fatalf("unsupported directives: %v", info.Unsupported)
	}
}

func TestProfileFromOvpn(t *testing.T) {
	text := sampleOvpn(t, "auth-user-pass")
	if _, err := ProfileFromOvpn(text, "", "", ""); err == nil {
		t.Fatal("expected a missing-credentials error")
	}
	profile, err := ProfileFromOvpn(text, "Office", "alice", "pw")
	if err != nil {
		t.Fatal(err)
	}
	if err := ValidateProfile(profile); err != nil {
		t.Fatalf("invalid profile: %v\n%s", err, profile)
	}
	p, err := ovpnToProxy(text, "Office", "alice", "pw")
	if err != nil {
		t.Fatal(err)
	}
	if p["key-direction"] != "1" || p["ping"] != 10 || p["ping-restart"] != 60 {
		t.Fatalf("directives not mapped: %v", p)
	}
	if dc := p["data-ciphers"].([]string); len(dc) != 2 || dc[1] != "CHACHA20-POLY1305" {
		t.Fatalf("data-ciphers: %v", dc)
	}
}

func TestOvpnErrors(t *testing.T) {
	if _, err := InspectOvpn("client\ndev tun\n"); err == nil {
		t.Fatal("missing remote accepted")
	}
	if _, err := InspectOvpn("remote a 1\n<ca>\nabc\n"); err == nil {
		t.Fatal("unterminated block accepted")
	}
}

// --- WireGuard ---------------------------------------------------------------

func wgKey(t *testing.T) (priv, pub string) {
	t.Helper()
	k, err := ecdhX25519()
	if err != nil {
		t.Fatal(err)
	}
	return base64.StdEncoding.EncodeToString(k.Bytes()), base64.StdEncoding.EncodeToString(k.PublicKey().Bytes())
}

func TestWireGuardParseAndUAPI(t *testing.T) {
	priv, _ := wgKey(t)
	_, peerPub := wgKey(t)
	conf := `[Interface]
PrivateKey = ` + priv + `
Address = 10.0.0.2/32, fd00::2/128
DNS = 1.1.1.1, example.internal
MTU = 1380
Jc = 4
Jmin = 40
Jmax = 70
S1 = 0
S2 = 0
H1 = 1
H2 = 2
H3 = 3
H4 = 4

[Peer]
PublicKey = ` + peerPub + `
AllowedIPs = 0.0.0.0/0, ::/0
Endpoint = wg.example.com:51820
PersistentKeepalive = 25
`
	js, err := InspectWireGuard(conf)
	if err != nil {
		t.Fatal(err)
	}
	var info wgInfo
	_ = json.Unmarshal([]byte(js), &info)
	if !info.Amnezia || info.MTU != 1380 || len(info.Addresses) != 2 || info.DNS[0] != "1.1.1.1" ||
		info.Search[0] != "example.internal" || info.Endpoints[0] != "wg.example.com" || len(info.Routes) != 2 {
		t.Fatalf("unexpected info: %+v", info)
	}

	cfg, _ := parseWireGuard(conf)
	if _, err := buildUAPI(cfg, nil); err == nil {
		t.Fatal("unresolved endpoint accepted")
	}
	uapi, err := buildUAPI(cfg, map[string]string{"wg.example.com": "203.0.113.5"})
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{"endpoint=203.0.113.5:51820", "jc=4", "h4=4", "persistent_keepalive_interval=25", "allowed_ip=::/0"} {
		if !strings.Contains(uapi, want) {
			t.Fatalf("UAPI missing %q:\n%s", want, uapi)
		}
	}

	profile, err := ProfileFromWireGuard(conf, "")
	if err != nil {
		t.Fatal(err)
	}
	if err := ValidateProfile(profile); err != nil {
		t.Fatalf("invalid profile: %v\n%s", err, profile)
	}
	if !strings.Contains(profile, "amnezia-wg-option") {
		t.Fatal("AmneziaWG options dropped")
	}
}

func TestWireGuardErrors(t *testing.T) {
	if _, err := InspectWireGuard("[Interface]\nAddress = 10.0.0.2/32\n"); err == nil {
		t.Fatal("missing key accepted")
	}
	priv, _ := wgKey(t)
	if _, err := InspectWireGuard("[Interface]\nPrivateKey = " + priv + "\nAddress = 10.0.0.2/32\n"); err == nil {
		t.Fatal("missing peer accepted")
	}
}

// --- End to end: rc4-md5 Shadowsocks -----------------------------------------

// echoTunnel answers every proxied TCP connection by echoing it back.
type echoTunnel struct{}

func (echoTunnel) HandleTCPConn(conn net.Conn, _ *C.Metadata) {
	defer conn.Close()
	_, _ = io.Copy(conn, conn)
}
func (echoTunnel) HandleUDPPacket(C.UDPPacket, *C.Metadata) {}
func (echoTunnel) NatTable() C.NatTable                     { return nil }

func TestShadowsocksRC4MD5EndToEnd(t *testing.T) {
	in, err := inbound.NewShadowSocks(&inbound.ShadowSocksOption{
		BaseOption: inbound.BaseOption{NameStr: "ss-in", Listen: "127.0.0.1", Port: "0"},
		Password:   "tidewall-test",
		Cipher:     "rc4-md5",
	})
	if err != nil {
		t.Fatal(err)
	}
	if err := in.Listen(echoTunnel{}); err != nil {
		t.Fatal(err)
	}
	defer in.Close()
	ap, err := netip.ParseAddrPort(in.Address())
	if err != nil {
		t.Fatal(err)
	}

	out, err := adapter.ParseProxy(map[string]any{
		"name": "ss", "type": "ss", "server": "127.0.0.1", "port": int(ap.Port()),
		"cipher": "rc4-md5", "password": "tidewall-test",
	})
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	conn, err := out.DialContext(ctx, &C.Metadata{NetWork: C.TCP, Host: "example.test", DstPort: 80})
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close()
	msg := []byte("hello through rc4-md5")
	// The stream cipher encrypts the write buffer in place, so send a copy.
	if _, err := conn.Write(append([]byte(nil), msg...)); err != nil {
		t.Fatal(err)
	}
	got := make([]byte, len(msg))
	_ = conn.SetReadDeadline(time.Now().Add(5 * time.Second))
	if _, err := io.ReadFull(conn, got); err != nil {
		t.Fatal(err)
	}
	if string(got) != string(msg) {
		t.Fatalf("echo mismatch: %q", got)
	}
}

func ecdhX25519() (*ecdh.PrivateKey, error) { return ecdh.X25519().GenerateKey(rand.Reader) }

func parseRaw(raw *config.RawConfig) (*config.Config, error) { return config.ParseRawConfig(raw) }

func TestProfileStats(t *testing.T) {
	js, err := ProfileStats("proxies:\n  - {name: a, type: ss}\n  - {name: b, type: ss}\nproxy-groups:\n  - {name: g, type: select, proxies: [a, b]}\nrules:\n  - MATCH,g\n")
	if err != nil {
		t.Fatal(err)
	}
	if js != `{"proxies":2,"groups":1,"providers":0,"rules":1}` {
		t.Fatalf("stats: %s", js)
	}
}

// A failed start must close the TUN fd, or Android keeps the VPN interface up.
func TestStartClosesFdOnError(t *testing.T) {
	for name, start := range map[string]func(fd int) error{
		"proxy":     func(fd int) error { return StartProxy(fd, "proxies: [", "") },
		"wireguard": func(fd int) error { return StartWireGuard(fd, "[Interface]\n", "") },
	} {
		fds, err := unix.Socketpair(unix.AF_UNIX, unix.SOCK_SEQPACKET, 0)
		if err != nil {
			t.Fatal(err)
		}
		if err := start(fds[0]); err == nil {
			t.Fatalf("%s: expected an error", name)
		}
		if _, err := unix.FcntlInt(uintptr(fds[0]), unix.F_GETFD, 0); err != unix.EBADF {
			t.Fatalf("%s: fd still open after failed start (err=%v)", name, err)
		}
		unix.Close(fds[1])
	}
}
