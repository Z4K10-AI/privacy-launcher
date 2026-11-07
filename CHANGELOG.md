# Changelog

All notable changes to Privacy Launcher. Newest first.

## [Unreleased]

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
