package main

import (
	"encoding/base64"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/metacubex/mihomo/config"
)

const testLinks = "vless://b831381d-6324-4d53-ad4f-8cda48b30811@1.2.3.4:443?encryption=none&security=tls&sni=example.com&type=ws&path=%2Fws#Node%20A\n" +
	"trojan://secret@5.6.7.8:443?sni=example.org#Node%20B\n" +
	"ss://" + "YWVzLTEyOC1nY206cGFzcw" + "@9.9.9.9:8388#Node%20C\n"

func TestShareLinksBecomeAProfile(t *testing.T) {
	for name, input := range map[string]string{
		"plain":  testLinks,
		"base64": base64.StdEncoding.EncodeToString([]byte(testLinks)),
	} {
		t.Run(name, func(t *testing.T) {
			path := filepath.Join(t.TempDir(), "profile")
			if err := os.WriteFile(path, []byte(input), 0o644); err != nil {
				t.Fatal(err)
			}
			if msg := handleValidateConfig(path); msg != "" {
				t.Fatalf("validate: %s", msg)
			}
			saved, _ := os.ReadFile(path)
			raw, err := config.UnmarshalRawConfig(saved)
			if err != nil {
				t.Fatal(err)
			}
			if len(raw.Proxy) != 3 || len(raw.ProxyGroup) != 2 {
				t.Fatalf("proxies/groups = %d/%d:\n%s", len(raw.Proxy), len(raw.ProxyGroup), saved)
			}
			if raw.Proxy[0]["type"] != "vless" || raw.Proxy[1]["type"] != "trojan" || raw.Proxy[2]["type"] != "ss" {
				t.Fatalf("types: %v %v %v", raw.Proxy[0]["type"], raw.Proxy[1]["type"], raw.Proxy[2]["type"])
			}
			if _, err := config.ParseRawConfig(raw); err != nil {
				t.Fatalf("converted profile does not load: %v", err)
			}
		})
	}
}

func TestClashProfilesAreNotRewritten(t *testing.T) {
	profile := "proxies:\n  - {name: a, type: ss, server: 1.2.3.4, port: 8388, cipher: aes-128-gcm, password: x}\n"
	if _, ok := profileFromShareLinks([]byte(profile)); ok {
		t.Fatal("a Clash profile was converted")
	}
	if _, ok := profileFromShareLinks([]byte("just some text")); ok {
		t.Fatal("text without links was converted")
	}
	if out, ok := profileFromShareLinks([]byte(strings.Split(testLinks, "\n")[1])); !ok ||
		!strings.Contains(string(out), "MATCH,Proxy") {
		t.Fatalf("single link: %v\n%s", ok, out)
	}
}
