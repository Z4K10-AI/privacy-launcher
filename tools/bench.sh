#!/bin/bash
# Measures cold start and frame timing with Macrobenchmark, on an emulator.
#
#   tools/bench.sh               both, on the a30 profile (about 20 minutes)
#   tools/bench.sh modern        both, on another profile
#   tools/bench.sh a30 startup   only cold start    (about 2 minutes)
#   tools/bench.sh a30 frames    only frame timing  (about 17 minutes)
#   tools/bench.sh a30 drawer    only the drawer's frames (about 8 minutes)
#
# It measures the release build, compiled two ways: with nothing compiled
# ahead of time (what a fresh install is like for its first day or so) and
# with the baseline profile applied (what it settles into).
#
# The emulator draws in software unless it was booted with PL_GPU=host, and
# for frame timing that matters a great deal: in software nearly every
# frame is late because of the emulator, not the app. The mode is chosen
# when the emulator boots, so to measure with the Mac's graphics chip:
#
#   tools/emulators.sh stop a30
#   PL_GPU=host tools/bench.sh a30
#
# What the numbers are worth. They come from an emulator on a Mac whose
# processor is several times faster than the phone this is aimed at. They
# are good for one thing: comparing one build with another, measured the
# same way, to catch a change that made things slower. They are not what a
# phone will show. docs/performance.md keeps the baseline.
set -u
cd "$(dirname "$0")/.."
. tools/env.sh
. tools/device.sh

PROFILE=a30
if is_profile "${1:-}"; then PROFILE="$1"; shift; fi
PKG=com.privacylauncher.baselineprofile
case "${1:-all}" in
  startup) CLASSES="$PKG.StartupBenchmark" ;;
  frames)  CLASSES="$PKG.FrameTimingBenchmark" ;;
  drawer)  CLASSES="$PKG.FrameTimingBenchmark#drawerAnimationBaselineProfile,$PKG.FrameTimingBenchmark#drawerAnimationNoCompilation" ;;
  all)     CLASSES="$PKG.StartupBenchmark,$PKG.FrameTimingBenchmark" ;;
  *) echo "say startup, frames, drawer, or nothing for startup and frames" >&2; exit 2 ;;
esac

pick_device "$PROFILE" || exit 2
DEVICE="$(describe_device)"
echo "benchmarks on $DEVICE"
echo "(this takes a while; Gradle's output goes to build/bench.log)"

# Measure from the same state every time: no debug build of the launcher
# installed, so the app behind each launch is Android's own home screen and
# not a second copy of this one drawing in the background.
adb uninstall com.privacylauncher.debug >/dev/null 2>&1
use_system_home

mkdir -p build
rm -rf baselineprofile/build/outputs/connected_android_test_additional_output
# suppressErrors=EMULATOR: Macrobenchmark refuses to run on an emulator
# unless told to, because the numbers are not a phone's. That objection is
# answered in the header above and in every place the numbers are quoted,
# not by pretending otherwise.
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=$CLASSES" \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR \
  > build/bench.log 2>&1
STATUS=$?

# The recorded baseline profile is a committed file that these Gradle tasks
# have been seen to delete. Put it back if that happened.
PROFILE_DIR=app/src/release/generated/baselineProfiles
if [ -n "$(git status --short "$PROFILE_DIR" 2>/dev/null)" ]; then
  echo "note: the benchmark run changed $PROFILE_DIR; restoring the committed copy"
  git restore "$PROFILE_DIR"
fi

echo
python3 - "$DEVICE" <<'PY'
import glob, json, sys
device = sys.argv[1]
import html, re
# Gradle says only "there were failing tests" and links an HTML page. The
# reasons are in the results file that page is made from.
failed = 0
for f in glob.glob('baselineprofile/build/outputs/androidTest-results/connected/**/*.xml', recursive=True):
    for m in re.finditer(r'<testcase name="([^"]+)"[^>]*?(?:/>|>(.*?)</testcase>)', open(f).read(), re.S):
        body = m.group(2) or ''
        why = re.search(r'<(?:failure|error)[^>]*>(.*?)\n', body, re.S)
        if why:
            failed += 1
            print('FAIL', m.group(1))
            print('     ', html.unescape(why.group(1))[:300])
files = glob.glob('baselineprofile/build/outputs/**/*benchmarkData.json', recursive=True)
if not files:
    print("no benchmark results: the run failed before measuring (see build/bench.log)")
    sys.exit(1)
if failed:
    print("the figures below are only from the benchmarks that did not fail")
BUDGET_MS = 1000.0 / 60          # one frame at 60 Hz
print(f"measured on {device}")
for f in sorted(files):
    data = json.load(open(f))
    for b in sorted(data.get('benchmarks', []), key=lambda b: b['name']):
        name = b['name']
        for metric, v in sorted(b.get('metrics', {}).items()):
            print(f"  {name:32} {metric:24} median {v['median']:7.1f}   "
                  f"min {v['minimum']:7.1f}   max {v['maximum']:7.1f}   ({len(v['runs'])} runs)")
        for metric, v in sorted(b.get('sampledMetrics', {}).items()):
            samples = [x for run in v.get('runs', []) for x in run]
            line = (f"  {name:32} {metric:24} P50 {v['P50']:6.1f}   P90 {v['P90']:6.1f}   "
                    f"P95 {v['P95']:6.1f}   P99 {v['P99']:6.1f}")
            if samples and metric == 'frameDurationCpuMs':
                over = sum(1 for x in samples if x > BUDGET_MS)
                line += f"   over 16.7 ms: {over} of {len(samples)} frames ({100.0 * over / len(samples):.1f}%)"
            if samples and metric == 'frameOverrunMs':
                late = sum(1 for x in samples if x > 0)
                line += f"   missed deadline: {late} of {len(samples)} frames ({100.0 * late / len(samples):.1f}%)"
            print(line)
print("All times in milliseconds. Emulator figures, not a phone's.")
PY
PARSE=$?
[ "$STATUS" -eq 0 ] && [ "$PARSE" -eq 0 ]
