package libcore

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"strings"
	"time"

	mihomoHttp "github.com/metacubex/mihomo/component/http"
)

type ipInfo struct {
	IP      string `json:"ip"`
	Country string `json:"country"` // ISO 3166 alpha-2
}

// ipInfoSources are tried in order; each maps its own JSON to ipInfo.
var ipInfoSources = []struct {
	url   string
	parse func(map[string]any) ipInfo
}{
	{"https://ipinfo.io/json", func(m map[string]any) ipInfo {
		return ipInfo{str(m["ip"]), str(m["country"])}
	}},
	{"https://api.ip.sb/geoip", func(m map[string]any) ipInfo {
		return ipInfo{str(m["ip"]), str(m["country_code"])}
	}},
	{"https://ipapi.co/json", func(m map[string]any) ipInfo {
		return ipInfo{str(m["ip"]), str(m["country_code"])}
	}},
}

func str(v any) string {
	s, _ := v.(string)
	return strings.TrimSpace(s)
}

// DetectIP asks public IP services for the exit IP and country of traffic
// routed by the proxy engine, following the profile's rules and the selected
// proxies. Works before connecting: it then shows where the current choice
// would take apps. Returns {"ip": "...", "country": "XX"}.
func DetectIP(timeoutMs int) (string, error) {
	if !IsProfileLoaded() {
		return "", errors.New("no proxy profile loaded")
	}
	if timeoutMs <= 0 {
		timeoutMs = 8000
	}
	var lastErr error
	for _, src := range ipInfoSources {
		info, err := fetchIPInfo(src.url, time.Duration(timeoutMs)*time.Millisecond, src.parse)
		if err == nil {
			b, _ := json.Marshal(info)
			return string(b), nil
		}
		lastErr = err
	}
	return "", lastErr
}

func fetchIPInfo(url string, timeout time.Duration, parse func(map[string]any) ipInfo) (ipInfo, error) {
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()
	resp, err := mihomoHttp.HttpRequest(ctx, url, http.MethodGet, http.Header{"Accept": {"application/json"}}, nil)
	if err != nil {
		return ipInfo{}, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return ipInfo{}, errors.New(resp.Status)
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, 64*1024))
	if err != nil {
		return ipInfo{}, err
	}
	var m map[string]any
	if err := json.Unmarshal(body, &m); err != nil {
		return ipInfo{}, err
	}
	info := parse(m)
	if info.IP == "" {
		return ipInfo{}, errors.New("no ip in response")
	}
	return info, nil
}
