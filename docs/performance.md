# Performance

**Every speed figure on this page was measured on an emulator, not on a
phone.** The emulator copies a Galaxy A30's screen, memory and Android
version. It does not copy its processor or its graphics chip, and this
Mac's processor is several times faster than the phone's. So these numbers
are good for one thing: comparing one build with the next, to catch a
change that made the launcher slower. They do not say what a phone will do,
and nothing here has been measured on real hardware yet.

## How it stays light

- The drawer uses `LazyVerticalGrid`, so only the cells on screen exist.
- Icons are decoded on a background thread, scaled to 128×128 (~64 KB each) and
  held in an `LruCache` capped at 160 entries — about 10 MB at worst.
- The clock wakes once a minute, on the minute, not once a second.
- An idle home screen draws nothing at all. The soak reads Android's own
  frame counter to check it.
- The drawer is built once, a moment after the home screen appears, and
  kept while the launcher is in front, so opening it is a slide and not a
  rebuild. It is let go when the launcher leaves the screen.
- Release builds run R8 with resource shrinking.
- `buildConfig` is off and no test or tooling libraries are in the build.
- Every state class that crosses into composition is `@Immutable`. They all
  hold a `List` or a `Set`, which Compose treats as unstable by default, and
  an unstable parameter means the composable taking it can never skip — so
  changing the battery percentage in the glance line would recompose the
  whole home grid. The annotation is a promise the code keeps: all of them
  are `val`s holding primitives, enums, strings, or lists built once and
  never mutated.

### APK size

The signed release APK is **2.3 MB** (2,289,518 bytes), against a 10 MB
target. Its largest single item is the bundled Inter typeface at 880 KB,
well over a third of the APK. That
is the obvious thing to cut if size ever matters more than it does now:
dropping to the system font would roughly halve it.

## The budget, and where it stands

As of 13 November 2026, at the end of Phase 2. The figures behind each line
are in the dated sections further down.

| | Budget | Measured on the `a30` emulator | Standing |
|---|---|---|---|
| Cold start | under 500 ms | 176 to 188 ms drawing in software, 138 to 145 ms drawing on the Mac's GPU | Met on the emulator. Unknown on a phone. |
| Frames in the drawer animation | none dropped | The launcher's own work on a frame never took more than 12 ms, and usually under 1 ms. Whether a frame then reached the screen in time depended on how busy the Mac's graphics chip was | **Cannot be settled on an emulator.** The launcher's part is inside the budget. One real cause of slow frames was found and removed. See "What the frame numbers say now". |
| Frames while scrolling Settings | none dropped | With the baseline profile, 2 and 16 frames in about 2,600 ran past 16.7 ms in two runs, in clusters | The same: not provable either way here. |
| Release APK | under 10 MB | 2.3 MB (2,289,518 bytes) | Met. A file is the same size everywhere, so this one is not an emulator figure. |
| Memory pressure | handles `onTrimMemory` | `IconCachePolicyTest`, `IconCacheTrimTest`, and the soak sends Android's own "running critical" signal to the real app | Met on the emulator. |
| Killed and restarted | state comes back | The soak ends the launcher's process with no warning and requires the home screen back as it was, on all three emulators | Met on the emulator. |
| Idle | (not in the budget, but it is battery) | 0 frames drawn in 6 seconds on the home screen, before and after the drawer is used | Met on the emulator. |

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

## Baseline (Phase 1)

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

## After Phase 2

Measured on 12 and 13 November 2026, app code as of commit `8e33043`, with
the baseline profile recorded again for that code. Same emulator profile
and same Mac as the baseline above.

### Cold start

Milliseconds: the median of ten starts, then the fastest and slowest.

| Rendering | Nothing compiled ahead | Baseline profile |
|---|---|---|
| Software | 176 (151 to 278) | 188 (179 to 202) |
| Mac's GPU | 145 (138 to 183) | 138 (121 to 155) |

All four are within the 40 ms that two runs of the same build can differ
by, so the honest reading is "no slower than before", after a phase that
added a tracker scanner, a second way to lock the phone, and a drawer that
is now built in advance. The release APK grew by 61 KB.

### Frame timing, drawing on the Mac's GPU: three runs, and why they differ

Milliseconds per frame, with the baseline profile.

| Drawer animation | Half under | 90% under | 99% under | Over 16.7 ms | Where the slow frames were |
|---|---|---|---|---|---|
| Run A: before the fix below | 5.1 | 6.8 | 16.2 | 25 of 2,692 (0.9%) | In seven rounds of eight, and always the same frames: the first two or three of each opening |
| Run B: after it | 5.1 | 6.7 | 13.2 | 23 of 2,689 (0.9%) | None at any opening. None at all in five rounds of eight. Twenty in a row in one round |
| Run C: same build as B, Mac busy | 5.2 | 14.9 | 19.7 | 213 of 2,694 (7.9%) | In long runs, in every round |

Run C is the same app as run B, measured twenty-five minutes later. The
difference was the Mac: a browser was drawing in another window, and macOS's
window server was using about 40% of a processor. The emulator shares the
Mac's graphics chip with all of that.

Without the profile the same three runs gave 28, 35 and 326 slow frames.
Scrolling Settings, with the profile, gave 2 slow frames in 2,583 in run A
and 16 in 2,601 in run B, in clusters; without it, 107 and 223.

### The launcher's own work, apart from the graphics chip

`tools/uiwork.sh` reads Android's frame statistics for the release build
and splits each frame in two: the main thread's work (input, animation,
layout, drawing), which is the launcher's own code, and handing the frame
over to be shown, which depends on the graphics chip and what else is
using it. Eight drawer openings and closings each time, with the Mac busy.

| Main thread, ms per frame | Frames | Half under | 90% under | 99% under | Longest | Over 16.7 ms |
|---|---|---|---|---|---|---|
| Opening, drawer rebuilt every time (the old way) | 351 | 1.2 | 3.1 | 7.1 | 23.2 | 1 |
| Opening, drawer kept built, first run | 348 | 1.0 | 2.3 | 3.9 | 4.6 | 0 |
| Opening, drawer kept built, second run | 348 | 1.0 | 2.3 | 4.0 | 11.7 | 0 |
| Closing, the old way | 181 | 0.9 | 2.5 | 5.4 | 10.1 | 0 |
| Closing, drawer kept built (both runs) | 183 | 1.0 | 2.2 | 3.7 | 5.0 | 0 |

In the same run where the main thread never took more than 4.6 ms, handing
frames over took more than 16.7 ms for 19 of 348 of them, and 61 of 348
frames were late end to end. Every one of those was late because of the
hand-over, not because of the launcher.

### What the frame numbers say now

**One real fault was found, and it is fixed.** The drawer used to be thrown
away each time it closed and built again from nothing when the next swipe
began. That showed in run A as slow frames at exactly the same place in
every round, and in the main thread's own figures as a 23 ms frame. The
drawer is now built a moment after the home screen first appears and kept,
off screen, while the launcher is in front. After that, no opening in any
round began with a slow frame, and the main thread's worst frame fell to
between 5 and 12 ms.

**The launcher's own work fits in a frame many times over, on this Mac.**
Half of all frames need less than 1 ms of it and 99% need less than 4 ms,
against 16.7 available.

**Whether frames are dropped cannot be decided here.** What is left after
the fix comes in runs of consecutive frames at about 18 ms, in different
places each time, far more of them when the Mac is busy. That is the
pattern of a shared graphics chip falling behind, not of an app doing too
much. But "it looks like the machine" is not proof, and the budget says no
dropped frames. So the standing is: not shown to be met, not shown to be
missed, and the part that is the launcher's is inside it.

**A phone will be slower than this.** The main thread's 1 to 4 ms here is
on a processor several times faster than a budget phone's. If it is five
times slower, the typical frame still fits easily and the worst ones are
close to the limit. Only a phone can say.

### Memory

How much memory the release build holds on the `a30` emulator (Android's
"PSS" figure, which shares out memory used jointly with other apps).

| | MB |
|---|---|
| On the home screen, 6 seconds after a cold start | 34.4 |
| With the drawer open | 38.0 |
| Back on the home screen | 37.3 |
| After Android's "running critical" memory warning | 36.8 |
| For scale: Android's own launcher on the same emulator | 46.6 |

The first figure includes the drawer, built in advance. An emulator with a
dozen apps is a small test: a phone with two hundred has more icons to
hold, up to the cache's limit of about 10 MB.

### The tracker scan

On `a30` (Android 11) a scan of the emulator's 11 apps took 26 ms and
raised the launcher's memory by under 2 MB. On `modern` (Android 15) 12
apps took 86 ms and under 5 MB. All 23 could be read. These are small
system apps on a fast processor; a phone's apps are many times larger.

## The baseline profile is a committed file, and tasks delete it

The recorded profile lives at
`app/src/release/generated/baselineProfiles/baseline-prof.txt` and is
committed. It is inside `src/`, not `build/`, precisely so that building
the release does not require a device.

**Benchmark and profile tasks delete it as a matter of course**, and a
`git add -A` straight afterwards will quietly commit its removal — taking
the whole of this work with it, because nothing else in the repository
contains those 15,268 rules. Before committing after any `:baselineprofile`
run:

```bash
git status --short app/src/release/generated/baselineProfiles/
```

If it shows `D`, the file was deleted rather than regenerated. Put it back
with `git restore` on that path; only accept the deletion if a fresh
recording has genuinely replaced it.

`tools/bench.sh` makes that check itself after every run and restores the
file if it has gone. The five benchmark runs of 12 November 2026 all left
it alone, so it may be only the recording task that removes it.

### Checking the profile actually ships

Worth doing, because a baseline profile that silently fails to apply looks
exactly like one that applies and does nothing:

```bash
unzip -p app/build/outputs/apk/release/app-release.apk assets/dexopt/baseline.prof | wc -c
```

Following it through the build shows the rules surviving R8:

| Stage | Rules | Naming `com/privacylauncher` |
|---|---|---|
| Recorded | 15,268 | 564 |
| Merged with library profiles | 22,023 | 564 |
| After wildcard expansion | 46,533 | 564 |
| After R8 | 11,635 | 17 |

That last row looks alarming and is not. R8 renames our classes, so a rule
for `LauncherViewModel` comes out as `Lsk0;`; the 17 are the few that keep
their original names because the manifest refers to them. You can see our
code in the profile under its new names — `SPLjm;-><init>(Lcom/privacylauncher/MainActivity;)V`
is one of ours. The overall drop from 46,533 is R8 removing library code
that the app never calls, and dropping the now-pointless rules with it.

---

## Measuring again

```bash
tools/bench.sh a30            # software rendering, about 20 minutes
tools/bench.sh a30 startup    # cold start only, about 2 minutes

tools/emulators.sh stop a30   # the rendering mode is chosen at boot
PL_GPU=host tools/bench.sh a30
PL_GPU=host tools/bench.sh a30 drawer   # the drawer's frames only, about 8 minutes

tools/uiwork.sh a30           # the launcher's own work per frame; needs the
                              # release build installed, see the script
tools/profile.sh a30          # record the baseline profile again
```

**Before measuring frames with the Mac's GPU, look at what else the Mac is
doing.** A browser playing video in another window is enough to make the
numbers several times worse. `tools/uiwork.sh` is the one to trust when the
Mac cannot be left alone, because the main thread's share does not depend
on the graphics chip.

Compare like with like: the same profile, the same rendering mode, the same
Mac, a freshly booted emulator. The script prints which emulator and which
rendering mode the figures came from. Put them in a new dated section here
rather than over the old ones, so the history stays readable.

## Not measured yet

- Anything at all on a real phone. Every number on this page would change.
- Frames that actually missed their turn on screen. Android 12 and later
  record that, so `tools/bench.sh modern` would report it. It would still
  be the Mac's graphics chip doing the drawing.
- The home screen's other animations: edit mode, opening a folder, the
  quick panel. The quick panel is still built from nothing each time it
  opens, as the drawer was.
- Memory with a phone's worth of apps installed.
- The tracker scan on a phone's worth of apps.
