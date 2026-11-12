#!/bin/bash
# The emulators this project is tested on: make them, start them, stop them.
#
#   tools/emulators.sh list             what exists and what is running
#   tools/emulators.sh create [name]    make one, or all three; safe to repeat
#   tools/emulators.sh start <name>     boot it and wait until it is usable
#   tools/emulators.sh stop [name]      shut one down, or all of this project's
#   tools/emulators.sh serial <name>    print the adb serial of a running one
#   tools/emulators.sh delete <name>    remove one that this script made
#
# Add --window to `start` to see the screen; without it the emulator runs
# with no window, which is all the tests need.
#
# The profiles:
#
#   a30      Android 11 (API 30), 1080x2340 at 420 dpi, 4 GB RAM, 4 cores.
#            The screen and memory of a Samsung Galaxy A30: the low-end
#            phone this launcher has to feel good on.
#   modern   Android 15 (API 35), 1080x2400 at 420 dpi, 4 GB RAM. There to
#            catch what newer versions of Android do differently.
#   small    Android 15 (API 35), 720x1280 at 320 dpi -- a 360x640 dp
#            screen, the smallest in common use -- with the system font at
#            1.3x and 2.5 GB RAM, the least the emulator will give Android
#            15. The accessibility squeeze: least room, biggest text.
#
# All three use the plain AOSP system image for Apple Silicon (arm64), not
# the Google one: no Play Services, which is what the launcher will find on
# the de-Googled OS it is being built for.
#
# What these are not. An emulator copies a phone's screen size, memory and
# Android version. It does not copy its processor, its graphics chip or its
# storage: this Mac is several times faster than a Galaxy A30, and by
# default the emulator draws the screen in software. So a pass here means
# "works on this version of Android at this screen size", and every speed
# number measured here is an emulator number, not a phone number.

set -u
cd "$(dirname "$0")/.."
. tools/env.sh

EMU="$ANDROID_HOME/emulator/emulator"
ADB="$ANDROID_HOME/platform-tools/adb"
AVDMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"
PROFILES="a30 modern small"

# How the emulator draws. `swiftshader_indirect` is software rendering: slow
# but identical on every machine. `host` uses the Mac's own graphics chip:
# far faster than any phone this is aimed at. Override with PL_GPU=host.
GPU="${PL_GPU:-swiftshader_indirect}"

say() { echo "$@" >&2; }
die() { say "error: $*"; exit 1; }

# Sets P_* for the profile named $1.
profile() {
  case "${1:-}" in
    a30)    P_AVD=pl_a30;    P_API=30; P_ANDROID=11; P_W=1080; P_H=2340; P_DPI=420; P_RAM=4096; P_FONT=1.0
            P_TITLE="Privacy Launcher: a30 (Galaxy A30 shape, Android 11)" ;;
    modern) P_AVD=pl_modern; P_API=35; P_ANDROID=15; P_W=1080; P_H=2400; P_DPI=420; P_RAM=4096; P_FONT=1.0
            P_TITLE="Privacy Launcher: modern (Android 15)" ;;
    # 2560, not 2048: asked for less, the emulator raises it to 2560 for
    # this version of Android anyway, and the profile should say what it is.
    small)  P_AVD=pl_small;  P_API=35; P_ANDROID=15; P_W=720;  P_H=1280; P_DPI=320; P_RAM=2560; P_FONT=1.3
            P_TITLE="Privacy Launcher: small screen, large font (Android 15)" ;;
    *) return 1 ;;
  esac
}

need_profile() {
  profile "${1:-}" || die "no profile called '${1:-}'. The profiles are: $PROFILES"
}

avd_exists() { "$EMU" -list-avds 2>/dev/null | grep -qx "$1"; }

# The adb serial of the running emulator whose AVD is $1, if there is one.
# Asked of each emulator in turn, because the serial is only a port number
# and says nothing about which emulator is behind it.
serial_of() {
  local s name
  for s in $("$ADB" devices 2>/dev/null | awk '/^emulator-[0-9]+[[:space:]]/{print $1}'); do
    name="$("$ADB" -s "$s" emu avd name 2>/dev/null | head -1 | tr -d '\r')"
    if [ "$name" = "$1" ]; then echo "$s"; return 0; fi
  done
  return 1
}

# setkey <file> <key> <value>: one line per key, whatever was there before.
setkey() {
  local f="$1" k="$2" v="$3" pattern
  pattern="^$(printf '%s' "$k" | sed 's/\./\\./g')[[:space:]]*="
  grep -v "$pattern" "$f" > "$f.tmp" || true
  printf '%s = %s\n' "$k" "$v" >> "$f.tmp"
  mv "$f.tmp" "$f"
}

create_one() {
  need_profile "$1"
  local image="system-images;android-$P_API;default;arm64-v8a"
  local imagedir="$ANDROID_HOME/system-images/android-$P_API/default/arm64-v8a"

  if [ ! -d "$imagedir" ]; then
    say "$1: the Android $P_ANDROID system image is not installed yet."
    say "    Downloading it (about 1 GB, once). You may be asked to accept"
    say "    Google's SDK licence; that is yours to read and answer."
    "$SDKMANAGER" --install "$image" || die "could not install $image"
  fi

  if avd_exists "$P_AVD"; then
    say "$1: $P_AVD exists; bringing its settings up to date"
  else
    say "$1: creating $P_AVD"
    echo no | "$AVDMANAGER" create avd -n "$P_AVD" -k "$image" -d pixel_3a >/dev/null 2>&1 \
      || die "avdmanager could not create $P_AVD"
  fi

  local cfg="$AVD_HOME/$P_AVD.avd/config.ini"
  [ -f "$cfg" ] || die "expected to find $cfg"

  # Everything that makes the profile what it is, written every time so an
  # emulator made last month and one made today are the same emulator.
  setkey "$cfg" avd.ini.displayname "$P_TITLE"
  setkey "$cfg" hw.lcd.width "$P_W"
  setkey "$cfg" hw.lcd.height "$P_H"
  setkey "$cfg" hw.lcd.density "$P_DPI"
  setkey "$cfg" hw.ramSize "$P_RAM"
  setkey "$cfg" hw.cpu.ncore 4
  # Room for test builds and benchmark traces. avdmanager's own default is
  # 800 MB, which fills up. The file only takes the space actually used.
  setkey "$cfg" disk.dataPartition.size 6144M
  # On-screen keyboard, as on a phone: the drawer opens it for search.
  setkey "$cfg" hw.keyboard no
  setkey "$cfg" showDeviceFrame no
  # Always a full boot, never a resumed snapshot, whoever starts it. A
  # snapshot brings back whatever state the last run left behind.
  setkey "$cfg" fastboot.forceColdBoot yes
  setkey "$cfg" fastboot.forceFastBoot no
  setkey "$cfg" fastboot.forceChosenSnapshotBoot no

  say "$1: ready  (Android $P_ANDROID, ${P_W}x${P_H} at $P_DPI dpi, $P_RAM MB RAM)"
}

# Shuts down the emulator at serial $1 and waits until adb has let go of
# it. adb goes on listing a dead emulator for a second or two, and anything
# that reads the list in that time sees a ghost: the first full run of the
# suite reported "another emulator is also running" about the one it had
# just stopped.
kill_serial() {
  local s="$1" i
  "$ADB" -s "$s" emu kill >/dev/null 2>&1
  for i in $(seq 1 40); do
    "$ADB" devices 2>/dev/null | grep -q "^$s[[:space:]]" || return 0
    sleep 1
  done
  return 1
}

stop_one() {
  need_profile "$1"
  local s
  s="$(serial_of "$P_AVD")" || { say "$1: not running"; return 0; }
  say "$1: stopping ($s)"
  kill_serial "$s" || die "$1 did not shut down"
}

# Booted far enough to install and run things.
wait_boot() {
  local s="$1" i
  for i in $(seq 1 150); do
    if [ "$("$ADB" -s "$s" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] \
       && "$ADB" -s "$s" shell pm path android >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  return 1
}

# The state every test run should start from.
settle() {
  local s="$1"
  "$ADB" -s "$s" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
  "$ADB" -s "$s" shell wm dismiss-keyguard >/dev/null 2>&1
  # A screen that switches itself off mid-run fails every check after it.
  "$ADB" -s "$s" shell settings put system screen_off_timeout 1800000 >/dev/null 2>&1
  "$ADB" -s "$s" shell svc power stayon true >/dev/null 2>&1
  "$ADB" -s "$s" shell settings put system font_scale "$P_FONT" >/dev/null 2>&1
}

start_one() {
  need_profile "$1"
  local name="$1" window="${2:-}" s other os
  avd_exists "$P_AVD" || die "$name has not been made yet. Run: tools/emulators.sh create $name"

  if s="$(serial_of "$P_AVD")"; then
    wait_boot "$s" || die "$name is running but never finished booting"
    settle "$s"
    echo "$s"
    return 0
  fi

  # One at a time. Two 4 GB emulators plus a build do not fit in 16 GB, and
  # a Mac that is swapping makes every timing meaningless.
  for other in $PROFILES; do
    [ "$other" = "$name" ] && continue
    if os="$(profile "$other" && serial_of "$P_AVD")"; then
      say "stopping $other first: one emulator at a time on this Mac"
      kill_serial "$os" || die "$other did not shut down"
    fi
  done
  need_profile "$name"
  if "$ADB" devices 2>/dev/null | grep -q '^emulator-'; then
    say "note: another emulator, not one of this project's, is also running."
    say "      Results will be slower and noisier until it is closed."
  fi

  local log="${TMPDIR:-/tmp}/privacy-launcher-$P_AVD.log"
  local win="-no-window"
  [ "$window" = "--window" ] && win=""
  say "$name: booting $P_AVD ($GPU rendering; log: $log)"
  # nohup, so the emulator outlives this script.
  # shellcheck disable=SC2086
  nohup "$EMU" -avd "$P_AVD" $win -no-audio -no-boot-anim -no-snapshot \
    -gpu "$GPU" > "$log" 2>&1 &
  local pid=$! i
  s=""
  for i in $(seq 1 120); do
    if s="$(serial_of "$P_AVD")"; then break; fi
    if ! kill -0 "$pid" 2>/dev/null; then
      say "the emulator exited before it came up. Last lines of its log:"
      tail -15 "$log" >&2
      exit 1
    fi
    sleep 1
  done
  [ -n "$s" ] || die "$name never appeared in adb"
  wait_boot "$s" || die "$name did not finish booting within five minutes"
  settle "$s"
  say "$name: up as $s"
  echo "$s"
}

list_all() {
  local p state s
  printf '%-8s %-10s %-8s %-15s %-8s %s\n' profile emulator android screen ram state
  for p in $PROFILES; do
    profile "$p"
    if ! avd_exists "$P_AVD"; then state="not made yet"
    elif s="$(serial_of "$P_AVD")"; then state="running as $s"
    else state="made, stopped"
    fi
    printf '%-8s %-10s %-8s %-15s %-8s %s\n' \
      "$p" "$P_AVD" "$P_ANDROID" "${P_W}x${P_H}@${P_DPI}" "${P_RAM}M" "$state"
  done
}

cmd="${1:-list}"
case "$cmd" in
  list)
    list_all ;;
  create)
    if [ -n "${2:-}" ] && [ "$2" != "all" ]; then create_one "$2"
    else for p in $PROFILES; do create_one "$p"; done
    fi ;;
  start)
    [ -n "${2:-}" ] || die "which one? tools/emulators.sh start a30"
    start_one "$2" "${3:-}" ;;
  stop)
    if [ -n "${2:-}" ] && [ "$2" != "all" ]; then stop_one "$2"
    else for p in $PROFILES; do stop_one "$p"; done
    fi ;;
  has)
    # Quietly: is this the name of a profile? For the other scripts.
    profile "${2:-}" ;;
  names)
    echo "$PROFILES" ;;
  serial)
    need_profile "${2:-}"
    serial_of "$P_AVD" || die "$2 is not running. Start it with: tools/emulators.sh start $2" ;;
  delete)
    need_profile "${2:-}"
    avd_exists "$P_AVD" || { say "$2: nothing to delete"; exit 0; }
    stop_one "$2"
    need_profile "$2"
    "$AVDMANAGER" delete avd -n "$P_AVD" >/dev/null 2>&1 || die "could not delete $P_AVD"
    say "$2: deleted $P_AVD" ;;
  *)
    sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//' >&2
    exit 2 ;;
esac
