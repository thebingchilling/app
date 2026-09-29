package libcore

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/netip"
	"strings"
	"sync"

	"github.com/metacubex/mihomo/component/process"
	"github.com/metacubex/mihomo/component/resolver"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/constant/features"
	"github.com/metacubex/mihomo/hub/executor"
	"github.com/metacubex/mihomo/listener"
	"github.com/metacubex/mihomo/tunnel"
	"github.com/metacubex/mihomo/tunnel/statistic"
	"golang.org/x/sys/unix"
)

// TunAddress is the IPv4 address mihomo assigns to the TUN interface. It is
// derived from the fake-ip range (the /30 at the start of it), so the Android
// VpnService must use the same address.
const (
	TunAddress4    = "198.18.0.1"
	TunPrefix4     = 30
	TunDNS4        = "198.18.0.2"
	TunAddress6    = "fdfe:dcba:9876::1"
	TunPrefix6     = 126
	defaultFakeIP  = "198.18.0.1/16"
	defaultTestURL = "https://www.gstatic.com/generate_204"
)

// proxyOptions are the app-level overrides applied on top of a profile.
type proxyOptions struct {
	Mode        string `json:"mode"`        // rule, global, direct; empty keeps the profile's
	IPv6        bool   `json:"ipv6"`        // route IPv6 through the tunnel
	Stack       string `json:"stack"`       // gvisor, system, mixed
	MTU         int    `json:"mtu"`         // TUN MTU
	LogLevel    string `json:"logLevel"`    // debug, info, warning, error, silent
	OverrideDNS bool   `json:"overrideDns"` // replace the profile's DNS with Tidewall's defaults
	Sniffing    bool   `json:"sniffing"`    // enable the TLS/HTTP domain sniffer
}

var (
	proxyMu      sync.Mutex
	proxyRunning bool
)

// defaultDNS is used when a profile has no DNS section, or the user asks to
// override it. fake-ip keeps DNS answers local so domain rules always match.
func defaultDNS(ipv6 bool) config.RawDNS {
	d := config.DefaultRawConfig().DNS
	d.Enable = true
	d.IPv6 = ipv6
	d.EnhancedMode = C.DNSFakeIP
	d.FakeIPRange = defaultFakeIP
	d.DefaultNameserver = []string{"1.1.1.1", "8.8.8.8", "223.5.5.5"}
	d.NameServer = []string{"https://1.1.1.1/dns-query", "https://dns.google/dns-query"}
	d.ProxyServerNameserver = []string{"https://1.1.1.1/dns-query", "223.5.5.5"}
	d.FakeIPFilter = []string{"+.lan", "+.local", "time.*.com", "ntp.*.com", "+.msftconnecttest.com"}
	return d
}

// buildRawConfig parses a profile and applies Tidewall's mandatory overrides:
// no local listeners or controllers (other apps on the phone could reach
// them), TUN fed from the VpnService file descriptor, and working DNS.
func buildRawConfig(profile []byte, fd int, opts proxyOptions) (*config.RawConfig, error) {
	raw, err := config.UnmarshalRawConfig(profile)
	if err != nil {
		return nil, fmt.Errorf("profile is not valid YAML: %w", err)
	}

	raw.Port, raw.SocksPort, raw.MixedPort, raw.RedirPort, raw.TProxyPort = 0, 0, 0, 0, 0
	raw.Listeners = nil
	raw.AllowLan = false
	raw.ExternalController = ""
	raw.ExternalControllerTLS = ""
	raw.ExternalControllerUnix = ""
	raw.ExternalControllerPipe = ""
	raw.ExternalUI = ""
	raw.FindProcessMode = process.FindProcessOff
	raw.Profile.StoreSelected = true
	raw.Profile.StoreFakeIP = true
	raw.IPv6 = opts.IPv6

	switch strings.ToLower(opts.Mode) {
	case "rule":
		raw.Mode = tunnel.Rule
	case "global":
		raw.Mode = tunnel.Global
	case "direct":
		raw.Mode = tunnel.Direct
	}
	if opts.LogLevel != "" {
		if err := raw.LogLevel.UnmarshalText([]byte(opts.LogLevel)); err != nil {
			return nil, err
		}
	}

	if opts.OverrideDNS || !raw.DNS.Enable {
		raw.DNS = defaultDNS(opts.IPv6)
	}
	raw.DNS.Listen = ""
	// The TUN address is carved out of the fake-ip range, so pin the range
	// to the one the VpnService is configured with.
	raw.DNS.FakeIPRange = defaultFakeIP

	if opts.Sniffing {
		raw.Sniffer.Enable = true
		if len(raw.Sniffer.Sniff) == 0 && len(raw.Sniffer.Sniffing) == 0 {
			raw.Sniffer.Sniff = map[string]config.RawSniffingConfig{
				"TLS":  {Ports: []string{"443", "8443"}},
				"HTTP": {Ports: []string{"80", "8080-8880"}},
				"QUIC": {Ports: []string{"443", "8443"}},
			}
		}
	}

	stack := C.TunGvisor
	if opts.Stack != "" {
		s, ok := C.StackTypeMapping[strings.ToLower(opts.Stack)]
		if !ok {
			return nil, fmt.Errorf("unknown TUN stack %q", opts.Stack)
		}
		stack = s
	}
	if fd > 0 && stack != C.TunSystem && !features.WithGVisor {
		return nil, fmt.Errorf("the %s TUN stack needs a core built with the with_gvisor tag", stack)
	}
	mtu := opts.MTU
	if mtu <= 0 {
		mtu = 9000
	}
	raw.Tun = config.RawTun{
		Enable:              fd > 0,
		Stack:               stack,
		DNSHijack:           []string{"any:53", "tcp://any:53"},
		AutoRoute:           false,
		AutoDetectInterface: false,
		MTU:                 uint32(mtu),
		FileDescriptor:      fd,
	}
	if opts.IPv6 {
		raw.Tun.Inet6Address = []netip.Prefix{netip.PrefixFrom(netip.MustParseAddr(TunAddress6), TunPrefix6)}
	}
	return raw, nil
}

// ValidateProfile checks that a profile parses and all proxies, groups and
// rules are valid, without starting anything. Returns nil when valid.
func ValidateProfile(profileYAML string) error {
	raw, err := buildRawConfig([]byte(profileYAML), 0, proxyOptions{})
	if err != nil {
		return err
	}
	_, err = config.ParseRawConfig(raw)
	return err
}

// StartProxy runs mihomo on the given TUN file descriptor. The engine takes
// ownership of fd. optionsJSON is a proxyOptions object.
func StartProxy(fd int, profileYAML string, optionsJSON string) (err error) {
	if fd <= 0 {
		return errors.New("invalid TUN file descriptor")
	}
	// Until mihomo's TUN listener owns fd, close it on failure: a leaked
	// VPN interface would blackhole the phone's traffic.
	owned := false
	defer func() {
		if err != nil && !owned {
			_ = unix.Close(fd)
		}
	}()
	var opts proxyOptions
	if optionsJSON != "" {
		if err := json.Unmarshal([]byte(optionsJSON), &opts); err != nil {
			return fmt.Errorf("bad options: %w", err)
		}
	}
	raw, err := buildRawConfig([]byte(profileYAML), fd, opts)
	if err != nil {
		return err
	}
	cfg, err := config.ParseRawConfig(raw)
	if err != nil {
		return err
	}

	proxyMu.Lock()
	defer proxyMu.Unlock()
	executor.ApplyConfig(cfg, true)
	if !listener.GetTunConf().Enable {
		cleanupProxy()
		return errors.New("TUN listener failed to start, see logs")
	}
	owned = true
	proxyRunning = true
	emit("info", "Tidewall: proxy engine started ("+Version()+")")
	return nil
}

// ReloadProfile applies a new profile to a running engine, keeping the TUN.
// optionsJSON must match the options the engine was started with: TUN
// settings (stack, MTU, IPv6) can only change by reconnecting.
func ReloadProfile(profileYAML string, optionsJSON string) error {
	proxyMu.Lock()
	defer proxyMu.Unlock()
	if !proxyRunning {
		return errors.New("engine is not running")
	}
	var opts proxyOptions
	if optionsJSON != "" {
		if err := json.Unmarshal([]byte(optionsJSON), &opts); err != nil {
			return fmt.Errorf("bad options: %w", err)
		}
	}
	raw, err := buildRawConfig([]byte(profileYAML), listener.GetTunConf().FileDescriptor, opts)
	if err != nil {
		return err
	}
	cfg, err := config.ParseRawConfig(raw)
	if err != nil {
		return err
	}
	executor.ApplyConfig(cfg, false)
	return nil
}

// StopProxy stops the engine, closing every connection and the TUN.
func StopProxy() {
	proxyMu.Lock()
	defer proxyMu.Unlock()
	if !proxyRunning {
		return
	}
	cleanupProxy()
	emit("info", "Tidewall: proxy engine stopped")
}

func cleanupProxy() {
	statistic.DefaultManager.Range(func(c statistic.Tracker) bool {
		_ = c.Close()
		return true
	})
	listener.Cleanup()
	resolver.StoreFakePoolState()
	proxyRunning = false
}

// IsProxyRunning reports whether the proxy engine is active.
func IsProxyRunning() bool {
	proxyMu.Lock()
	defer proxyMu.Unlock()
	return proxyRunning
}
