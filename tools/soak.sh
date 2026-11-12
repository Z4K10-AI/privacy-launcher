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

# Where on screen is the first thing whose text or description contains $1,
# in any case? With "re:" in front, $1 is a pattern the whole text must
# match instead: for a button like "Scan 11 apps", whose words also turn up
# in the sentence above it.
coords_of() {
  dump | python3 -c '
import sys, re
want = sys.argv[1].lower()
pattern = re.compile(want[3:], re.I) if want.startswith("re:") else None
def hit(text):
    if not text:
        return False
    return bool(pattern.fullmatch(text)) if pattern else want in text.lower()
for m in re.finditer(r"<node[^>]*>", sys.stdin.read()):
    tag = m.group(0)
    t = (re.search(r"text=\"([^\"]*)\"", tag) or [None,""])[1]
    d = (re.search(r"content-desc=\"([^\"]*)\"", tag) or [None,""])[1]
    if hit(t) or hit(d):
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

# report <label> <command...>: ok if the command succeeds.
report() {
  local label="$1"; shift
  if "$@"; then echo "  ok      $label"; PASS=$((PASS+1))
  else echo "  WRONG   $label"; FAIL=$((FAIL+1)); fi
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

# --- turned on its side ----------------------------------------------------
#
# A phone's home screen stays upright when the phone is turned. Android is
# told the user has turned it (rotation locked sideways, which is what the
# rotate button does), and the launcher's screen has to stay taller than it
# is wide all the same. A tablet or an opened folding phone is different:
# there the launcher is allowed to turn, so there is nothing to hold it to.
DENSITY="$($ADB shell wm density | tail -1 | sed 's/.*: *//' | tr -d '\r')"
case "$DENSITY" in ''|*[!0-9]*) DENSITY=420 ;; esac
SHORT_SIDE=$W; [ "$H" -lt "$W" ] && SHORT_SIDE=$H
SMALLEST_DP=$(( SHORT_SIDE * 160 / DENSITY ))

screen_is_upright() {
  dump | python3 -c '
import sys, re
m = re.search(r"bounds=\"\[0,0\]\[(\d+),(\d+)\]\"", sys.stdin.read())
sys.exit(0 if m and int(m.group(2)) > int(m.group(1)) else 1)
'
}

run_upright() {
  echo "--- turned on its side ---"
  if [ "$SMALLEST_DP" -ge 600 ]; then
    echo "  note    a large screen ($SMALLEST_DP dp across): the launcher may turn here, so nothing to check"
    return
  fi
  local auto turned
  auto="$($ADB shell settings get system accelerometer_rotation | tr -d '\r')"
  turned="$($ADB shell settings get system user_rotation | tr -d '\r')"
  $ADB shell settings put system accelerometer_rotation 0 >/dev/null
  $ADB shell settings put system user_rotation 1 >/dev/null
  sleep 3
  check  "on its side, the home screen is there"   "$HOME_MARK"    home
  report "and it is upright"                        screen_is_upright
  check  "the drawer opens"                         "Search apps"   retry_until "Search apps" up
  report "and it is upright too"                    screen_is_upright
  home
  case "$auto" in 0|1) ;; *) auto=0 ;; esac
  case "$turned" in 0|1|2|3) ;; *) turned=0 ;; esac
  $ADB shell settings put system user_rotation "$turned" >/dev/null
  $ADB shell settings put system accelerometer_rotation "$auto" >/dev/null
  sleep 2
}

# --- the hidden-apps lock ------------------------------------------------
#
# "Require unlock" puts the list of hidden apps behind the phone's own PIN.
# It used to be possible to switch that off with one tap and no PIN, which
# made it a lock with the key left in it. An emulator has no PIN, and with
# no PIN there is nothing to ask for, so the fault cannot show without one.
# This gives the emulator a PIN for the length of the check and takes it
# away again, whatever happens in between.
LOCK_PIN=1234
lock_cleanup() { $ADB shell locksettings clear --old "$LOCK_PIN" >/dev/null 2>&1; }
# Gives the emulator the PIN. If it already has it, left by a run that was
# killed before it could clean up, that counts too.
give_pin() {
  $ADB shell locksettings set-pin "$LOCK_PIN" 2>&1 | grep -q "Pin set" && return 0
  $ADB shell locksettings verify --old "$LOCK_PIN" 2>&1 | grep -qi "verified successfully"
}

# Is the switch on the row called $1 on? Prints true or false.
switch_state() {
  dump | python3 -c '
import sys, re
want = sys.argv[1].lower()
nodes = []
for m in re.finditer(r"<node[^>]*>", sys.stdin.read()):
    tag = m.group(0)
    b = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", tag)
    if b:
        nodes.append((tag, tuple(map(int, b.groups()))))
def text_of(tag):
    m = re.search(r"text=\"([^\"]*)\"", tag)
    return m.group(1).lower() if m else ""
label = next((box for tag, box in nodes if want in text_of(tag)), None)
if label is None:
    sys.exit(1)
cx, cy = (label[0] + label[2]) // 2, (label[1] + label[3]) // 2
# The smallest checkable thing that contains the label: its row.
rows = [(tag, box) for tag, box in nodes
        if "checkable=\"true\"" in tag
        and box[0] <= cx <= box[2] and box[1] <= cy <= box[3]]
if not rows:
    sys.exit(1)
tag, _ = min(rows, key=lambda r: (r[1][2] - r[1][0]) * (r[1][3] - r[1][1]))
print("true" if "checked=\"true\"" in tag else "false")
' "$1" 2>/dev/null
}

# check_switch <label> <row> <true|false>: the switch on <row> is in that state.
check_switch() {
  local got; got="$(switch_state "$2")"
  if [ "$got" = "$3" ]; then
    echo "  ok      $1"; PASS=$((PASS+1))
  else
    echo "  WRONG   $1  (the \"$2\" switch reads \"${got:-not found}\", expected $3)"
    FAIL=$((FAIL+1))
  fi
}

lock_row() { home; sleep 1; tap_on "Settings"; sleep 2; scroll_until "Require unlock" 30; }

run_lock() {
  echo "--- the hidden-apps lock ---"
  if ! give_pin; then
    echo "  skip    could not give this emulator a PIN, so there is nothing to ask for"
    return
  fi
  trap lock_cleanup EXIT
  lock_row >/dev/null 2>&1
  if [ "$(switch_state "Require unlock")" != "true" ]; then
    # Left off by an earlier run or by hand. Turning it on is always free.
    tap_on "Require unlock" >/dev/null 2>&1; sleep 2
  fi
  check_switch "the lock starts on" "Require unlock" true
  check "turning it off asks first"  "turning this lock off"  tap_on "Require unlock"
  # Refuse: back out of Android's "confirm it is you" screen. The first
  # back only puts its keyboard away, so keep going until the screen has
  # gone, but never so far as to back out of Settings as well.
  local i
  for i in 1 2 3 4; do
    present "turning this lock off" || break
    back; sleep 2
  done
  scroll_until "Require unlock" 30 >/dev/null 2>&1
  check_switch "refusing leaves it on" "Require unlock" true
  home
  lock_cleanup
  trap - EXIT
}

# --- settings in search, only when asked --------------------------------
#
# Drawer search finds apps. Android's own settings screens are found too
# only after a switch is turned on. This turns it on, checks, and puts it
# back off, which is how it starts.
search_switch() { home; sleep 1; tap_on "Settings"; sleep 2; scroll_until "Search Android settings" 30; }
search_for_display() {
  home; sleep 1
  retry_until "Search apps" up || return 1
  $ADB shell input text "displ"; sleep 2
}

run_search_optin() {
  echo "--- settings in search, only when asked ---"
  search_switch >/dev/null 2>&1
  if [ "$(switch_state "Search Android settings")" = "true" ]; then
    echo "  note    it was left on; turning it off to start from the default"
    tap_on "Search Android settings" >/dev/null 2>&1; sleep 2
  fi
  check_switch "it starts off" "Search Android settings" false
  check "off: no settings in search"  "Close app drawer+!Display"  search_for_display
  search_switch >/dev/null 2>&1
  tap_on "Search Android settings" >/dev/null 2>&1; sleep 2
  check_switch "it can be turned on" "Search Android settings" true
  check "on: search finds Display"    "Close app drawer+Display"   search_for_display
  search_switch >/dev/null 2>&1
  tap_on "Search Android settings" >/dev/null 2>&1; sleep 2
  check_switch "and it is put back off" "Search Android settings" false
  home
}

# --- trackers inside apps -------------------------------------------------
#
# The scan in the real app, on whatever this emulator has installed: open
# the dashboard, press the button, wait for an answer. It asserts that an
# answer comes and that it is remembered, not what the answer is. What a
# stock emulator's apps contain is not this project's to promise.
open_dashboard() {
  home; sleep 1
  tap_on "Settings"; sleep 2
  scroll_until "Privacy dashboard" 30 || return 1
  tap_on "Privacy dashboard"; sleep 3
  scroll_until "Trackers inside apps" 10
}

scan_for_trackers() {
  local i
  open_dashboard || return 1
  # Down to the last line of the card, so the button above it is on screen.
  # "List from Exodus" and not just "Exodus Privacy": the card's first
  # paragraph names Exodus Privacy too, and a search for that stops at the
  # top of the card with the button still below the edge of the screen.
  scroll_until "List from Exodus" 6
  if present "Read every app again"; then
    tap_on "Read every app again"
  else
    tap_on 're:scan \d+ apps?'
  fi
  # Until the progress line has gone and the results are up. A minute and
  # a half is far more than an emulator's couple of dozen apps need.
  for i in $(seq 1 45); do
    sleep 2
    present_all "Read every app again+!Reading app" && return 0
  done
  return 1
}

run_trackers() {
  echo "--- trackers inside apps ---"
  check "the dashboard offers a scan"   "Trackers inside apps"                         open_dashboard
  # Two looks, because the answer's first line and the button under it are
  # not always on screen together: on a short screen with large text the
  # card is taller than what is left of the screen. The scan having ended
  # is checked here; what it said is checked on the way back in.
  check "a scan runs to the end"        "Read every app again+!Reading app"            scan_for_trackers
  home
  check "its answer is shown, and remembered" "known tracker"                          open_dashboard_answer
  home
}

# The dashboard again, scrolled to the first line of the answer.
open_dashboard_answer() { open_dashboard && scroll_until "known tracker" 6; }

# --- short of memory, and killed -------------------------------------------
#
# On a 4 GB phone Android asks apps to give memory back, and when that is
# not enough it kills them, launchers included. Two things have to be true.
# Asked to give memory back, the launcher does so and carries on. Killed
# outright, it comes back by itself with the home screen as the user left
# it: what they arranged is on disk, not in the memory that was just lost.
#
# The kill is a real one: the process is ended with no warning, the way the
# system does it, not asked politely to stop. That needs a debug build,
# where adb is allowed to act as the app. For any other build the nearest
# thing is a force-stop, which is harsher still.
launcher_pid() { $ADB shell pidof "$PKG" 2>/dev/null | tr -d '\r'; }

kill_launcher() {
  local pid; pid="$(launcher_pid)"
  [ -z "$pid" ] && return 1
  $ADB shell run-as "$PKG" kill -9 "$pid" >/dev/null 2>&1 \
    || $ADB shell am force-stop "$PKG" >/dev/null 2>&1
  sleep 4
  home
}

calendar_on_home() {
  calendar_menu >/dev/null 2>&1
  local on=1
  present "Remove from home screen" && on=0
  back; sleep 1; home; sleep 1
  return $on
}

run_memory() {
  echo "--- short of memory, and killed ---"
  calendar_on_home || carry_calendar_home >/dev/null 2>&1
  sleep 2
  check "an icon is on the home screen"       "$HOME_MARK+Calendar"   home

  local before; before="$(launcher_pid)"
  check "asked for memory back, it carries on" "$HOME_MARK+Calendar" \
        $ADB shell am send-trim-memory "$PKG" RUNNING_CRITICAL
  report "and is still the same process" test "$(launcher_pid)" = "$before"

  check "killed, it comes back as it was"      "$HOME_MARK+Calendar"   kill_launcher
  report "as a new process (was $before, is $(launcher_pid))" \
         test -n "$(launcher_pid)" -a "$(launcher_pid)" != "$before"
  check "the icon is taken off again"          "$HOME_MARK+!Calendar"  take_calendar_off_home
}

# --- lock down -------------------------------------------------------------
#
# Lock down has to do more than lock the screen: after it, Android must
# accept only the PIN, not a fingerprint. Android keeps a number that says
# exactly that, and prints it on request. An ordinary lock, the power
# button, leaves it at 0. Lock down has to make it 2 ("strong
# authentication required after a device-admin lock"). This reads the
# number before, after, and once the PIN has been typed.
#
# It needs a PIN on the emulator and the launcher allowed to lock the
# screen. Both are given here and both are taken away again. The allowing is
# done through adb, because that screen is Android's and its wording
# differs between versions; the taking away is done through the launcher's
# own Settings, because that part is the launcher's and should be tested.
LOCKDOWN_ADMIN="$PKG/com.privacylauncher.system.LockDownAdminReceiver"

auth_flags() {
  $ADB shell dumpsys lock_settings 2>/dev/null | grep -A3 'PrimaryAuthFlags' \
    | grep -oE 'primaryAuthFlags=[0-9]+' | head -1 | sed 's/.*=//'
}
asleep() { $ADB shell dumpsys power 2>/dev/null | grep -q 'mWakefulness=Asleep'; }
admin_on() { $ADB shell dumpsys device_policy 2>/dev/null | grep -q "$LOCKDOWN_ADMIN"; }
# Android acts on the request a moment after it is made.
admin_gone() {
  local i
  for i in 1 2 3 4 5; do admin_on || return 0; sleep 1; done
  return 1
}

unlock_with_pin() {
  $ADB shell input keyevent KEYCODE_WAKEUP; sleep 1
  $ADB shell input swipe $MIDX $((H * 77 / 100)) $MIDX $((H * 25 / 100)) 200; sleep 2
  $ADB shell input text "$LOCK_PIN"
  $ADB shell input keyevent KEYCODE_ENTER; sleep 3
}

# Through the launcher's own Settings: Gestures, Lock down, Turn off.
turn_lockdown_off() {
  home; sleep 1
  tap_on "Settings"; sleep 2
  scroll_until "Tap to turn it off" 30 || return 1
  tap_on "Tap to turn it off"; sleep 2
  tap_on "re:turn off"; sleep 3
}

lockdown_cleanup() {
  # Whatever state it was left in: no PIN, awake, not a device admin.
  $ADB shell locksettings clear --old "$LOCK_PIN" >/dev/null 2>&1
  $ADB shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
  $ADB shell wm dismiss-keyguard >/dev/null 2>&1
  if admin_on; then turn_lockdown_off >/dev/null 2>&1; fi
  if admin_on; then
    echo "  NOTE    the launcher is still allowed to lock this emulator's screen."
    echo "          Turn it off: launcher Settings, Gestures, Lock down. Until"
    echo "          then Android will refuse to uninstall the launcher."
  fi
}

# The quick panel, with the Lock down tile on screen. It is the first tile,
# so this is normally no scrolling at all; the scroll is for a screen so
# short that even the first row is below the brightness card.
open_quick_at_lockdown() {
  retry_until "Quick controls" left || return 1
  scroll_until "Locks until your PIN" 4
}

run_lockdown() {
  echo "--- lock down ---"
  if ! give_pin; then
    echo "  skip    could not give this emulator a PIN"
    return
  fi
  trap lockdown_cleanup EXIT
  if ! admin_on && ! $ADB shell dpm set-active-admin --user 0 "$LOCKDOWN_ADMIN" 2>&1 | grep -q Success; then
    echo "  skip    could not allow the launcher to lock the screen through adb"
    lockdown_cleanup; trap - EXIT
    return
  fi
  if [ -z "$(auth_flags)" ]; then
    echo "  skip    this Android does not print the number this check reads"
    lockdown_cleanup; trap - EXIT
    return
  fi

  home; sleep 1
  check "the tile says it is ready"   "Locks until your PIN"   open_quick_at_lockdown
  local before; before="$(auth_flags)"
  tap_on "Lock down" >/dev/null 2>&1; sleep 4
  report "it locks the phone"                    asleep
  report "and Android now demands the PIN (was $before, is $(auth_flags), must be 2)" \
         test "$(auth_flags)" = "2"
  unlock_with_pin
  report "the PIN opens it again"                test "$(auth_flags)" = "0"
  check  "back on the home screen"    "$HOME_MARK"             home
  turn_lockdown_off >/dev/null 2>&1
  report "it can be turned off in the launcher's Settings" admin_gone
  home
  lockdown_cleanup
  trap - EXIT
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

# SOAK_ONLY=carry (or lock, search, trackers, memory, lockdown, or screens)
# runs one part. SOAK_ONLY=tour is one quick pass through the screens and
# one carried icon, at whatever size and font the emulator is set to; it is
# what tools/shapes.sh runs at each screen shape. It is for working on
# this script, not for calling the app tested: a full run has no SOAK_ONLY.
ONLY="${SOAK_ONLY:-}"
wanted() { [ -z "$ONLY" ] || [ "$ONLY" = "$1" ]; }

if [ "$ONLY" = "tour" ]; then
echo "=== one pass, on a ${W}x${H} screen (font $FONT_BEFORE) ==="
run_suite "as set"
run_carry
run_upright
fi

if wanted screens; then
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
sleep 3
fi

if wanted carry; then
echo
echo "=== D. a finger carrying an icon (font $FONT_BEFORE) ==="
run_carry
fi

if wanted upright; then
echo
echo "=== turned on its side ==="
run_upright
fi

if wanted lock; then
echo
echo "=== E. the hidden-apps lock, with a PIN on the emulator ==="
run_lock
fi

if wanted search; then
echo
echo "=== F. what search finds ==="
run_search_optin
fi

if wanted trackers; then
echo
echo "=== G. the tracker scan ==="
run_trackers
fi

if wanted memory; then
echo
echo "=== H. short of memory, and killed ==="
run_memory
fi

# Last, because it locks the emulator, and a lock that would not open again
# would otherwise take every part after it down too.
if wanted lockdown; then
echo
echo "=== I. lock down, with a PIN on the emulator ==="
run_lockdown
fi

[ -n "$ONLY" ] && echo && echo "(SOAK_ONLY=$ONLY: only part of the soak ran)"

echo
echo "=== result: $PASS ok, $FAIL problems ==="
[ "$FAIL" -eq 0 ]
