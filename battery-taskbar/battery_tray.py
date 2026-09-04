"""Battery percentage in the Windows taskbar tray icon.

Draws the current battery percentage directly onto the tray icon, as large
as it can legibly go, and refreshes it periodically.
"""

import ctypes
import os
import sys
import threading
import time
import traceback
from ctypes import wintypes
from pathlib import Path

import psutil
import pystray
from PIL import Image, ImageDraw, ImageFont

LOG_PATH = Path(os.environ.get("LOCALAPPDATA", ".")) / "BatteryTaskbar" / "error.log"

# Fallback poll interval. Real-time updates come from WM_POWERBROADCAST
# (see _start_power_event_watcher) firing the moment Windows reports a
# power/battery status change, but that event doesn't fire reliably on
# every machine, so keep this short enough that plug/unplug still shows
# up quickly even when it doesn't.
UPDATE_INTERVAL_SECONDS = 3
ICON_SIZE = 64

WM_POWERBROADCAST = 0x0218
PBT_APMPOWERSTATUSCHANGE = 0x000A
WNDPROC = ctypes.WINFUNCTYPE(ctypes.c_long, wintypes.HWND, ctypes.c_uint, wintypes.WPARAM, wintypes.LPARAM)


class _WNDCLASSEXW(ctypes.Structure):
    _fields_ = [
        ("cbSize", ctypes.c_uint),
        ("style", ctypes.c_uint),
        ("lpfnWndProc", WNDPROC),
        ("cbClsExtra", ctypes.c_int),
        ("cbWndExtra", ctypes.c_int),
        ("hInstance", wintypes.HINSTANCE),
        ("hIcon", wintypes.HICON),
        ("hCursor", wintypes.HANDLE),
        ("hbrBackground", wintypes.HBRUSH),
        ("lpszMenuName", wintypes.LPCWSTR),
        ("lpszClassName", wintypes.LPCWSTR),
        ("hIconSm", wintypes.HICON),
    ]

_FONT_CANDIDATES = ("seguisb.ttf", "arialbd.ttf", "DejaVuSans-Bold.ttf", "DejaVuSans.ttf")
_OUTLINE_OFFSETS = ((-2, 0), (2, 0), (0, -2), (0, 2), (-1, -1), (1, 1), (-1, 1), (1, -1))


def _load_font(size):
    for name in _FONT_CANDIDATES:
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def _fit_font(draw, text, max_size, box):
    for size in range(max_size, 3, -1):
        font = _load_font(size)
        bbox = draw.textbbox((0, 0), text, font=font)
        if (bbox[2] - bbox[0]) <= box and (bbox[3] - bbox[1]) <= box:
            return font, bbox
    font = _load_font(4)
    return font, draw.textbbox((0, 0), text, font=font)


def _text_color(percent, plugged):
    if percent is None:
        return (200, 200, 200, 255)
    if plugged:
        return (60, 220, 100, 255)
    if percent <= 10:
        return (235, 70, 70, 255)
    if percent <= 20:
        return (245, 175, 45, 255)
    return (255, 255, 255, 255)


def make_icon_image(percent, plugged):
    size = ICON_SIZE
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    text = f"{percent}" if percent is not None else "--"
    font, bbox = _fit_font(draw, text, size, int(size * 0.96))

    w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
    x = (size - w) / 2 - bbox[0]
    y = (size - h) / 2 - bbox[1]

    for dx, dy in _OUTLINE_OFFSETS:
        draw.text((x + dx, y + dy), text, font=font, fill=(0, 0, 0, 255))
    draw.text((x, y), text, font=font, fill=_text_color(percent, plugged))

    return img


def read_battery():
    battery = psutil.sensors_battery()
    if battery is None:
        return None, False
    return round(battery.percent), battery.power_plugged


def _status_text():
    percent, plugged = read_battery()
    if percent is None:
        return "No battery detected"
    return f"{percent}% - {'Charging' if plugged else 'On battery'}"


def build_menu(icon):
    return pystray.Menu(
        pystray.MenuItem(lambda item: _status_text(), None, enabled=False),
        pystray.MenuItem("Quit", lambda: icon.stop()),
    )


def _log(msg):
    print(msg, flush=True)


def refresh_icon(icon):
    try:
        percent, plugged = read_battery()
        _log(f"refresh: percent={percent} plugged={plugged}")
        icon.icon = make_icon_image(percent, plugged)
        icon.title = _status_text()
    except Exception:
        try:
            LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
            with open(LOG_PATH, "a", encoding="utf-8") as f:
                f.write(traceback.format_exc())
        except OSError:
            pass


def update_loop(icon):
    while True:
        time.sleep(UPDATE_INTERVAL_SECONDS)
        refresh_icon(icon)


def _start_power_event_watcher(icon):
    """Runs a hidden window on its own thread that reacts instantly to
    WM_POWERBROADCAST (Windows fires this the moment AC/battery status
    changes), instead of waiting on the poll interval."""

    def wndproc(hwnd, msg, wparam, lparam):
        if msg == WM_POWERBROADCAST and wparam == PBT_APMPOWERSTATUSCHANGE:
            _log("WM_POWERBROADCAST received - refreshing immediately")
            refresh_icon(icon)
        return ctypes.windll.user32.DefWindowProcW(hwnd, msg, wparam, lparam)

    def run():
        try:
            wndproc_ref = WNDPROC(wndproc)  # must stay alive for the window's lifetime
            wc = _WNDCLASSEXW()
            wc.cbSize = ctypes.sizeof(_WNDCLASSEXW)
            wc.lpfnWndProc = wndproc_ref
            wc.hInstance = ctypes.windll.kernel32.GetModuleHandleW(None)
            wc.lpszClassName = "BatteryTaskbarPowerWatcher"

            if not ctypes.windll.user32.RegisterClassExW(ctypes.byref(wc)):
                _log(f"RegisterClassExW failed: {ctypes.GetLastError()}")
                return

            hwnd = ctypes.windll.user32.CreateWindowExW(
                0, wc.lpszClassName, "BatteryTaskbarPowerWatcher", 0,
                0, 0, 0, 0, None, None, wc.hInstance, None,
            )
            if not hwnd:
                _log(f"CreateWindowExW failed: {ctypes.GetLastError()}")
                return

            _log("power event watcher window created, pumping messages")
            msg = wintypes.MSG()
            while ctypes.windll.user32.GetMessageW(ctypes.byref(msg), None, 0, 0) != 0:
                ctypes.windll.user32.TranslateMessage(ctypes.byref(msg))
                ctypes.windll.user32.DispatchMessageW(ctypes.byref(msg))
        except Exception:
            try:
                LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
                with open(LOG_PATH, "a", encoding="utf-8") as f:
                    f.write(traceback.format_exc())
            except OSError:
                pass

    threading.Thread(target=run, daemon=True).start()


def main():
    _log(f"pystray backend: {pystray.Icon.__module__}")

    percent, plugged = read_battery()
    _log(f"battery read: percent={percent} plugged={plugged}")

    image = make_icon_image(percent, plugged)
    _log(f"icon image built: size={image.size} mode={image.mode}")

    icon = pystray.Icon("battery-taskbar", image, _status_text())
    icon.menu = build_menu(icon)
    _log("pystray.Icon object created")

    def _setup(i):
        _log("setup() invoked, marking icon visible")
        i.visible = True
        _log(f"icon.visible = {i.visible}")
        threading.Thread(target=update_loop, args=(i,), daemon=True).start()
        _log("fallback poll thread started")
        if sys.platform == "win32":
            _start_power_event_watcher(i)

    _log("calling icon.run() - entering message loop")
    icon.run(setup=_setup)
    _log("icon.run() returned - icon stopped")


def _report_fatal_error(exc):
    try:
        LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
        with open(LOG_PATH, "w", encoding="utf-8") as f:
            f.write("".join(traceback.format_exception(type(exc), exc, exc.__traceback__)))
    except OSError:
        pass
    if sys.platform == "win32":
        ctypes.windll.user32.MessageBoxW(
            0,
            f"BatteryTaskbar failed to start:\n\n{exc}\n\nDetails written to:\n{LOG_PATH}",
            "BatteryTaskbar - Error",
            0x10,
        )


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # surfaced via a message box since this is a --noconsole build
        _report_fatal_error(exc)
        raise
