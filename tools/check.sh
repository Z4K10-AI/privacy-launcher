#!/bin/bash
# Everything, in one command: the JVM tests, the colour-contrast check, the
# on-device tests and the soak, and then a short summary.
#
#   tools/check.sh            on the a30 profile
#   tools/check.sh modern     on another profile
#   tools/check.sh all        on every profile, one after the other
#
# The profiles are the emulators in tools/emulators.sh; the one asked for is
# booted if it is not already up. The exit status is 0 only if every part
# passed. Each part's full output is kept in build/check/, which is where to
# look when the summary says something failed.
#
# Roughly five minutes per profile, most of it the soak.
set -u
cd "$(dirname "$0")/.."
. tools/env.sh
. tools/device.sh

WHAT="${1:-a30}"
if [ "$WHAT" = "all" ]; then
  NAMES="$(tools/emulators.sh names)"
elif is_profile "$WHAT"; then
  NAMES="$WHAT"
else
  echo "no profile called '$WHAT'. Try: tools/check.sh a30   (or: all)" >&2
  exit 2
fi

OUT=build/check
mkdir -p "$OUT"
SUMMARY=""
BAD=0

# line <name> <result> <seconds> <ok|bad>
line() {
  local mark="ok  "
  if [ "$4" != "ok" ]; then mark="FAIL"; BAD=1; fi
  SUMMARY="$SUMMARY$(printf '  %s  %-13s %-36s %4ss' "$mark" "$1" "$2" "$3")
"
}
heading() { SUMMARY="$SUMMARY$1
"; }

# --- the parts that need no device -------------------------------------

echo "JVM tests..."
t=$SECONDS
rm -rf app/build/test-results/testDebugUnitTest
./gradlew :app:testDebugUnitTest -q > "$OUT/jvm.log" 2>&1
read -r ran failed <<< "$(python3 - <<'PY'
import glob, re
ran = failed = 0
for p in glob.glob('app/build/test-results/testDebugUnitTest/*.xml'):
    m = re.search(r'<testsuite [^>]*>', open(p).read(4000))
    if not m: continue
    get = lambda k: int(re.search(k + r'="(\d+)"', m.group(0)).group(1))
    ran += get('tests'); failed += get('failures') + get('errors')
print(ran, failed)
PY
)"
if [ "${ran:-0}" -eq 0 ]; then
  line "JVM tests" "did not run: see $OUT/jvm.log" $((SECONDS - t)) bad
elif [ "$failed" -ne 0 ]; then
  line "JVM tests" "$((ran - failed)) passed, $failed FAILED" $((SECONDS - t)) bad
else
  line "JVM tests" "$ran passed, 0 failed" $((SECONDS - t)) ok
fi

echo "Colour contrast..."
t=$SECONDS
if python3 tools/contrast.py > "$OUT/contrast.log" 2>&1; then
  line "Contrast" "$(grep -m1 'pairs checked' "$OUT/contrast.log" | sed 's/\.$//')" $((SECONDS - t)) ok
else
  line "Contrast" "$(grep -m1 'pairs checked' "$OUT/contrast.log" || echo "failed: see $OUT/contrast.log")" $((SECONDS - t)) bad
fi

# --- the parts that need an emulator, once per profile -----------------

for NAME in $NAMES; do
  unset ANDROID_SERIAL
  echo "Booting $NAME..."
  if ! pick_device "$NAME"; then
    heading "$NAME"
    line "Emulator" "would not start" 0 bad
    continue
  fi
  heading "$NAME: $(describe_device)"

  echo "Device tests on $NAME..."
  t=$SECONDS
  tools/devtest.sh "$NAME" > "$OUT/devtest-$NAME.log" 2>&1
  rc=$?
  res="$(grep -E '^[0-9]+ device tests, [0-9]+ failed' "$OUT/devtest-$NAME.log" | tail -1)"
  if [ -z "$res" ]; then
    line "Device tests" "did not run: see $OUT/devtest-$NAME.log" $((SECONDS - t)) bad
  else
    n="${res%% *}"; f="$(echo "$res" | awk '{print $4}')"
    if [ "$f" -eq 0 ] && [ "$rc" -eq 0 ]; then
      line "Device tests" "$n passed, 0 failed" $((SECONDS - t)) ok
    else
      line "Device tests" "$((n - f)) passed, $f FAILED" $((SECONDS - t)) bad
    fi
  fi

  # The device tests remove the app when they finish, and the soak needs it
  # installed and set as the home app.
  echo "Soak on $NAME..."
  t=$SECONDS
  if tools/install.sh "$NAME" --home > "$OUT/install-$NAME.log" 2>&1; then
    tools/soak.sh "$NAME" > "$OUT/soak-$NAME.log" 2>&1
    rc=$?
    res="$(grep -E '^=== result: ' "$OUT/soak-$NAME.log" | tail -1 | sed 's/^=== result: //; s/ ===$//')"
    if [ -z "$res" ]; then
      line "Soak" "stopped early: see $OUT/soak-$NAME.log" $((SECONDS - t)) bad
    elif [ "$rc" -eq 0 ]; then
      line "Soak" "$res" $((SECONDS - t)) ok
    else
      line "Soak" "$res" $((SECONDS - t)) bad
    fi
  else
    line "Soak" "app would not install: see $OUT/install-$NAME.log" $((SECONDS - t)) bad
  fi
done

echo
echo "=== Privacy Launcher: full check ==="
printf '%s' "$SUMMARY"
if [ "$BAD" -eq 0 ]; then
  echo "RESULT: PASS"
else
  echo "RESULT: FAIL  (details in $OUT/)"
fi
echo "Emulator results. A real phone may differ."
exit "$BAD"
