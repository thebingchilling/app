//go:build !(android && cmfa)

package libcore

// UpdateSystemDNS is a no-op outside the embedded Android build, where mihomo
// reads the system resolver configuration itself.
func UpdateSystemDNS(servers string) {}
