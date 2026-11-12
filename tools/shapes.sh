#!/bin/bash
# Runs the on-device tests on one emulator made to look like other phones.
#
#   tools/shapes.sh                 every shape, on the a30 emulator
#   tools/shapes.sh modern          every shape, on that emulator
#   tools/shapes.sh a30 tiny        one shape
#
# The launcher is not written for one phone. It has to lay itself out on a
# 320 dp wide Android Go handset, a 411 dp flagship, a phone held on its
# side and a folding phone opened flat, and no test that only ever sees one
# screen can say whether it does.
#
# A separate emulator per screen would cost about a gigabyte of disk each.
# Android can instead be told to treat the screen it has as a different
# size and density (`wm size`, `wm density`), and apps cannot tell the
# difference: they are given the new size and lay out for it. So each shape
# below is the same emulator, relabelled, with every device test and a
# short tour of the real app run on it. The emulator's own size is put back
# afterwards, whatever happens.
#
# What this does not vary is the Android version: that is what the
# emulator profiles are for. Run this once per profile.
set -u
cd "$(dirname "$0")/.."
. tools/env.sh
ADB="$ANDROID_HOME/platform-tools/adb"
. tools/device.sh

PROFILE=a30
if is_profile "${1:-}"; then PROFILE="$1"; shift; fi
ONLY="${1:-}"
pick_device "$PROFILE" || exit 2

# name      pixels     dpi  what it stands for
SHAPES="
tiny        480x854    240  the smallest screens Android Go phones have (320 dp wide)
budget      720x1600   320  a tall budget phone (360 dp wide)
dense       1080x2340  480  full HD at the density many makers choose (360 dp wide)
roomy       1080x2400  360  display size turned down to fit more (480 dp wide)
flagship    1440x3120  560  a large high-resolution phone (411 dp wide)
sideways    turned     -    a phone turned on its side
unfolded    2208x1768  420  a folding phone, opened flat
"

# What the emulator's rotation was set to before this script touched it.
AUTO_BEFORE="$("$ADB" shell settings get system accelerometer_rotation | tr -d '\r')"
TURN_BEFORE="$("$ADB" shell settings get system user_rotation | tr -d '\r')"
case "$AUTO_BEFORE" in 0|1) ;; *) AUTO_BEFORE=0 ;; esac
case "$TURN_BEFORE" in 0|1|2|3) ;; *) TURN_BEFORE=0 ;; esac

restore() {
  "$ADB" shell wm size reset >/dev/null 2>&1
  "$ADB" shell wm density reset >/dev/null 2>&1
  "$ADB" shell settings put system user_rotation "$TURN_BEFORE" >/dev/null 2>&1
  "$ADB" shell settings put system accelerometer_rotation "$AUTO_BEFORE" >/dev/null 2>&1
}
trap restore EXIT

mkdir -p build/shapes
FAILED=0
SUMMARY=""
# The list is read on its own channel (3), not the usual one. adb and Gradle
# both read whatever is on the usual one, and with the list there they ate
# it: the first shape ran, the rest had been swallowed, and the script
# reported a pass on one shape out of seven.
while read -r NAME SIZE DPI WHAT <&3; do
  [ -z "$NAME" ] && continue
  [ -n "$ONLY" ] && [ "$ONLY" != "$NAME" ] && continue
  if [ "$SIZE" = "turned" ]; then
    # Not a different screen: the same one, with Android told the user has
    # turned the phone. The launcher itself stays upright on a phone, and
    # the soak's tour checks that it does. The tests' own screens are not
    # held upright, so they are laid out sideways here, which is the only
    # place any of this is seen on a screen that is wide and short.
    echo "=== $NAME: $WHAT ==="
    "$ADB" shell settings put system accelerometer_rotation 0 >/dev/null
    "$ADB" shell settings put system user_rotation 1 >/dev/null
  else
    echo "=== $NAME: $SIZE at $DPI dpi, $WHAT ==="
    "$ADB" shell wm size "$SIZE" >/dev/null
    "$ADB" shell wm density "$DPI" >/dev/null
  fi
  sleep 4

  tools/devtest.sh > "build/shapes/devtest-$NAME.log" 2>&1
  TESTS="$(grep -E '^[0-9]+ device tests, ' "build/shapes/devtest-$NAME.log" | tail -1)"
  grep -E '^FAIL|^      ' "build/shapes/devtest-$NAME.log" | head -40

  tools/install.sh --home > "build/shapes/install-$NAME.log" 2>&1
  SOAK_ONLY=tour tools/soak.sh > "build/shapes/soak-$NAME.log" 2>&1
  TOUR="$(grep -E '^=== result: ' "build/shapes/soak-$NAME.log" | tail -1 | sed 's/^=== result: //; s/ ===$//')"
  grep -E '^  (WRONG|CRASH|GONE)|^ABORT' "build/shapes/soak-$NAME.log" | head -20

  LINE="$(printf '%-9s %-10s %3s dpi   %-28s tour: %s' "$NAME" "$SIZE" "$DPI" "${TESTS:-no test results}" "${TOUR:-did not run}")"
  echo "$LINE"
  SUMMARY="$SUMMARY$LINE
"
  case "$TESTS" in *", 0 failed") ;; *) FAILED=1 ;; esac
  case "$TOUR" in *", 0 problems") ;; *) FAILED=1 ;; esac
  restore
  sleep 3
done 3<<EOF2
$SHAPES
EOF2

echo
RAN="$(printf '%s' "$SUMMARY" | grep -c .)"
WANTED="$(printf '%s\n' "$SHAPES" | grep -c .)"
[ -n "$ONLY" ] && WANTED=1
if [ "$RAN" -ne "$WANTED" ]; then
  echo "only $RAN of $WANTED shapes ran"
  FAILED=1
fi
echo "=== $RAN screen shapes on $(describe_device) ==="
printf '%s' "$SUMMARY"
if [ "$FAILED" -eq 0 ]; then echo "RESULT: PASS"; else echo "RESULT: FAIL  (logs in build/shapes/)"; fi
echo "Emulator results. A real phone may differ."
exit "$FAILED"
