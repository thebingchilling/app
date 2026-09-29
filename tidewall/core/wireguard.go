package libcore

import (
	"bufio"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/netip"
	"os"
	"strconv"
	"strings"
	"sync"

	awg "github.com/metacubex/amneziawg-go/device_v1"
	"github.com/metacubex/wireguard-go/conn"
	wgdevice "github.com/metacubex/wireguard-go/device"
	"github.com/metacubex/wireguard-go/tun"
	"golang.org/x/sys/unix"
)

type wgPeer struct {
	PublicKey    string
	PresharedKey string
	Endpoint     string // host:port as written in the file
	AllowedIPs   []string
	Keepalive    int
}

type wgConfig struct {
	PrivateKey string
	Addresses  []string
	DNS        []string
	Search     []string
	MTU        int
	ListenPort int
	Amnezia    map[string]string // jc, jmin, jmax, s1-s4, h1-h4, i1-i5, j1-j3, itime
	Peers      []wgPeer
}

var awgKeys = map[string]bool{
	"jc": true, "jmin": true, "jmax": true, "s1": true, "s2": true, "s3": true, "s4": true,
	"h1": true, "h2": true, "h3": true, "h4": true, "i1": true, "i2": true, "i3": true,
	"i4": true, "i5": true, "j1": true, "j2": true, "j3": true, "itime": true,
}

func splitList(v string) []string {
	var out []string
	for _, s := range strings.Split(v, ",") {
		if s = strings.TrimSpace(s); s != "" {
			out = append(out, s)
		}
	}
	return out
}

func parseWireGuard(text string) (*wgConfig, error) {
	cfg := &wgConfig{Amnezia: map[string]string{}}
	section := ""
	sc := bufio.NewScanner(strings.NewReader(text))
	for sc.Scan() {
		line := sc.Text()
		if i := strings.IndexAny(line, "#;"); i >= 0 {
			line = line[:i]
		}
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		if strings.HasPrefix(line, "[") {
			section = strings.ToLower(strings.Trim(line, "[] "))
			if section == "peer" {
				cfg.Peers = append(cfg.Peers, wgPeer{})
			}
			continue
		}
		k, v, ok := strings.Cut(line, "=")
		if !ok {
			return nil, fmt.Errorf("invalid line %q", line)
		}
		k = strings.ToLower(strings.TrimSpace(k))
		v = strings.TrimSpace(v)
		switch section {
		case "interface":
			switch {
			case k == "privatekey":
				cfg.PrivateKey = v
			case k == "address":
				cfg.Addresses = append(cfg.Addresses, splitList(v)...)
			case k == "dns":
				for _, d := range splitList(v) {
					if _, err := netip.ParseAddr(d); err == nil {
						cfg.DNS = append(cfg.DNS, d)
					} else {
						cfg.Search = append(cfg.Search, d)
					}
				}
			case k == "mtu":
				cfg.MTU, _ = strconv.Atoi(v)
			case k == "listenport":
				cfg.ListenPort, _ = strconv.Atoi(v)
			case awgKeys[k]:
				cfg.Amnezia[k] = v
			}
		case "peer":
			p := &cfg.Peers[len(cfg.Peers)-1]
			switch k {
			case "publickey":
				p.PublicKey = v
			case "presharedkey":
				p.PresharedKey = v
			case "endpoint":
				p.Endpoint = v
			case "allowedips":
				p.AllowedIPs = append(p.AllowedIPs, splitList(v)...)
			case "persistentkeepalive":
				p.Keepalive, _ = strconv.Atoi(v)
			}
		}
	}
	if cfg.PrivateKey == "" {
		return nil, errors.New("missing [Interface] PrivateKey")
	}
	if len(cfg.Peers) == 0 {
		return nil, errors.New("no [Peer] section")
	}
	for i, p := range cfg.Peers {
		if p.PublicKey == "" {
			return nil, fmt.Errorf("peer %d has no PublicKey", i+1)
		}
	}
	if len(cfg.Addresses) == 0 {
		return nil, errors.New("missing [Interface] Address")
	}
	return cfg, nil
}

func keyToHex(b64 string) (string, error) {
	b, err := base64.StdEncoding.DecodeString(b64)
	if err != nil || len(b) != 32 {
		return "", fmt.Errorf("invalid key %q", b64)
	}
	return hex.EncodeToString(b), nil
}

// wgInfo is what the Android side needs to configure VpnService.Builder.
type wgInfo struct {
	Addresses []string `json:"addresses"`
	DNS       []string `json:"dns"`
	Search    []string `json:"search"`
	MTU       int      `json:"mtu"`
	Routes    []string `json:"routes"`
	Endpoints []string `json:"endpoints"` // hosts to resolve before starting
	Amnezia   bool     `json:"amnezia"`
}

// InspectWireGuard parses a wg-quick style .conf (WireGuard or AmneziaWG)
// and returns the tunnel settings as JSON (see wgInfo).
func InspectWireGuard(text string) (string, error) {
	cfg, err := parseWireGuard(text)
	if err != nil {
		return "", err
	}
	info := wgInfo{
		Addresses: cfg.Addresses,
		DNS:       cfg.DNS,
		Search:    cfg.Search,
		MTU:       cfg.MTU,
		Amnezia:   len(cfg.Amnezia) > 0,
	}
	if info.MTU == 0 {
		info.MTU = 1280
	}
	for _, p := range cfg.Peers {
		info.Routes = append(info.Routes, p.AllowedIPs...)
		if p.Endpoint != "" {
			host, _, err := net.SplitHostPort(p.Endpoint)
			if err != nil {
				return "", fmt.Errorf("bad endpoint %q: %w", p.Endpoint, err)
			}
			info.Endpoints = append(info.Endpoints, host)
		}
	}
	b, err := json.Marshal(info)
	return string(b), err
}

// buildUAPI renders the config in the userspace API format. resolved maps
// endpoint hosts to IP addresses (the phone resolves them before the tunnel
// is up; Go's resolver cannot read Android's DNS settings).
func buildUAPI(cfg *wgConfig, resolved map[string]string) (string, error) {
	var b strings.Builder
	pk, err := keyToHex(cfg.PrivateKey)
	if err != nil {
		return "", err
	}
	fmt.Fprintf(&b, "private_key=%s\n", pk)
	if cfg.ListenPort > 0 {
		fmt.Fprintf(&b, "listen_port=%d\n", cfg.ListenPort)
	}
	for _, k := range []string{"jc", "jmin", "jmax", "s1", "s2", "s3", "s4", "h1", "h2", "h3", "h4", "i1", "i2", "i3", "i4", "i5", "j1", "j2", "j3", "itime"} {
		if v, ok := cfg.Amnezia[k]; ok {
			fmt.Fprintf(&b, "%s=%s\n", k, v)
		}
	}
	b.WriteString("replace_peers=true\n")
	for _, p := range cfg.Peers {
		pub, err := keyToHex(p.PublicKey)
		if err != nil {
			return "", err
		}
		fmt.Fprintf(&b, "public_key=%s\n", pub)
		if p.PresharedKey != "" {
			psk, err := keyToHex(p.PresharedKey)
			if err != nil {
				return "", err
			}
			fmt.Fprintf(&b, "preshared_key=%s\n", psk)
		}
		if p.Endpoint != "" {
			host, port, err := net.SplitHostPort(p.Endpoint)
			if err != nil {
				return "", err
			}
			ip := host
			if _, err := netip.ParseAddr(host); err != nil {
				ip = resolved[host]
				if ip == "" {
					return "", fmt.Errorf("could not resolve endpoint %q", host)
				}
			}
			fmt.Fprintf(&b, "endpoint=%s\n", net.JoinHostPort(ip, port))
		}
		if p.Keepalive > 0 {
			fmt.Fprintf(&b, "persistent_keepalive_interval=%d\n", p.Keepalive)
		}
		b.WriteString("replace_allowed_ips=true\n")
		for _, a := range p.AllowedIPs {
			fmt.Fprintf(&b, "allowed_ip=%s\n", a)
		}
	}
	return b.String(), nil
}

// fdTun is a tun.Device over the file descriptor from Android's VpnService.
type fdTun struct {
	file      *os.File
	mtu       int
	events    chan tun.Event
	closeOnce sync.Once
}

func newFdTun(fd, mtu int) (*fdTun, error) {
	if err := unix.SetNonblock(fd, true); err != nil {
		return nil, err
	}
	t := &fdTun{file: os.NewFile(uintptr(fd), "tun"), mtu: mtu, events: make(chan tun.Event, 1)}
	t.events <- tun.EventUp
	return t, nil
}

func (t *fdTun) File() *os.File           { return t.file }
func (t *fdTun) MTU() (int, error)        { return t.mtu, nil }
func (t *fdTun) Name() (string, error)    { return "tun", nil }
func (t *fdTun) Events() <-chan tun.Event { return t.events }
func (t *fdTun) BatchSize() int           { return 1 }
func (t *fdTun) Read(bufs [][]byte, sizes []int, offset int) (int, error) {
	n, err := t.file.Read(bufs[0][offset:])
	if err != nil {
		return 0, err
	}
	sizes[0] = n
	return 1, nil
}

func (t *fdTun) Write(bufs [][]byte, offset int) (int, error) {
	for i, b := range bufs {
		if _, err := t.file.Write(b[offset:]); err != nil {
			return i, err
		}
	}
	return len(bufs), nil
}

func (t *fdTun) Close() error {
	var err error
	t.closeOnce.Do(func() {
		close(t.events)
		err = t.file.Close()
	})
	return err
}

var (
	wgMu     sync.Mutex
	wgDevice *awg.Device
)

// StartWireGuard runs a WireGuard/AmneziaWG tunnel directly on the TUN fd
// (Direct WireGuard mode). The engine takes ownership of fd. resolvedJSON is
// an object mapping endpoint hostnames to IP addresses.
func StartWireGuard(fd int, confText string, resolvedJSON string) (err error) {
	// fdTun owns fd once created (dev.Close closes it); before that, close it
	// here on failure so the VPN interface does not linger.
	owned := false
	defer func() {
		if err != nil && !owned {
			_ = unix.Close(fd)
		}
	}()
	cfg, err := parseWireGuard(confText)
	if err != nil {
		return err
	}
	resolved := map[string]string{}
	if resolvedJSON != "" {
		if err := json.Unmarshal([]byte(resolvedJSON), &resolved); err != nil {
			return fmt.Errorf("bad resolved endpoints: %w", err)
		}
	}
	uapi, err := buildUAPI(cfg, resolved)
	if err != nil {
		return err
	}
	mtu := cfg.MTU
	if mtu == 0 {
		mtu = 1280
	}

	wgMu.Lock()
	defer wgMu.Unlock()
	if wgDevice != nil {
		wgDevice.Close()
		wgDevice = nil
	}
	t, err := newFdTun(fd, mtu)
	if err != nil {
		return err
	}
	owned = true
	logger := &wgdevice.Logger{
		Verbosef: func(format string, args ...any) { emit("debug", "[WireGuard] "+fmt.Sprintf(format, args...)) },
		Errorf:   func(format string, args ...any) { emit("error", "[WireGuard] "+fmt.Sprintf(format, args...)) },
	}
	dev := awg.NewDevice(t, conn.NewDefaultBind(), logger, 0)
	if err := dev.IpcSet(uapi); err != nil {
		dev.Close()
		return fmt.Errorf("WireGuard config rejected: %w", err)
	}
	if err := dev.Up(); err != nil {
		dev.Close()
		return err
	}
	wgDevice = dev
	emit("info", "Tidewall: Direct WireGuard started")
	return nil
}

// StopWireGuard tears down the Direct WireGuard tunnel.
func StopWireGuard() {
	wgMu.Lock()
	defer wgMu.Unlock()
	if wgDevice != nil {
		wgDevice.Close()
		wgDevice = nil
		emit("info", "Tidewall: Direct WireGuard stopped")
	}
}

// WireGuardStats returns "rx,tx,lastHandshakeUnixSec" summed over peers.
func WireGuardStats() string {
	wgMu.Lock()
	dev := wgDevice
	wgMu.Unlock()
	if dev == nil {
		return "0,0,0"
	}
	state, err := dev.IpcGet()
	if err != nil {
		return "0,0,0"
	}
	var rx, tx, hs int64
	for _, line := range strings.Split(state, "\n") {
		k, v, _ := strings.Cut(line, "=")
		n, _ := strconv.ParseInt(v, 10, 64)
		switch k {
		case "rx_bytes":
			rx += n
		case "tx_bytes":
			tx += n
		case "last_handshake_time_sec":
			if n > hs {
				hs = n
			}
		}
	}
	return fmt.Sprintf("%d,%d,%d", rx, tx, hs)
}

// ProfileFromWireGuard converts a .conf into a Proxy-mode profile, so
// WireGuard can be combined with rules or chained behind other proxies.
func ProfileFromWireGuard(text string, name string) (string, error) {
	cfg, err := parseWireGuard(text)
	if err != nil {
		return "", err
	}
	if len(cfg.Peers) != 1 {
		return "", errors.New("Proxy mode supports WireGuard configs with exactly one peer")
	}
	peer := cfg.Peers[0]
	if peer.Endpoint == "" {
		return "", errors.New("peer has no Endpoint")
	}
	host, portStr, err := net.SplitHostPort(peer.Endpoint)
	if err != nil {
		return "", err
	}
	port, _ := strconv.Atoi(portStr)
	if name == "" {
		name = "WireGuard " + host
	}
	p := map[string]any{
		"name":        name,
		"type":        "wireguard",
		"server":      host,
		"port":        port,
		"private-key": cfg.PrivateKey,
		"public-key":  peer.PublicKey,
		"allowed-ips": peer.AllowedIPs,
		"udp":         true,
	}
	for _, a := range cfg.Addresses {
		pfx, err := netip.ParsePrefix(a)
		if err != nil {
			addr, err2 := netip.ParseAddr(a)
			if err2 != nil {
				return "", fmt.Errorf("bad address %q", a)
			}
			pfx = netip.PrefixFrom(addr, addr.BitLen())
		}
		if pfx.Addr().Is4() {
			p["ip"] = pfx.Addr().String()
		} else {
			p["ipv6"] = pfx.Addr().String()
		}
	}
	if peer.PresharedKey != "" {
		p["pre-shared-key"] = peer.PresharedKey
	}
	if peer.Keepalive > 0 {
		p["persistent-keepalive"] = peer.Keepalive
	}
	if cfg.MTU > 0 {
		p["mtu"] = cfg.MTU
	}
	if len(cfg.DNS) > 0 {
		p["remote-dns-resolve"] = true
		p["dns"] = cfg.DNS
	}
	if len(cfg.Amnezia) > 0 {
		opt := map[string]any{}
		for k, v := range cfg.Amnezia {
			// jc/jmin/jmax/s1-s4/itime are integers in mihomo; h*, i* and j*
			// are strings (ranges and packet templates).
			numeric := k == "itime" || k == "jc" || k == "jmin" || k == "jmax" || strings.HasPrefix(k, "s")
			if n, err := strconv.ParseInt(v, 10, 64); numeric && err == nil {
				opt[k] = n
			} else {
				opt[k] = v
			}
		}
		p["amnezia-wg-option"] = opt
	}
	if err := validateProxy(p); err != nil {
		return "", err
	}
	return profileFromProxies([]map[string]any{p})
}
