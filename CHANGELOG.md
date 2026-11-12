# Changelog

All notable changes to Privacy Launcher. Newest first.

## [Unreleased]

### Every screen walked for accessibility, and eight of twenty failed

`AccessibilityWalkTest` puts each of twenty screens and dialogs on the
emulator and checks two rules against what the screen tells Android's
accessibility services: everything that can be pressed has a name, and is
at least 48 by 48 dp. The first run failed on eight screens. Fixed:

- The six accent-colour swatches were unnamed buttons (Settings and the
  welcome pages). Each now says its colour and whether it is chosen.
- The brightness slider had no name and was 44 dp tall. Now "Brightness",
  and 48 dp.
- Hidden apps and focus apps announced every app twice: a row with a name
  and no state, then a switch with a state and no name. Each is now one
  switch.
- The search box lost its only name with the first letter typed.
- The folder sheet: its name field had no name; the dimmed area round it
  had an action but no name; and the sheet itself claimed to be a button
  that did nothing.

All twenty pass now (emulator `a30`). The test reads the same information
TalkBack acts on, but it is not TalkBack: it cannot judge whether a name is
a good one or the reading order makes sense. TalkBack is not on these
emulator images, so nobody has listened to the launcher with it yet.

### Killed, and back as it was

The performance budget says the launcher must hand memory back when asked
and come back intact after being killed. The first half had a test; the
second had never been tried.

New soak part H does both in the real app, on every run:

- asks for memory back with Android's own "running critical" signal, and
  requires the same process to carry on with the home screen intact;
- then ends the process with no warning, as the system does when memory
  runs out, and requires the launcher to return by itself, as a new
  process, with the icon that was on the home screen still there.

On emulator `a30` (Android 11) both hold. An emulator with 4 GB and little
installed is rarely short of memory by itself, so this shows the launcher
recovers from a kill, not how often a real phone would kill it.

Also corrected: the welcome pages said everything the launcher knows "lives
in one file". With tracker scan results now remembered that is two files.
It now says what is true and what matters: it all stays in the launcher's
private storage on the phone.

### Lock down now means what it says

The Lock down tile used to lock the screen and nothing more: a fingerprint
opened it again. A lock-down is for the moment someone else might press
your finger to the phone, so that was the wrong thing under the right name.
The tile said as much, which was honest, but the feature was half there.

It now locks so that **only the PIN, pattern or password opens the phone**.

- **How.** Android lets an app the user has approved as a "device admin
  app" lock the screen with `DevicePolicyManager.lockNow()`, after which,
  in Android's own words, "the device must be unlocked using strong
  authentication". The launcher asks for that one ability,
  locking the screen, and lists no other.
- **Measured, not assumed.** Android keeps a number saying whether the lock
  screen will take a fingerprint, and prints it on request. On emulator
  `a30` (Android 11): an ordinary power-button lock leaves it at 0; the
  Lock down tile makes it 2; typing the PIN puts it back to 0. New soak
  part H checks exactly that on every run.
- **The cost is said first.** While Lock down is allowed, Android will not
  uninstall the launcher. The dialog says so before sending the user to
  Android's screen, and Settings → Gestures → Lock down takes it back.
- **No screen lock, no promise.** On a phone with no PIN the tile says it
  needs one, instead of "locking" a phone anyone can swipe open.
- **Still not all of Android's Lockdown.** That also hides notifications on
  the lock screen. An app cannot, and the panel says so.

Double-tap to lock is unchanged: it is the ordinary lock, through the
optional accessibility service, and a fingerprint opens it.

The two permissions the app asks for are unchanged. Being a device admin is
not a permission an app requests; it is something the user switches on.
`PermissionPromiseTest` now also reads the admin's list of abilities from
the installed app and fails if it is ever more than "lock the screen".

Tests: 3 JVM, 5 on the emulator, 6 soak checks.

### A tracker scan, done on the phone

The privacy dashboard can now look inside each app for known advertising,
analytics and profiling code. This was the one feature on the list that did
not exist at all.

- **The list** is Exodus Privacy's: 428 trackers and 588 code signatures in
  the snapshot of 12 November 2026. It is published under the Open Database
  License 1.0, which asks for credit and that copies stay open; both are
  done, and `licenses/README.md` says how. `tools/trackers.py` cuts their
  half-megabyte file down to the 24 KB the app carries. Nothing is fetched
  on the phone, and nothing could be.
- **How it reads an app.** An app's code is in files inside its APK. The
  scanner streams each one once through a small buffer, picks out the names
  of the classes the app defines, and compares them with the list. The file
  is never loaded whole.
- **One app at a time, remembered.** Results are kept with the version they
  describe, so a later scan only reads apps that are new or updated.
- **Only when asked.** Opening the dashboard shows the last results. A scan
  starts when the button is pressed, and can be stopped.
- **Honest about what it shows.** The card says that "found" is not proof
  the code runs, that "not found" is not proof an app is clean, and gives
  the date of the list. An app that could not be read is counted as unread,
  never as clean.
- **It trusts nothing it reads.** A code file comes from the app being
  scanned. Every count and position in it is checked before use.

Checked two ways beyond the tests. The class names the reader finds were
compared with those from Android's own `dexdump` tool on three real apps
(Contacts, 3,542 classes; Settings, 7,718; this launcher's debug build,
17,899 in 13 code files): identical every time. And on emulator `a30`
(Android 11) a scan of the emulator's own 11 apps read all 11, took 26 ms
and raised the launcher's memory by under 2 MB. That says the scan works
and is light on an emulator. It says little about speed on a phone, where
apps are many times larger and the processor several times slower.

Limits, stated rather than hidden:

- It sees apps with an icon, like the rest of the dashboard. Seeing the
  rest would need `QUERY_ALL_PACKAGES`, which this launcher does not ask
  for.
- A signature is matched where a part of a class name starts. Exodus's own
  tool also matches in the middle of a word, so on rare apps this reports
  slightly less than their website does. That reading was chosen because
  the looser one reports code that is not the tracker's.
- An app whose code has been renamed to hide it will not be recognised.
- Preinstalled apps on real phones often cannot be read at all. On the
  emulator all of them could.

Tests: 43 on the build machine, 18 on the emulator, and 3 checks in the
soak that run the scan in the real app.

### The hidden-apps lock could be switched off without the PIN

"Require unlock" puts the list of hidden apps behind the phone's own lock.
The switch that turns that off sat beside it and took one tap. Shown on
emulator `a30` with a PIN set for the purpose: before the fix, tapping the
switch turned the lock off with no question asked.

- Turning the lock off now goes through the lock.
- So do the two other ways round it, while anything is hidden: saving a
  backup (the file lists the hidden apps in plain text) and restoring one
  (it replaces the list and the lock setting).
- Each prompt says what it is for, so nobody types a PIN into a prompt that
  does not say why.
- New soak part E does it for real: gives the emulator a PIN, taps the
  switch, expects Android's "confirm it is you" screen, backs out, and
  checks the lock is still on. Failed before, passes now. 9 JVM tests hold
  the rule itself (`HiddenAppsLockTest`).

Still true, and now said in the README: hiding is not a vault. A hidden app
is still listed in Android's Settings and in this launcher's own lists of
installed apps. The lock keeps private which apps you chose to hide.

### Settings screens in search are now a choice

Drawer search listed Android's settings screens (Wi‑Fi, Display and so on)
for everyone. The brief asks for apps by default and anything more only
after the user opts in, as contacts already were.

- New switch, **Settings → Search → Search Android settings**, off by
  default. It is saved in backups.
- While it is off the launcher no longer asks Android which of those
  screens exist at every start.
- New soak part F checks the real app both ways: off, searching "displ"
  lists no settings; on, it lists Display. 6 JVM tests (`SettingsSearchTest`).

### Icons can be carried between the grid, the dock and the drawer

Drag and drop used to stop at the edge of the home grid. Now:

- **Grid to dock.** Carry a home icon down onto the dock and it moves there.
- **Dock to grid, and along the dock.** Long-press a dock icon to reorder
  it, or carry it up onto the grid or onto another icon to make a folder.
  While editing, each dock icon has a ✕.
- **Out of the drawer.** Hold an app and drag: the drawer closes and the icon
  stays under the finger until it is dropped on the grid, on another icon,
  or in the dock.

An icon moves; a drag never leaves it in both places. A full dock (five
apps) refuses a new icon and says so by not lighting up.

One change in behaviour to know about: **the drawer's long-press menu now
opens when the finger lifts**, not while it is still held. That is what
leaves room for the drag. The buzz and a slight lift of the icon say the
hold has registered.

Three faults turned up on the way, each with a test that failed first
(emulator `a30`, Android 11):

- **A drop on empty space could make a folder.** Dropping an icon on the
  middle of an empty cell after the last icon made a folder with the last
  icon, which the finger was nowhere near.
  `HomeGridDragTest.aDropOnEmptySpaceAfterTheLastIconNeverMakesAFolder`.
- **In a right-to-left language a drag went the wrong way.** The grid
  assumed the first column was on the left. In Arabic, Hebrew, Persian or
  Urdu it is on the right, so the finger went one way and the icon the
  other. `…inARightToLeftLanguageADragGoesWhereTheFingerGoes`.
- **A screen reader could name a home icon but not open it**, and could not
  name a dock icon at all. `…anIconCanBeOpenedWithoutTouchingTheScreen`,
  `HomeCarryTest.aDockIconHasANameAndCanBeOpenedWithoutTouchingTheScreen`.

Also fixed in passing, because the new drawing layer made it free: starting
a drag turns on edit mode, which adds a line to the bottom bar and moves the
grid up under the finger. The carried icon used to move with the grid. It
now stays with the finger
(`HomeCarryTest.theIconStaysUnderTheFingerWhenStartingADragMovesTheGrid`).

Tests added: 47 on the build machine, 22 on the emulator (19 in the new
`HomeCarryTest`, 3 in `HomeGridDragTest`), and 4 checks in `tools/soak.sh`
that do the drawer drag in the real app with a real long-press.

Known limits, not hidden:

- The dock is not drawn when it is empty, so the first app has to be pinned
  from the drawer's menu. After that, dragging works.
- The grid does not scroll by itself while an icon is carried, so on a home
  screen taller than the display an icon cannot be dragged to a row that is
  off screen.
- Minimal mode has no grid or dock, so there a long-press in the drawer is
  only the menu.

### The signing key moved out of the project

The release signing key and the file holding its passwords used to sit in
the project folder, kept out of git only by `.gitignore`. They now live in
`~/.privacy-launcher-signing/`, outside the project, and the build looks
for them there (or wherever the `PL_KEYSTORE_PROPERTIES` environment
variable points). If a key is ever found inside the project folder the
build stops and says so. Checked: the release build is still signed with
the same key (signature schemes v2 and v3), and a build with no key still
succeeds, unsigned.

It is still a **development key**. A real release needs a real one.

### One less thing happening at start-up

AndroidX had quietly registered a start-up helper that asks a "font
provider" app for a newer emoji font. On a phone with Google Play Services
that provider is a Google app, which may fetch the font over the network on
the launcher's behalf. Our emulators have no such app, so this could not
happen there, but the project's promise is not "only on our emulators".

- New test, failing first: `PermissionPromiseTest.
  noStartUpHelperGoesLookingForAnEmojiFont` found
  `androidx.emoji2.text.EmojiCompatInitializer` in the installed app.
- The helper is removed in the manifest; the test passes (emulator `a30`).
- Emoji are drawn with the phone's own emoji font, as before on any phone
  without a font provider.

### A measured baseline, at last

`tools/bench.sh` measures cold start and frame timing with Macrobenchmark,
and `docs/performance.md` records the first baseline. All of it is from the
`a30` emulator (Android 11, 1080x2340 at 420 dpi, 4 GB RAM) on a MacBook
Air M4, 12 November 2026. **Emulator figures, not a phone's.**

| | Drawing in software | Drawing on the Mac's GPU |
|---|---|---|
| Cold start, median of 10 | 212 ms uncompiled, 240 ms with the baseline profile | 134 ms and 132 ms |
| Drawer animation, frames over 16.7 ms | 95.6% and 95.8% | 1.2% and 0.7% |
| Settings scroll, frames over 16.7 ms | 97.1% and 96.7% | 0.7% and none |

What that says, plainly:

- Cold start is inside the 500 ms budget on the emulator, on a processor
  several times faster than the phone's. It says nothing about the phone.
- In software nearly every frame is late. That is the emulator painting
  on the processor: the same build with a GPU takes 4 to 6 ms a frame.
- **The "no dropped frames" budget is not met**, even with the GPU. A few
  frames in each drawer run take 17 to 25 ms. Not yet investigated.
- The baseline profile makes no measurable difference to the typical start
  here. It makes starts more consistent and removes most of the slow
  frames after the first few rounds.

**The cold-start benchmark now proves which screen it timed.** After each
start it checks the home screen is up. This project once timed the welcome
page and reported it as the launcher, twice. With the welcome page left in
place the check fails with "The cold start that was just timed did not draw
the home screen", and with it cleared the run passes.

The README's old startup figures (2097 ms and 2306 ms, both of the welcome
page) are gone, replaced by a pointer to the baseline.

### Three emulators, each asked for by name

`tools/emulators.sh` makes the emulators this project is tested on, the
same way every time, and every test script now takes one of their names:

- `a30`: Android 11, 1080x2340, 4 GB RAM. The shape of a Galaxy A30.
- `modern`: Android 15, 1080x2400, 4 GB RAM.
- `small`: Android 15, 720x1280 (360 dp wide), 2.5 GB RAM, text at 1.3x.

`tools/devtest.sh a30`, `tools/soak.sh modern` and `tools/install.sh small`
boot the emulator they name if it is not already up. One runs at a time.

`tools/check.sh` is the whole suite in one command: JVM tests, the contrast
check, the on-device tests and the soak, then a summary. `tools/check.sh
all` does it on every emulator in turn. Its first full run, 15 minutes:

    ok    JVM tests     142 passed, 0 failed
    ok    Contrast      72 pairs checked, 0 below AA
    a30: emulator pl_a30: Android 11 (API 30), 1080x2340 at 420 dpi, 3.8 GB RAM, software rendering
    ok    Device tests  53 passed, 0 failed
    ok    Soak          27 ok, 0 problems
    modern: emulator pl_modern: Android 15 (API 35), 1080x2400 at 420 dpi, 3.8 GB RAM, software rendering
    ok    Device tests  53 passed, 0 failed
    ok    Soak          27 ok, 0 problems
    small: emulator pl_small: Android 15 (API 35), 720x1280 at 320 dpi, 2.4 GB RAM, software rendering
    ok    Device tests  53 passed, 0 failed
    ok    Soak          27 ok, 0 problems
    RESULT: PASS

(The RAM figures are what each emulator reports, which is a little under
what it was given. `small` was first set to 2 GB; the emulator will not go
below 2.5 GB for Android 15, so the profile now says 2.5.)

**The old "A30" emulator was the wrong shape.** This file and the README
said a Galaxy A30's screen is 720x1560. It is 1080x2340. The emulator built
to the wrong figure had under half the pixels of the phone, so it was an
easier target than the one it stood in for. Everything is re-run on the
corrected profile.

**The soak script could not find the home screen on a fresh boot.** It
recognised the home screen by the word "Battery" under the clock. On power
that line reads "Charging 100%", and a freshly booted emulator is always on
power, so the script stopped with "could not reach the home screen" while
looking at it. It now looks for the label on the drawer handle, which is on
the home screen and nowhere else. That is also a stricter check: the
battery line stays in the screen's contents underneath the quick panel, so
"back to home" after the panel could have passed with the panel still open.

Proof, on `a30` (emulator: Android 11, 1080x2340 at 420 dpi, 4 GB RAM,
software rendering) with the battery left as the emulator boots it: the
unchanged script aborted, and the fixed one reports 27 ok, 0 problems. The
53 device tests pass there too.

The scripts now refuse anything that is not an emulator, so a phone left
plugged in cannot have a test build installed on it by accident.
`tools/emulator.sh`, which made the single old emulator, is gone.

### Dragging the icon you meant

Three faults in rearranging the home screen. None crashed, and each one
moved or grouped an icon other than the one under the finger.

**With focus mode on, a drag moved a different icon.** The home screen does
not always draw everything that is saved: focus mode hides apps, and an app
removed a moment ago is skipped until it is cleaned up. The grid reported a
drag as "position 1 to position 2" and those numbers were applied to the
saved layout, where position 1 can be a hidden app. Drags are now reported
by the icons' ids, which mean the same thing in both lists.

**After one rearrangement, the next drag used the old layout.** Each cell's
drag handler kept the list of icons, the callbacks and the row height it
was given when it was first touched. Rearranging keeps the same number of
icons, so nothing told the handler to let go of them. The second drag was
then judged against the layout before the first one, an app sitting where a
folder had been could not be dropped onto anything, and after an icon-size
change a drop landed a row too far down. The handler now reads all of these
fresh at the moment it needs them.

**If the icons shifted while one was in the air, the wrong one was moved.**
A side effect of the fix above, caught by a test written before finishing
it: if an app disappears during a drag, the cell under the finger has a
different icon in it by the time the finger lifts. The grid now remembers
which icon was picked up and does nothing if that is no longer the one in
its cell.

Proof, on an Android 11 emulator (`a30_api30`: 720x1560, 4 GB RAM, software
rendering): `HomeReorderTest` (8 JVM tests) covers the first fault and
`HomeGridDragTest` (8 device tests, a real finger on the real grid) covers
the other two. Three of the device tests were watched failing before the
fix, with the wrong pair of icons in the message, and the icons-shifted
test was watched failing against the half-finished fix:

    expected:<[]> but was:<[(a:p.D/A, a:p.A/A)]>

`tools/devtest.sh` is new: it runs the device tests and prints which ones
failed and why, where Gradle prints only a link to an HTML report.

Tests are now 142 on the JVM and 53 on a device, all passing.

### Surviving a bad day

Four fixes, none of which a user could have reported as a bug, because
three of them destroy something quietly and the fourth only shows up on a
phone that is busier than the test machine.

**The home screen could be wiped by a failed question.** The launcher asks
Android what is installed, then deletes every reference to anything not in
the answer — home screen, dock, hidden apps and focus list, written
straight to disk with no undo. The two halves met like this:

```kotlin
val apps = appRepository.loadApps()          // empty list when the query failed
settingsRepository.pruneMissing(apps.keys)   // therefore: delete everything
```

An empty list meant both "this phone has no apps" and "I could not ask",
so a query that failed read as a phone with nothing on it. The user would
press home and find a bare screen, already saved. This fires on every
package install, uninstall, update and enable, and on every launcher
start.

`loadApps` now returns null when the system cannot be asked, which says
nothing about the phone, and an empty list only when the phone genuinely
has nothing. Pruning refuses an empty set outright — a phone with no
launchable apps has no Settings app either, so that is never a true answer
— and the check sits both at the call site and in the function that does
the writing. Proved by removing the guard and watching the test fail with
`expected:<[App(com.phone/Main), ...]> but was:<[]>`.

One misbehaving app can no longer take the whole drawer down with it
either: reading a label can throw while its app is mid-upgrade, and that
used to abandon every other app in the list.

**A damaged settings file bricked the launcher.** `preferencesDataStore`
had no corruption handler, so an unparseable file made the settings flow
throw — and the launcher reads it before it can draw. That is a crash on
every start, in a loop, on the one app you cannot avoid, with no way out
from the home screen. Damaged files now reset to defaults. That costs the
user their settings, which is a real loss and the reason the backup file
exists, but defaults you can see and change beat perfection you cannot
reach.

**Nothing answered Android when it asked for memory back.** There was no
`onTrimMemory` anywhere in the app. A launcher holding up to 10 MB of
icons while invisible is a cheap kill for the system, and a killed
launcher means a cold start every time you press home — on the one app
that has to be instant. On a 4 GB A30 with a browser open this is routine,
not rare. The icon cache now empties when the launcher goes off screen and
shrinks under pressure while it is still visible, never to nothing, which
would blank the icons under the user's finger and then decode them again.
Icons are both the only large thing held and the cheapest to rebuild.

**Two claims in the code were wrong.** `ContactRepository` described
READ_CONTACTS as "the only permission the launcher declares"; there are
two. This is the same error, in a third place, as the README and
onboarding text that said two permissions when the built manifest had
three — so the permission list is now asserted by a test that reads it out
of the *installed* package rather than the source manifest, which is the
only list that is true. A dependency upgrade is enough to add one, and the
test fails on a new permission whether or not anyone thought to forbid it.


### Tested on the phone it is actually for

The target is a Galaxy A30: Android 11, 4 GB of RAM, 720x1560 at 320dpi,
which is **360dp wide**. Everything until now was tested on Android 15 at
411dp. Both gaps hid a real problem.

Fixed:
- **The sixth accent colour was cut off the screen edge** on anything
  narrower than about 400dp. Six 48dp targets with 12dp gaps need 348dp;
  a 360dp phone has 312dp after insets. A `Row` overflows silently — no
  scroll, no ellipsis, just a missing colour — so this was invisible on
  the wider emulator and would have been plainly wrong on the phone. It
  wraps now, as the theme chips above it already did.
- `tools/soak.sh` sized its gestures in fixed pixels for a tall screen, so
  on a 720x1560 display the swipes began below the bottom edge and nine
  checks failed for the harness's reasons. It reads `wm size` now.

Verified on an Android 11 AVD shaped like the A30 — the three code paths
that differ below Android 12 had never once executed:

| Path | Behaviour on Android 11 | Result |
|---|---|---|
| Material You | unavailable | row reads "Needs Android 12 or newer" |
| Window blur | unavailable | quick panel falls back to an opaque surface |
| Privacy dashboard | older `getInstalledPackages` | reads all 36 packages |

The soak passes 27 of 27 there across three font scales and dark mode, and
monkey runs without a crash.

### Tests, and the two things they caught

Added:
- **55 JVM unit tests** — the first automated tests in this project.
  `FocusTest` covers the clock arithmetic, `SettingsBackupTest` the backup
  codec against files it should not trust, `SearchAndLayoutTest` drawer
  ranking and the layout codec. They need no device and run in about a
  second.
- **`tools/soak.sh`**, which walks every screen at three font scales and in
  dark mode. It clears the crash buffer before each step and checks it
  after, so a crash names the step that caused it, and each step asserts
  what should be on screen — an earlier version only checked "no crash,
  still running", which passes just as well when a tap misses and the app
  sits on the home screen for twenty steps.

Fixed:
- **The baseline profile was recorded against the onboarding screen.** It
  held 45 rules mentioning `Onboarding` and none at all for `AppDrawer`,
  `HomeScreen` or `SettingsScreen`: a fresh install has an empty DataStore,
  so the app draws the welcome flow, and the generator walked that instead
  of the launcher. Re-recorded, it is 20,757 rules with 63 for the drawer,
  61 for the home screen and 58 for settings, and 1 for onboarding. The
  earlier claim that the profile was "verified" checked that it reached the
  APK and never checked what was in it.
- **The app told users it declares two permissions.** The merged release
  manifest has three: AndroidX core generates
  `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` for runtime-registered
  receivers. It is `protectionLevel="signature"`, so only something signed
  with the same key could hold it and its only job is to stop other apps
  broadcasting to this one — but the onboarding page that says it is headed
  "a promise is only worth what can be checked", so it now says three and
  explains the third.
- The two focus-stepper labels were `%d minutes` strings rather than
  plurals, which lint flagged and which would be wrong in most languages.
- `README.md` claimed 2.0 MB in one place and 2.1 MB in another, and its
  Status paragraph still described the app as it stood four features ago.

Notes:
- `minutesUntilChange` has a guard against returning zero that **cannot
  currently fire**: standing on the window's start is inside it, standing
  on its end is outside it, so neither edge is ever the target. The comment
  claimed the guard was doing real work; it now says what it is, and
  `FocusTest` fails if a change ever makes it reachable.
- Lint is at 0 errors and 1 warning — the deliberate `targetSdk 35` hold.

### Performance pass, release signing and accessibility fixes

Added:
- **Baseline profile.** A test-only `:baselineprofile` Macrobenchmark module
  records a profile from a scripted journey through startup, the drawer and
  Settings. None of its dependencies reach the app — it builds a separate
  test APK — and the recorded profile is verified to survive R8 and arrive in
  the shipped APK as `assets/dexopt/baseline.prof`.
- **R8 with resource shrinking** on release builds.
- **Release signing** read from a gitignored `keystore.properties`. The build
  still succeeds unsigned when the file is absent, so a fresh clone needs no
  key.
- **Startup and frame-timing benchmarks** (`StartupBenchmark`,
  `FrameTimingBenchmark`) alongside the profile generator.

Changed:
- **Every state class that crosses into composition is now `@Immutable`.**
  They all hold a `List` or a `Set`, which Compose treats as unstable, so the
  composables taking them could never skip — changing the battery percentage
  in the glance line recomposed the whole home grid. All of them are `val`s
  over content that is never mutated, so the annotation is a promise the code
  keeps.

Fixed:
- **Every switch in Settings was announced twice by TalkBack.** The row was
  clickable with `Role.Button` while containing an interactive `Switch`, so a
  screen reader met each setting first as a stateless button and then again as
  the switch. It is now one toggleable row with `Role.Switch` holding a
  non-interactive switch, which also makes the whole row the target.
- The **remove badge on home icons** was a 20 dp touch target, well under the
  48 dp minimum. It only appears in edit mode, where tapping an icon does
  nothing, so enlarging it steals no other gesture.
- The **minimal home's empty-state text carried no shadow** while every other
  line on that screen did, leaving the faintest text on the screen unreadable
  over a pale wallpaper. The shadow is now shared rather than written out per
  call site, which is how the omission happened.
- The **onboarding page dots** were four unlabelled nodes to a screen reader;
  they now announce "Page 3 of 4".
- **`baselineprofile/build` was being committed** — 977 files and 255 MB of
  build intermediates, including four copies of the 12–14 MB trace processor
  binaries. `.gitignore` listed `/build` and `/app/build`, neither of which
  covers a new module, so it is now `build/` for every module.

Notes:
- **The signed release APK is 2.1 MB** against a 10 MB target. The bundled
  Inter typeface is 880 KB of that, 42% — the obvious thing to cut if size
  ever matters more than it does now.
- **The signing key is a development key.** The README says plainly not to
  publish anything signed with it.
- **There is still no trustworthy startup number, and the first set was
  measuring the wrong screen.** An earlier pass reported 2097 ms uncompiled
  against 2306 ms with the profile. Both figures were the time to draw the
  onboarding welcome page: the benchmark installs the app fresh, a fresh
  install has an empty DataStore, and an empty DataStore means
  `MainActivity` shows the welcome flow rather than the home screen.
  `StartupBenchmark` now walks past onboarding in `@Before` and force-stops
  the app so the first measured iteration is genuinely cold, but the
  corrected run has not yet produced usable figures.
- Independently of that, this emulator cannot give trustworthy numbers: it
  renders in software through SwiftShader on the machine running the build,
  and Macrobenchmark only runs on it at all with `suppressErrors=EMULATOR`.
  So the profile is recorded, verified to reach the APK, and **unmeasured** —
  confirming it is worth having needs the 4 GB Samsung.

### Onboarding, settings sections and local backup

Added:
- **First-run onboarding**, four pages: welcome, set as default launcher,
  choose a theme and accent, and what the app does not do. Paged with a
  parallax and fade, with Skip on every page but the last.
- **Settings regrouped into six sections** — Appearance, Home, Drawer,
  Gestures, Privacy, About — replacing the older Appearance / Layout /
  Search / Focus / Privacy / Gestures / System split.
- **Live previews.** Changing icon size or the column count shows a card of
  your own apps at that size and count; changing the font shows a
  clock-sized sample. The card draws from the icon cache the drawer has
  already filled, so it costs no extra decoding.
- **Backup and restore** to a local JSON file through Android's file
  picker. No cloud, no account, and no storage permission.
- An **About** section: version, a licence summary, and the two file
  actions.

Notes:
- **Onboarding is never a gate.** No page asks for a permission, the
  home-app page can be walked past, and page three's controls are the real
  settings rather than copies, so they apply as you tap and skipping keeps
  what you chose. A launcher is the home screen; a flow you cannot leave
  would leave you with no phone.
- **Whether onboarding is done is read through its own flow**, which is
  null until the stored file has been read. Every other preference has a
  default that is safe to show for those few milliseconds; this one does
  not, and defaulting to "not onboarded" would flash the welcome screen at
  every cold start.
- **The backup leaves out launch counts, any running focus session, and the
  onboarding flag**, and the screens say so. See the README for why each.
- Restore is all-or-nothing in a single `edit`, so a failure cannot leave
  the launcher holding half of two different configurations. A file that is
  not a backup is reported differently from one that could not be read: the
  first is the wrong file, the second is the system's problem, and one
  message for both would leave you unable to tell which.
- The backup file is dated from the phone's clock rather than UTC, so a
  backup made after midnight is not labelled with yesterday. The `savedAt`
  field inside it stays UTC.

### Focus mode and minimal mode

Added:
- **Focus mode.** Choose the apps that stay visible; everything else
  leaves the home screen, the dock, the drawer and the search results
  until focus ends. Start it by hand for 30 minutes, an hour, two,
  four, or open-ended, or give it a daily window — which may run
  overnight, as the 22:00–07:00 default does.
- A calm theme while it runs: the accent is drained towards grey, app
  icons are desaturated to a trace of their hue, and a veil settles
  over the wallpaper.
- A banner at the top of the drawer saying how many apps are set
  aside, carrying **End focus**.
- **Minimal mode.** A text-only home screen: app names in large type,
  no icons, no grid, no dock. Folders are flattened and dock apps join
  the list, so nothing becomes unreachable.

Notes:
- **No background work, and no alarm.** Focus is not an event with a
  timer behind it; it is a question about the clock, answered whenever
  something asks. The only timer is a coroutine `delay` — it cannot
  wake a sleeping phone — and it sleeps until the next moment the
  answer could change: the end of a timed session, the end of a
  snooze, or the next edge of the schedule. It exists purely so a
  screen someone is looking at redraws at the right minute.
  `SCHEDULE_EXACT_ALARM` is not declared and no new permission was
  added.
- **That sleep is capped at an hour**, so during a nine-hour overnight
  window it wakes hourly rather than once. The cap is a backstop: the
  phone's clock can jump for a time zone or a daylight-saving change,
  and a single long sleep computed before the jump would leave the
  screen showing a stale answer. Coming back to the foreground
  recomputes it too, which is what covers a phone that dozed straight
  through the boundary.
- **Ending a scheduled session snoozes the schedule** until that
  window's own end. Without it, "end focus" inside a window would be
  undone a second later by the schedule that started it, and the only
  way out would be switching the whole schedule off.
- **Focus hides, it does not block**, and the screen says so: a
  set-aside app is still installed, still running, and still reachable
  from a notification, a link or another launcher. The launcher's own
  screens are never hidden, so focus can always be switched off.
- Starting focus with no apps allowed is refused rather than obeyed —
  it would empty the home screen and the drawer at once, which reads
  as a crash, not a feature.
- The theme swap is instant while the wallpaper veil fades. Animating
  the colour scheme would rebuild it every frame, and a new
  `ColorScheme` invalidates everything in the tree that reads a
  colour; the veil is one rectangle.
- The wallpaper itself is veiled rather than desaturated. Greying it
  would mean reading the wallpaper bitmap, which costs a permission
  this app does not take — the same reason panels are opaque rather
  than frosted.
- Material You is set aside while focus runs, for the same reason:
  wallpaper-derived colours can be as saturated as the wallpaper is.
- The schedule's times step in quarter hours rather than opening a
  time picker. Material 3's `TimePicker` is still experimental API,
  and the platform's `TimePickerDialog` arrives wearing the system's
  theme rather than this one.
- Both home screens now share one `homeGestures` modifier. A second
  copy would have drifted from the first the moment either was fixed.

Fixed:
- **The minimal home swallowed its own gestures.** Its list of names
  scrolled unconditionally, and a scrollable claims a drag before the
  screen behind it sees one — so swiping up never opened the drawer
  and swiping down never reached the notification shade, even with
  three names and nothing to scroll. A swallowed swipe up from the
  bottom then fell through to Android's navigation gesture, so the
  launcher looked like it had shoved itself aside to show Recents.
  Scrolling is now enabled only when the names really overflow, which
  is the guard the ordinary home screen already had.
- App names in minimal mode carry a heavier text shadow than the
  clock. A name in headline weight has far less ink than a 64sp time,
  and over the pale part of a wallpaper the thin strokes went first.

### Quick controls panel

Added:
- A side sheet of eight tiles, pulled in from the right by swiping
  right-to-left anywhere on the home screen, or opened from Settings.
  It follows the finger and springs to open or closed, like the drawer,
  and the home screen behind it is genuinely blurred.
- Tiles for Wi‑Fi, Bluetooth, Flashlight, Location, Do Not Disturb,
  Auto-rotate, Private DNS and Lock down, each showing its current
  state.
- A brightness slider with a haptic tick every twentieth of the bar.

Notes:
- **One tile is a real switch.** `CameraManager.setTorchMode` needs no
  permission, so the flashlight toggles. Everything else is closed to
  ordinary apps: `setWifiEnabled` has been a no-op since Android 10,
  `BluetoothAdapter.enable` since 13, and Do Not Disturb, auto-rotate
  and Private DNS each need a permission or special access this
  launcher does not take. Those tiles read the state, show it, carry a
  corner arrow, and open Android's own screen. A line under the grid
  says so in as many words.
- **Wi‑Fi opens a Settings panel**, not a whole screen: on Android 10
  and later `Settings.Panel.ACTION_WIFI` is an inline slice with a
  working switch on it.
- **State is read without permissions.** Wi‑Fi, Bluetooth, Do Not
  Disturb, rotation and Private DNS come from the Settings provider,
  which any app may read; location uses
  `LocationManager.isLocationEnabled`, which needs none. Reading
  Bluetooth through `BluetoothAdapter` would have cost BLUETOOTH_CONNECT
  on Android 12 and later, and reading the live Private DNS state would
  have cost ACCESS_NETWORK_STATE, which this app strips from the merged
  manifest on purpose.
- **Lock down is not Android's Lockdown**, and the panel says so.
  Android's own, which also disables fingerprint unlock until the next
  PIN entry, lives in the power menu and cannot be started by an app.
  This tile locks the screen, through the same optional accessibility
  service as double-tap-to-lock.
- The panel is opened by a right-to-left drag anywhere rather than one
  from the right edge. On a phone with gesture navigation that edge
  belongs to the system Back gesture, and competing with it is a fight
  this would lose.
- Observers are registered while the panel is on screen and torn down
  when it leaves, so a panel nobody is looking at costs nothing.

Changed:
- The manifest declares a second permission, `WRITE_SETTINGS`, for the
  brightness slider. It is an app-op permission: declaring it grants
  nothing, no dialog can grant it, and the user has to allow it in
  Android's own "Modify system settings" screen. Until then the slider
  is disabled and shows that screen one tap away. Android gives an
  ordinary app no other way to change screen brightness, and nothing
  else in the launcher uses it.
- `DrawerSwipe` became `SheetSwipe`, with `heightPx` as `extentPx`. It
  now drives two sheets on two axes and knew nothing about direction
  anyway.

Fixed:
- The panel's travel distance was set from inside the block that
  composes it, which only runs once it is already sliding. With the
  default extent of one pixel, the first pixel of a drag slammed it
  fully open.
- The brightness slider wrote to the Settings provider on every frame
  of a drag. Writes are throttled now, with the final value written
  when the finger lifts so the throttle cannot swallow where the drag
  ended.
- Every tile read "Unknown" for the first frames of a drag, because
  nothing had read the state until the panel composed. The read now
  happens when the drag starts.
- The "opens Android settings" marker was a chevron rotated 135
  degrees, which read as a stray bracket rather than an arrow.
- Private DNS showed "Unknown" when the setting had never been touched.
  An absent key means the platform default, which has been
  opportunistic since Android 9, so it now reads "Automatic".

### Privacy dashboard

Added:
- A Privacy screen reached from Settings, with a privacy score in an
  animated ring and a plain-words breakdown of what came off it and
  why.
- A card per permission group -- Location, Microphone, SMS, Camera,
  Contacts, Phone, Files -- each expanding to the apps that hold it,
  with the icon, the app name and a tap through to Android's App info.
- The launcher lists itself among them. A privacy dashboard that
  quietly leaves out the app showing it has answered the wrong
  question.
- A statement on the screen that everything there is worked out on the
  phone and nothing is sent anywhere.

Notes:
- **The score is scored on apps you installed.** Preinstalled apps are
  listed under their group but not counted: you cannot remove them, and
  every phone of a given model ships the same set, so counting them
  would measure the manufacturer rather than anything you did.
- **"Holds" means granted, not declared.** Read from PackageInfo's
  requested-permission flags, so an app that asks for the camera in its
  manifest and has never been given it does not appear.
- **The list is what this launcher can see.** It does not hold
  QUERY_ALL_PACKAGES, which is a sensitive permission that would
  undercut the whole app, so background-only packages with no launcher
  entry are invisible to it. The screen says so rather than implying
  the list is exhaustive.
- **Recent permission use cannot be shown inline.** Android 12 and
  later do keep that timeline, but reading it needs GET_APP_OPS_STATS,
  which is signature|privileged. The screen says this plainly and
  offers to open Android's own privacy settings, where the timeline
  lives. (In the ROM this launcher is eventually for, a privileged
  system build could hold that permission.)
- **"One tap to the permission page" is App info, not the permission
  list.** `MANAGE_APP_PERMISSIONS` was tried first and is refused for
  an ordinary app -- it requires GRANT_RUNTIME_PERMISSIONS. The row is
  labelled App info for that reason, and the screen explains that
  permissions are one tap further in.
- The sweep is an `Animatable` read inside the Canvas draw lambda, so
  it animates without recomposing, and the number beside it goes
  through `derivedStateOf` so only the rounded integer invalidates it.
- Nothing is scanned until the screen is opened. Walking every visible
  package's permission list would otherwise be charged to every cold
  start, for a screen most people open rarely.

Fixed:
- The first scoring model was so-much-per-app with a cap, and the
  arithmetic made the feature useless: an ordinary phone with a
  browser, a messenger, a camera app and a maps app ran past every cap
  and floored at zero. Each group's cost now rises towards a weight and
  levels off. On sample data a light phone scores about 79, a typical
  one 57 and a heavily permissioned one 44.
- `resolveActivity` returned null for the permission controller, which
  has no launcher icon and so is hidden by package visibility, meaning
  a "check it resolves first" guard sent every tap to the fallback. The
  same class of bug as the default-home check earlier: visibility
  filters queries, not implicit starts.
- The breakdown read "Looked at 1 apps", and said "0 more came with the
  phone; those are listed but not scored" when none were.
- The recent-use card claimed to open Android's Privacy dashboard. On
  Android 14 and later the same action opens Safety Center instead, so
  it now says privacy settings.

### Hidden apps behind the phone's own lock

Added:
- Opening the hidden apps list can require the device credential:
  fingerprint or face where they are enrolled, PIN, pattern or
  password otherwise. On by default, with a switch in Settings.
- A phone with no screen lock says so on that row rather than leaving a
  switch that silently does nothing.

Notes:
- Built on `KeyguardManager.createConfirmDeviceCredentialIntent` rather
  than `androidx.biometric`. The library is the usual answer, but it is
  a dependency of some size for one dialog in a project meant to stay
  small. The platform call needs no permission -- not even
  USE_BIOMETRIC -- and shows the same system sheet, with the system
  rather than this app deciding which factor to offer.
- With no screen lock set the screen opens straight away. Refusing
  would lock someone out of their own setting with no way back.

### Search beyond apps: settings screens and contacts

Added:
- Drawer search also finds system settings screens — Wi‑Fi, Display,
  Battery, Accessibility and so on. The table is deliberately
  optimistic and every entry is resolve-checked once on a background
  thread, so screens this device does not have are simply dropped
  rather than offering a result that opens nothing.
- Optional contact search, off by default. While searching, the drawer
  offers a "Search contacts too" row; tapping it is the only thing in
  this app that ever requests a permission. There is also a switch in
  Settings, and an App info row for taking the grant back.

Changed:
- The manifest now declares one permission, `READ_CONTACTS`. It
  declared none before. Declaring it is what makes the runtime request
  possible and grants nothing on its own. The README has the full
  account of what is read; in short: matching happens inside the
  contacts provider via the filter URI, only a display name and lookup
  key are read, and nothing is cached, counted or written anywhere.
  There is still no network permission of any kind.

Notes:
- The contact query is debounced with a cancellable job rather than
  `Flow.debounce`, so the whole path stays on stable coroutine API and
  the guards run before anything is scheduled: with the feature off, or
  the permission not granted, the provider is never touched.
- Settings labels are this app's own strings, not the system's. Asking
  Android means `loadLabel` on each activity, and many settings
  activities report a generic "Settings" — which would give a result
  list of sixteen identical rows.

Fixed:
- A single refusal was treated as final, so the offer row immediately
  turned into "go to App info" even though Android was still willing to
  ask. It now reads `shouldShowRequestPermissionRationale` after the
  dialog closes, which is how the system signals it has stopped asking.

### Drawer search and browsing

Added:
- Fuzzy search. Results reorder on every keystroke, ranked in tiers: the
  whole name, then a prefix, then a word start ("maps" finds Google
  Maps), then initials ("wv" finds WebView Browser Tester), then
  anywhere in the name, then the letters in order with gaps. Within a
  tier the shorter name wins, which is almost always the one meant.
- Names are split on separators and on a lower-to-upper transition, so
  "WebView Browser" has the initials w, v, b.
- An alphabetical grid with a heading per letter and a fast-scroll
  letter bar down the right edge. Dragging the bar jumps between
  sections, with a bubble beside the finger showing where it is.
- An optional "most used" row at the top of the drawer, counted on the
  phone and stored only there. Settings gains a switch for it and a
  Clear usage history action; turning the row off erases the counts too.

Notes:
- Headings and the letter bar only appear once there are at least 24
  apps. A heading takes a whole line, so sectioning a short list spreads
  a screenful of icons over several screens of empty space.
- The counts live outside the main settings flow. They change on every
  launch, and folding them in would make the home grid and the dock
  recompute each time as well. The stored table is capped, so a phone
  with 300 apps does not accumulate a 300-row usage record.
- Search terms (lowercased name, words, initials) are computed once when
  the app list loads, not per keystroke.

Fixed:
- The letter bar's letters were laid out as a clump in the middle while
  its hit test spanned the full height, so the letter under the finger
  was not the one it jumped to.

### Drawer that follows the finger

Added:
- The app drawer tracks the finger as you swipe up, and springs to open
  or closed when you let go. Dragging the handle at the top pulls it
  back down the same way. Either gesture commits once the sheet has
  moved about a third of the way from where the drag started, or
  immediately on a fast flick.
- The keyboard opens as soon as the drawer settles, with a switch in
  Settings under Search to turn that off.

Notes:
- The sheet's position is an `Animatable` read only inside
  `graphicsLayer` blocks, which re-run without recomposing. Dragging the
  drawer therefore costs no recomposition at all.
- The drawer layer sets no alpha. A translucent full-screen layer forces
  an offscreen buffer every frame, which is the sort of cost a 4 GB
  phone cannot absorb mid-drag. It is opaque and simply slides; the home
  screen underneath is the thing that fades.
- Drag-to-close is on the handle only. A downward drag anywhere else
  belongs to the app grid's scroll, and whichever won would be a coin
  toss.
- With the keyboard open, the first Back press dismisses the keyboard
  and the second closes the drawer. That is Android's own behaviour and
  it cannot be intercepted; Home, or a pull down on the handle, still
  closes the drawer in one go.

Fixed:
- A released drag was judged against a stale sheet position. Writing to
  an `Animatable` suspends on its mutex, so the last frame of a drag
  could still be queued when the finger lifted. The drag fraction is now
  also kept in a plain field written on the spot.
- The commit threshold was measured from the bottom of the screen rather
  than from where the drag started, so closing needed a drag seven
  tenths of the way down while opening needed three.
- The keyboard stayed on top of the sheet while it was being pulled
  away. It now hides as the close drag starts.

### Home screen gestures

Added:
- Swipe down for the notification shade, and double-tap to lock the
  screen. Both need an optional accessibility service, because Android
  gives an ordinary app no other route to either.
- Pinch in on the home screen opens settings.
- The accessibility service is configured as narrowly as the platform
  allows: no `accessibilityEventTypes` at all (the attribute is a flags
  value with no "none" constant, so omitting it is how you subscribe to
  zero events), and `canRetrieveWindowContent="false"`, so the system
  withholds the ability to read screens rather than us promising not to.
  It handles no events and performs exactly two global actions.
- Using a gesture while the service is off opens an explainer stating
  what it can and cannot do, rather than failing silently. A gesture that
  does nothing with no feedback reads as a bug.
- Settings gains a Gestures group showing whether the service is on.

Notes:
- The pinch detector is hand-rolled on `awaitEachGesture` rather than
  `detectTransformGestures`, which also reports single-finger pans and
  would fight the swipe-up-to-open-drawer detector. It observes only: it
  consumes nothing and ignores anything that is not two fingers.

Fixed:
- A folder's remove badge announced "Remove from home screen" when it
  actually takes the app out of the folder.
- `dock_full` is now a plural.

Verified on the emulator:
- Swipe down opens the notification shade with the service enabled, and
  opens the explainer with it disabled.
- Double-tap locks the screen (`mWakefulness` goes to Asleep).
- No crashes; lint 0 errors, 1 warning.

Not verified: pinch-to-open-settings. Producing a genuine two-finger
pinch over adb is not practical, so the code path is unexercised and
needs a real finger on a real screen.

### Bug hunt and usability pass

Fixed — layout:
- **The Apps button and settings gear could be pushed off the screen.**
  With eleven apps on the home screen at a large Display size, the grid
  grew past the available height and the bottom bar went with it, leaving
  the swipe-up gesture as the only way into the drawer — which a new user
  has no reason to know exists. The clock and grid now live in a
  scrollable area and the bottom bar never scrolls.
- Making that area scrollable then swallowed swipe-up-to-open-drawer,
  because a `verticalScroll` consumes vertical drags even with nothing to
  scroll. Scrolling is now enabled only when the content really overflows.
- The clock wrapped "3:40 PM" onto two lines, and the date onto three, at
  large Display sizes. It now steps down through the display scale on
  narrow or short windows instead of wrapping.
- Landscape sliced the second row of apps in half. Column counts now
  double in a wide window (capped at eight) and the clock goes compact on
  a short one.
- Home cell height ignored the user's font scale, so two-line app names
  were clipped at larger text sizes.
- Window size is read from `LocalWindowInfo` rather than
  `Configuration.screenWidthDp`, which describes the screen rather than
  the window and over-reports in split-screen.

Fixed — behaviour:
- The clock could show a stale time after the phone had been dozing: the
  minute ticker does not run on schedule in app standby, so unlocking
  could show the wrong time for up to a minute on the one screen whose
  job is telling you the time. It now re-reads on every resume.
- Changing the phone's 12/24-hour setting had no effect until the
  launcher's process restarted. Also re-read on resume.
- Back in the drawer threw away the whole drawer even with a search
  typed. It now clears the search first and closes on the second press.
- The drawer's empty state always read "No apps match that search", even
  with every app hidden and no search typed.

Changed:
- Long-press on a home icon now carries straight into a drag. Entering
  edit mode and dragging used to be two separate gestures: long-press,
  release, then long-press again to move anything.

Verified on the emulator (paths that existed but had never been run):
- The ✕ badge removes an app and the grid reflows.
- Hiding an app that is on the home screen removes it from both.
- Tap still launches; long-press-and-drag reorders in one motion.
- Back clears a search, then closes the drawer, then exits edit mode.
- Portrait and landscape both fit with no clipping; no crashes.

### Design system

Added `designsystem/`, and refactored every existing screen onto it.

- **Spacing**: one 4dp scale (4/8/12/16/24/32/48) plus a 48dp minimum touch
  target. No ad-hoc padding values remain in the UI code.
- **Typography**: Inter 4.1 bundled as a single variable font (859 KB,
  SIL OFL 1.1, licence in `licenses/`). One file covers every weight;
  three static weights would have been ~1.2 MB and still fixed the choice
  at three. Display sizes use Light with negative tracking.
- **Colour**: warm off-white and near-black neutrals, never pure #FFF or
  #000. Six accents, each with a separate tone for light and dark.
- **Shapes**: 8dp controls, 12dp cards, 20dp sheets, fully round chips.
- **Motion**: 150/250/350ms with a standard curve, springs for anything
  following a finger. Panels slide and fade; chips animate their selection.
- **Haptics**: tick, long-press, drag start/end, via
  `View.performHapticFeedback` — which needs no permission, unlike driving
  the Vibrator directly.
- **Surfaces**: four tonal layers. Depth is never a drop shadow.
- **Material You** as an option (Android 12+), off by default. The manual
  accent row greys out while it is on rather than disappearing.
- **Design preview screen** at Settings → Design preview, rendering every
  token: type scale, colour roles, tonal layers, controls, the spacing
  scale and a motion demo.

Fixed during the build:
- Five font chips in an equal-weight Row squeezed "Monospace" until it
  broke mid-word. `ChoiceRow` now uses `FlowRow`, so chips size to their
  text and wrap.

Known and deliberate — panels are opaque, not frosted glass:
- `Window.setBackgroundBlurRadius` was implemented and tested on a device.
  It blurs the windows *behind* yours, and a launcher declares
  `windowShowWallpaper`, so the wallpaper belongs to our own window and
  there is nothing behind to blur. It produced a translucent panel over a
  sharp wallpaper, which is worse than either extreme.
- Blurring the wallpaper ourselves needs `WallpaperManager.getDrawable()`
  and a storage permission, which this app does not request.
- `Modifier.blurBehind()` remains in `Surfaces.kt` and does work, because
  it blurs our own composables. It is intended for in-app overlays such as
  an open folder.

Verified on the emulator:
- No crashes. Lint 0 errors, 1 warning (the deliberate targetSdk hold).
- Inter renders in both themes; accent swatches switch to their dark tones
  under the dark theme; panels are readable over the wallpaper.
- Release APK 1.4 MB -> 1.9 MB. The font is 859 KB on disk; compression and
  R8 bring the delta to about 500 KB. Target for the performance pass is
  under 10 MB.

### Feature 2 — Customisation

Added:
- A real settings screen replacing the one-off dialog, with three groups:
  Appearance, Layout and System.
- Theme, plus six accent colours and four fonts. Each accent carries two
  tones: a hue readable as text on white is too dim on near-black, so
  light and dark schemes use different values of the same colour.
- Fonts are families Android already ships (default, sans, serif, mono),
  so nothing is bundled and the APK does not grow.
- Icon size (small / medium / large) and column count (3-6), set
  separately for the drawer and the home screen.
- Home screen app grid. Long-press to enter edit mode, then drag to
  rearrange or tap the ✕ badge to remove.
- Add to / remove from home screen, and hide from drawer, in the drawer's
  long-press menu.
- Hidden apps screen with a switch per app, stating plainly that hiding is
  cosmetic: the app stays installed, keeps running and keeps its
  permissions.
- Wallpaper: opens Android's own picker via ACTION_SET_WALLPAPER. The
  launcher deliberately does not browse images itself, which would need a
  storage permission and break the zero-permission property.
- Settings that reference uninstalled apps are pruned automatically.

Notes on the implementation:
- All preferences are read as one `LauncherSettings` object from a single
  Flow, so a change recomposes once rather than through eight separate
  subscriptions.
- The home grid is a plain Column of Rows, not a LazyVerticalGrid. Every
  cell is on screen and at a known position, which is what drag-to-reorder
  needs; computing it against a lazy layout's scroll state would be more
  code for no benefit at this size.
- Home apps are stored newline-joined rather than as a preference Set,
  because order matters and DataStore sets are unordered.
- `AppCell` takes an `interactive` flag. While reordering, a
  `combinedClickable` on the cell would claim the pointer stream and the
  parent's drag detector would never see the gesture.

Fixed during the build:
- `setHomeEditMode` clashed with the JVM setter Kotlin generates for the
  `homeEditMode` property; renamed to `setHomeEditing`.
- `tools/emulator.sh` backgrounded the emulator without `nohup`, so it
  could be killed when the script exited.
- The home remove badge and the drawer's close handle had no content
  description, so a screen reader announced nothing for either.

Verified on the emulator:
- No crashes across the whole session.
- Four apps added to the home screen, then the first dragged to last:
  order became Camera, Contacts, Files, Clock as expected.
- Accent change recolours the whole UI live; icon size and 5-column drawer
  both applied.
- Hiding Calendar removed it from the drawer and the count fell to 11.
- Serif font, violet accent, large icons and the reordered home apps all
  survived a `force-stop` and relaunch.

### First run on a device (Android 15 / API 35 emulator, AOSP arm64)

Fixed:
- `isDefaultHome()` always returned true, so the "Set as default home app"
  button was hidden on first run -- the one button a new launcher needs.
  Cause: package visibility. The manifest declared a `<queries>` entry for
  LAUNCHER but not HOME, so Android hid every other launcher from this app
  and the only visible answer to "which app is home?" was ourselves. Now
  asks `RoleManager.isRoleHeld(ROLE_HOME)` on API 29+, with the
  resolveActivity path kept as the 26-28 fallback and a `<queries>` entry
  for HOME added so that fallback is correct too.
- The drawer was 97% opaque, so the home screen's clock and Apps button
  ghosted through it and read as a rendering fault. Now fully opaque.
- Status and navigation bar icons did not follow the launcher's own theme.
  Choosing Dark while the system was Light left dark icons on a dark bar,
  effectively invisible. Both bars are now transparent, with icon colour
  driven by what is actually on screen: the themed surface when the drawer
  or settings is open, and the wallpaper (always light icons, matching the
  white clock) on the bare home screen. `isNavigationBarContrastEnforced`
  is off on API 29+, which removes the grey band under the drawer.

Verified on the emulator:
- Launches without crashing; no FATAL or ANR in logcat across the session.
- Swipe-up opens the drawer; all 12 installed apps listed with correct
  icons and labels.
- Search "se" narrows to 3 apps and the counter reads "3 of 12".
- With the keyboard open the grid sits correctly below the search field,
  confirming the double-IME-padding fix.
- Long-press shows the app's package, App info, Uninstall in red, and
  Cancel as the only button.
- Theme switches light/dark and survives being reselected.
- Set as home app via RoleManager; pressing Home returns to the launcher.
- Back on the bare home screen leaves the same ActivityRecord resumed,
  confirming the Activity is not being finished and relaunched.
- Tapping an app launches it; Home returns with the drawer closed.

### Layout and safety review (no device yet)

Fixed:
- The drawer applied both `safeDrawingPadding()` and `imePadding()`.
  `safeDrawing` is the union of system bars, display cutout *and* the IME,
  so opening the keyboard shifted the content up by the keyboard height
  twice, hiding the top of the app grid.
- The app grid and the empty-state box used `fillMaxSize()` as children of
  a Column. A Column child asks for the full parent height that way rather
  than the space left after the search field, pushing the bottom of the
  list off screen. Both now use `weight(1f)`.
- "Uninstall" sat in `AlertDialog`'s `dismissButton` slot -- bottom-left,
  where Cancel normally is. Someone dismissing the long-press dialog out of
  habit would have started an uninstall. Android shows its own confirmation
  so nothing could be lost silently, but a destructive action does not
  belong where muscle memory expects an escape. Both actions are now rows
  in the dialog body and the only button is Cancel.

### Toolchain modernisation

Changed:
- AGP 8.7.3 -> 9.5.1, Gradle 8.11.1 -> 9.9.0, Kotlin 2.0.21 -> 2.4.21,
  Compose BOM 2024.12.01 -> 2026.09.00, core-ktx 1.15.0 -> 1.19.1,
  Lifecycle 2.8.7 -> 2.11.0, Activity Compose 1.9.3 -> 1.13.0,
  DataStore 1.1.1 -> 1.3.0.
- compileSdk 35 -> 37, required by core-ktx 1.19.1. targetSdk stays at 35:
  it controls runtime behaviour and is a separate decision, to be taken
  after the launcher has been tested on a phone.
- Removed the `org.jetbrains.kotlin.android` plugin. Since AGP 9.0 the
  Android plugin compiles Kotlin itself and applying both is an error.
- `android { kotlinOptions { } }` no longer exists; JVM target now set via
  the top-level `kotlin { compilerOptions { } }` extension.
- Replaced `androidx.compose.material:material-icons-core` with three
  in-repo `ImageVector`s (`ui/components/LauncherIcons.kt`). That artifact
  is frozen at 1.7.8 and is no longer a dependency of material3 1.4.0+.
  Path data is from Material Design Icons (Apache 2.0); each glyph was
  rendered and visually checked before being committed.
- Enabled Gradle's configuration cache.
- Declared `android:roundIcon`, which was defined but never referenced.

Fixed:
- `ViewModel.onCleared` no longer calls its empty super.
- Removed the unused `drawer_close` string.

Known:
- Lint's `ObsoleteSdkInt` advice to rename `res/mipmap-anydpi-v26` to
  `res/mipmap-anydpi` is wrong: AAPT2 then cannot resolve
  `@mipmap/ic_launcher` at all. Verified on a fully clean build and
  suppressed with an explanatory note in `app/lint.xml`.

Verified:
- `assembleDebug`, `assembleRelease` and `lintDebug` all succeed.
- Lint: 0 errors, 1 warning (the deliberate targetSdk hold).
- Release APK 1.4 MB; still no Android permissions, still no `INTERNET`.

### Feature 1 — Basic launcher (builds clean, awaiting on-phone test)

Added:
- Gradle project: Kotlin 2.0.21, Jetpack Compose, minSdk 26, targetSdk 35.
- `MainActivity` registered with the `HOME` intent category, so Android can
  use it as the home screen.
- Home screen: large clock (follows the phone's 12/24-hour setting), date,
  swipe-up and button to open the app drawer, settings button.
- App drawer: searchable grid of all installed apps, backed by
  `LazyVerticalGrid` with icons loaded on demand and LRU-cached.
- Search matches substrings and initials (`gm` finds "Google Maps").
- Long-press an app for "App info" (system permission page) or "Uninstall".
- Light / dark / follow-system theme, stored locally with DataStore.
- Settings shortcut to Android's home-app picker, plus a note when Privacy
  Launcher is already the home app.
- Drawer refreshes automatically when apps are installed, removed or updated.
- Zero permissions requested. `INTERNET`, `ACCESS_NETWORK_STATE` and
  `AD_ID` are explicitly stripped from the merged manifest so no dependency
  can add them.
- Cloud backup and device transfer disabled, so settings never leave the phone.
- `tools/env.sh` to put the JDK, Android SDK and Gradle on `PATH`.
- Gradle wrapper committed (8.11.1).

Fixed:
- Back on a bare home screen no longer fell through to the Activity's
  default handler, which would `finish()` the home Activity and make the
  system relaunch it — a visible flicker. The handler now stays enabled
  and swallows the press.
- The drawer showed the total installed count while a search was
  narrowing the list; it now shows "3 of 182" when filtering.
- `app_count` is a proper `<plurals>` resource, so "1 app" reads
  correctly and other languages can supply their own forms.

Verified:
- `gradle assembleDebug` succeeds; debug APK is 9.0 MB.
- `aapt2 dump permissions` on the built APK reports no Android permissions.
  The only entry is `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, a
  signature-level permission AndroidX defines for the app's own broadcast
  receiver; it grants no access to anything outside the app.
- `INTERNET` appears nowhere in the APK's merged manifest.
- `gradle lintDebug`: zero errors. Remaining warnings are dependency
  version-staleness notices, recorded below.
- `gradle assembleRelease` succeeds: R8 and resource shrinking bring the
  APK down to 1.2 MB, so no missing keep rules.

Known:
- Dependency versions were roughly two years behind; resolved by the
  toolchain modernisation above.
- An app icon can stay stale in an already-scrolled drawer until the cell
  is recycled, because the icon cache is cleared but composed cells keep
  their bitmap.
