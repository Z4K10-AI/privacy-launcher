#!/bin/sh
# Boots the test emulator, creating it the first time.
#
#   tools/emulator.sh          headless (no window), good for screenshots
#   tools/emulator.sh --window shows the emulator window
#
# Stop it with:  adb emu kill

set -e
cd "$(dirname "$0")/.."
. tools/env.sh

AVD=privacy_test
IMAGE="system-images;android-35;default;arm64-v8a"

# The AOSP image, not google_apis: no Play Services, which is both lighter
# and closer to the de-Googled ROM this launcher is eventually for.
if ! "$ANDROID_HOME/emulator/emulator" -list-avds | grep -qx "$AVD"; then
    echo "Creating AVD $AVD (first run only)..."
    "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --install "emulator" "$IMAGE"
    echo no | "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" \
        create avd -n "$AVD" -k "$IMAGE" -d pixel_6
fi

WINDOW="-no-window"
[ "$1" = "--window" ] && WINDOW=""

LOG="${TMPDIR:-/tmp}/privacy-launcher-emulator.log"
echo "Booting $AVD (log: $LOG)..."
# nohup, so the emulator outlives this script rather than being killed with
# it when the shell exits.
# shellcheck disable=SC2086
nohup "$ANDROID_HOME/emulator/emulator" -avd "$AVD" $WINDOW \
    -no-audio -no-boot-anim -no-snapshot -memory 2048 \
    -gpu swiftshader_indirect > "$LOG" 2>&1 &

adb wait-for-device
printf "Waiting for boot"
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    printf "."
    sleep 2
done
echo " done."
