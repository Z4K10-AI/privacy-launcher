#!/bin/bash
# Walk every screen and assert we actually arrived, not merely that nothing
# crashed.
#
#   tools/soak.sh                the one emulator that is running
#   tools/soak.sh a30            that profile, booted if it is not up
#   tools/soak.sh a30 <package>  a build other than the debug one
#
# The app has to be installed already; tools/install.sh does that.
#
# The first version of this only checked "no crash, process alive". That
# passes just as happily when a tap finds nothing and the app sits on the
# home screen for twenty steps — which is the failure mode most likely here,
# because synthetic taps on this emulator miss often. Every navigation step
# now names what should be on screen afterwards, and a step that does not
# find it is a failure.
set -u
cd "$(dirname "$0")/.."
. tools/env.sh
ADB="$ANDROID_HOME/platform-tools/adb"
. tools/device.sh

PROFILE=""
if is_profile "${1:-}"; then PROFILE="$1"; shift; fi
PKG="${1:-com.privacylauncher.debug}"
pick_device "$PROFILE" || exit 2
echo "soak on $(describe_device)"

if ! $ADB shell pm path "$PKG" >/dev/null 2>&1; then
  echo "ABORT: $PKG is not installed on this emulator."
  echo "       Install it first: tools/install.sh $PROFILE"
  exit 2
fi

# What says "this is the home screen" and appears nowhere else: the label
# on the handle that opens the drawer. It flips to "Close app drawer" when
# the drawer is up and is gone under the quick panel and in Settings.
#
# This used to be the word "Battery", from the line under the clock, and
# that was wrong twice over. The line reads "Charging 100%" whenever the
# device is on power, which a freshly booted emulator always is, so the
# script could not find a home screen it was looking straight at and
# aborted. And the line stays in the tree underneath the quick panel, so
# "back to home" after the panel would have passed with the panel still up.
HOME_MARK="Open app drawer"

# Put back afterwards exactly what was there before, not a guess at it.
FONT_BEFORE="$($ADB shell settings get system font_scale 2>/dev/null | tr -d '\r')"
case "$FONT_BEFORE" in ''|null) FONT_BEFORE=1.0 ;; esac
NIGHT_BEFORE="$($ADB shell cmd uimode night 2>/dev/null | tr -d '\r' | awk '{print $NF}')"
case "$NIGHT_BEFORE" in yes|no|auto) ;; *) NIGHT_BEFORE=no ;; esac

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

# Is everything in $1 on screen? $1 is one or more strings joined by "+",
# and a string starting with "!" must NOT be there. One look at the screen
# answers the lot, so "on the home screen and showing Calendar" is a single
# statement about a single moment.
present_all() {
  local xml part ok=0
  xml="$(dump)" || return 1
  while IFS= read -r part; do
    [ -z "$part" ] && continue
    case "$part" in
      '!'*) printf '%s' "$xml" | grep -qi -- "${part#!}" && ok=1 ;;
      *)    printf '%s' "$xml" | grep -qi -- "$part" || ok=1 ;;
    esac
  done <<< "$(printf '%s' "$1" | tr '+' '\n')"
  return $ok
}

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
  if [ -n "$expect" ] && ! present_all "$expect"; then
    echo "  WRONG   $label  (expected \"$expect\" on screen)"
    FAIL=$((FAIL+1)); return
  fi
  echo "  ok      $label"
  PASS=$((PASS+1))
}

tap_on() { local c; c=$(coords_of "$1"); [ -z "$c" ] && return 1; $ADB shell input tap $c; }

# Press and hold on $1, then let go without moving.
hold_on() { local c; c=$(coords_of "$1"); [ -z "$c" ] && return 1; $ADB shell input swipe $c $c 900; }

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
# screen a swipe starting at y=1800 begins below the bottom of the display,
# so nothing happens and every check downstream reports a failure that is
# the harness's fault, not the app's.
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
  check "home screen"        "$HOME_MARK"        home
  check "open drawer"        "Search apps"       retry_until "Search apps" up
  check "search finds apps"  "Calendar"          $ADB shell input text "ca"
  check "back to home"       "$HOME_MARK"        home
  check "settings opens"     "APPEARANCE"        tap_on "Settings"
  check "reaches About"      "ABOUT"             scroll_until "ABOUT"
  check "back to home"       "$HOME_MARK"        home
  # Was an empty expectation, which only asserted "did not crash" -- the
  # weak check this script's own header complains about. It passed happily
  # while the other eight were failing, because a swipe that does nothing
  # also does not crash.
  check "quick panel"        "Quick controls"    retry_until "Quick controls" left
  check "back to home"       "$HOME_MARK"        home
}

# --- carrying an icon ---------------------------------------------------
#
# Everything above is taps and swipes. This is the one gesture that is
# neither: press, hold, and carry. `input draganddrop` holds for Android's
# own long-press time before it moves, which a plain swipe does not.
#
# It uses Calendar, which every one of these emulators has, and leaves the
# home screen as it found it only if Calendar was not on it to begin with:
# if it was, it is taken off first so there is something to prove, and it
# stays off afterwards. These are test emulators; that is said out loud
# rather than done quietly.

# The drawer, open, searched down to Calendar.
#
# The search is for "calen", not the whole word. coords_of takes the first
# thing on screen whose text contains what it is given, in any case, and
# with "calendar" typed in the search box the first such thing is the
# search box. The finger then holds the search box, nothing is carried, and
# the check fails for a reason that has nothing to do with the app.
drawer_showing_calendar() {
  home; sleep 1
  retry_until "Search apps" up || return 1
  $ADB shell input text "calen"; sleep 2
}

# Carry Calendar out of the drawer and let go over the clock: on the home
# screen, but not on any icon, which means "add it at the end".
carry_calendar_home() {
  local c
  drawer_showing_calendar || return 1
  c=$(coords_of "Calendar"); [ -z "$c" ] && return 1
  $ADB shell input draganddrop $c $MIDX $((H * 15 / 100)) 1500
}

# Calendar's menu in the drawer, opened by holding it and letting go.
calendar_menu() {
  drawer_showing_calendar || return 1
  hold_on "Calendar"; sleep 2
}

take_calendar_off_home() {
  calendar_menu || return 1
  tap_on "Remove from home screen"; sleep 1
  home
}

run_carry() {
  echo "--- carrying an icon ---"
  calendar_menu >/dev/null 2>&1
  if present "Remove from home screen"; then
    echo "  note    Calendar was already on the home screen; taking it off first"
    tap_on "Remove from home screen" >/dev/null 2>&1; sleep 1
  fi
  home; sleep 1
  check "calendar not on home"   "$HOME_MARK+!Calendar"        home
  check "carry it out of drawer" "$HOME_MARK+Calendar"         carry_calendar_home
  check "hold opens its menu"    "Remove from home screen"     calendar_menu
  check "taken off again"        "$HOME_MARK+!Calendar"        take_calendar_off_home
}

# A fresh install opens on the onboarding flow, not the home screen.
#
# Without this, every check below looks for home-screen text, finds a welcome
# page instead, and reports a failure -- 24 of them, none of them about the
# app. That is not a hypothetical: it happened on the first run after a data
# wipe, and it is the same trap that once had a baseline profile recorded
# against the onboarding screen and called verified. A harness that can
# silently measure the wrong screen is worse than no harness, so this one
# refuses to start until it knows where it is.
clear_onboarding() {
  local i
  for i in 1 2 3 4 5 6; do
    present "$HOME_MARK" && return 0
    if present "Skip"; then
      tap_on "Skip" >/dev/null 2>&1
    elif present "Next"; then
      tap_on "Next" >/dev/null 2>&1
    elif present "Done" || present "Finish"; then
      tap_on "Done" >/dev/null 2>&1 || tap_on "Finish" >/dev/null 2>&1
    else
      home
    fi
    sleep 2
  done
  present "$HOME_MARK"
}

# Be the home app before pressing home.
#
# If another launcher holds the role, pressing home either lands on that
# launcher or raises Android's "Use which app as Home?" chooser -- and then
# the checks below measure Quickstep, or a system dialog, and report the
# app as broken. Seen for real on a freshly wiped emulator.
ensure_default_home() {
  local current
  # --brief still prints a priority line first; the component is last.
  current="$($ADB shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | tail -1 | tr -d '\r')"
  case "$current" in
    "$PKG"/*) return 0 ;;
  esac
  echo "home app is '$current', switching to $PKG"
  $ADB shell cmd package set-home-activity "$PKG/com.privacylauncher.MainActivity" >/dev/null 2>&1
  # --brief still prints a priority line first; the component is last.
  current="$($ADB shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | tail -1 | tr -d '\r')"
  case "$current" in
    "$PKG"/*) return 0 ;;
    *) echo "could not become the home app (still '$current')"; return 1 ;;
  esac
}

if ! ensure_default_home; then
  echo "ABORT: not the default home app, so pressing home would measure"
  echo "       another launcher. Set it by hand and run again."
  exit 2
fi

$ADB shell am force-stop "$PKG" >/dev/null 2>&1
$ADB shell input keyevent KEYCODE_HOME >/dev/null 2>&1
sleep 3
if ! clear_onboarding; then
  echo "ABORT: could not reach the home screen -- still on onboarding or"
  echo "       another launcher is in front. Nothing below would be"
  echo "       measuring this app, so the run stops here rather than"
  echo "       printing failures that are not the app's."
  $ADB shell uiautomator dump /sdcard/soak.xml >/dev/null 2>&1
  $ADB shell cat /sdcard/soak.xml 2>/dev/null \
    | tr '<' '\n' | grep -o 'text="[^"]*"' | grep -v 'text=""' | head -8 | sed 's/^/       saw: /'
  exit 2
fi
echo "onboarding cleared, home screen reached"
echo

echo "=== A. as the emulator is set (font $FONT_BEFORE) ==="
run_suite "as set"

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
$ADB shell settings put system font_scale "$FONT_BEFORE" >/dev/null
$ADB shell "cmd uimode night $NIGHT_BEFORE" >/dev/null 2>&1


echo
echo "=== D. a finger carrying an icon (font $FONT_BEFORE) ==="
sleep 3
run_carry

echo
echo "=== result: $PASS ok, $FAIL problems ==="
[ "$FAIL" -eq 0 ]
