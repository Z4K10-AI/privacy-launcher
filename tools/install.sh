#!/bin/sh
# Builds the debug APK and installs it on whatever single device adb sees:
# a phone over USB, or the emulator.
#
#   tools/install.sh           build + install
#   tools/install.sh --home    also make it the home app (emulator only;
#                              on a real phone use the in-app button)

set -e
cd "$(dirname "$0")/.."
. tools/env.sh

APP_ID=com.privacylauncher.debug
ACTIVITY="$APP_ID/com.privacylauncher.MainActivity"

COUNT=$(adb devices | grep -cw device || true)
if [ "$COUNT" -eq 0 ]; then
    echo "No device. Plug in a phone with USB debugging on, or run tools/emulator.sh" >&2
    exit 1
fi

echo "Device: $(adb shell getprop ro.product.model | tr -d '\r') \
(Android $(adb shell getprop ro.build.version.release | tr -d '\r'))"

gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

if [ "$1" = "--home" ]; then
    # Only works where the shell user may set it, i.e. an emulator or a
    # rooted/debug build. On a normal phone, use the in-app button, which
    # opens Android's own picker.
    adb shell cmd package set-home-activity "$ACTIVITY"
    adb shell input keyevent KEYCODE_HOME
else
    adb shell am start -n "$ACTIVITY" >/dev/null
fi

echo "Installed. Recent errors, if any:"
adb logcat -d -s AndroidRuntime:E 2>/dev/null | grep -i privacylauncher | tail -5 || true
