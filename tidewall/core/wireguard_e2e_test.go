package libcore

import (
	"bytes"
	"encoding/base64"
	"encoding/binary"
	"fmt"
	"net"
	"os"
	"testing"
	"time"

	awg "github.com/metacubex/amneziawg-go/device_v1"
	"github.com/metacubex/wireguard-go/conn"
	wgdevice "github.com/metacubex/wireguard-go/device"
	"golang.org/x/sys/unix"
)

// ipv4Packet builds a minimal UDP/IPv4 packet (checksums are not verified by
// WireGuard, but the header must be well formed).
func ipv4Packet(src, dst net.IP, payload []byte) []byte {
	total := 20 + 8 + len(payload)
	p := make([]byte, total)
	p[0] = 0x45
	binary.BigEndian.PutUint16(p[2:], uint16(total))
	p[8] = 64
	p[9] = 17
	copy(p[12:16], src.To4())
	copy(p[16:20], dst.To4())
	var sum uint32
	for i := 0; i < 20; i += 2 {
		sum += uint32(binary.BigEndian.Uint16(p[i:]))
	}
	for sum > 0xffff {
		sum = (sum >> 16) + (sum & 0xffff)
	}
	binary.BigEndian.PutUint16(p[10:], ^uint16(sum))
	binary.BigEndian.PutUint16(p[20:], 1111)
	binary.BigEndian.PutUint16(p[22:], 2222)
	binary.BigEndian.PutUint16(p[24:], uint16(8+len(payload)))
	copy(p[28:], payload)
	return p
}

func freeUDPPort(t *testing.T) int {
	c, err := net.ListenPacket("udp4", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer c.Close()
	return c.LocalAddr().(*net.UDPAddr).Port
}

// TestWireGuardEndToEnd runs two AmneziaWG devices on fdTun over socketpairs
// (standing in for Android TUN fds) and sends a packet through the tunnel.
func TestWireGuardEndToEnd(t *testing.T) {
	for _, amnezia := range []bool{false, true} {
		t.Run(fmt.Sprintf("amnezia=%v", amnezia), func(t *testing.T) {
			testWireGuardEndToEnd(t, amnezia)
		})
	}
}

func testWireGuardEndToEnd(t *testing.T, amnezia bool) {
	privA, pubA := wgKey(t)
	privB, pubB := wgKey(t)
	portA, portB := freeUDPPort(t), freeUDPPort(t)
	awgParams := ""
	if amnezia {
		awgParams = "Jc = 3\nJmin = 10\nJmax = 50\nS1 = 15\nS2 = 20\nH1 = 11111\nH2 = 22222\nH3 = 33333\nH4 = 44444\n"
	}
	confA := fmt.Sprintf("[Interface]\nPrivateKey = %s\nAddress = 10.9.0.1/32\nListenPort = %d\n%s\n[Peer]\nPublicKey = %s\nAllowedIPs = 10.9.0.2/32\nEndpoint = 127.0.0.1:%d\n", privA, portA, awgParams, pubB, portB)
	confB := fmt.Sprintf("[Interface]\nPrivateKey = %s\nAddress = 10.9.0.2/32\nListenPort = %d\n%s\n[Peer]\nPublicKey = %s\nAllowedIPs = 10.9.0.1/32\nEndpoint = 127.0.0.1:%d\n", privB, portB, awgParams, pubA, portA)

	start := func(conf string) (*awg.Device, *os.File) {
		fds, err := unix.Socketpair(unix.AF_UNIX, unix.SOCK_SEQPACKET, 0)
		if err != nil {
			t.Fatal(err)
		}
		cfg, err := parseWireGuard(conf)
		if err != nil {
			t.Fatal(err)
		}
		uapi, err := buildUAPI(cfg, nil)
		if err != nil {
			t.Fatal(err)
		}
		tn, err := newFdTun(fds[0], 1420)
		if err != nil {
			t.Fatal(err)
		}
		dev := awg.NewDevice(tn, conn.NewDefaultBind(), wgdevice.NewLogger(wgdevice.LogLevelError, "test "), 0)
		if err := dev.IpcSet(uapi); err != nil {
			t.Fatal(err)
		}
		if err := dev.Up(); err != nil {
			t.Fatal(err)
		}
		return dev, os.NewFile(uintptr(fds[1]), "app-side")
	}
	devA, appA := start(confA)
	defer devA.Close()
	devB, appB := start(confB)
	defer devB.Close()
	defer appA.Close()
	defer appB.Close()

	payload := []byte("tidewall wireguard e2e " + base64.StdEncoding.EncodeToString([]byte{1, 2, 3}))
	pkt := ipv4Packet(net.IPv4(10, 9, 0, 1), net.IPv4(10, 9, 0, 2), payload)
	got := make(chan []byte, 1)
	go func() {
		buf := make([]byte, 2048)
		for {
			n, err := appB.Read(buf)
			if err != nil {
				return
			}
			if n >= 28 && bytes.Equal(buf[28:n], payload) {
				got <- append([]byte(nil), buf[:n]...)
				return
			}
		}
	}()
	deadline := time.After(10 * time.Second)
	tick := time.NewTicker(300 * time.Millisecond)
	defer tick.Stop()
	for {
		// The first packets trigger the handshake; resend until one arrives.
		if _, err := appA.Write(pkt); err != nil {
			t.Fatal(err)
		}
		select {
		case <-got:
			return
		case <-deadline:
			t.Fatal("packet never crossed the tunnel")
		case <-tick.C:
		}
	}
}
