# Tests

## Tests

```bash
tools/check.sh                     # all four below on the a30 emulator, with a summary
tools/check.sh all                 # the same on all three emulators (about an hour)
./gradlew :app:testDebugUnitTest   # 259 JVM unit tests, no device needed
tools/devtest.sh a30               # 125 device tests, on the a30 emulator
python3 tools/contrast.py          # every colour pair against WCAG AA
tools/soak.sh a30                  # 61 checks in the real app, on the a30 emulator
tools/shapes.sh a30                # the device tests and a tour on seven screen shapes
```

### Unit tests

JVM tests, so they need no emulator and run in about a second. They cover
the parts that are pure functions of their inputs, which is deliberate —
that code was written that way so it could be tested at all:

| File | What it pins down |
|---|---|
| `FocusTest` | The clock arithmetic: overnight windows, empty windows, boundaries, snooze, the sleep clamp |
| `SettingsBackupTest` | The backup codec, mostly against files it should not trust |
| `SearchAndLayoutTest` | Drawer search ranking, section letters, the home layout codec |
| `PruneGuardTest` | That a failed "what is installed?" cannot delete the home screen |
| `IconCachePolicyTest` | How much of the icon cache survives each memory-pressure level |
| `HomeLayoutOpsTest`, `HomeDropOpsTest`, `HomeReorderTest` | Every rearrangement of the home screen and dock, by id and never by position |
| `HomeDropTargetTest` | Where a carried icon would land, on a made-up screen of round numbers |
| `FittedIconSizeTest` | That icons fit their slot at every common phone width |
| `DexClassesTest`, `TrackerListTest`, `TrackerScanTest` | The tracker scan, mostly against files it should not trust |
| `HiddenAppsLockTest`, `LockDownTest`, `OrientationTest`, `GestureSummaryTest` | Small rules with consequences: what the hidden-apps lock guards, when Lock down is ready, when the screen may turn, what an old Android is promised |

Two of them are exhaustive rather than example-based: one walks all 1,440
minutes of the day across five window shapes to assert the focus timer can
never return a zero delay, and one counts the minutes a window covers to
assert an overnight window partitions the day exactly. An overnight bug is
wrong for a few minutes a day, at two in the morning, and picking those
minutes by hand is how you miss them.

The backup tests are the ones worth reading. A backup file is the only
input a user can hand-edit, truncate, or pick by mistake from a folder of
unrelated JSON, so there are cases for a truncated file, another app's
JSON, a newer format version, unknown enum values, wrong types where a list
belongs, absurd column counts and out-of-range schedule minutes. The rule
they encode: a bad file costs you the settings it mangled and never stops
the launcher starting.

### Failures that destroy something quietly

Three of the defects found so far share a shape: nothing crashes, nothing is
logged, and the user finds out later. They are the ones worth writing tests
for, because they are the ones no amount of using the app will reveal.

**A failed question deleting the home screen.** The launcher asks Android
what is installed and then deletes everything not in the answer. An empty
list meant both "nothing is installed" and "I could not ask", so a failed
query wiped the home screen, dock, hidden list and focus list to disk with
no undo. `loadApps` now returns null for "could not ask", and pruning
refuses an empty set: a phone with no launchable apps has no Settings app
either, so that is never a true answer.

The test was checked by removing the guard and watching it fail:

```
home screen expected:<[App(com.phone/Main), App(com.uninstalled/Main),
                       Folder(id=f1, name=Tools, ...)]> but was:<[]>
```

**A damaged settings file bricking the launcher.** Verified on a device
rather than argued about. Writing rubbish over the preferences file and
starting the app, without a corruption handler:

```
FATAL EXCEPTION: main
androidx.datastore.core.CorruptionException: Corruption in file
  /data/data/com.privacylauncher.debug/files/datastore/launcher_settings.preferences_pb
Caused by: Unable to parse preferences proto. Consider providing a
  CorruptionHandler to the DataStore factory to handle such scenarios.
```

The process was gone, and it crashed again on every relaunch -- on the one
app you cannot avoid, with no way out from the home screen. With the handler,
the same corrupt file starts cleanly and DataStore replaces it. The user
loses their settings, which is why the backup file exists; they do not lose
the use of the phone.

**Nothing answering Android's request for memory.** There was no
`onTrimMemory` in the app at all. A launcher holding 10 MB of icons while
invisible is a cheap kill, and a killed launcher means a cold start every
time home is pressed. The cache now empties off screen and shrinks -- never
to nothing -- while visible.

### The permission list is asserted, not documented

`PermissionPromiseTest` reads the permissions out of the *installed package*
and asserts the exact set. The source manifest is not the list that ships: a
dependency can add a permission during manifest merge, which is exactly how
this project once came to tell users it declared two permissions when the
built app asked for three. An exact set fails on a new permission whether or
not anyone thought to forbid it, and separate cases assert there is no route
to the network at all.

### When the harness is the thing that is broken

`tools/soak.sh` reported 24 failures against a perfectly working build. The
emulator's data had been wiped, so the app opened on its onboarding flow
while every check looked for home-screen text. Not a hypothetical risk: the
same trap previously produced a baseline profile recorded against the
onboarding screen and a set of startup numbers that timed the wrong screen.

The script now refuses to start until it has established where it is -- that
the launcher is the default home app, and that onboarding is cleared and the
home screen is on screen -- and aborts with what it actually saw instead of
printing failures that belong to it rather than the app. The quick-panel
check also asserted nothing at all, so it passed while the eight checks
around it failed; it names a string on the panel now.

### The emulators

Everything here is tested on emulators, and on three of them, each there
for a reason. `tools/emulators.sh` makes them, the same way every time:

| Profile | Android | Screen | RAM | Why it exists |
|---|---|---|---|---|
| `a30` | 11 (API 30) | 1080x2340 at 420 dpi | 4 GB | The low bar. The screen, memory and Android version of a Samsung Galaxy A30, as an example of the slowest phone this has to feel good on. It is not the only phone the launcher is for |
| `modern` | 15 (API 35) | 1080x2400 at 420 dpi | 4 GB | What newer versions of Android do differently |
| `small` | 15 (API 35) | 720x1280 at 320 dpi, text at 1.3x | 2.5 GB | The accessibility squeeze: a 360 dp wide screen, the narrowest in common use, with large text |

```bash
tools/emulators.sh create      # make all three; safe to repeat
tools/emulators.sh list        # what exists and what is running
tools/emulators.sh start a30   # boot one; add --window to watch it
tools/emulators.sh stop        # shut down
```

Every test script takes a profile name and boots that emulator if it is
not already up: `tools/devtest.sh a30`, `tools/soak.sh modern`,
`tools/install.sh small --home`. Only one runs at a time, and starting
another stops the first: two 4 GB emulators and a build do not fit in this
Mac's 16 GB, and a Mac that is swapping makes every timing meaningless.

All three use the plain AOSP system image for Apple Silicon, not the
Google one. It has no Play Services, which is what the launcher will find
on the de-Googled OS it is being built for.

**An earlier version of the README said a Galaxy A30's screen is 720x1560
at 320 dpi. That was wrong.** It is 1080x2340, and the emulator built to
the wrong description (`a30_api30`) was a smaller and easier target than
the phone. The `a30` profile replaces it. The 420 dpi figure is the usual
one for a screen of this size and is the one part of the profile that has
not been checked against a real A30.

**What an emulator is not.** It copies a phone's screen size, memory and
Android version. It does not copy its processor, its graphics chip or its
storage: this Mac is several times faster than a budget phone, and by
default the emulator draws the screen in software. A pass here means "works on this
version of Android at this size". It does not mean "fast enough on the
phone", and every speed figure in this repository is an emulator figure.
It also runs plain Android. Phone makers ship their own versions (Samsung's
One UI, Xiaomi's HyperOS and so on), which differ in icon shapes, in where
the home-app picker lives and in how gestures are handled.

Three code paths differ below Android 12, which is what the `a30` profile
runs:

| Path | On Android 11 | Checked |
|---|---|---|
| Material You | unavailable | row reads "Needs Android 12 or newer" |
| Window blur | unavailable | quick panel falls back to an opaque surface |
| Privacy dashboard | older `getInstalledPackages` overload | reads all 36 packages, no crash |

Those three were checked by hand on the earlier Android 11 emulator, and
none of them depends on screen size. On `a30` itself, as of 13 November
2026, all 61 soak checks and all 122 device tests pass.

### Why a narrow screen has its own profile

A 1080-pixel screen at 420 dpi is 411 dp wide. A great many phones are
**360 dp** wide instead. Fifty-one dp is enough to break a layout, and it
did: the sixth accent swatch was cut off at the screen edge, invisible on
the test device and in every screenshot taken on it. The `small` profile
is the standing check for that width.

To try another size without making another emulator:

```bash
adb shell wm size 720x1560 && adb shell wm density 320
adb shell wm size reset && adb shell wm density reset    # back to normal
```

`tools/soak.sh` derives its gestures from `wm size`, so it works at any
size. It did not at first, and a swipe starting below the bottom of a
shorter screen made nine checks fail for the harness's reasons rather than
the app's.

### Compose UI tests

On a device, for the things only a real screen shows: 125 tests in
seventeen classes. The sections after this one describe the larger groups (dragging,
other screen shapes, the tracker scan). Two of the oldest are worth a
paragraph each because of what they caught.

`SettingsRowSemanticsTest` exists because of a bug nothing else could have
caught. A settings switch was a `clickable` row with `Role.Button` wrapping
an interactive `Switch`, so a screen reader met every setting twice: once
as a button with no on/off state, then again as the switch. The code read
fine, the screen looked fine, and tapping worked. It now asserts there is
exactly one toggleable node, that it is a `Role.Switch`, that it carries
its state, that the subtitle is merged into it, and that tapping the title
toggles it.

`MinimalHomeTest` covers the list-building that happens inside the
composable, in a `remember`, where no unit test can reach it: folders
flattened into their apps, dock apps appended, an app in both the grid and
the dock listed once, and the two ways out of minimal mode always on
screen.

None of this ships. The instrumentation tests build a separate APK;
`ui-test-manifest` is the one piece in the app module and it is
`debugImplementation`, so it is absent from release. Verified rather than
assumed: the release APK contains no `androidx/test`, `junit` or
`ui-test` entries, and is 2.3 MB with the same three manifest permissions.

`FolderRenameTest` is the newest: renaming a folder by deleting its old
name letter by letter used to give "WebFolder" instead of "Web", because
a name saved empty read back as the default and reappeared in the box. The
screenshot script found it; the test failed first and passes now.

`DrawerResetTest` is the one before it. The drawer is kept built between openings
(see [docs/performance.md](performance.md) for why), so it no longer
starts afresh by being thrown away. The test closes and reopens it and
requires the list back at the top and any app menu gone.

### A finger on the real home grid

`HomeGridDragTest` long-presses an icon on the real grid and drags it, and
checks which two icons the grid says the drag was about. It exists because
of a fault that only shows on the *second* drag: each cell's gesture
handler lives on between drags and used to keep the icons it was first
given, so after one rearrangement it was still working from the layout
before it. Every test there changes something first and drags second, and
one changes the icons while an icon is in the air.

`HomeReorderTest` is its JVM partner for the step after: with focus mode
hiding apps, the third icon on screen can be the fifth one saved, so a drag
has to be reported by the icons' ids rather than by where they were drawn.

### The drawer at the size a phone reaches

This emulator has a dozen apps; a phone in use has well over a hundred.
Everything about the drawer that only matters at scale had therefore never
been drawn, only reasoned about. `AppDrawerScaleTest` renders the real
drawer with 156 apps and checks it comes up, reports the count, shows its
section headers, searches, and **scrolls to the last row** -- the one that
fails if the grid composes everything at once instead of lazily, which on
a 4 GB phone is the difference between scrolling and being killed.

Three of its assertions were wrong on the first run and none of them was
the app: two nodes legitimately read "A" (the section header and the
fast-scroll letter), the label fixture yields six matches rather than the
ten that looked obvious, and a plain text match counts the search box
along with the results because the box holds the typed query. The
expected counts are now derived from the fixture rather than written in,
so they stay honest if it changes.

### Backup round trip, on the real preferences file

`BackupRoundTripTest` guards a bug that does not exist yet. `importBackup`
writes about twenty keys by hand, so adding a setting means remembering to
add it there as well; forget, and every restore silently drops it, with no
error and no log. The test sets every field away from its default, exports,
restores and compares field by field, so a forgotten one comes back as its
default and fails by name.

It also holds the promises the backup screens make: a running focus session
is not carried across, the usage record never reaches the file, restoring
does not wipe that record, and a file that is not a backup changes nothing.

### An icon carried across an edge

`HomeCarryTest` puts the real home screen on the emulator, clock, grid, dock
and bottom bar, and carries icons across its edges: grid to dock, dock to
grid, along the dock, and out of a drawer cell onto either. Those drags can
go wrong in ways one inside the grid cannot. The icon can be cut off at an
edge, land somewhere nothing was highlighted, or be left hanging when the
cell it came from disappears, which is exactly what happens when the drawer
closes under a finger that is still carrying. One test takes the gesture
away mid-carry, as a phone call would, and checks nothing is put down.

Every place a finger is sent in that test is read from what the screen
reports to accessibility services, not from the numbers the code keeps for
itself. Aiming with the code's own numbers would miss in the same direction
as the code and pass.

Where the icon would land is plain arithmetic, so it is also tested on the
build machine with no screen at all (`HomeDropTargetTest`,
`HomeDropOpsTest`): 47 cases, including a right-to-left layout, a full dock,
an app that is already where it is being carried to, and a target that
vanished while the finger was down.

### The tracker scan

Reading another app's code is reading something written by someone who is
not on the launcher's side, so most of these tests are about what happens
when the file is wrong.

- `DexClassesTest` (JVM) reads code files built by hand: good ones, one cut
  off at every possible length, ones whose tables point outside the file or
  claim two billion entries, and 5,000 copies of a good file with a few
  bytes changed at random. Every bad file has to end in "could not be
  read". None may crash, hang, or make the reader set memory aside for a
  size the file only claims.
- `TrackerListTest` (JVM) reads the list that ships and checks well-known
  trackers are recognised and ordinary code is not, then pins down how a
  name is matched: at the start of a part of the name, never in the middle
  of a word.
- `TrackerScanTest` (JVM) scans hand-made APKs: several code files, an app
  delivered in pieces, one with no code, one half unreadable.
- `TrackerScanDeviceTest` (emulator) is the check on all of the above,
  against files the real Android tools built. The test app carries two
  empty classes in packages named like trackers', and the scanner must find
  both. Then it scans the launcher's own installed file, which must contain
  **no known tracker**. Then every app on the emulator with an icon, to
  show Android lets them be read and none breaks the scan.
- `PrivacyDashboardTest` (emulator) walks the card through never-scanned,
  scanning and each shape of result, and holds in place the sentence that
  says what a result does not mean.

### Other screens

The three emulators have three screens between them, and phones come in
many more. `tools/shapes.sh` tells a running emulator to behave as a screen
of another size and density, which apps cannot tell from the real thing,
and runs every device test and a short tour of the real app on each:

| Shape | Screen | Stands for |
|---|---|---|
| `tiny` | 480x854 at 240 dpi, 320 dp wide | The smallest screens Android Go phones have |
| `budget` | 720x1600 at 320 dpi, 360 dp wide | A tall budget phone |
| `dense` | 1080x2340 at 480 dpi, 360 dp wide | Full HD at the density many makers choose |
| `roomy` | 1080x2400 at 360 dpi, 480 dp wide | Display size turned down to fit more on screen |
| `flagship` | 1440x3120 at 560 dpi, 411 dp wide | A large high-resolution phone |
| `sideways` | the emulator's own, turned | A phone turned on its side |
| `unfolded` | 2208x1768 at 420 dpi | A folding phone, opened flat |

The first run of this found what no test on one screen could: five dock
apps did not fit on a 360 dp wide phone, which is most phones (see
`HomeFitTest`), and sideways the drawer's search results were entirely
behind the keyboard. The first is fixed by fitting icons to their slot.
The second is why a phone's home screen now stays upright.

`AccessibilityWalkTest` runs on every shape too, with a third rule for
narrow screens: nothing that can be pressed is cut off at the side.

On 12 November 2026 all seven shapes passed on both emulators, Android 11
(`a30`) and Android 15 (`modern`): 120 device tests and the tour on each,
fourteen runs in all.

### Device soak

`tools/soak.sh` walks home, drawer, search, settings, the About section and
the quick panel, at three font scales and in dark mode. Then, once, it
carries an icon: it holds Calendar in the real drawer, drags it onto the
home screen, checks it is there, and takes it off again through the
long-press menu.

Two more parts check a promise each. With a PIN put on the emulator for the
purpose, turning the hidden-apps lock off must raise Android's "confirm it
is you" screen, and backing out of that must leave the lock on. And search
must list no Android settings until the switch for that is turned on. The
PIN is removed again afterwards, and the emulator scripts clear it at boot
in case a run was killed half way.

Another runs the tracker scan in the real app: opens the dashboard, presses
the button, waits for an answer, leaves, comes back, and checks the answer
is still there without a second scan.

One part is about memory, which on a 4 GB phone is the thing most likely
to go wrong. It asks the launcher to give memory back, as Android does when
it runs short, and requires the same process to carry on with the home
screen intact. Then it kills the process outright, with no warning, the way
the system does, and requires the launcher to come back by itself as a new
process with the home screen as it was left.

The last is Lock down, and it does not judge by the screen. Android keeps a
number that says whether the lock screen will take a fingerprint or only
the PIN, and prints it on request. An ordinary lock leaves it at 0. The
check presses the Lock down tile and requires the phone to be asleep and
that number to be 2, types the PIN and requires it to be 0 again, then
turns Lock down off through the launcher's own Settings and requires
Android to agree it is off.

And one part checks that nothing happens when nothing should. It reads how
many frames Android says the launcher has drawn, waits six seconds on the
home screen, and reads it again: the two must be equal. Then it opens and
closes the drawer and does the same, because the drawer is kept built while
closed and a search box left with the cursor would blink it, unseen, for
ever. That is 61 checks in all, and the signed release build passes them
too.

It clears the crash buffer before each step and checks it after, so a crash
is attributed to the step that caused it rather than appearing as a wall of
log afterwards. Each step also names what should be on screen when it
finishes: an earlier version only checked "no crash, still running", which
passes just as happily when a tap misses and the app sits on the home
screen for twenty steps. On this emulator taps do miss, so that mattered.

It knows it is on the home screen by the label on the handle that opens
the drawer, which is on that screen and no other. It used to look for the
word "Battery" in the line under the clock, and that line reads "Charging
100%" whenever the device is on power -- which a freshly booted emulator
always is. So on a fresh boot the script could not recognise a home screen
it was looking straight at, and stopped. Run unchanged on the `a30`
emulator it aborted; with the new marker, on the same emulator in the same
state, it passed all 27 of the checks it had at the time.

### What is not tested

- **A real phone.** Every result in this file comes from an emulator.
- **Android 8, 9, 10, 12, 13, 14 and 16.** The emulators run 11 and 15.
  See [Which phones it runs on](compatibility.md#which-phones-it-runs-on) for what stands
  in for a test there.
- **Any phone maker's own version of Android.** The emulators run plain
  Android.
- **TalkBack itself.** The accessibility tests read the same information
  TalkBack is given, but nobody has listened to it.
- **How it looks** at large font and display sizes. The tests show nothing
  is cut off, too small to press, or missing. They cannot show it looks
  right; that has only been checked by eye, on emulators.
- **Whether frames are dropped.** See
  [docs/performance.md](performance.md): an emulator cannot settle it.
