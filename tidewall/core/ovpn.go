package libcore

import (
	"bufio"
	"encoding/json"
	"fmt"
	"strconv"
	"strings"
)

// OvpnInfo summarises a .ovpn file for the import screen.
type ovpnInfo struct {
	Name          string   `json:"name"`
	Server        string   `json:"server"`
	Port          int      `json:"port"`
	Proto         string   `json:"proto"`
	NeedsPassword bool     `json:"needsPassword"`
	Unsupported   []string `json:"unsupported"` // directives Proxy mode ignores
}

type ovpnFile struct {
	directives map[string][]string // directive -> args of the last occurrence
	remotes    [][]string
	blocks     map[string]string // inline <ca>, <cert>, ...
	order      []string
}

// directives mihomo's openvpn outbound understands, or that are harmless to
// drop because they only affect a local tun device.
var ovpnKnown = map[string]bool{
	"client": true, "dev": true, "proto": true, "remote": true, "port": true,
	"cipher": true, "data-ciphers": true, "ncp-ciphers": true, "data-ciphers-fallback": true,
	"auth": true, "comp-lzo": true, "compress": true, "ca": true, "cert": true, "key": true,
	"tls-auth": true, "key-direction": true, "tls-crypt": true, "tls-crypt-v2": true,
	"auth-user-pass": true, "ping": true, "ping-restart": true, "keepalive": true,
	"tran-window": true, "tun-mtu": true, "nobind": true, "persist-key": true,
	"persist-tun": true, "resolv-retry": true, "verb": true, "mute": true,
	"remote-cert-tls": true, "tls-client": true, "pull": true, "auth-nocache": true,
	"setenv": true, "redirect-gateway": true, "block-outside-dns": true, "mute-replay-warnings": true,
	"remote-random": true, "float": true, "explicit-exit-notify": true, "sndbuf": true, "rcvbuf": true,
	"connect-retry": true, "connect-retry-max": true, "server-poll-timeout": true, "hand-window": true,
	"reneg-sec": true, "tls-version-min": true, "verify-x509-name": true, "dhcp-option": true,
	"route-delay": true, "route-method": true, "script-security": true, "ignore-unknown-option": true,
	"tls-cipher": true, "tls-ciphersuites": true, "allow-compression": true,
}

func parseOvpn(text string) (*ovpnFile, error) {
	f := &ovpnFile{directives: map[string][]string{}, blocks: map[string]string{}}
	sc := bufio.NewScanner(strings.NewReader(text))
	sc.Buffer(make([]byte, 64*1024), 4*1024*1024)
	var block string
	var body strings.Builder
	for sc.Scan() {
		line := strings.TrimSpace(sc.Text())
		if block != "" {
			if line == "</"+block+">" {
				f.blocks[block] = strings.TrimSpace(body.String()) + "\n"
				block = ""
				body.Reset()
				continue
			}
			body.WriteString(line)
			body.WriteString("\n")
			continue
		}
		if line == "" || line[0] == '#' || line[0] == ';' {
			continue
		}
		if strings.HasPrefix(line, "<") && strings.HasSuffix(line, ">") && !strings.HasPrefix(line, "</") {
			block = strings.Trim(line, "<>")
			continue
		}
		fields := splitOvpnArgs(line)
		name := strings.ToLower(fields[0])
		args := fields[1:]
		if name == "remote" {
			f.remotes = append(f.remotes, args)
		}
		if _, dup := f.directives[name]; !dup {
			f.order = append(f.order, name)
		}
		f.directives[name] = args
	}
	if err := sc.Err(); err != nil {
		return nil, err
	}
	if block != "" {
		return nil, fmt.Errorf("unterminated <%s> block", block)
	}
	if len(f.remotes) == 0 {
		return nil, fmt.Errorf("no remote server in .ovpn file")
	}
	return f, nil
}

// splitOvpnArgs splits a directive line, honouring double quotes.
func splitOvpnArgs(line string) []string {
	var out []string
	var cur strings.Builder
	inQuote := false
	for _, r := range line {
		switch {
		case r == '"':
			inQuote = !inQuote
		case (r == ' ' || r == '\t') && !inQuote:
			if cur.Len() > 0 {
				out = append(out, cur.String())
				cur.Reset()
			}
		default:
			cur.WriteRune(r)
		}
	}
	if cur.Len() > 0 {
		out = append(out, cur.String())
	}
	return out
}

func (f *ovpnFile) arg(name string, i int) string {
	a := f.directives[name]
	if i < len(a) {
		return a[i]
	}
	return ""
}

func (f *ovpnFile) has(name string) bool {
	_, ok := f.directives[name]
	return ok
}

// endpoint returns the first remote's host, port and protocol.
func (f *ovpnFile) endpoint() (string, int, string) {
	r := f.remotes[0]
	host := r[0]
	port := 1194
	if p := f.arg("port", 0); p != "" {
		port, _ = strconv.Atoi(p)
	}
	if len(r) > 1 {
		if p, err := strconv.Atoi(r[1]); err == nil {
			port = p
		}
	}
	proto := strings.ToLower(f.arg("proto", 0))
	if len(r) > 2 {
		proto = strings.ToLower(r[2])
	}
	switch {
	case strings.HasPrefix(proto, "tcp"):
		proto = "tcp"
	default:
		proto = "udp"
	}
	return host, port, proto
}

// InspectOvpn returns a JSON summary of a .ovpn file (see ovpnInfo).
func InspectOvpn(text string) (string, error) {
	f, err := parseOvpn(text)
	if err != nil {
		return "", err
	}
	host, port, proto := f.endpoint()
	info := ovpnInfo{
		Name:          host,
		Server:        host,
		Port:          port,
		Proto:         proto,
		NeedsPassword: f.has("auth-user-pass") && len(f.directives["auth-user-pass"]) == 0,
	}
	if f.has("auth-user-pass") && len(f.directives["auth-user-pass"]) > 0 {
		// auth-user-pass pointing at a file cannot be read on the phone.
		info.NeedsPassword = true
	}
	for _, d := range f.order {
		if !ovpnKnown[d] {
			info.Unsupported = append(info.Unsupported, d)
		}
	}
	if f.arg("dev", 0) != "" && strings.HasPrefix(f.arg("dev", 0), "tap") {
		info.Unsupported = append(info.Unsupported, "dev tap (bridged mode)")
	}
	b, err := json.Marshal(info)
	return string(b), err
}

// OvpnToProxy converts a .ovpn file into a mihomo "openvpn" proxy mapping.
func ovpnToProxy(text, name, username, password string) (map[string]any, error) {
	f, err := parseOvpn(text)
	if err != nil {
		return nil, err
	}
	host, port, proto := f.endpoint()
	if name == "" {
		name = "OpenVPN " + host
	}
	p := map[string]any{
		"name":   name,
		"type":   "openvpn",
		"server": host,
		"port":   port,
		"proto":  proto,
		"udp":    true,
	}
	if ca, ok := f.blocks["ca"]; ok {
		p["ca"] = ca
	} else {
		return nil, fmt.Errorf("the .ovpn file has no inline <ca> block")
	}
	if v, ok := f.blocks["cert"]; ok {
		p["cert"] = v
	}
	if v, ok := f.blocks["key"]; ok {
		p["key"] = v
	}
	if v, ok := f.blocks["tls-auth"]; ok {
		p["tls-auth"] = v
		kd := f.arg("key-direction", 0)
		if kd == "" && len(f.directives["tls-auth"]) > 1 {
			kd = f.arg("tls-auth", 1)
		}
		if kd != "" {
			p["key-direction"] = kd
		}
	}
	if v, ok := f.blocks["tls-crypt"]; ok {
		p["tls-crypt"] = v
	}
	if v, ok := f.blocks["tls-crypt-v2"]; ok {
		p["tls-crypt-v2"] = v
	}
	if v := f.arg("cipher", 0); v != "" {
		p["cipher"] = v
	}
	dc := f.arg("data-ciphers", 0)
	if dc == "" {
		dc = f.arg("ncp-ciphers", 0)
	}
	if dc != "" {
		p["data-ciphers"] = strings.Split(dc, ":")
	}
	if v := f.arg("data-ciphers-fallback", 0); v != "" {
		p["data-ciphers-fallback"] = v
	}
	if v := f.arg("auth", 0); v != "" {
		p["auth"] = v
	}
	if f.has("comp-lzo") {
		v := f.arg("comp-lzo", 0)
		if v == "" {
			v = "adaptive"
		}
		p["comp-lzo"] = v
	}
	if f.has("keepalive") {
		if v, err := strconv.Atoi(f.arg("keepalive", 0)); err == nil {
			p["ping"] = v
		}
		if v, err := strconv.Atoi(f.arg("keepalive", 1)); err == nil {
			p["ping-restart"] = v
		}
	}
	if v, err := strconv.Atoi(f.arg("ping", 0)); err == nil {
		p["ping"] = v
	}
	if v, err := strconv.Atoi(f.arg("ping-restart", 0)); err == nil {
		p["ping-restart"] = v
	}
	if v, err := strconv.Atoi(f.arg("tun-mtu", 0)); err == nil {
		p["mtu"] = v
	}
	if f.has("auth-user-pass") {
		if username == "" {
			return nil, fmt.Errorf("this server needs a username and password")
		}
		p["username"] = username
		p["password"] = password
	}
	return p, nil
}

// ProfileFromOvpn converts a .ovpn file into a Proxy-mode profile.
func ProfileFromOvpn(text, name, username, password string) (string, error) {
	p, err := ovpnToProxy(text, name, username, password)
	if err != nil {
		return "", err
	}
	if err := validateProxy(p); err != nil {
		return "", err
	}
	return profileFromProxies([]map[string]any{p})
}
