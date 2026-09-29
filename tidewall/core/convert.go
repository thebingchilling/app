package libcore

import (
	"encoding/json"
	"errors"
	"fmt"
	"strings"

	"github.com/metacubex/mihomo/adapter"
	"github.com/metacubex/mihomo/common/convert"
	"go.yaml.in/yaml/v3"
)

// ProfileFromLinks turns share links (vless://, vmess://, ss://, trojan://,
// hysteria2://, tuic://, ... one per line, or a base64 subscription body)
// into a complete profile with a "Proxy" selector, an "Auto" url-test group
// and a catch-all rule.
func ProfileFromLinks(text string) (string, error) {
	proxies, err := convert.ConvertsV2Ray([]byte(strings.TrimSpace(text)))
	if err != nil {
		return "", err
	}
	if len(proxies) == 0 {
		return "", errors.New("no supported links found")
	}
	return profileFromProxies(proxies)
}

// profileFromProxies wraps proxy mappings in a ready-to-use profile.
func profileFromProxies(proxies []map[string]any) (string, error) {
	names := make([]string, 0, len(proxies))
	seen := map[string]int{}
	for _, p := range proxies {
		name, _ := p["name"].(string)
		if name == "" {
			name = fmt.Sprint(p["type"])
		}
		// Duplicate names are rejected by mihomo, so number them.
		if n := seen[name]; n > 0 {
			seen[name] = n + 1
			name = fmt.Sprintf("%s (%d)", name, n+1)
		} else {
			seen[name] = 1
		}
		p["name"] = name
		names = append(names, name)
	}

	groups := []map[string]any{
		{"name": "Proxy", "type": "select", "proxies": append([]string{"Auto"}, names...)},
		{"name": "Auto", "type": "url-test", "proxies": names, "url": defaultTestURL, "interval": 300, "tolerance": 50},
	}
	if len(names) == 1 {
		groups = []map[string]any{{"name": "Proxy", "type": "select", "proxies": names}}
	}
	doc := map[string]any{
		"mode":         "rule",
		"proxies":      proxies,
		"proxy-groups": groups,
		"rules":        []string{"MATCH,Proxy"},
	}
	b, err := yaml.Marshal(doc)
	if err != nil {
		return "", err
	}
	return string(b), nil
}

// ValidateProxy checks a single proxy mapping with mihomo's own parser.
func validateProxy(mapping map[string]any) error {
	_, err := adapter.ParseProxy(mapping)
	return err
}

// AddProxyToProfile appends one proxy (given as YAML mapping) to a profile's
// proxies and to its first selector group, returning the new profile.
func AddProxyToProfile(profileYAML string, proxyYAML string) (string, error) {
	var proxy map[string]any
	if err := yaml.Unmarshal([]byte(proxyYAML), &proxy); err != nil {
		return "", err
	}
	if err := validateProxy(proxy); err != nil {
		return "", err
	}
	if strings.TrimSpace(profileYAML) == "" {
		return profileFromProxies([]map[string]any{proxy})
	}
	var doc map[string]any
	if err := yaml.Unmarshal([]byte(profileYAML), &doc); err != nil {
		return "", err
	}
	list, _ := doc["proxies"].([]any)
	doc["proxies"] = append(list, proxy)
	if groups, ok := doc["proxy-groups"].([]any); ok {
		for _, g := range groups {
			gm, ok := g.(map[string]any)
			if !ok || gm["type"] != "select" {
				continue
			}
			members, _ := gm["proxies"].([]any)
			gm["proxies"] = append(members, proxy["name"])
			break
		}
	}
	b, err := yaml.Marshal(doc)
	return string(b), err
}

type profileStats struct {
	Proxies   int `json:"proxies"`
	Groups    int `json:"groups"`
	Providers int `json:"providers"`
	Rules     int `json:"rules"`
}

// ProfileStats counts the proxies, groups, providers and rules in a profile
// as JSON, for the profile list. It does not validate the profile.
func ProfileStats(profileYAML string) (string, error) {
	var doc struct {
		Proxies   []any          `yaml:"proxies"`
		Groups    []any          `yaml:"proxy-groups"`
		Providers map[string]any `yaml:"proxy-providers"`
		Rules     []any          `yaml:"rules"`
	}
	if err := yaml.Unmarshal([]byte(profileYAML), &doc); err != nil {
		return "", err
	}
	b, err := json.Marshal(profileStats{len(doc.Proxies), len(doc.Groups), len(doc.Providers), len(doc.Rules)})
	return string(b), err
}
