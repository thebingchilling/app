package main

import (
	"github.com/metacubex/mihomo/common/convert"
	"github.com/metacubex/mihomo/common/yaml"
	"github.com/metacubex/mihomo/config"
)

// Probe URL of the "Auto" group; the same default as the app's test URL.
const shareLinksTestURL = "https://www.gstatic.com/generate_204"

// profileFromShareLinks turns share links (vless://, vmess://, ss://,
// trojan://, hysteria2://, tuic://, ...) or a base64 "v2rayN" subscription
// into a mihomo profile. ok is false when buf already is a profile or holds
// no links.
func profileFromShareLinks(buf []byte) (profile []byte, ok bool) {
	if raw, err := config.UnmarshalRawConfig(buf); err == nil &&
		(len(raw.Proxy) > 0 || len(raw.ProxyProvider) > 0 || len(raw.ProxyGroup) > 0) {
		return nil, false
	}
	proxies, err := convert.ConvertsV2Ray(buf)
	if err != nil || len(proxies) == 0 {
		return nil, false
	}
	names := make([]string, 0, len(proxies))
	for _, proxy := range proxies {
		name, _ := proxy["name"].(string)
		names = append(names, name)
	}
	groups := []map[string]any{
		{"name": "Proxy", "type": "select", "proxies": names},
	}
	if len(names) > 1 {
		groups = []map[string]any{
			{"name": "Proxy", "type": "select", "proxies": append([]string{"Auto"}, names...)},
			{
				"name": "Auto", "type": "url-test", "proxies": names,
				"url": shareLinksTestURL, "interval": 300, "tolerance": 50,
			},
		}
	}
	out, err := yaml.Marshal(map[string]any{
		"mode":         "rule",
		"proxies":      proxies,
		"proxy-groups": groups,
		"rules":        []string{"MATCH,Proxy"},
	})
	if err != nil {
		return nil, false
	}
	return out, true
}
