#!/bin/bash
# Takes the screenshots used in the README and the F-Droid listing.
#
#   tools/screenshots.sh            on the modern emulator (Android 15)
#   tools/screenshots.sh a30        on another
#
# It installs the release build (build it first: ./gradlew assembleRelease),
# wipes the launcher's own data so every run starts the same, walks through
# the welcome pages, sets up a home screen the way a person might (a few
# apps, a folder, a dock), and photographs the main screens. The pictures go
# to fastlane/metadata/android/en-US/images/phoneScreenshots/, where F-Droid
# looks for them; the README shows the same files.
#
# Everything in them is the emulator's: its stock apps and its wallpaper.
# The status bar is put in Android's demo mode (a fixed time, full signal,
# no notifications) so it is tidy and the same every time, and everything
# changed on the emulator is put back at the end.
set -u
cd "$(dirname "$0")/.."
. tools/env.sh
ADB="$ANDROID_HOME/platform-tools/adb"
. tools/device.sh

PROFILE=modern
if is_profile "${1:-}"; then PROFILE="$1"; shift; fi
pick_device "$PROFILE" || exit 2
echo "screenshots on $(describe_device)"

PKG=com.privacylauncher
APK=app/build/outputs/apk/release/app-release.apk
OUT=fastlane/metadata/android/en-US/images/phoneScreenshots
[ -f "$APK" ] || { echo "ABORT: build the release first: ./gradlew assembleRelease" >&2; exit 2; }

read -r W H <<< "$($ADB shell wm size | tail -1 | sed 's/.*: *//' | tr 'x' ' ')"
MIDX=$((W / 2))
w() { perl -e "select(undef,undef,undef,$1)"; }

# --- finding things on screen --------------------------------------------

dump() { $ADB shell uiautomator dump /sdcard/shot.xml >/dev/null 2>&1; $ADB shell cat /sdcard/shot.xml 2>/dev/null; }

# The middle of the first thing whose text or spoken label is exactly $1.
# Exactly, not "contains": a search box holding "cal" must not be mistaken
# for the Calendar it found. With $2 set to "desc", only the spoken label
# counts: the launcher's settings button and Android's Settings app, if it
# is on the home screen, are both called "Settings".
where() {
  dump | python3 -c '
import sys, re
want, only_desc = sys.argv[1], len(sys.argv) > 2
for m in re.finditer(r"<node[^>]*>", sys.stdin.read()):
    tag = m.group(0)
    t = (re.search(r"text=\"([^\"]*)\"", tag) or [None, ""])[1]
    d = (re.search(r"content-desc=\"([^\"]*)\"", tag) or [None, ""])[1]
    if (d == want) if only_desc else (want in (t, d)):
        b = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", tag)
        x1, y1, x2, y2 = map(int, b.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
' "$@"
}

on_screen() { dump | grep -q -F "\"$1\""; }
# The same, for part of a longer text.
on_screen_part() { dump | grep -q -F "$1"; }

wait_for() {
  local i
  for i in $(seq 1 20); do on_screen "$1" && return 0; w 0.5; done
  echo "never saw \"$1\"" >&2
  return 1
}

tap()  { local c; c="$(where "$1")"; [ -n "$c" ] || { echo "nothing called \"$1\" to tap" >&2; return 1; }; $ADB shell input tap $c; }
tap_desc() { local c; c="$(where "$1" desc)"; [ -n "$c" ] || { echo "no button called \"$1\"" >&2; return 1; }; $ADB shell input tap $c; }
hold() { local c; c="$(where "$1")"; [ -n "$c" ] || { echo "nothing called \"$1\" to hold" >&2; return 1; }; $ADB shell input swipe $c $c 900; }

# Before each picture, one touch on an empty patch of screen ($3, as "x y",
# when the default would land on something). The script moves around with
# key presses, and after a key press Android draws a focus outline round
# whatever has focus, which is right for keyboard users and wrong in a
# picture of a touch screen. A touch puts it back in touch mode.
shoot() {
  $ADB shell input tap ${3:-$((W - 4)) $((H / 2))}; w 1
  $ADB exec-out screencap -p > "$OUT/$1.png"; echo "  $1.png  $2"
}

home()  { $ADB shell input keyevent KEYCODE_HOME; w 1.5; }
up()    { $ADB shell input swipe $MIDX $((H * 80 / 100)) $MIDX $((H * 20 / 100)) 250; }
left()  { $ADB shell input swipe $((W * 85 / 100)) $((H / 2)) $((W * 15 / 100)) $((H / 2)) 250; }
scroll() { $ADB shell input swipe $MIDX $((H * 70 / 100)) $MIDX $((H * 35 / 100)) 400; w 1; }

# Scrolls down until something containing $1 is on screen.
scroll_to() {
  local i
  for i in $(seq 1 25); do on_screen_part "$1" && return 0; scroll; done
  on_screen_part "$1"
}

open_drawer() {
  home
  up
  wait_for "Search apps" || wait_for "Close app drawer"
  w 1
}

# Adds the app found by typing $1, called $2, to the home screen or the dock
# ($3 is the menu item to choose), through the drawer's own long-press menu.
add_app() {
  open_drawer
  $ADB shell input text "$1"; w 1.5
  hold "$2" && w 1.5 && tap "$3"
  w 1
  home
}

# --- the emulator, as it was, and as it will be put back --------------------

NIGHT_BEFORE="$($ADB shell cmd uimode night 2>/dev/null | tr -d '\r' | awk '{print $NF}')"
case "$NIGHT_BEFORE" in yes|no|auto) ;; *) NIGHT_BEFORE=no ;; esac

demo() { $ADB shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null 2>&1; }

restore() {
  demo exit
  $ADB shell appops set "$PKG" WRITE_SETTINGS default >/dev/null 2>&1
  $ADB shell dumpsys battery reset >/dev/null 2>&1
  $ADB shell "cmd uimode night $NIGHT_BEFORE" >/dev/null 2>&1
}
trap restore EXIT

$ADB shell settings put global sysui_demo_allowed 1 >/dev/null
demo enter
demo clock -e hhmm 0941
demo battery -e level 86 -e plugged false
demo network -e wifi show -e level 4 -e fully true
demo network -e mobile hide
demo notifications -e visible false
# The glance line under the clock reads the real battery, not the demo one.
$ADB shell dumpsys battery unplug >/dev/null
$ADB shell dumpsys battery set ac 0 >/dev/null
$ADB shell dumpsys battery set usb 0 >/dev/null
$ADB shell dumpsys battery set status 3 >/dev/null
$ADB shell dumpsys battery set level 86 >/dev/null
# Dark, because a home screen mostly sits over a wallpaper, and the panels
# then match it.
$ADB shell "cmd uimode night yes" >/dev/null

mkdir -p "$OUT"
rm -f "$OUT"/*.png

$ADB install -r "$APK" >/dev/null
$ADB shell pm clear "$PKG" >/dev/null
# As though the person had allowed the brightness slider, so the quick
# controls show it working rather than asking for access.
$ADB shell appops set "$PKG" WRITE_SETTINGS allow >/dev/null
$ADB shell cmd package set-home-activity "$PKG/$PKG.MainActivity" >/dev/null
home
w 2

# --- the welcome pages: the last one is the promise ---------------------------

wait_for "Next"
tap "Next"; w 1.5
tap "Next"; w 1.5
tap "Next"; w 2
shoot 7 "what it does not do (the last welcome page)"
tap "Get started"; w 2
wait_for "Open app drawer"

# --- a home screen a person might have -----------------------------------------

add_app "clock"    "Clock"    "Add to home screen"
add_app "calen"    "Calendar" "Add to home screen"
add_app "galle"    "Gallery"  "Add to home screen"
add_app "files"    "Files"    "Add to home screen"
add_app "conta"    "Contacts" "Add to home screen"
add_app "setti"    "Settings" "Add to home screen"
add_app "searc"    "Search"   "Add to home screen"
add_app "webvi"    "WebView Browser Tester" "Add to home screen"
add_app "phone"    "Phone"     "Add to dock"
add_app "messa"    "Messaging" "Add to dock"
add_app "camer"    "Camera"    "Add to dock"
# The emulator's SIM toolkit, whose own label is misspelt "TMoble", hidden
# the way a person would hide an app they never use.
add_app "tmob"     "TMoble"    "Hide from drawer"

# A folder: the browser tester carried onto Search.
from="$(where "WebView Browser Tester")"; onto="$(where "Search")"
if [ -n "$from" ] && [ -n "$onto" ]; then
  $ADB shell input draganddrop $from $onto 1500; w 2
  tap "Done"; w 1.5
  tap "Folder"; w 1.5
  # Rename it: to the end of the name, delete it, type a new one.
  tap "Folder name"; w 1
  # Wherever the tap left the cursor, delete to both sides of it.
  $ADB shell input keyevent KEYCODE_MOVE_END
  $ADB shell input keyevent KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL
  $ADB shell input keyevent KEYCODE_MOVE_HOME
  $ADB shell input keyevent KEYCODE_FORWARD_DEL KEYCODE_FORWARD_DEL KEYCODE_FORWARD_DEL KEYCODE_FORWARD_DEL KEYCODE_FORWARD_DEL KEYCODE_FORWARD_DEL KEYCODE_FORWARD_DEL KEYCODE_FORWARD_DEL
  $ADB shell input text "Web"; w 1
  $ADB shell input keyevent KEYCODE_BACK; w 1
  $ADB shell input keyevent KEYCODE_BACK; w 1.5
fi
home; w 2
# The touch before the picture lands between the clock and the icons.
shoot 1 "the home screen" "$MIDX $((H * 40 / 100))"

# --- the drawer ----------------------------------------------------------------

open_drawer
# The keyboard comes up with the drawer, for typing; one back puts it away
# and leaves the drawer open, which shows the grid and the letter headings.
$ADB shell input keyevent KEYCODE_BACK; w 1.5
shoot 2 "the app drawer"
home

# --- the privacy dashboard ------------------------------------------------------

tap_desc "Settings"; w 2
scroll_to "Privacy dashboard" && tap "Privacy dashboard"
wait_for "Privacy score"; w 2
shoot 3 "the privacy dashboard"
scroll_to "List from Exodus Privacy" >/dev/null
for label in $(dump | grep -o -E 'text="Scan [0-9]+ apps?"' | head -1 | sed 's/text="//; s/"$//' | tr ' ' '_'); do
  tap "${label//_/ }"
done
wait_for "Read every app again"; w 1.5
shoot 4 "the tracker scan"
home

# --- quick controls -------------------------------------------------------------

left
wait_for "Quick controls"; w 1.5
shoot 5 "quick controls"
home

# --- settings, with the live preview ---------------------------------------------

tap_desc "Settings"; w 2
wait_for "Theme"
shoot 6 "settings"
home

echo "done: $(ls "$OUT" | wc -l | tr -d ' ') pictures in $OUT"
