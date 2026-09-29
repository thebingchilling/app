//go:build android && cmfa

package libcore

import (
	"strings"

	"github.com/metacubex/mihomo/dns"
)

// UpdateSystemDNS sets the servers mihomo uses for "system" DNS (comma
// separated IPs of the phone's physical network). In the embedded (cmfa)
// build mihomo cannot discover them itself.
func UpdateSystemDNS(servers string) {
	var list []string
	for _, s := range strings.Split(servers, ",") {
		if s = strings.TrimSpace(s); s != "" {
			list = append(list, s)
		}
	}
	dns.UpdateSystemDNS(list)
	dns.FlushCacheWithDefaultResolver()
}
