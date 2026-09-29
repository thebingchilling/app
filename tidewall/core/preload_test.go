package libcore

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

const preloadProfile = `
proxies:
  - {name: A, type: ss, server: 192.0.2.1, port: 8388, cipher: aes-128-gcm, password: x}
  - {name: B, type: ss, server: 192.0.2.2, port: 8388, cipher: aes-128-gcm, password: x}
proxy-groups:
  - {name: Pick, type: select, proxies: [A, B]}
  - {name: Hidden, type: select, proxies: [A], hidden: true}
rules:
  - MATCH,Pick
`

// Proxies can be listed and chosen before the VPN connects.
func TestLoadProfileBeforeConnect(t *testing.T) {
	if err := Init(t.TempDir()); err != nil {
		t.Fatal(err)
	}
	opts := `{"mode":"rule","selected":{"Pick":"B","Missing":"A"}}`
	if err := LoadProfile(preloadProfile, opts); err != nil {
		t.Fatal(err)
	}
	if IsProxyRunning() {
		t.Fatal("LoadProfile must not start the VPN engine")
	}
	if !IsProfileLoaded() {
		t.Fatal("profile not reported as loaded")
	}
	var state proxiesState
	if err := json.Unmarshal([]byte(ProxiesJSON()), &state); err != nil {
		t.Fatal(err)
	}
	if len(state.Groups) != 1 || state.Groups[0].Name != "Pick" {
		t.Fatalf("groups: %+v", state.Groups)
	}
	if state.Groups[0].Now != "B" {
		t.Fatalf("remembered selection not restored: now=%q", state.Groups[0].Now)
	}
	if err := SelectProxy("Pick", "A"); err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(ProxiesJSON(), `"now":"A"`) {
		t.Fatal("selection not applied")
	}

	// Latencies survive re-applying the same profile (as happens on connect).
	rememberDelay("A", 123)
	if err := LoadProfile(preloadProfile, `{}`); err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(ProxiesJSON(), `"delay":123`) {
		t.Fatal("cached delay lost")
	}
	// ...but not switching to another profile.
	if err := LoadProfile(strings.Replace(preloadProfile, "Pick", "Other", -1), `{}`); err != nil {
		t.Fatal(err)
	}
	if strings.Contains(ProxiesJSON(), `"delay":123`) {
		t.Fatal("delay of the previous profile kept")
	}
}

// Latency tests and the IP check go through the selected proxy before the VPN
// is connected.
func TestDelayAndIPBeforeConnect(t *testing.T) {
	if err := Init(t.TempDir()); err != nil {
		t.Fatal(err)
	}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/json" {
			_, _ = w.Write([]byte(`{"ip":"203.0.113.7","country":"NL"}`))
			return
		}
		// Loopback answers in under 1 ms, which mihomo reports as 0 (= failed).
		time.Sleep(5 * time.Millisecond)
		w.WriteHeader(http.StatusNoContent)
	}))
	defer srv.Close()
	saved := ipInfoSources
	ipInfoSources = append(ipInfoSources[:0:0], saved[0])
	ipInfoSources[0].url = srv.URL + "/json"
	defer func() { ipInfoSources = saved }()

	profile := `
proxies:
  - {name: Out, type: direct}
proxy-groups:
  - {name: Pick, type: select, proxies: [Out]}
rules:
  - MATCH,Pick
`
	if err := LoadProfile(profile, `{"mode":"rule"}`); err != nil {
		t.Fatal(err)
	}
	if IsProxyRunning() {
		t.Fatal("must not be connected")
	}
	if d := TestDelay("Out", srv.URL+"/generate_204", 3000); d <= 0 {
		t.Fatalf("delay test before connecting failed: %d", d)
	}
	res, err := TestGroupDelay("Pick", srv.URL+"/generate_204", 3000)
	if err != nil || !strings.Contains(res, `"Out":`) || strings.Contains(res, `"Out":-1`) {
		t.Fatalf("group delay test: %s %v", res, err)
	}
	js, err := DetectIP(3000)
	if err != nil {
		t.Fatal(err)
	}
	var info ipInfo
	if err := json.Unmarshal([]byte(js), &info); err != nil || info.IP != "203.0.113.7" || info.Country != "NL" {
		t.Fatalf("ip info: %s %v", js, err)
	}
}

func TestInlineOvpnCredentials(t *testing.T) {
	text := sampleOvpn(t, "auth-user-pass\n<auth-user-pass>\nalice\ns3cret\n</auth-user-pass>")
	js, err := InspectOvpn(text)
	if err != nil {
		t.Fatal(err)
	}
	var info ovpnInfo
	if err := json.Unmarshal([]byte(js), &info); err != nil {
		t.Fatal(err)
	}
	if info.NeedsPassword || info.Username != "alice" || info.Password != "s3cret" {
		t.Fatalf("inline credentials not used: %+v", info)
	}
	p, err := ovpnToProxy(text, "", "", "")
	if err != nil {
		t.Fatal(err)
	}
	if p["username"] != "alice" || p["password"] != "s3cret" {
		t.Fatalf("proxy credentials: %v", p)
	}
}
