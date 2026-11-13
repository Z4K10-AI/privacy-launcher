#!/bin/bash
# Measures the launcher's own work per frame while the drawer opens and
# closes, from Android's frame statistics.
#
#   tools/uiwork.sh                 on the emulator that is running
#   tools/uiwork.sh a30             on that profile
#   tools/uiwork.sh a30 <package>   a build other than the release one
#
# It needs the release build installed (it is the one that ships, and a
# debug build is several times slower for reasons that are not the app's):
#
#   ./gradlew assembleRelease
#   adb install -r app/build/outputs/apk/release/app-release.apk
#
# Why this exists beside tools/bench.sh. That one reports how long each
# frame took from start to finish, and "finish" includes handing the frame
# to the graphics chip. On an emulator the graphics chip is the Mac's, and
# it is shared with everything else the Mac is drawing: with a browser busy
# in another window the same build measured ten times worse than it had
# twenty minutes before. So this splits each frame into the part that is
# the launcher's own doing and the part that is not:
#
#   main thread   input, animation, layout and drawing: the launcher's code,
#                 on one thread, and nothing else's fault.
#   late start    how long after the frame was due the main thread began it.
#   handing over  getting the frame to the screen. Depends on the graphics
#                 chip and on what else is using it.
#
# A frame has 16.7 ms at 60 frames a second. If the main thread's share is
# a small part of that, the launcher is doing its job and what is left is
# the machine's. It is still an emulator on a fast Mac: a phone's main
# thread is several times slower, and only a phone can say how it feels.
set -u
cd "$(dirname "$0")/.."
. tools/env.sh
ADB="$ANDROID_HOME/platform-tools/adb"
. tools/device.sh

PROFILE=""
if is_profile "${1:-}"; then PROFILE="$1"; shift; fi
PKG="${1:-com.privacylauncher}"
pick_device "$PROFILE" || exit 2
echo "main-thread work per frame on $(describe_device)"
if ! $ADB shell pm path "$PKG" >/dev/null 2>&1; then
  echo "ABORT: $PKG is not installed. See the top of this script." >&2
  exit 2
fi

read -r W H <<< "$($ADB shell wm size | tail -1 | sed 's/.*: *//' | tr 'x' ' ')"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

$ADB shell cmd package set-home-activity "$PKG/com.privacylauncher.MainActivity" >/dev/null 2>&1
$ADB shell input keyevent KEYCODE_HOME; sleep 3

ROUNDS=8
for i in $(seq 1 $ROUNDS); do
  $ADB shell dumpsys gfxinfo "$PKG" reset >/dev/null
  $ADB shell input swipe $((W / 2)) $((H * 85 / 100)) $((W / 2)) $((H * 15 / 100)) 250
  sleep 2
  $ADB shell dumpsys gfxinfo "$PKG" framestats > "$OUT/open-$i.txt"
  $ADB shell dumpsys gfxinfo "$PKG" reset >/dev/null
  $ADB shell input keyevent KEYCODE_HOME
  sleep 2
  $ADB shell dumpsys gfxinfo "$PKG" framestats > "$OUT/close-$i.txt"
done

python3 - "$OUT" "$ROUNDS" <<'PY'
import glob, sys
out, rounds = sys.argv[1], int(sys.argv[2])

def frames(path):
    rows, names = [], None
    block = False
    for line in open(path, errors="replace"):
        line = line.strip()
        if line == "---PROFILEDATA---":
            block = not block
            names = None
            continue
        if not block or not line:
            continue
        cells = line.rstrip(",").split(",")
        if names is None:
            names = cells
            continue
        if len(cells) != len(names):
            continue
        try:
            row = dict(zip(names, map(int, cells)))
        except ValueError:
            continue
        # Flags other than 0 mark a frame that is not a normal one: the
        # first after a window appears, or one that was skipped.
        if row.get("Flags", 0) != 0:
            continue
        rows.append(row)
    return rows

def pct(values, p):
    if not values:
        return 0.0
    values = sorted(values)
    return values[min(len(values) - 1, int(round(p / 100 * (len(values) - 1))))]

def report(label, rows):
    seen, unique = set(), []
    for r in rows:
        if r["IntendedVsync"] in seen:
            continue
        seen.add(r["IntendedVsync"])
        unique.append(r)
    main = [(r["SyncQueued"] - r["HandleInputStart"]) / 1e6 for r in unique]
    late = [(r["HandleInputStart"] - r["IntendedVsync"]) / 1e6 for r in unique]
    over = [(r["FrameCompleted"] - r["SyncStart"]) / 1e6 for r in unique]
    whole = [(r["FrameCompleted"] - r["IntendedVsync"]) / 1e6 for r in unique]
    def line(name, v):
        print(f"    {name:<14} half under {pct(v,50):5.1f}   90% under {pct(v,90):5.1f}   "
              f"99% under {pct(v,99):5.1f}   longest {max(v) if v else 0:5.1f}   "
              f"over 16.7 ms: {sum(1 for x in v if x > 16.7)} of {len(v)}")
    print(f"  {label}: {len(unique)} frames")
    line("main thread", main)
    line("late start", late)
    line("handing over", over)
    line("whole frame", whole)

for kind, label in (("open", "opening the drawer"), ("close", "closing it")):
    rows = []
    for path in sorted(glob.glob(f"{out}/{kind}-*.txt")):
        rows += frames(path)
    report(f"{label}, {rounds} times", rows)
print("All times in milliseconds. Emulator figures, not a phone's.")
PY
