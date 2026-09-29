package libcore

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"sync"
	"time"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/common/utils"
	"github.com/metacubex/mihomo/component/profile/cachefile"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/tunnel"
	"github.com/metacubex/mihomo/tunnel/statistic"
)

type proxyItem struct {
	Name  string `json:"name"`
	Type  string `json:"type"`
	UDP   bool   `json:"udp"`
	Delay int    `json:"delay"` // ms of the last test, 0 = untested, -1 = failed
	Group bool   `json:"group"`
}

type proxyGroup struct {
	Name       string      `json:"name"`
	Type       string      `json:"type"`
	Now        string      `json:"now"`
	Selectable bool        `json:"selectable"`
	Hidden     bool        `json:"hidden"`
	Icon       string      `json:"icon"`
	Proxies    []proxyItem `json:"proxies"`
}

type proxiesState struct {
	Mode   string       `json:"mode"`
	Groups []proxyGroup `json:"groups"`
}

// Latest test results by proxy name. Proxies are rebuilt whenever a profile
// is applied (e.g. on connect), which drops their own delay history.
var (
	delayMu    sync.Mutex
	delayCache = map[string]int{}
)

func rememberDelay(name string, d int) {
	delayMu.Lock()
	delayCache[name] = d
	delayMu.Unlock()
}

func clearDelays() {
	delayMu.Lock()
	delayCache = map[string]int{}
	delayMu.Unlock()
}

func lastDelay(p C.Proxy) int {
	hist := p.DelayHistory()
	if len(hist) == 0 {
		delayMu.Lock()
		defer delayMu.Unlock()
		return delayCache[p.Name()]
	}
	d := int(hist[len(hist)-1].Delay)
	if d == 0 {
		return -1
	}
	return d
}

func describeGroup(p C.Proxy, g outboundgroup.ProxyGroup) proxyGroup {
	_, selectable := p.Adapter().(outboundgroup.SelectAble)
	out := proxyGroup{
		Name:       p.Name(),
		Type:       p.Type().String(),
		Now:        g.Now(),
		Selectable: selectable && p.Type() == C.Selector,
		Hidden:     g.Hidden(),
		Icon:       g.Icon(),
	}
	for _, m := range g.Proxies() {
		_, isGroup := m.Adapter().(outboundgroup.ProxyGroup)
		out.Proxies = append(out.Proxies, proxyItem{
			Name:  m.Name(),
			Type:  m.Type().String(),
			UDP:   m.SupportUDP(),
			Delay: lastDelay(m),
			Group: isGroup,
		})
	}
	return out
}

// ProxiesJSON returns the proxy groups in profile order as JSON:
// {"mode": "...", "groups": [{name, type, now, selectable, proxies: [...]}]}.
// GLOBAL is listed first when the engine is in global mode.
func ProxiesJSON() string {
	proxies := tunnel.Proxies()
	state := proxiesState{Mode: tunnel.Mode().String()}

	global, ok := proxies["GLOBAL"]
	if !ok {
		b, _ := json.Marshal(state)
		return string(b)
	}
	globalGroup, ok := global.Adapter().(outboundgroup.ProxyGroup)
	if !ok {
		b, _ := json.Marshal(state)
		return string(b)
	}
	if tunnel.Mode() == tunnel.Global {
		gg := describeGroup(global, globalGroup)
		gg.Selectable = true
		state.Groups = append(state.Groups, gg)
	}
	// GLOBAL's members are every proxy and group in profile order.
	for _, m := range globalGroup.Proxies() {
		g, isGroup := m.Adapter().(outboundgroup.ProxyGroup)
		if !isGroup || g.Hidden() {
			continue
		}
		state.Groups = append(state.Groups, describeGroup(m, g))
	}
	b, _ := json.Marshal(state)
	return string(b)
}

// SelectProxy picks proxy name inside a selector group and remembers it.
func SelectProxy(group string, name string) error {
	p, ok := tunnel.Proxies()[group]
	if !ok {
		return fmt.Errorf("group %q not found", group)
	}
	sel, ok := p.Adapter().(outboundgroup.SelectAble)
	if !ok {
		return fmt.Errorf("%q is not a selector group", group)
	}
	if err := sel.Set(name); err != nil {
		return err
	}
	cachefile.Cache().SetSelected(group, name)
	// Switching server should not leave apps on the old one.
	statistic.DefaultManager.Range(func(c statistic.Tracker) bool {
		for _, chain := range c.Chains() {
			if chain == group {
				_ = c.Close()
				break
			}
		}
		return true
	})
	return nil
}

func findProxy(name string) (C.Proxy, bool) {
	if p, ok := tunnel.Proxies()[name]; ok {
		return p, true
	}
	for _, pd := range tunnel.Providers() {
		for _, p := range pd.Proxies() {
			if p.Name() == name {
				return p, true
			}
		}
	}
	return nil, false
}

func testURL(url string) string {
	if strings.TrimSpace(url) == "" {
		return defaultTestURL
	}
	return url
}

// TestDelay measures one proxy's latency in milliseconds, or -1 on failure.
func TestDelay(name string, url string, timeoutMs int) int {
	p, ok := findProxy(name)
	if !ok {
		return -1
	}
	if timeoutMs <= 0 {
		timeoutMs = 5000
	}
	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(timeoutMs)*time.Millisecond)
	defer cancel()
	d, err := p.URLTest(ctx, testURL(url), utils.IntRanges[uint16]{})
	v := int(d)
	if err != nil || d == 0 {
		v = -1
	}
	rememberDelay(name, v)
	return v
}

// TestGroupDelay tests every member of a group concurrently and returns a JSON
// object of name -> delay (ms, or -1 when the test failed).
func TestGroupDelay(group string, url string, timeoutMs int) (string, error) {
	p, ok := tunnel.Proxies()[group]
	if !ok {
		return "", fmt.Errorf("group %q not found", group)
	}
	g, ok := p.Adapter().(outboundgroup.ProxyGroup)
	if !ok {
		return "", fmt.Errorf("%q is not a group", group)
	}
	if timeoutMs <= 0 {
		timeoutMs = 5000
	}
	members := g.Proxies()
	result := make(map[string]int, len(members))
	var mu sync.Mutex
	var wg sync.WaitGroup
	sem := make(chan struct{}, 16)
	for _, m := range members {
		wg.Add(1)
		go func(m C.Proxy) {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()
			ctx, cancel := context.WithTimeout(context.Background(), time.Duration(timeoutMs)*time.Millisecond)
			defer cancel()
			d, err := m.URLTest(ctx, testURL(url), utils.IntRanges[uint16]{})
			v := int(d)
			if err != nil || d == 0 {
				v = -1
			}
			rememberDelay(m.Name(), v)
			mu.Lock()
			result[m.Name()] = v
			mu.Unlock()
		}(m)
	}
	wg.Wait()
	b, err := json.Marshal(result)
	return string(b), err
}

// SetMode switches between rule, global and direct routing.
func SetMode(mode string) error {
	m, ok := tunnel.ModeMapping[strings.ToLower(mode)]
	if !ok {
		return fmt.Errorf("unknown mode %q", mode)
	}
	tunnel.SetMode(m)
	return nil
}

// GetMode returns the current routing mode.
func GetMode() string {
	return tunnel.Mode().String()
}

// Traffic is a snapshot of throughput (bytes/s) and session totals (bytes).
type Traffic struct {
	Up        int64
	Down      int64
	UpTotal   int64
	DownTotal int64
}

// GetTraffic returns the current proxy traffic counters.
func GetTraffic() *Traffic {
	up, down := statistic.DefaultManager.Now()
	upT, downT := statistic.DefaultManager.Total()
	return &Traffic{Up: up, Down: down, UpTotal: upT, DownTotal: downT}
}

type connection struct {
	ID       string   `json:"id"`
	Network  string   `json:"network"`
	Host     string   `json:"host"`
	Dest     string   `json:"dest"`
	Chains   []string `json:"chains"`
	Rule     string   `json:"rule"`
	Upload   int64    `json:"upload"`
	Download int64    `json:"download"`
	Start    int64    `json:"start"` // unix ms
}

// ConnectionsJSON returns the active connections as a JSON array.
func ConnectionsJSON() string {
	snap := statistic.DefaultManager.Snapshot()
	out := make([]connection, 0, len(snap.Connections))
	for _, c := range snap.Connections {
		md := c.Metadata
		host := md.Host
		if host == "" {
			host = md.DstIP.String()
		}
		rule := c.Rule
		if c.RulePayload != "" {
			rule += " (" + c.RulePayload + ")"
		}
		out = append(out, connection{
			ID:       c.UUID.String(),
			Network:  md.NetWork.String(),
			Host:     host,
			Dest:     md.RemoteAddress(),
			Chains:   c.Chain,
			Rule:     rule,
			Upload:   c.UploadTotal.Load(),
			Download: c.DownloadTotal.Load(),
			Start:    c.Start.UnixMilli(),
		})
	}
	b, _ := json.Marshal(out)
	return string(b)
}

// CloseConnection closes one connection by id; an empty id closes all.
func CloseConnection(id string) error {
	if id == "" {
		statistic.DefaultManager.Range(func(c statistic.Tracker) bool {
			_ = c.Close()
			return true
		})
		return nil
	}
	c := statistic.DefaultManager.Get(id)
	if c == nil {
		return errors.New("connection not found")
	}
	return c.Close()
}
