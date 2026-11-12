#!/bin/bash
# Sourced by the test scripts. Decides which emulator they talk to.
#
#   . tools/device.sh
#   pick_device "$name"     # a profile from tools/emulators.sh, or empty
#
# With a name, that emulator is booted if it is not already up. With none,
# the one emulator that is running is used. Either way ANDROID_SERIAL is
# exported, which adb and Gradle both obey, so everything after that line
# talks to that emulator and to nothing else.
#
# Anything that is not an emulator is refused. For now this project is
# built and tested on emulators only, and a phone that happens to be plugged
# in must never have a test build installed on it by accident.

ADB="${ADB:-${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb}"

# Is $1 the name of an emulator profile?
is_profile() { [ -n "${1:-}" ] && tools/emulators.sh has "$1" 2>/dev/null; }

pick_device() {
  local want="${1:-}" s all count
  if [ -n "$want" ]; then
    s="$(tools/emulators.sh start "$want")" || return 1
  elif [ -n "${ANDROID_SERIAL:-}" ]; then
    s="$ANDROID_SERIAL"
  else
    all="$("$ADB" devices 2>/dev/null | awk 'NR>1 && $2=="device"{print $1}')"
    count="$(printf '%s\n' "$all" | grep -c .)"
    if [ "$count" -eq 0 ]; then
      echo "No emulator is running. Name a profile, for example: $0 a30" >&2
      echo "(tools/emulators.sh list shows them.)" >&2
      return 1
    elif [ "$count" -gt 1 ]; then
      echo "More than one device is connected. Say which profile, for example: $0 a30" >&2
      return 1
    fi
    s="$all"
  fi
  case "$s" in
    emulator-*) ;;
    *)
      echo "Refusing to use '$s': it is not an emulator." >&2
      echo "This project is tested on emulators only for now. Unplug the" >&2
      echo "phone, or name a profile, for example: $0 a30" >&2
      return 1 ;;
  esac
  export ANDROID_SERIAL="$s"
}

# One line saying what the chosen emulator is, read from the emulator
# itself rather than from what it was asked to be.
describe_device() {
  local avd rel sdk size dens mem render
  avd="$("$ADB" emu avd name 2>/dev/null | head -1 | tr -d '\r')"
  rel="$("$ADB" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')"
  sdk="$("$ADB" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
  size="$("$ADB" shell wm size 2>/dev/null | tail -1 | sed 's/.*: *//' | tr -d '\r')"
  dens="$("$ADB" shell wm density 2>/dev/null | tail -1 | sed 's/.*: *//' | tr -d '\r')"
  mem="$("$ADB" shell cat /proc/meminfo 2>/dev/null | awk '/MemTotal/{printf "%.1f", $2/1048576}')"
  if "$ADB" shell dumpsys SurfaceFlinger 2>/dev/null | grep -m1 'GLES:' | grep -qi swiftshader; then
    render="software rendering"
  else
    render="rendering on the Mac's graphics chip"
  fi
  echo "emulator $avd: Android $rel (API $sdk), $size at $dens dpi, $mem GB RAM, $render"
}

# Makes Android's own launcher the home app again.
#
# The benchmarks and the profile recording install the release build as a
# second launcher and start it by name. Before that they remove the debug
# build, which is usually the home app at that moment, and removing the
# home app leaves Android with two candidates and no choice made: the next
# press of Home raises its "which home app?" chooser, the launcher under
# test never comes to the front, and the run fails saying the app "is not
# running". Giving the role back to the system's launcher first means Home
# goes somewhere definite.
use_system_home() {
  local system_home
  system_home="$(adb shell cmd package query-activities --brief \
      -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null \
    | tr -d '\r' | grep '/' | grep -v 'privacylauncher' | grep -v 'FallbackHome' \
    | head -1 | tr -d ' ')"
  if [ -z "$system_home" ]; then
    echo "note: found no launcher other than this one to hand the home role to" >&2
    return 1
  fi
  adb shell cmd package set-home-activity "$system_home" >/dev/null 2>&1
  adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1
}
