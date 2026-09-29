// Package libcore is Tidewall's Go engine. It is exported to Android with
// gomobile, so every exported identifier must use gomobile-compatible types
// (string, bool, int/int32/int64, []byte, error, or exported interfaces).
package libcore

import (
	"errors"
	"os"
	"path/filepath"
	"strings"
	"sync"

	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/constant/features"
	"github.com/metacubex/mihomo/log"
)

// MihomoVersion is the mihomo release this core is built against.
const MihomoVersion = "v1.19.31"

var (
	initOnce sync.Once
	homeDir  string

	logMu       sync.Mutex
	logListener LogListener
	logStop     chan struct{}
)

// LogListener receives engine log lines. Implemented on the Kotlin side.
type LogListener interface {
	OnLog(level string, message string)
}

// Init sets the directory mihomo uses for caches, geo databases and provider
// downloads. It must be called once before anything else.
func Init(dir string) error {
	if dir == "" {
		return errors.New("home dir is empty")
	}
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return err
	}
	initOnce.Do(func() {
		homeDir = dir
		C.SetHomeDir(dir)
		C.SetConfig(filepath.Join(dir, "config.yaml"))
	})
	return nil
}

// Version returns a human readable engine version string, including the
// mihomo build tags (e.g. with_gvisor).
func Version() string {
	v := "mihomo " + MihomoVersion
	if tags := features.Tags(); len(tags) > 0 {
		v += " (" + strings.Join(tags, ", ") + ")"
	}
	return v
}

// SetLogListener subscribes l to engine logs at the given level
// (debug, info, warning, error, silent). Passing nil unsubscribes.
func SetLogListener(l LogListener, level string) {
	logMu.Lock()
	defer logMu.Unlock()
	if logStop != nil {
		close(logStop)
		logStop = nil
	}
	logListener = l
	if lvl, ok := log.LogLevelMapping[level]; ok {
		log.SetLevel(lvl)
	}
	if l == nil {
		return
	}
	stop := make(chan struct{})
	logStop = stop
	sub := log.Subscribe()
	go func() {
		defer log.UnSubscribe(sub)
		for {
			select {
			case <-stop:
				return
			case ev, ok := <-sub:
				if !ok {
					return
				}
				if ev.LogLevel < log.Level() {
					continue
				}
				l.OnLog(ev.Type(), ev.Payload)
			}
		}
	}()
}

// emit forwards a Tidewall-originated message to the current listener.
func emit(level, msg string) {
	logMu.Lock()
	l := logListener
	logMu.Unlock()
	if l != nil {
		l.OnLog(level, msg)
	}
}
