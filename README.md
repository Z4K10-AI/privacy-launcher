# Privacy Launcher

An Android home screen (launcher) built for privacy. It **cannot reach the
network**, keeps every setting in a file inside its own private directory, and
declares **two** Android permissions, neither of which does anything until you
switch on the feature that needs it.

- `READ_CONTACTS`, for the optional "search contacts" row in the app drawer.
  Off by default; nothing is requested or read until you turn it on yourself.
  [Contacts, in detail](#contacts-a-permission-you-switch-on).
- `WRITE_SETTINGS`, for the brightness slider in the quick controls panel. An
  app-op permission: declaring it grants nothing and no dialog can grant it —
  you allow it in Android's own "Modify system settings" screen, or the slider
  stays disabled.

Written in Kotlin and Jetpack Compose so it can later be built into a
LineageOS-based ROM as the default launcher.

Status: **home screen, app drawer, privacy dashboard with a tracker scan,
quick controls with a real lock-down, focus mode, a text-only minimal mode,
first-run onboarding, grouped settings with live previews, and local backup
and restore — all on a design system. It is for any phone on Android 8.0 or
later, not one model: see [Which phones it runs on](#which-phones-it-runs-on).
Verified on emulators only, at Android 11 and Android 15 and on seven
screen shapes; not yet tested on a physical phone.**

Built with current tooling as of November 2026: AGP 9.5.1, Kotlin 2.4.21,
Compose BOM 2026.09.00, Gradle 9.9.0, compileSdk 37.

Release APK: 2.1 MB.

**`INTERNET` appears nowhere in the merged release manifest** — checked
directly, not inferred from the source manifest. The merged manifest holds
three `uses-permission` entries rather than the two this file used to claim:

| Permission | Where from |
|---|---|
| `READ_CONTACTS` | ours, inert until contact search is switched on |
| `WRITE_SETTINGS` | ours, inert until the brightness slider is used |
| `com.privacylauncher.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | generated |

The third is not a capability the app gains. AndroidX core generates it when
a receiver is registered at runtime, declares it `android:protectionLevel="signature"`,
and has the app hold its own permission — so only something signed with the
same key could satisfy it, which means nothing else on the phone. Its whole
job is to stop other apps delivering broadcasts to ours. It reaches nothing
of yours, but it is in the manifest, and a privacy claim that quietly
rounds three down to two is not one worth making.

Check it yourself:

```bash
aapt2 dump permissions app/build/outputs/apk/release/app-release.apk
```

---

## Which phones it runs on

**Any phone running Android 8.0 or later.** It is not written for one phone
or one maker.

- **No maker-specific code.** Nothing in it asks what brand or model the
  phone is.
- **Nothing it insists on.** Its manifest requires no piece of hardware, so
  no phone is refused for lacking one. Where a phone has no camera flash the
  flashlight tile says so.
- **No Google Play Services.** It works the same on phones without them.
- **Any screen.** It lays itself out from 320 dp wide, the narrowest
  Android Go handsets, upwards. Icons are drawn at the size you chose, or
  at the largest that fits when the screen is too narrow for it. On a phone
  the home screen stays upright when the phone is turned; on a tablet or an
  opened folding phone it turns freely.
- **Either reading direction.** Right-to-left languages lay out mirrored and
  dragging follows the finger. The words themselves are in English only so
  far.
- **Light.** The release is a little over 2 MB and is measured against a
  4 GB phone.

The **Galaxy A30** comes up often in this project. It is not the phone the
launcher is for. It is the slowest phone the launcher is expected to feel
good on: one of the three test emulators copies its screen, memory and
Android version, so there is a fixed low bar to measure against.

### What that claim rests on

| What varies between phones | Checked how | Result |
|---|---|---|
| Android version | Every test on emulators at Android 11 and Android 15 | see [Tests](#tests) |
| Android 8, 9, 10, 12, 13, 14 and 16 | **Not run.** The build refuses any call a supported version does not have (lint's `NewApi`), and the six places the code branches on version are listed below | unproven on a device |
| Screen size and density | `tools/shapes.sh`: seven shapes from 480x854 to 1440x3120, one turned sideways, one a folding phone opened, each with every device test and a tour of the real app | all seven pass on Android 11 and on Android 15 |
| Makers' own versions of Android (One UI, MIUI, ColorOS and so on) | **Not run.** The emulators run plain Android | unproven |
| A real phone | **Not run** | unproven |

Where the code does something different by Android version:

| From | What changes | Below it |
|---|---|---|
| Android 9 | Double-tap to lock, through the optional gesture service | Not offered; Settings says the phone is too old for it. Lock down works on every version |
| Android 9 | Whether location is on is read from `LocationManager` | Read from the older setting |
| Android 10 | The Wi‑Fi tile opens Android's inline Wi‑Fi panel | Opens the Wi‑Fi settings screen |
| Android 10 | "Is this the home app?" asks the role manager | Asks the package manager |
| Android 12 | Wallpaper colours, blur behind panels, a link to Android's own privacy dashboard | None of the three exist; the dashboard says so |
| Android 13 | A newer form of the installed-apps query | The older form |

Only the first row changes what the user is told, and that choice has a
unit test (`GestureSummaryTest`) because no emulator here is old enough to
show it.

### What it does not do, that some launchers do

Worth knowing before choosing it as a daily home screen:

- **Apps in a work profile are not shown.** The launcher lists the apps of
  the person using the phone. A phone set up by an employer with a separate
  work profile keeps its work apps there, and they do not appear. This is
  the largest gap for "any phone", and it is the next thing to build.
- **No widgets**, no icon packs, no app shortcuts on long-press, and no
  notification dots. Dots would need access to your notifications, which
  this launcher does not ask for.
- **Android's "private space"** (Android 15) is not shown either.
- On some makers' phones, **swipe navigation works poorly with any launcher
  that is not the maker's own.** That is the phone's rule, not something a
  launcher can change. Three-button navigation is unaffected.

---

## What it does today

**Gestures**
- Swipe up for the app drawer, pinch in for settings.
- Swipe down for notifications and double-tap to lock, both via an
  optional accessibility service. Android gives an ordinary app no other
  way to do either. The service subscribes to no events and is refused the
  ability to read window content by the system; it performs two global
  actions and nothing else. The launcher works fully without it.

**Home screen**
- Clock (12 or 24 hour, following the phone's own setting) and date, over
  your wallpaper.
- A grid of apps and folders you choose. Long-press carries straight into a
  drag: rearrange by dropping near an edge, or make a folder by dropping onto
  the centre of another icon. Folders open as a rounded sheet over a blurred
  home screen.
- A dock of up to five apps. Carry a grid icon down onto it to keep the app
  there, or long-press a dock icon to slide it along the dock or carry it up
  onto the grid. While you are editing, each dock icon has the same ✕ as the
  grid's. An icon moves: it is never left in both places by a drag.
- An optional "at a glance" line under the clock: next alarm and battery,
  both read from the phone with no network and no permission. No weather,
  deliberately — it would need both.
- Tapping the clock opens your clock app.
- Swipe up, or tap **Apps**, for the drawer. The sheet follows your finger
  and springs to open or closed when you let go.

**App drawer**
- Every installed, non-hidden app in a grid, alphabetical with a heading per
  letter and a fast-scroll letter bar once there are enough apps to need one.
- Fuzzy search, reordered on every keystroke. Results are ranked in tiers:
  the whole name, a prefix (`tele` finds Telegram), a word start (`maps`
  finds Google Maps), initials (`gm` finds Google Maps, `wv` finds WebView
  Browser Tester), anywhere in the name, then the letters in order with gaps.
  Within a tier the shorter name wins.
- The keyboard opens with the drawer, with a switch in Settings to stop it.
- Search finds apps. Two more things are found only after you switch them
  on in Settings: Android's own **settings screens** (Wi‑Fi, Display,
  Battery and so on, a fixed list of shortcuts that reads nothing from the
  phone) and **contacts**. See
  [Contacts, in detail](#contacts-a-permission-you-switch-on).
- An optional **most used** row at the top, counted on the phone, stored only
  there, and erasable from Settings.
- Drag the handle at the top to pull the drawer back down.
- Hold an app and let go for its menu: add to / remove from home screen, add
  to dock, hide from drawer, **App info** (Android's own page, where you
  revoke permissions), uninstall.
- Hold an app and drag to carry it out of the drawer. The drawer closes and
  the icon stays under your finger: drop it on the grid, onto another icon
  to make a folder, or in the dock.

**Quick controls** (swipe right-to-left on the home screen)
- A side sheet of eight tiles over a blurred home screen, following the finger
  and springing to open or closed.
- **The flashlight is the only real switch**, because
  `CameraManager.setTorchMode` is the one of these Android still lets an
  ordinary app call. Wi‑Fi, Bluetooth, Location, Do Not Disturb, Auto-rotate
  and Private DNS show their state, carry a corner arrow, and open Android's
  own screen — Wi‑Fi opens an inline Settings *panel* with a working switch on
  it. A line under the grid says all of this plainly.
- **Lock down** shuts the phone so that only your PIN, pattern or password
  opens it. Fingerprint and face unlock stay off until you have typed it
  once: the point of a lock-down is that nobody can open the phone by
  pressing your finger to it.
  - It needs your OK once, on one of Android's own screens, to let the
    launcher lock the screen. Android calls that a "device admin app". The
    launcher asks for that one ability and no other; it cannot erase the
    phone, change your PIN or see anything, and Android enforces that.
  - While it is allowed, **Android will not uninstall the launcher**. That
    is Android's rule for every such app. A dialog says so before you
    agree, and Settings → Gestures → Lock down takes it back.
  - It is not all of Android's own Lockdown, the one in the power menu,
    which also hides notifications on the lock screen. An app cannot do
    that part, and the panel says so.
  - Double-tap to lock, which goes through the optional accessibility
    service, is the ordinary kind of lock: a fingerprint opens it again.
- A brightness slider with a haptic tick every twentieth of the bar. Disabled
  until you allow "Modify system settings"; the panel explains and links.
- Every state is read without a permission: the Settings provider for Wi‑Fi,
  Bluetooth, Do Not Disturb, rotation and Private DNS, and
  `LocationManager.isLocationEnabled` for location.

**Privacy dashboard** (Settings → Privacy)
- A privacy score in an animated ring, with the arithmetic shown: which
  permission group, how many apps, how much each took off.
- A card per group — Location, Microphone, SMS, Camera, Contacts, Phone,
  Files — expanding to the apps that hold it, with a tap through to Android's
  App info.
- The launcher lists itself. A privacy dashboard that leaves out the app
  showing it has answered the wrong question.
- **Trackers inside apps.** A scan looks inside each app's code for the
  advertising, analytics and profiling libraries that Exodus Privacy have
  catalogued: 428 of them in the list that ships. It runs on the phone,
  from a list inside the launcher, when you press the button and not
  before. Apps are read one at a time and each answer is remembered until
  the app is updated, so a second scan only reads what is new. Tap an app
  to see which trackers and to reach its App info page.
  - The card says what a result means. "Found" is the tracker's code being
    inside the app, not proof it runs or what it sends. "Not found" is
    nothing on the list being recognised: code can be disguised, and the
    list is a snapshot with its date shown.
  - An app that could not be read is counted as unread, never as clean.
    That is usual for apps that came with a phone, whose code is often
    stored where an ordinary app may not look.
- Everything is computed on the phone, which the screen says in so many words.
- What it deliberately does **not** do:
  - It does not ask for `QUERY_ALL_PACKAGES`, so it lists the apps it can see
    — the ones you can open — and says so rather than implying otherwise.
  - It cannot show the recent-use timeline inline. Android 12 and later keep
    one, but reading it needs `GET_APP_OPS_STATS`, which is
    `signature|privileged`. The screen says this and links to Android's own
    privacy settings. (A privileged build inside a ROM could hold it.)
  - It cannot open the permission list directly: `MANAGE_APP_PERMISSIONS`
    requires `GRANT_RUNTIME_PERMISSIONS`. App info is as close as an
    installable app gets.

**Focus mode** (Settings → Focus and quiet → Focus mode)
- Pick a handful of apps; everything else is set aside — gone from the home
  screen, the dock and the drawer, and out of search results too — until
  focus ends.
- Start it by hand for 30 minutes, an hour, two, four, or until you stop it,
  or set a daily window. The window may run overnight: 22:00 to 07:00 is the
  default.
- While it runs the interface drains of colour: the accent goes a warm grey,
  app icons are desaturated to a trace of their hue, and a veil settles over
  the wallpaper.
- The drawer carries a banner saying how many apps are set aside, with **End
  focus** on it. Ending a session the schedule started snoozes the schedule
  until that window's own end, so "end" means end rather than "end for one
  second".
- **Nothing runs in the background.** No alarm, no service, no
  `SCHEDULE_EXACT_ALARM`. Focus is a question about the clock, answered when
  something asks. The one timer involved is a coroutine `delay`, which cannot
  wake a sleeping phone; it sleeps until the next moment the answer could
  change, and never longer than an hour — a backstop in case the clock itself
  jumps for a time zone or a daylight-saving change. The state is recomputed
  whenever the launcher returns to the foreground, so a phone that dozed
  through the boundary is right again before you see it.
- The screen states the limits: focus **hides, it does not block**. A
  set-aside app is still installed, still running, and can still be opened
  from a notification, a link, or another launcher. The launcher's own
  screens are never hidden, so focus can always be switched off.
- Starting focus with nothing allowed is refused rather than obeyed: the
  button stays disabled and says why.

**Minimal mode** (Settings → Focus and quiet → Minimal mode)
- A text-only home screen: app names in large type, no icons, no grid, no
  dock. The clock shrinks to make room for the list.
- Folders are flattened, because "just app names" cannot draw a pile of
  icons, and dock apps join the end of the list so nothing becomes
  unreachable.
- Every gesture still works — swipe up for the drawer, right-to-left for
  quick controls, double-tap to lock — because both home screens share one
  gesture modifier.
- There is no reordering here; switch it off to rearrange. The drawer keeps
  its grid: it is search-first, and an icon is the fastest way to recognise
  an app you are hunting for.
- It composes with focus mode: minimal changes how the home screen looks,
  focus changes what is on it.

**Customisation**, all stored on the device
- Theme: light, dark, or follow system.
- Accent colour: six choices, each with a separate tone for light and dark so
  contrast holds either way.
- Font: system default, sans serif, serif or monospace — all families Android
  already ships, so nothing is bundled.
- Icon size: small, medium or large.
- Grid columns: 3–6, set separately for the drawer and the home screen.
- Wallpaper: opens Android's own picker.
- Hidden apps: a switch per app, with a clear note that hiding is cosmetic —
  the app stays installed, keeps running, and keeps its permissions. The list
  itself can be put behind the phone's own lock: fingerprint or face where
  they are enrolled, PIN, pattern or password otherwise. That uses the
  platform's `createConfirmDeviceCredentialIntent`, not the `androidx.biometric`
  library, which would be a sizeable dependency for one dialog.
  - The lock also stands in front of the ways round it: turning the lock
    off, and, while anything is hidden, saving a backup (the file lists the
    hidden apps) or restoring one (it replaces the list). Each prompt says
    what it is for.
  - It is not a vault. A hidden app is still listed in Android's own
    Settings, and in this launcher's privacy dashboard and focus picker,
    because those are lists of what is installed. The lock keeps one thing
    private: which apps you chose to hide. On a phone with no screen lock
    there is nothing to ask for, and the Settings screen says so.
- Search: auto keyboard, the most used row, clearing usage history, and the
  two opt-ins: Android settings and contacts.
- Focus and quiet: focus mode and its allowed apps, and minimal mode.
- Set as default home app: a shortcut into Android's home-app picker.

Settings are grouped into six sections — Appearance, Home, Drawer, Gestures,
Privacy and About — by *where you will see the change* rather than by which
part of the code owns it. Icon size sits under Home with the grid it resizes,
even though it also changes the drawer, because the home screen is where
anyone notices.

Changing icon size, the column count or the font shows a **live preview**: a
card with a few of your own apps at the chosen size and column count, and a
clock-sized sample of the chosen font. It uses real icons from the cache the
drawer has already filled, so showing it costs no extra decoding.

### First run

Four short pages: welcome, set as default launcher, choose theme and accent,
and what the app does not do. Smooth paging, and a Skip button on every page
but the last.

Nothing in it is a gate. No page asks for a permission, the home-app page can
be walked straight past, and the theme controls on page three are the real
ones — they apply as you tap, so skipping from there keeps what you chose.
This matters more than it would in another app: a launcher *is* the home
screen, so an onboarding flow you cannot get out of leaves you with no phone.

### Backup and restore

**Settings → About → Back up settings** writes a readable JSON file wherever
you point it, through Android's own file picker. No cloud, no account, and no
storage permission — the picker hands back one file, which is the only thing
the app can touch. Restore reads one back, after a dialog that says plainly
what will be replaced and that there is no undo.

Three things are deliberately left out of the file, and the screens say so:

- **Launch counts.** They are a record of what you use and when you started,
  which is the most revealing thing this app stores. Settings belong in a
  backup; a usage history does not, and writing it into a file you might mail
  to yourself would undo the care taken to keep it in private storage.
- **A running focus session.** Its end time is an instant, not a preference.
  Restoring last week's would switch focus on for no reason, or off while you
  were using it. The *schedule* is a preference and is kept.
- **Whether onboarding is done**, since restoring happens from inside the app.

Apps named in a restored layout that are not installed on this phone are
dropped when the app list next loads, rather than leaving dead icons behind.

## Design system

Everything visual comes from `app/src/main/java/com/privacylauncher/designsystem/`.
Settings → Design preview renders every token on one screen, which is the
point: a design system scattered through screens as constants cannot be
reviewed as a whole, and drift goes unnoticed until something already looks
wrong.

| File | Holds |
|---|---|
| `Spacing.kt` | The only padding values allowed: 4, 8, 12, 16, 24, 32, 48 |
| `Typography.kt` | Inter, as one variable font, and the display/title/body/label scale |
| `Palette.kt` | Neutrals and the six accents, two tones each |
| `Shapes.kt` | 8dp controls, 12dp cards, 20dp sheets, fully round chips |
| `Motion.kt` | 150 / 250 / 350ms, standard easing, springs for gestures |
| `Haptics.kt` | Tick, long-press, drag start and end |
| `Surfaces.kt` | Tonal layers and the content-blur modifier |
| `LauncherTheme.kt` | Puts it together; also the Material You path |

A few decisions worth knowing:

- **Nothing is pure #FFF or #000.** Pure white glares, and pure black makes
  any colour on it look slightly radioactive. Both read as "default" rather
  than chosen.
- **Each accent has two tones.** A hue dark enough to read on off-white is too
  dim on near-black. One shared colour per accent would look fine in whichever
  theme you happened to check.
- **Depth is tonal, never a drop shadow.** Four surface steps, each slightly
  lighter (dark theme) or darker (light theme) than the one beneath.
- **No screen uses more than two font weights.** Hierarchy comes from size and
  colour, not from piling on boldness.
- **Display text is Light with negative tracking.** Large text at normal
  weight and default tracking looks heavy and cramped, and is the single most
  common thing that makes an interface look undesigned.

### Why panels are opaque rather than frosted glass

The obvious approach is `Window.setBackgroundBlurRadius`. It was implemented,
run on a device, and does not work here: that API blurs the windows *behind*
yours, and a launcher declares `windowShowWallpaper`, so the wallpaper belongs
to our own window. There is nothing behind it to blur. The result was a
translucent panel over a perfectly sharp wallpaper, which reads worse than
either extreme.

The alternative is to read the wallpaper bitmap with
`WallpaperManager.getDrawable()` and blur it ourselves. That needs a storage
permission, and this app requests none.

So panels are opaque tonal surfaces and the home screen fades out beneath
them. `Modifier.blurBehind()` in `Surfaces.kt` does real blurring and does
work, because it blurs our own composables — it is there for in-app overlays
such as an open folder, where the thing being blurred is our own UI.

## Privacy properties

| Property | How it is enforced |
|---|---|
| Two permissions, declared | `AndroidManifest.xml` declares `READ_CONTACTS` and `WRITE_SETTINGS` and nothing else. Declaring either grants nothing; see below |
| Nothing asked for unprompted | The only `requestPermission` call is reached from the contact-search row in the drawer and the switch in Settings. `WRITE_SETTINGS` cannot be requested at all — only allowed by the user in Android's own screen, reached from the disabled brightness slider |
| Cannot reach the network | `INTERNET` and `ACCESS_NETWORK_STATE` are removed from the merged manifest with `tools:node="remove"`, so no dependency can reintroduce them |
| No advertising ID | `com.google.android.gms.permission.AD_ID` removed the same way |
| No Google Play Services | Not a dependency. See the note below |
| No analytics, ads or crash reporting | No such dependency exists in the build |
| Settings stay on the phone | `android:allowBackup="false"` plus `data_extraction_rules.xml` excluding every domain from cloud backup and device transfer |
| Narrow app visibility | The manifest's `<queries>` block asks only for apps with a launcher icon, not `QUERY_ALL_PACKAGES`. The privacy dashboard works within that limit and states it on screen |
| Two optional abilities, each off until you turn it on in Android's settings | An accessibility service that can open the notification shade and lock the screen and is refused everything else, and a device admin right that can lock the screen and nothing else (`res/xml/lock_down_admin.xml`). Neither is a permission the app requests, and `PermissionPromiseTest` reads both lists back from the installed app |
| No tracker code inside it | The launcher's own tracker scan is run against its own installed file in `TrackerScanDeviceTest`, and must find none of the 428 trackers on the list |

### Contacts: a permission you switch on

Drawer search can include the phone's contacts. That needs `READ_CONTACTS`,
so it is worth being exact about what the feature does and does not do.

- **Off by default.** The launcher is fully usable without it, and in that
  state no contact query is ever made.
- **Nothing asks for it unprompted.** Not at startup, not on first run, not
  when the drawer opens. The permission request happens when you tap "Search
  contacts too" in the drawer or the switch in Settings, and at no other time.
- **Matching happens in the provider, not here.** The query goes through
  `ContactsContract.Contacts.CONTENT_FILTER_URI`, so this app never reads the
  contact list — only the few rows that match what was typed.
- **Two columns.** A display name and a lookup key. No numbers, no email
  addresses, no postal addresses, no photos, no groups, no call history.
- **Nothing is kept.** Results are not cached, counted, indexed or written
  anywhere. They exist for as long as the search text does.
- **Tapping a result hands off.** The lookup URI goes to whichever contacts
  app is installed; this launcher does not display contact details itself.
- **Reversible.** The switch in Settings stops every query. Only Android can
  take the grant itself back, so Settings links to App info where you can.

There is still no network permission of any kind, so nothing read here can
leave the phone.

The alternative design — no permission, and a result that opens the contacts
app's own search — was considered and rejected: it cannot show a matching name
in the drawer, which is the whole point of the feature. The compromise chosen
instead is to declare the permission and make the asking entirely
user-initiated.

### A note on "no Google"

Jetpack Compose, AndroidX and Kotlin are developed by Google and JetBrains, but
they are open source under Apache 2.0 and ship as ordinary libraries compiled
into the APK. They are **not** Google Play Services: they contain no Google
account integration, no network clients, and no telemetry. Play Services is a
separate, closed-source system app, and this project does not depend on it.
The launcher therefore runs on a de-Googled ROM.

## Accessibility

Audited in full before the first release build. What was checked, and what it
found:

| Check | Result |
|---|---|
| Hard-coded user-facing strings | none — every string comes from `strings.xml` |
| `left`/`right` instead of `start`/`end` | none, and `supportsRtl="true"` |
| Text sized in `dp` rather than `sp` | none — every size is `sp`, all of them in `Typography.kt` |
| Fixed-height rows that would clip text at a large font size | none |
| Icon-only controls with no label | two, both fixed — see below |
| Touch targets under 48 dp | none; the two 20 dp badges use `minimumInteractiveComponentSize()` |
| Decorative icons announced to a screen reader | all seven carry `contentDescription = null` |
| Heading structure | absent everywhere; now on every section and screen title |

Fixed in this pass:

- **Headings.** Nothing in the app was marked as a heading, so TalkBack's
  "next heading" gesture had nothing to move between: the only way through
  Settings with a screen reader was row by row from the top. Every
  `SettingsGroup` title, every screen title in `ScreenScaffold`, and the
  drawer's A–Z section letters are now headings.
- **The focus schedule's two steppers.** "Starts at" and "Ends at" are each a
  `−` and a `+` with no text, so a screen reader met four buttons announced as
  "minus sign" and "plus sign", with nothing to say which row they belonged to
  or what they changed. Each now says what it does — "Starts at, 15 minutes
  earlier" — and the glyph itself is no longer read out after the label.
- **The onboarding bullets.** Four `•` glyphs were announced as "bullet"
  before each privacy point. They are decorative and are now cleared.

Fixed in the performance pass before it: switches announced twice over (the
row was a `Role.Button` containing an interactive `Switch`, so each setting
was met first as a stateless button and then again as the switch; it is now
one `toggleable` row with `Role.Switch`), the 20 dp remove badge, the four
unlabelled onboarding page dots (now one spoken "Page 3 of 4"), and the
empty-state text on the minimal home that carried no shadow.

### Every screen, walked by a test

The audits above were done by reading the code, and reading the code is how
things get missed. `AccessibilityWalkTest` puts each of twenty screens and
dialogs on the emulator and reads what it tells Android's accessibility
services about itself, which is what TalkBack and Switch Access act on. It
holds every screen to two rules: **everything that can be pressed has a
name**, and **everything that can be pressed is at least 48 by 48 dp**. Long
screens are checked a screenful at a time.

The first run failed on eight of the twenty. All are fixed, and the test now
passes:

- **The six accent colours** were six unnamed buttons, in Settings and on
  the welcome pages. Choosing a colour without seeing the screen was a
  guess. Each now says its colour and whether it is the one chosen.
- **The brightness slider** had no name, only a percentage, and was 44 dp
  tall. It is now "Brightness", and 48 dp.
- **Hidden apps and focus apps** had the fault the Settings rows were cured
  of earlier: each app was two things to a screen reader, a row with a name
  and no state and a switch with a state and no name. Each is one switch
  now.
- **The search box** was named only by the hint inside it, which goes away
  with the first letter typed.
- **The folder sheet** had three: its name field had no name, the dimmed
  area round it said what a double-tap would do but not what it was, and the
  sheet itself claimed to be a button that did nothing.

Found the same way, earlier in this phase: home screen icons could be named
by a screen reader but not opened, dock icons could not be named at all, and
a drag went the wrong way in right-to-left languages.

**What this does not tell you.** It cannot judge whether a name is a good
one, or whether a screen is read out in a sensible order. That takes a
person listening. TalkBack is not installed on the emulator images this
project uses, so **nobody has yet listened to this launcher with TalkBack**.

### Colour contrast

Every text-on-surface pair in the palette is checked against WCAG 2.1 AA by
[`tools/contrast.py`](tools/contrast.py), which reads the colours out of
`Palette.kt` and `LauncherTheme.kt` rather than keeping copies of them, and
exits non-zero on a failure so it can be a gate rather than a report:

```bash
python3 tools/contrast.py
```

**72 pairs, none below AA (4.5:1).** That covers body text and secondary text
on all four surface steps in both themes, all six accents used as small text
on three surfaces each, button labels on every accent, the three status tones,
and the error colour. The tightest pair in the whole palette is Amber as
small text on `Surface2` at 4.71:1; the loosest is body text on the base
background at 17.3:1.

The accents were chosen to "clear roughly 4.5:1 against their own
background", and the measurement now says they do, which is a different
statement from the one the comment in `Palette.kt` was making.

What the checker deliberately cannot judge: **any text drawn straight onto
the wallpaper** — the home-screen clock, the labels under the icons, every
line of the minimal home. Those sit on whatever photo the user chose, so
their legibility comes from the shadow each one carries rather than from a
pair of known colours. The checker says so in its own output rather than
quietly reporting a clean run over a smaller set than you think.

### Still open

- **Listening with TalkBack.** Not done: see above.
- **Layout by eye at large font and display sizes, on a real screen**, and
  the wallpaper-backed text above. The emulators run every test at 1.3 times
  font size on a narrow screen and the soak at up to 1.5 times, which shows
  nothing crashes or goes missing, not that it looks right.

## Tests

```bash
tools/check.sh                     # all four below on the a30 emulator, with a summary
tools/check.sh all                 # the same on all three emulators (15 minutes)
./gradlew :app:testDebugUnitTest   # 142 JVM unit tests, no device needed
tools/devtest.sh a30               # 53 device tests, on the a30 emulator
python3 tools/contrast.py          # every colour pair against WCAG AA
tools/soak.sh a30                  # drive every screen on the a30 emulator
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

**An earlier version of this file said a Galaxy A30's screen is 720x1560
at 320 dpi. That was wrong.** It is 1080x2340, and the emulator built to
the wrong description (`a30_api30`) was a smaller and easier target than
the phone. The `a30` profile replaces it. The 420 dpi figure is the usual
one for a screen of this size and is the one part of the profile that has
not been checked against a real A30.

**What an emulator is not.** It copies a phone's screen size, memory and
Android version. It does not copy its processor, its graphics chip or its
storage: this Mac is several times faster than an A30, and by default the
emulator draws the screen in software. A pass here means "works on this
version of Android at this size". It does not mean "fast enough on the
phone", and every speed figure in this repository is an emulator figure.
It also runs plain Android rather than Samsung's One UI, which differs in
icon shapes, in where the home-app picker lives and in how gestures are
handled.

Three code paths differ below Android 12, which is what the `a30` profile
runs:

| Path | On Android 11 | Checked |
|---|---|---|
| Material You | unavailable | row reads "Needs Android 12 or newer" |
| Window blur | unavailable | quick panel falls back to an opaque surface |
| Privacy dashboard | older `getInstalledPackages` overload | reads all 36 packages, no crash |

Those three were checked by hand on the earlier Android 11 emulator, and
none of them depends on screen size. On `a30` itself the soak passes 27 of
27 and all 53 device tests pass.

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

Sixteen, on a device, for the things only the semantics tree shows.

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
`ui-test` entries, and is still 2.1 MB with three manifest permissions.

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
Android to agree it is off. That is 54 checks in all.

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
state, it passes 27 of 27.

### What is not tested

Layout at large font and display sizes has been checked by eye on the
emulator, not asserted. TalkBack has not been run against it; the
accessibility tests read the same information TalkBack would, but nobody
has listened to it. And none of it has run on a real phone: every result
in this file comes from an emulator.

## Dependencies and licences

Every dependency is open source. Versions live in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml).

| Dependency | Purpose | Licence |
|---|---|---|
| `org.jetbrains.kotlin:kotlin-stdlib` | Kotlin standard library (supplied by AGP's built-in Kotlin support) | Apache 2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | Background threads (pulled in by Lifecycle) | Apache 2.0 |
| `androidx.core:core-ktx` | Drawable→Bitmap, broadcast registration helpers | Apache 2.0 |
| `androidx.activity:activity-compose` | Activity ↔ Compose bridge, edge-to-edge, back handling | Apache 2.0 |
| `androidx.lifecycle:lifecycle-runtime-ktx` | Coroutine scopes tied to lifecycle | Apache 2.0 |
| `androidx.lifecycle:lifecycle-runtime-compose` | `collectAsStateWithLifecycle` | Apache 2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | `viewModel()` in Compose | Apache 2.0 |
| `androidx.compose.ui:ui`, `ui-graphics` | Compose UI toolkit | Apache 2.0 |
| `androidx.compose.material3:material3` | Material 3 components | Apache 2.0 |
| `androidx.datastore:datastore-preferences` | Local settings file | Apache 2.0 |
| `androidx.profileinstaller:profileinstaller` | Installs the baseline profile on first run | Apache 2.0 |

`profileinstaller` was already arriving transitively — the AndroidX libraries
ship baseline profiles of their own and depend on it to install them — so
declaring it adds no bytes. It is listed because an undeclared dependency is
an unexamined one. It writes only inside the app's own files and has no
network access.

Bundled as a file in the APK rather than resolved as a Gradle dependency,
which is precisely why it is easy to leave off a dependency list — and it is
42% of the APK:

| Asset | Purpose | Licence |
|---|---|---|
| Inter — `app/src/main/res/font/inter_variable.ttf` | The default typeface, as a single variable font | SIL Open Font License 1.1 |
| Exodus Privacy tracker list — `app/src/main/assets/trackers.tsv` | What the tracker scan looks for: 428 trackers, cut down to names and code signatures | Open Database License 1.0 |

The font's full licence text is kept at
[`licenses/Inter-OFL.txt`](licenses/Inter-OFL.txt), with a note on what is
bundled and where it came from in [`licenses/README.md`](licenses/README.md).
The same note covers the tracker list: it is data under the Open Database
License, which asks that its source is credited and that copies stay open.
Both are done, and the note says how. That licence covers the list, not the
launcher's code. I am not a lawyer; if this is ever shipped commercially,
have one read it.

Test-only, in the `:baselineprofile` module. These build a separate test APK
and are never part of the launcher, so none of them reaches the shipped
dependency list or adds a byte to its APK:

| Dependency | Purpose | Licence |
|---|---|---|
| `androidx.benchmark:benchmark-macro-junit4` | Records the baseline profile; measures startup | Apache 2.0 |
| `androidx.test.uiautomator:uiautomator` | Drives the app while the profile is recorded | Apache 2.0 |
| `androidx.test.ext:junit` | AndroidJUnit4 runner | Apache 2.0 |
| `junit:junit` | Test framework | Eclipse Public License 1.0 |

Build-time only, not shipped in the APK: Gradle 9.9.0, Android Gradle Plugin
9.5.1, the Compose compiler plugin and the `androidx.baselineprofile` plugin
— all Apache 2.0. Since AGP 9.0 the
Android plugin compiles Kotlin itself, so there is no separate
`org.jetbrains.kotlin.android` plugin; applying one is an error.

The three icons in `ui/components/LauncherIcons.kt` use path data from the
Material Design Icons set (Apache 2.0). They are defined in this repository
rather than pulled from `androidx.compose.material:material-icons-core`, which
is frozen at 1.7.8 and is no longer a dependency of material3 from 1.4.0.

Deliberately **not** used: Coil or Glide (icons come straight from
`PackageManager`), Hilt or Dagger (three objects are wired by hand in
`PrivacyLauncherApp`), Room (no database), and any Compose icons artifact
(the three glyphs we need are in-repo).

## Project layout

```
privacy-launcher/
├── build.gradle.kts              root build, declares plugin versions
├── settings.gradle.kts           module list and repositories
├── gradle/libs.versions.toml     every dependency version in one place
├── tools/
│   ├── env.sh                 puts the JDK, SDK and Gradle on PATH
│   ├── emulators.sh           makes, starts and stops the three emulators
│   ├── device.sh              picks the emulator the other scripts use
│   ├── install.sh             builds and installs on an emulator
│   ├── check.sh               every test in one command, with a summary
│   ├── bench.sh               cold start and frame timing (Macrobenchmark)
│   ├── devtest.sh             runs the on-device tests
│   ├── soak.sh                drives every screen and checks it came up
│   ├── contrast.py            checks the palette against WCAG AA
│   └── trackers.py            makes the tracker list the app carries
├── docs/
│   └── performance.md         the measured baseline, and what it is worth
├── licenses/                  licences of the bundled font and tracker list
└── app/
    ├── build.gradle.kts          the app module's build config
    ├── lint.xml                  one documented lint suppression
    └── src/main/
        ├── AndroidManifest.xml   HOME intent filter, permission removals
        ├── assets/trackers.tsv   known trackers (Exodus Privacy, ODbL)
        ├── res/                  strings, window theme, launcher icon
        └── java/com/privacylauncher/
            ├── PrivacyLauncherApp.kt   app singleton (3 objects)
            ├── MainActivity.kt         the only Activity
            ├── data/
            │   ├── AppEntry.kt          one app + search matching
            │   ├── AppRepository.kt      reads installed apps, launches them
            │   ├── IconLoader.kt         background icon decode + LRU cache
            │   ├── LauncherSettings.kt   every preference, one data class
            │   ├── SettingsRepository.kt local settings (DataStore)
            │   ├── HomeLayoutOps.kt      every home screen rearrangement
            │   ├── PrivacyScanner.kt     who holds which permission
            │   ├── TrackerList.kt        the tracker list and its matcher
            │   ├── DexClasses.kt         reads class names out of app code
            │   └── TrackerScanner.kt     scans apps one at a time, remembers
            └── ui/
                ├── LauncherViewModel.kt state that survives rotation
                ├── HomeScreen.kt        clock, app grid, dock, drag and drop
                ├── HomeDrop.kt          where a carried icon would land
                ├── AppDrawer.kt         search + lazy grid
                ├── settings/
                │   ├── SettingsScreen.kt      all preferences
                │   ├── HiddenAppsScreen.kt    per-app hide switches
                │   └── ScreenScaffold.kt      shared header + back
                ├── components/          icons, cells, settings widgets
                └── theme/               colours, accents, typography
```

## Running on a 4 GB phone

- The drawer uses `LazyVerticalGrid`, so only the cells on screen exist.
- Icons are decoded on a background thread, scaled to 128×128 (~64 KB each) and
  held in an `LruCache` capped at 160 entries — about 10 MB at worst.
- The clock wakes once a minute, on the minute, not once a second.
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

The signed release APK is **2.1 MB**, against a 10 MB target. Its largest
single item is the bundled Inter typeface at 880 KB — 42% of the APK. That
is the obvious thing to cut if size ever matters more than it does now:
dropping to the system font would roughly halve it.

### Measured startup and frame timing

`tools/bench.sh` measures cold start and frame timing with Macrobenchmark.
[docs/performance.md](docs/performance.md) keeps the baseline, how it was
taken, and what it can and cannot tell you. In short, on the `a30` emulator
(Android 11, 1080x2340, 4 GB RAM), measured on 12 November 2026:

| | Drawing in software | Drawing on the Mac's GPU |
|---|---|---|
| Cold start, median | 212 to 240 ms | 132 to 134 ms |
| Drawer animation frames over 16.7 ms | 96% | 0.7% to 1.2% |

**These are emulator figures.** This Mac's processor is several times
faster than a Galaxy A30's, so they are for comparing one build with the
next and say nothing reliable about the phone. The 96% is the emulator
painting every pixel on the processor, not the launcher being slow, which
is why the same build is measured both ways. The budget is no dropped
frames at all, and even on the Mac's GPU that is **not met yet**: a few
frames in each drawer run take 17 to 25 ms.

Earlier versions of this file quoted 2097 ms and 2306 ms for cold start,
and those were worse than noise. The benchmark installed the app fresh, a
fresh install opens on the four-page welcome flow, and every one of those
figures was the time to draw the welcome page. The benchmark now walks past
it first, and after every start it times it checks that the home screen is
what came up. That check was watched failing with the welcome flow left in
place:

    The cold start that was just timed did not draw the home screen:
    no "Open app drawer" on it. Is onboarding showing?

On this emulator the baseline profile does not make the typical start
faster. What it changes here is consistency, and a handful of slow frames
early in the drawer animation. Whether it is worth more on a slow phone,
where running uncompiled code costs far more, can only be measured on one.

### The profile is a committed file, and tasks delete it

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

## Prerequisites

Android Studio is **not** required. The toolchain is three directories under
your home folder:

| Tool | Version | Location |
|---|---|---|
| Temurin JDK | 17.0.21+9 | `~/Library/Java/JavaVirtualMachines/temurin-17.jdk` |
| Android SDK | platform-tools 37.0.1, platform 37.0, build-tools 37.0.0 | `~/Library/Android/sdk` |
| Gradle | 9.9.0 | `~/Library/Gradle/gradle-9.9.0` |

Nothing was installed system-wide and no admin password was used. Deleting
those three directories removes the toolchain completely.

Put them on your `PATH` for the current shell:

```bash
source tools/env.sh
```

To make it permanent, add that same `source` line to your `~/.zshrc`.

Gradle also needs to know where the SDK is. From the project root, once:

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

`local.properties` is machine-specific and is in `.gitignore`.

## Build

```bash
gradle assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

A Gradle wrapper (`./gradlew`) is committed so anyone cloning the repo gets the
same Gradle version without installing it. Note that the first `./gradlew` run
downloads its own copy of Gradle (~135 MB) into `~/.gradle/wrapper/dists`. Since
Gradle 9.9.0 is already installed here, plain `gradle` avoids that duplicate.

### Signed release build

```bash
gradle assembleRelease
```

The signed APK lands at `app/build/outputs/apk/release/app-release.apk`
(2.1 MB). Install it with:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Then make it the home screen: **Settings → Apps → Default apps → Home app**,
or from inside the launcher, **Settings → Home → Set as default home app**.

The release build installs alongside a debug build rather than replacing it:
debug carries an `applicationIdSuffix` of `.debug`, so the two are different
packages to Android and both can be the home app candidate at once.

#### About the signing key

The signing key does **not** live in this project folder. The build looks
for a small settings file, `keystore.properties`, in two places, in order:

1. the path in the `PL_KEYSTORE_PROPERTIES` environment variable (what an
   automated build uses);
2. `~/.privacy-launcher-signing/keystore.properties`, in the home folder of
   whoever is building.

That file names the key (`storeFile`, read relative to the file's own
folder), its alias and its two passwords. **If no file or no key is found
the release build still succeeds and comes out unsigned**, so a fresh clone
builds without being handed a key first.

If the key turns up *inside* the project folder the build stops and says so.
A key in the project is one `git add` away from being public, and
`.gitignore` alone is not treated as enough. (`*.p12`, `*.jks`, `*.keystore`
and `keystore.properties` are ignored as well, as a second line of defence.)

On the Mac this was developed on, the key is
`~/.privacy-launcher-signing/privacy-launcher-dev.p12`. It is a
**development key**: it exists so release builds can be installed and
measured. Do not publish anything signed with it. Its password sits in plain
text in the file beside it, and it has never been backed up or protected the
way a real release key must be.

For a real release, make your own and keep it somewhere safe, with a backup:

```bash
keytool -genkeypair -v -keystore ~/.privacy-launcher-signing/my-release.p12 -storetype PKCS12 -alias privacy-launcher -keyalg RSA -keysize 4096 -validity 10000
```

Then write `~/.privacy-launcher-signing/keystore.properties` with four
lines:

```
storeFile=my-release.p12
storePassword=<the password you chose>
keyAlias=privacy-launcher
keyPassword=<the same password, for a .p12 key>
```

Guard both files. Android refuses an update signed with a different key than
the installed app, so losing the key means never being able to update an
installed copy again: the only way forward is uninstalling, which takes the
user's settings with it.

### compileSdk 37, targetSdk 35

These move independently and the difference matters:

- **compileSdk 37** decides which APIs the compiler can see. core-ktx 1.19.1
  requires it, so it is not optional.
- **targetSdk 35** is what opts the app in to new *runtime* behaviour. It is
  held back deliberately until the launcher has been tested on a real phone,
  because raising it changes how Android treats the app. Lint's one remaining
  warning (`OldTargetApi`) is that choice, left visible on purpose.

### The three manifest warnings

Every build prints:

```
uses-permission#android.permission.INTERNET was tagged ... to remove other
declarations but no other declaration present
```

That is the expected and desired result. The `tools:node="remove"` entries are
a tripwire for a dependency sneaking a permission in; the warning says the
tripwire found nothing to remove. If one of those warnings ever *disappears*,
some dependency started asking for that permission and the build should be
investigated.

## Install on a phone over USB

On the phone: **Settings → About phone → Software information**, tap **Build
number** seven times to unlock Developer options, then **Settings → Developer
options → USB debugging** on. Plug in the USB cable and accept the
"Allow USB debugging?" prompt that appears on the phone.

```bash
adb devices
```

You should see your phone listed as `device`. If it says `unauthorized`, accept
the prompt on the phone screen. Then:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`-r` replaces an existing install and keeps your settings.

To build and install in one step:

```bash
./gradlew installDebug
```

## Helper scripts

All of these work on emulators only, for now. A phone that happens to be
plugged in is refused rather than installed on (`tools/device.sh` is where
that is decided), because this project is not being tested on real phones
yet.

Make and boot the emulators (see "The emulators" above for what they are):

```bash
tools/emulators.sh create
tools/emulators.sh start a30
```

Build the app and install it on one, and make it the home app there:

```bash
tools/install.sh a30 --home
```

Run everything -- JVM tests, contrast check, on-device tests and the soak
-- and get a short summary. `all` does it on every emulator in turn:

```bash
tools/check.sh
tools/check.sh all
```

Run the on-device tests, and see which failed and why:

```bash
tools/devtest.sh a30
tools/devtest.sh a30 HomeGridDragTest   # one class
```

Drive every screen and check each one really came up:

```bash
tools/soak.sh a30
```

Run the device tests and a short tour on seven screen shapes, by making one
emulator behave as each in turn (about half an hour):

```bash
tools/shapes.sh a30
tools/shapes.sh modern
```

Measure cold start and frame timing (see
[docs/performance.md](docs/performance.md) before reading the numbers):

```bash
tools/bench.sh a30
```

Check every colour pair in the palette against WCAG AA (no device needed):

```bash
python3 tools/contrast.py
```

Take a screenshot of whatever is on screen:

```bash
adb exec-out screencap -p > shot.png
```

## Make it the home screen

The app cannot do this itself — only you can, in system settings. Either:

- open Privacy Launcher's settings button and tap **Set as default home app**, or
- **Settings → Apps → Choose default apps → Home app → Privacy Launcher**.

To go back to Samsung's launcher, pick **One UI Home** on that same screen.

## Uninstall

If you allowed **Lock down**, turn it off first: Settings → Gestures → Lock
down → Turn off. Android will not uninstall an app while it is allowed to
lock the screen, and says "this app is a device admin app" if you try.

```bash
adb uninstall com.privacylauncher.debug
```

Note the `.debug` suffix: debug builds use a separate application ID so they
can sit alongside a release build.
