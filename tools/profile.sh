#!/bin/bash
# Records the start-up profile again, on an emulator.
#
#   tools/profile.sh           on the a30 profile (about five minutes)
#   tools/profile.sh modern    on another
#
# A baseline profile is a list of the code the launcher runs when it starts
# and in its first few screens. Android compiles that code ahead of time
# when the app is installed, so the first start and the first swipes are
# not spent interpreting it. The list is a file in this repository,
# app/src/release/generated/baselineProfiles/baseline-prof.txt, and it goes
# stale: code that has been renamed or added since it was recorded is not
# on it. Record it again after any change to what the launcher does at
# start-up or on its main screens, look at what this prints, and commit
# the file.
#
# The recording drives the real app through a journey (the home screen,
# the drawer, search, quick controls, Settings, the privacy dashboard) and
# writes down what ran. See BaselineProfileGenerator for the journey.
#
# It must be checked. A recording that quietly drove the wrong screen once
# shipped a profile of the welcome pages and nothing else; the counts
# printed at the end are how that is caught.
set -u
cd "$(dirname "$0")/.."
. tools/env.sh
. tools/device.sh

PROFILE=a30
if is_profile "${1:-}"; then PROFILE="$1"; shift; fi
pick_device "$PROFILE" || exit 2
echo "recording the start-up profile on $(describe_device)"
echo "(Gradle's output goes to build/profile.log)"

FILE=app/src/release/generated/baselineProfiles/baseline-prof.txt
BEFORE="$(wc -l < "$FILE" 2>/dev/null | tr -d ' ')"

adb uninstall com.privacylauncher.debug >/dev/null 2>&1
use_system_home
mkdir -p build
./gradlew :app:generateBaselineProfile \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR \
  > build/profile.log 2>&1
STATUS=$?

if [ "$STATUS" -ne 0 ] || [ ! -s "$FILE" ]; then
  echo "the recording failed; the committed profile is put back"
  git restore "$FILE" 2>/dev/null
  grep -E 'FAILED|Exception|error:|What went wrong' -A3 build/profile.log | head -30
  python3 - <<'PY'
import glob, html, re
for f in glob.glob('baselineprofile/build/outputs/androidTest-results/connected/**/*.xml', recursive=True):
    for m in re.finditer(r'<testcase name="([^"]+)"[^>]*>\s*<(?:failure|error)[^>]*>(.*?)\n', open(f).read(), re.S):
        print('FAIL', m.group(1)); print('     ', html.unescape(m.group(2))[:300])
PY
  exit 1
fi

AFTER="$(wc -l < "$FILE" | tr -d ' ')"
echo
echo "rules: $BEFORE before, $AFTER now"
# Each of these is a screen the journey is meant to pass through. A zero
# means the recording did not go where it was supposed to.
BAD=0
for NAME in HomeScreen AppDrawer SettingsScreen QuickPanel PrivacyDashboardScreen Fuzzy; do
  COUNT="$(grep -c "$NAME" "$FILE")"
  printf '  %-24s %s rules\n' "$NAME" "$COUNT"
  [ "$COUNT" -eq 0 ] && BAD=1
done
if [ "$BAD" -ne 0 ]; then
  echo "a screen the journey should cover is missing from the profile."
  echo "Do not commit it: git restore $FILE"
  exit 1
fi
echo "Recorded. Look at 'git diff --stat $FILE', then commit it."
