#!/usr/bin/env bash
# Emulator smoke test (run by CI inside android-emulator-runner):
# installs the debug APK, makes BumBoard the keyboard, opens the settings app,
# types in its test field and saves screenshots + logcat to $OUT.
set -uo pipefail
APK="$1"
OUT="${2:-smoke}"
mkdir -p "$OUT"
PKG=app.bumboard.android.debug
IME="$PKG/org.fcitx.fcitx5.android.input.FcitxInputMethodService"

shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
tap_field() {
  # find the first EditText on screen and tap its centre
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml "$OUT/ui-$1.xml" >/dev/null 2>&1
  local b
  b=$(grep -o 'class="android.widget.EditText"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' "$OUT/ui-$1.xml" | head -1 | grep -o '\[[0-9,]*\]\[[0-9,]*\]')
  [ -z "$b" ] && { echo "no text field found"; return 1; }
  local x1 y1 x2 y2
  read -r x1 y1 x2 y2 <<<"$(echo "$b" | tr '[],' '   ')"
  adb shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
}

adb logcat -c
adb install -r -g "$APK"
adb shell ime enable "$IME"
adb shell ime set "$IME"
adb shell am start -W -n "$PKG/app.bumboard.settings.SettingsActivity"
sleep 8
shot 01-settings
tap_field settings && sleep 6
shot 02-keyboard
adb shell input text neihou
sleep 4
shot 03-cantonese-candidates
adb shell input keyevent KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL
adb shell input text syutgwai
sleep 3
shot 04-syutgwai
adb logcat -d > "$OUT/logcat.txt"
grep -n "FATAL EXCEPTION\|AndroidRuntime: \|BumBoard" "$OUT/logcat.txt" | head -80 > "$OUT/crashes.txt" || true
echo "--- crashes / BumBoard log lines ---"
cat "$OUT/crashes.txt"
