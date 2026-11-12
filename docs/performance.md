# Performance

**Every speed figure on this page was measured on an emulator, not on a
phone.** The emulator copies a Galaxy A30's screen, memory and Android
version. It does not copy its processor or its graphics chip, and this
Mac's processor is several times faster than the phone's. So these numbers
are good for one thing: comparing one build with the next, to catch a
change that made the launcher slower. They do not say what a phone will do,
and nothing here has been measured on real hardware yet.

## The budget, and where it stands

| | Budget | Measured on the `a30` emulator | Standing |
|---|---|---|---|
| Cold start | under 500 ms | 212 to 240 ms drawing in software, 132 to 134 ms drawing on the Mac's GPU | Met on the emulator. Unknown on a phone. |
| Frames in the drawer animation | none dropped | 0.7% to 1.2% of frames ran past 16.7 ms on the Mac's GPU; 96% in software | **Not met.** See "What the frame numbers say". |
| Frames while scrolling Settings | none dropped | none past 16.7 ms with the baseline profile, 0.7% without, on the Mac's GPU; 97% in software | Met only with the profile, and only with a GPU. |
| Release APK | under 10 MB | 2.2 MB (2,232,693 bytes) | Met. A file is the same size everywhere, so this one is not an emulator figure. |
| Memory pressure | handles `onTrimMemory` | `IconCachePolicyTest` (JVM) and `IconCacheTrimTest` (device) pass | Tested. |
| Killed and restarted | state comes back | not tested | **Open.** |

## How it is measured

`tools/bench.sh a30` runs AndroidX Macrobenchmark against the release
build (same shrinking and optimising as the APK that ships), compiled two
ways:

- **Nothing compiled ahead.** The code is interpreted and compiled as it
  runs. Roughly what a fresh install is like for its first day or so.
- **Baseline profile.** The code paths recorded in the profile are compiled
  in advance. What the app settles into.

**Cold start** is the time from asking Android to start the launcher, with
its process not running, to its first frame. Ten starts each. After every
start the benchmark checks that the home screen is what came up, because
this project once timed the welcome screen by mistake and called it the
launcher. That check has been watched failing with the welcome screen left
in place.

**Frame timing** is how long the launcher's own work on each frame took
(Macrobenchmark's `frameDurationCpuMs`). At 60 frames a second there are
16.7 ms for each. Android 11 does not record whether a frame actually
missed its turn on screen (Android 12 and later do), so "took longer than
16.7 ms" is the nearest thing to "dropped" that this emulator can give.
Two journeys are measured, eight rounds each:

- opening and closing the app drawer, three times a round (about 2,700
  frames);
- flinging the Settings screen down and up, twice a round (about 2,500
  frames).

The emulator can draw in two ways and both are measured, because they
answer different questions:

- **Software rendering** (the default for the test scripts). The Mac's
  processor paints every pixel. Identical on every machine, and very slow
  at 1080x2340.
- **The Mac's GPU** (`PL_GPU=host`). The Mac's graphics chip paints, as a
  phone's would, though it is far more powerful than a phone's.

## Baseline

Measured on 12 November 2026, app code as of commit `febdf36`.
Emulator profile `a30` (`pl_a30`): Android 11 (API 30), 1080x2340 at
420 dpi, 4 GB RAM (3.8 GB as the emulator reports it), 4 cores, AOSP image,
freshly booted, on a MacBook Air M4 with Android Emulator 37.2.13.

### Cold start

Milliseconds: the median of ten starts, then the fastest and slowest.

| Rendering | Nothing compiled ahead | Baseline profile |
|---|---|---|
| Software | 212 (158 to 287) | 240 (210 to 307) |
| Mac's GPU | 134 (109 to 196) | 132 (119 to 152) |

Two earlier software-rendered runs the same morning gave medians of 259
and 220 ms without the profile, and 241 and 240 ms with it. The slowest
single start in any of the 80 was 418 ms. So runs of the same build can
differ by 40 ms or more, and a change smaller than that means nothing.

On this emulator the baseline profile does not make the typical start
faster. What it does, with a GPU, is make starts more alike: 119 to 152 ms
against 109 to 196 ms.

### Frame timing, drawing on the Mac's GPU

Milliseconds per frame.

| | Half of frames under | 90% under | 95% under | 99% under | Over 16.7 ms | Longest |
|---|---|---|---|---|---|---|
| Drawer animation, nothing compiled ahead | 5.7 | 12.6 | 13.5 | 17.3 | 31 of 2,694 (1.2%) | 24.9 |
| Drawer animation, baseline profile | 5.6 | 12.3 | 13.0 | 15.1 | 19 of 2,689 (0.7%) | 24.2 |
| Settings scroll, nothing compiled ahead | 4.1 | 11.2 | 11.7 | 14.4 | 18 of 2,522 (0.7%) | 30.7 |
| Settings scroll, baseline profile | 3.9 | 13.1 | 13.6 | 14.5 | 0 of 2,542 (0.0%) | 16.4 |

### Frame timing, drawing in software

| | Half of frames under | 90% under | 95% under | 99% under | Over 16.7 ms | Longest |
|---|---|---|---|---|---|---|
| Drawer animation, nothing compiled ahead | 26.1 | 59.2 | 65.5 | 71.8 | 2,202 of 2,303 (95.6%) | 88.9 |
| Drawer animation, baseline profile | 24.6 | 57.3 | 64.5 | 72.8 | 2,228 of 2,325 (95.8%) | 116.8 |
| Settings scroll, nothing compiled ahead | 35.0 | 45.6 | 47.5 | 51.2 | 2,249 of 2,315 (97.1%) | 62.1 |
| Settings scroll, baseline profile | 35.2 | 46.7 | 48.5 | 52.2 | 2,321 of 2,400 (96.7%) | 85.0 |

### What the frame numbers say

**In software, nearly every frame is late, and that is the emulator, not
the launcher.** The same build on the same emulator takes 4 to 6 ms for a
typical frame once a graphics chip does the painting. The software figures
measure this Mac's processor painting two and a half million pixels a
frame. They are kept so a later run has something to be compared with, and
smoothness should not be judged from them.

**With a GPU, the launcher's own work fits in a frame almost always, and
not always.** Ninety-nine frames in a hundred are done within about 15 ms
with the baseline profile. But a few frames in each drawer run take 17 to
25 ms.
With the profile there were 19 of them, all in the first five of the eight
rounds, and none in the last three. Without it there were 31, spread
through all eight. No frame in either journey took as long as two frames'
worth.

So the budget of no dropped frames is **not met**, even in the kindest
conditions this Mac can offer. Which frames run long, and why, has not
been looked into yet. It is the first performance job for Phase 2, and
there is now a number to beat: 19 of 2,689.

**None of this says the phone is smooth.** A frame that costs 5 ms of
processor time here could cost most of a frame's allowance on a Galaxy
A30. That can only be measured on one.

## Measuring again

```bash
tools/bench.sh a30            # software rendering, about 20 minutes
tools/bench.sh a30 startup    # cold start only, about 2 minutes

tools/emulators.sh stop a30   # the rendering mode is chosen at boot
PL_GPU=host tools/bench.sh a30
```

Compare like with like: the same profile, the same rendering mode, the same
Mac, a freshly booted emulator. The script prints which emulator and which
rendering mode the figures came from. Put them in a new dated section here
rather than over the old ones, so the history stays readable.

## Not measured yet

- Anything at all on a real phone.
- Whether the launcher comes back with its state after Android kills it.
- How much memory it uses.
- Frames that actually missed their turn on screen. Android 12 and later
  record that, so `tools/bench.sh modern` would report it.
- The home screen's own animations: edit mode, opening a folder, the quick
  panel. The two journeys above are the drawer and Settings only.
