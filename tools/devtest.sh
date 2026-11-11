#!/bin/sh
# Runs the on-device tests and prints one line per test.
#
#   tools/devtest.sh                 everything
#   tools/devtest.sh HomeGridDragTest   one class (name, or package.Name)
#
# Gradle's own output for a failing device test is a link to an HTML report
# and nothing else, which is no use from a terminal. This reads the same
# results file the report is built from.
cd "$(dirname "$0")/.."
. tools/env.sh

ARG=""
if [ -n "$1" ]; then
    case "$1" in
        *.*) CLASS="$1" ;;
        *)   CLASS="$(grep -rl "^class $1\b" app/src/androidTest/java | head -1 \
                 | sed 's|app/src/androidTest/java/||; s|\.kt$||; s|/|.|g')" ;;
    esac
    [ -n "$CLASS" ] || { echo "no test class called $1" >&2; exit 2; }
    ARG="-Pandroid.testInstrumentationRunnerArguments.class=$CLASS"
fi

rm -rf app/build/outputs/androidTest-results
./gradlew connectedDebugAndroidTest $ARG -q > /dev/null 2>&1
STATUS=$?

python3 - <<'PY'
import re, glob, html, sys
files = glob.glob('app/build/outputs/androidTest-results/connected/debug/*.xml')
if not files:
    print("no results: the build failed before any test ran"); sys.exit(1)
total = failed = 0
for f in files:
    s = open(f).read()
    for m in re.finditer(r'<testcase name="([^"]+)" classname="([^"]+)"[^>]*?(/>|>(.*?)</testcase>)', s, re.S):
        body = m.group(4) or ''
        total += 1
        if '<failure' in body or '<error' in body:
            failed += 1
            why = re.search(r'<(?:failure|error)[^>]*>(.*?)\n', body, re.S)
            print('FAIL', m.group(2).split('.')[-1] + '.' + m.group(1))
            if why: print('     ', html.unescape(why.group(1))[:220])
print(f"{total} device tests, {failed} failed")
PY
exit $STATUS
