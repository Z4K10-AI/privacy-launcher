#!/bin/bash
# The first step towards an operating system, in the emulator: plain
# Android 15 with Privacy Launcher built into the system partition as its
# home screen.
#
#   tools/os-preview.sh build [apk]     make the emulator if needed, put the
#                                       launcher into its system, factory-reset
#                                       it, and check it
#   tools/os-preview.sh check [apk]     prove what the preview claims
#   tools/os-preview.sh reset [apk]     factory-reset it, then check again
#   tools/os-preview.sh start [--window]   boot it
#   tools/os-preview.sh stop
#   tools/os-preview.sh delete          remove the emulator and all that is in it
#
# `build` installs app/build/outputs/apk/release/app-release.apk unless it
# is given another APK. Build that first:  ./gradlew assembleRelease
#
# What this is. The emulator's own copy of Android (the plain AOSP image, no
# Google apps) with one thing added to its system partition: the launcher,
# at /system/priv-app/PrivacyLauncher/, which is where rom/Android.bp puts
# it in a real build. Android then treats it as part of itself: it is the
# home screen from the first boot, it survives a factory reset, and it
# cannot be uninstalled.
#
# What this is not. It is not an operating system built from source, and
# it cannot be installed on a phone. It is this emulator's Android with
# one app moved into it. It exists to find out cheaply how the launcher
# behaves as a system app, before anyone spends a day building Android.
# docs/os.md says what a real build needs.

set -u
cd "$(dirname "$0")/.."
. tools/env.sh

EMU="$ANDROID_HOME/emulator/emulator"
ADB="$ANDROID_HOME/platform-tools/adb"
AVDMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
QEMU_IMG="$ANDROID_HOME/emulator/qemu-img"
AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"

AVD=pl_os
API=35
IMAGE="system-images;android-$API;default;arm64-v8a"
PKG=com.privacylauncher
HOME_ACTIVITY="$PKG/.MainActivity"
SYSDIR=/system/priv-app/PrivacyLauncher
SYSAPK="$SYSDIR/PrivacyLauncher.apk"
# Android's own launcher. It is not the home screen here, but it has to
# stay: the recent-apps screen and the navigation buttons live inside it.
STOCK=com.android.launcher3
STOCK_RECENTS="$STOCK/com.android.quickstep.RecentsActivity"
DEFAULT_APK=app/build/outputs/apk/release/app-release.apk
GPU="${PL_GPU:-swiftshader_indirect}"

say() { echo "$@" >&2; }
die() { say "error: $*"; exit 1; }

sha() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -d' ' -f1
}

avd_exists() { "$EMU" -list-avds 2>/dev/null | grep -qx "$AVD"; }

# The adb serial of the running OS-preview emulator, if there is one.
serial_of() {
  local s name
  for s in $("$ADB" devices 2>/dev/null | awk '/^emulator-[0-9]+[[:space:]]/{print $1}'); do
    name="$("$ADB" -s "$s" emu avd name 2>/dev/null | head -1 | tr -d '\r')"
    if [ "$name" = "$AVD" ]; then echo "$s"; return 0; fi
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

create() {
  [ -d "$ANDROID_HOME/system-images/android-$API/default/arm64-v8a" ] \
    || die "the Android 15 system image is not installed. Run: tools/emulators.sh create modern"
  if ! avd_exists; then
    say "creating the emulator $AVD"
    echo no | "$AVDMANAGER" create avd -n "$AVD" -k "$IMAGE" -d pixel_3a >/dev/null 2>&1 \
      || die "avdmanager could not create $AVD"
  fi
  local cfg="$AVD_HOME/$AVD.avd/config.ini"
  [ -f "$cfg" ] || die "expected to find $cfg"
  # The same shape as the `modern` test emulator, so the two can be compared.
  setkey "$cfg" avd.ini.displayname "Privacy Launcher: OS preview (Android 15)"
  setkey "$cfg" hw.lcd.width 1080
  setkey "$cfg" hw.lcd.height 2400
  setkey "$cfg" hw.lcd.density 420
  setkey "$cfg" hw.ramSize 4096
  setkey "$cfg" hw.cpu.ncore 4
  setkey "$cfg" disk.dataPartition.size 6144M
  setkey "$cfg" hw.keyboard no
  setkey "$cfg" showDeviceFrame no
  setkey "$cfg" fastboot.forceColdBoot yes
  setkey "$cfg" fastboot.forceFastBoot no
  setkey "$cfg" fastboot.forceChosenSnapshotBoot no
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

# Restarts Android inside the running emulator and waits for it. Android
# goes on answering "booted" for a moment after being told to restart, so
# wait for it to go away first.
restart_android() {
  local s="$1" i
  "$ADB" -s "$s" reboot >/dev/null 2>&1
  for i in $(seq 1 30); do
    [ "$("$ADB" -s "$s" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] || break
    sleep 1
  done
  wait_boot "$s"
}

uid_of() { "$ADB" -s "$1" shell id -u 2>/dev/null | tr -d '\r'; }

as_root() {
  local s="$1" i
  [ "$(uid_of "$s")" = "0" ] && return 0
  "$ADB" -s "$s" root >/dev/null 2>&1
  for i in $(seq 1 30); do
    [ "$(uid_of "$s")" = "0" ] && return 0
    sleep 1
  done
  return 1
}

# Back to the ordinary, unprivileged shell user, which is all a person with
# a cable and a computer gets on a real phone.
as_shell() {
  local s="$1" i
  [ "$(uid_of "$s")" = "2000" ] && return 0
  "$ADB" -s "$s" unroot >/dev/null 2>&1
  for i in $(seq 1 30); do
    [ "$(uid_of "$s")" = "2000" ] && return 0
    sleep 1
  done
  return 1
}

# Boots the emulator and prints its serial. Always with -writable-system:
# without it the emulator starts from the untouched system image, and the
# launcher is not in that. $1 is --window, to see the screen.
boot() {
  local window="${1:-}" s pid i
  avd_exists || die "the OS preview has not been made yet. Run: tools/os-preview.sh build"
  if s="$(serial_of)"; then
    wait_boot "$s" || die "it is running but never finished booting"
    echo "$s"
    return 0
  fi
  if "$ADB" devices 2>/dev/null | grep -q '^emulator-'; then
    die "another emulator is running. Stop it first (tools/emulators.sh stop): one at a time on this Mac."
  fi
  local log="${TMPDIR:-/tmp}/privacy-launcher-$AVD.log"
  local win="-no-window"
  [ "$window" = "--window" ] && win=""
  say "booting $AVD ($GPU rendering; log: $log)"
  # shellcheck disable=SC2086
  nohup "$EMU" -avd "$AVD" $win -no-audio -no-boot-anim -no-snapshot \
    -gpu "$GPU" -writable-system > "$log" 2>&1 &
  pid=$!
  s=""
  for i in $(seq 1 120); do
    if s="$(serial_of)"; then break; fi
    if ! kill -0 "$pid" 2>/dev/null; then
      say "the emulator exited before it came up. Last lines of its log:"
      tail -15 "$log" >&2
      exit 1
    fi
    sleep 1
  done
  [ -n "$s" ] || die "the emulator never appeared in adb"
  wait_boot "$s" || die "it did not finish booting within five minutes"
  "$ADB" -s "$s" shell 'input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard; settings put system screen_off_timeout 1800000; svc power stayon true' >/dev/null 2>&1
  echo "$s"
}

stop() {
  local s i
  s="$(serial_of)" || { say "not running"; return 0; }
  say "stopping $AVD ($s)"
  "$ADB" -s "$s" emu kill >/dev/null 2>&1
  for i in $(seq 1 40); do
    "$ADB" devices 2>/dev/null | grep -q "^$s[[:space:]]" || return 0
    sleep 1
  done
  die "it did not shut down"
}

build() {
  local apk="${1:-$DEFAULT_APK}" s out
  [ -f "$apk" ] || die "no APK at $apk. Build it first: ./gradlew assembleRelease"
  create
  s="$(boot)" || exit 1
  as_root "$s" || die "could not get root on the emulator"

  # Android's system partition is read-only and checked against a
  # signature. `adb remount` switches the check off and lays a writable
  # layer over the partition. That layer is kept beside the system
  # partition, not in the user's data, so a factory reset does not remove
  # it (`reset` below checks that it does not).
  out="$("$ADB" -s "$s" remount 2>&1)"
  echo "$out" | grep -q 'Remount succeeded' || { say "$out"; die "could not make the system partition writable"; }
  "$ADB" -s "$s" shell mount | grep -q '^overlay on /system type overlay (rw' \
    || die "the system partition is still read-only after remount"
  "$ADB" -s "$s" shell 'test -e /dev/block/mapper/scratch' \
    || die "the writable layer is not beside the system partition, so a factory reset would lose it"

  say "putting $(basename "$apk") into $SYSDIR"
  "$ADB" -s "$s" shell "mkdir -p $SYSDIR" || die "could not make $SYSDIR"
  "$ADB" -s "$s" push "$apk" "$SYSAPK" >/dev/null 2>&1 || die "could not copy the APK"
  # Owner, mode and security label as for every other system app; Android
  # ignores an app in the system partition that it cannot read as system.
  "$ADB" -s "$s" shell "chown root:root $SYSDIR $SYSAPK && chmod 755 $SYSDIR && chmod 644 $SYSAPK && restorecon -R $SYSDIR >/dev/null 2>&1 && sync" \
    || die "could not set the APK's owner and label"

  say "restarting Android so it finds the launcher in its system"
  restart_android "$s" || die "it did not come back after the restart"
  # This Android has already had a first boot, without the launcher, and
  # chose its own home screen then. An operating system is judged by its
  # first boot with everything in place, so start it over from there.
  reset "$apk"
}

# Runs a command on the emulator as whoever adb currently is.
on() { "$ADB" -s "$SERIAL" shell "$@" 2>/dev/null | tr -d '\r'; }

top_activity() { on dumpsys activity activities | sed -n 's/.*topResumedActivity=ActivityRecord{[^ ]* [^ ]* \([^ ]*\) .*/\1/p' | head -1; }

FAIL=0
ok()   { echo "  ok    $1"; }
bad()  { echo "  FAIL  $1"; FAIL=1; }
note() { echo "  note  $1"; }

check() {
  local apk="${1:-$DEFAULT_APK}" x y
  SERIAL="$(serial_of)" || die "the OS preview is not running. Start it with: tools/os-preview.sh start"
  wait_boot "$SERIAL" || die "it never finished booting"
  # Everything below runs as the ordinary shell user, not as root.
  as_shell "$SERIAL" || die "could not drop root"
  on 'input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard' >/dev/null
  FAIL=0
  echo "checking the OS preview: Android $(on getprop ro.build.version.release), $(on getprop ro.build.fingerprint)"

  x="$(on pm path $PKG)"
  if [ "$x" = "package:$SYSAPK" ]; then
    ok "the launcher is in the system partition, at $SYSDIR"
  else
    bad "the launcher is not in the system partition (Android says: ${x:-not installed})"
  fi

  x="$(on dumpsys package $PKG)"
  if echo "$x" | grep -q 'pkgFlags=\[.* SYSTEM ' && echo "$x" | grep -q 'privateFlags=\[.* PRIVILEGED '; then
    ok "Android counts it as part of the system (flags SYSTEM and PRIVILEGED)"
  else
    bad "Android does not count it as a privileged system app"
  fi

  if [ -f "$apk" ]; then
    y="$(on sha256sum $SYSAPK | cut -d' ' -f1)"
    if [ -n "$y" ] && [ "$y" = "$(sha "$apk")" ]; then
      ok "it is the same file as $apk (SHA-256 ${y:0:16}...)"
    else
      bad "the file in the system is not $apk"
    fi
  else
    note "no APK at $apk to compare the installed file with"
  fi

  # No INTERNET permission, and so no membership of the group (3003) that
  # the kernel requires before it lets a process open a network socket.
  if echo "$x" | grep -q -E 'android\.permission\.(INTERNET|ACCESS_NETWORK_STATE)'; then
    bad "as a system app it asks for a network permission"
  elif echo "$x" | grep -E '^ *gids=' | grep -q -w 3003; then
    bad "as a system app it is in the internet group"
  else
    ok "still no internet permission, and not in the internet group"
  fi

  x="$(on cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)"
  y="$(on cmd role get-role-holders android.app.role.HOME)"
  if [ "$x" = "$HOME_ACTIVITY" ] && [ "$y" = "$PKG" ]; then
    ok "Android's home screen is the launcher, with nothing chosen by hand"
  else
    bad "Android's home screen is ${x:-unset} (role holder: ${y:-none}), not the launcher"
  fi

  # The recent-apps screen and the navigation buttons are a service inside
  # Android's own launcher, which SystemUI connects to. If that service
  # cannot start, both are gone, whatever the home screen is.
  x="$(on dumpsys activity service com.android.systemui/.SystemUIService | sed -n 's/^ *isConnected=//p' | head -1)"
  y="$(on logcat -d -b crash | grep -c "Process: $STOCK")"
  if [ "$x" = "true" ] && [ "${y:-0}" = "0" ]; then
    ok "Android's navigation service is running (connected, no crashes since boot)"
  else
    bad "Android's navigation service is not running (connected: ${x:-unknown}; crashes since boot: ${y:-unknown})"
  fi

  on am start -W -n com.android.settings/.Settings >/dev/null
  sleep 2
  on input keyevent KEYCODE_APP_SWITCH >/dev/null
  sleep 3
  x="$(top_activity)"
  if [ "$x" = "$STOCK_RECENTS" ]; then
    ok "the recent-apps screen opens"
  else
    bad "the recent-apps screen does not open (on top instead: ${x:-nothing})"
  fi

  on input keyevent KEYCODE_HOME >/dev/null
  sleep 2
  x="$(top_activity)"
  if [ "$x" = "$HOME_ACTIVITY" ]; then
    ok "pressing Home shows the launcher"
  else
    bad "pressing Home shows ${x:-nothing}, not the launcher"
  fi

  x="$(on pm uninstall $PKG)"
  y="$(on pm path $PKG)"
  if [ "$y" = "package:$SYSAPK" ] && echo "$x" | grep -q '^Failure'; then
    ok "it cannot be uninstalled like an ordinary app ($x)"
  else
    bad "uninstalling it did not fail as it should for a system app (pm said: ${x:-nothing})"
  fi

  # Not a pass or a fail: a difference from the installed app that anyone
  # shipping this in an OS should know about.
  if on dumpsys package $PKG | grep -q 'android.permission.WRITE_SETTINGS: granted=true'; then
    note "\"Modify system settings\" is granted from the first boot: Android gives it to"
    note "apps in the system image without asking. As an installed app it starts off."
  fi

  if [ "$FAIL" -eq 0 ]; then echo "all checks passed"; else echo "SOME CHECKS FAILED"; fi
  return "$FAIL"
}

# fresh_disk <folder> <disk> <format of the disk>
# The emulator keeps each disk as two files: the disk as it was when new,
# and beside it a .qcow2 file holding everything written to it since.
# Replacing the second with an empty one puts the disk back to new.
fresh_disk() {
  local dir="$1" base="$2" fmt="$3"
  [ -f "$dir/$base" ] && [ -f "$dir/$base.qcow2" ] || die "expected to find $dir/$base and $base.qcow2"
  rm -f "$dir/$base.qcow2"
  ( cd "$dir" && "$QEMU_IMG" create -q -f qcow2 -F "$fmt" -b "$base" "$base.qcow2" ) \
    || die "could not make a fresh $base.qcow2"
}

# A factory reset as a phone does it: the user's data goes and the system
# partition stays. The emulator's own -wipe-data is not that. It also
# throws away the emulator's writable copy of the system partition, the
# launcher with it, and so does starting the emulator with a data disk
# missing; the first two versions of this check duly found no launcher
# left. So the emulator is stopped and only its three user-data disks are
# put back to new: the data partition, the cache, and the partition that
# holds the data partition's encryption key. The system disk is not
# touched.
reset() {
  local apk="${1:-$DEFAULT_APK}" dir="$AVD_HOME/$AVD.avd" x i
  avd_exists || die "the OS preview has not been made yet. Run: tools/os-preview.sh build"
  [ -f "$dir/system.img.qcow2" ] || die "there is no modified system to keep. Run: tools/os-preview.sh build"
  stop
  say "factory reset: emptying the user's data, keeping the system"
  fresh_disk "$dir" userdata-qemu.img qcow2
  fresh_disk "$dir" encryptionkey.img raw
  fresh_disk "$dir" cache.img raw
  SERIAL="$(boot)" || exit 1
  # The first boot shows Android's "starting" screen for a moment before
  # it settles on a home screen.
  x=""
  for i in $(seq 1 30); do
    x="$(top_activity)"
    case "$x" in ""|com.android.settings/.FallbackHome) sleep 2 ;; *) break ;; esac
  done
  echo "after a factory reset, the first screen is: ${x:-nothing}"
  if [ "$x" = "$HOME_ACTIVITY" ]; then
    echo "  ok    straight to the launcher, with no \"choose a home app\" question"
  else
    echo "  FAIL  the first screen is not the launcher"
    check "$apk"
    return 1
  fi
  check "$apk"
}

cmd="${1:-}"
case "$cmd" in
  build)  build "${2:-}" ;;
  check)  check "${2:-}" ;;
  reset)  reset "${2:-}" ;;
  start)  boot "${2:-}" ;;
  stop)   stop ;;
  delete)
    avd_exists || { say "nothing to delete"; exit 0; }
    stop
    "$AVDMANAGER" delete avd -n "$AVD" >/dev/null 2>&1 || die "could not delete $AVD"
    say "deleted $AVD" ;;
  *)
    sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//' >&2
    exit 2 ;;
esac
