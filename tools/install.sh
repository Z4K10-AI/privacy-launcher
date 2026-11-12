#!/bin/bash
# Builds the debug APK and installs it on an emulator.
#
#   tools/install.sh              on the one emulator that is running
#   tools/install.sh a30          on that profile, booted if it is not up
#   tools/install.sh a30 --home   and make it the emulator's home app
#
# Emulators only, for now. A phone that happens to be plugged in is refused
# (see tools/device.sh): this project is not being installed on real phones
# yet, and when that changes it should be changed there, on purpose.

set -e
cd "$(dirname "$0")/.."
. tools/env.sh
. tools/device.sh

PROFILE=""
if is_profile "${1:-}"; then PROFILE="$1"; shift; fi
pick_device "$PROFILE" || exit 1

APP_ID=com.privacylauncher.debug
ACTIVITY="$APP_ID/com.privacylauncher.MainActivity"

echo "installing on $(describe_device)"

./gradlew assembleDebug -q
adb install -r app/build/outputs/apk/debug/app-debug.apk

if [ "${1:-}" = "--home" ]; then
    # The shell user may set this on an emulator. On a retail phone it may
    # not, which is what the in-app button and Android's own picker are for.
    adb shell cmd package set-home-activity "$ACTIVITY"
    adb shell input keyevent KEYCODE_HOME
else
    adb shell am start -n "$ACTIVITY" >/dev/null
fi

echo "Installed. Recent errors, if any:"
adb logcat -d -s AndroidRuntime:E 2>/dev/null | grep -i privacylauncher | tail -5 || true
