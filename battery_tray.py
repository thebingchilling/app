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
from pathlib import Path

import psutil
import pystray
from PIL import Image, ImageDraw, ImageFont

LOG_PATH = Path(os.environ.get("LOCALAPPDATA", ".")) / "BatteryTaskbar" / "error.log"

UPDATE_INTERVAL_SECONDS = 5
ICON_SIZE = 64

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
        return (90, 210, 130, 255)
    if percent <= 15:
        return (235, 70, 70, 255)
    if percent <= 35:
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


def update_loop(icon):
    while True:
        try:
            percent, plugged = read_battery()
            _log(f"update: percent={percent} plugged={plugged}")
            icon.icon = make_icon_image(percent, plugged)
            icon.title = _status_text()
        except Exception:
            try:
                LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
                with open(LOG_PATH, "a", encoding="utf-8") as f:
                    f.write(traceback.format_exc())
            except OSError:
                pass
        time.sleep(UPDATE_INTERVAL_SECONDS)


def _log(msg):
    print(msg, flush=True)


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
        thread = threading.Thread(target=update_loop, args=(i,), daemon=True)
        thread.start()
        _log("update thread started")

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
