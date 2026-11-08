#!/bin/bash
# Walk every screen and assert we actually arrived, not merely that nothing
# crashed.
#
# The first version of this only checked "no crash, process alive". That
# passes just as happily when a tap finds nothing and the app sits on the
# home screen for twenty steps — which is the failure mode most likely here,
# because synthetic taps on this emulator miss often. Every navigation step
# now names what should be on screen afterwards, and a step that does not
# find it is a failure.
set -u
PKG="${1:-com.privacylauncher.debug}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"

PASS=0; FAIL=0

dump() {
  local i out
  for i in 1 2 3 4 5; do
    $ADB shell uiautomator dump /sdcard/soak.xml >/dev/null 2>&1
    out="$($ADB shell cat /sdcard/soak.xml 2>/dev/null)"
    case "$out" in *"<hierarchy"*) printf '%s' "$out"; return 0 ;; esac
    sleep 1
  done
  return 1
}

# Is $1 present as text or content-desc?
present() { dump | grep -qi -- "$1"; }

coords_of() {
  dump | python3 -c '
import sys, re
want = sys.argv[1].lower()
for m in re.finditer(r"<node[^>]*>", sys.stdin.read()):
    tag = m.group(0)
    t = (re.search(r"text=\"([^\"]*)\"", tag) or [None,""])[1]
    d = (re.search(r"content-desc=\"([^\"]*)\"", tag) or [None,""])[1]
    if want in t.lower() or (d and want in d.lower()):
        b = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", tag)
        if b:
            x1,y1,x2,y2 = map(int, b.groups())
            print((x1+x2)//2, (y1+y2)//2); break
' "$1" 2>/dev/null
}

crashed() { $ADB logcat -b crash -d 2>/dev/null | grep -q "$PKG"; }

# check <label> <expected-on-screen> <command...>
check() {
  local label="$1" expect="$2"; shift 2
  $ADB logcat -b crash -c 2>/dev/null
  "$@" >/dev/null 2>&1
  sleep 2
  if crashed; then
    echo "  CRASH   $label"
    $ADB logcat -b crash -d 2>/dev/null | grep -m1 -A4 "FATAL EXCEPTION" | sed 's/^/          /'
    FAIL=$((FAIL+1)); return
  fi
  if [ -z "$($ADB shell pidof "$PKG" 2>/dev/null | tr -d '\r')" ]; then
    echo "  GONE    $label"; FAIL=$((FAIL+1)); return
  fi
  if [ -n "$expect" ] && ! present "$expect"; then
    echo "  WRONG   $label  (expected \"$expect\" on screen)"
    FAIL=$((FAIL+1)); return
  fi
  echo "  ok      $label"
  PASS=$((PASS+1))
}

tap_on() { local c; c=$(coords_of "$1"); [ -z "$c" ] && return 1; $ADB shell input tap $c; }

# Swipe up to $2 times until $1 appears. A single swipe does not reach the
# bottom of a six-section Settings list, and asserting that it does makes the
# test fail for its own reasons rather than the app's.
scroll_until() {
  local want="$1" tries="${2:-20}" i
  for i in $(seq 1 "$tries"); do
    present "$want" && return 0
    scroll
    sleep 1
  done
  present "$want"
}

# Retry a gesture that the emulator sometimes drops entirely.
retry_until() {
  local want="$1"; shift
  local i
  for i in 1 2 3; do
    "$@" >/dev/null 2>&1
    sleep 2
    present "$want" && return 0
  done
  return 1
}
# Gestures sized from the real display, not hardcoded.
#
# These were fixed pixel values for a 1080x2400 emulator. On a 720x1560
# phone -- a Galaxy A30, say -- a swipe starting at y=1800 begins below the
# bottom of the screen, so nothing happens and every check downstream
# reports a failure that is the harness's fault, not the app's.
read -r W H <<< "$($ADB shell wm size | tail -1 | sed 's/.*: *//' | tr 'x' ' ')"
W=${W:-1080}; H=${H:-2400}
MIDX=$((W / 2))

home()   { $ADB shell input keyevent KEYCODE_HOME; }
back()   { $ADB shell input keyevent KEYCODE_BACK; }
# Start clear of the bottom edge, where the system's own gesture lives.
up()     { $ADB shell input swipe $MIDX $((H * 75 / 100)) $MIDX $((H * 25 / 100)) 250; }
left()   { $ADB shell input swipe $((W * 85 / 100)) $((H / 2)) $((W * 15 / 100)) $((H / 2)) 250; }
scroll() { $ADB shell input swipe $MIDX $((H * 68 / 100)) $MIDX $((H * 30 / 100)) 400; }

run_suite() {
  echo "--- $1 ---"
  check "home screen"        "Battery"           home
  check "open drawer"        "Search apps"       retry_until "Search apps" up
  check "search finds apps"  "Calendar"          $ADB shell input text "ca"
  check "back to home"       "Battery"           home
  check "settings opens"     "APPEARANCE"        tap_on "Settings"
  check "reaches About"      "ABOUT"             scroll_until "ABOUT"
  check "back to home"       "Battery"           home
  check "quick panel"        ""                  left
  check "back to home"       "Battery"           home
}

echo "=== A. default font, light ==="
run_suite "default"

echo
echo "=== B. large font (1.3) ==="
$ADB shell settings put system font_scale 1.3 >/dev/null; sleep 3
run_suite "font 1.3"

echo
echo "=== C. largest font (1.5) + dark ==="
$ADB shell settings put system font_scale 1.5 >/dev/null
$ADB shell "cmd uimode night yes" >/dev/null 2>&1; sleep 3
run_suite "font 1.5 dark"

echo
echo "=== restore device settings ==="
$ADB shell settings put system font_scale 1.0 >/dev/null
$ADB shell "cmd uimode night no" >/dev/null 2>&1

echo
echo "=== result: $PASS ok, $FAIL problems ==="
[ "$FAIL" -eq 0 ]
