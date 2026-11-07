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

Status: **home screen, app drawer, privacy dashboard and quick controls
complete, on a design system. Verified on an Android 15 (API 35) AOSP
emulator; not yet tested on physical hardware.**

Built with current tooling as of November 2026: AGP 9.5.1, Kotlin 2.4.21,
Compose BOM 2026.09.00, Gradle 9.9.0, compileSdk 37.

Release APK: 2.0 MB. Verified with `aapt2 dump permissions` to hold exactly
those two permissions and no reference to `INTERNET` anywhere in the merged
manifest.

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
- A dock of up to five apps.
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
- Search also finds **system settings screens** — Wi‑Fi, Display, Battery and
  so on — and, if you switch it on, **contacts**. See
  [Contacts, in detail](#contacts-a-permission-you-switch-on).
- An optional **most used** row at the top, counted on the phone, stored only
  there, and erasable from Settings.
- Drag the handle at the top to pull the drawer back down.
- Long-press an app for: add to / remove from home screen, add to dock, hide
  from drawer, **App info** (Android's own page, where you revoke
  permissions), uninstall.

**Quick controls** (swipe right-to-left on the home screen)
- A side sheet of eight tiles over a blurred home screen, following the finger
  and springing to open or closed.
- **The flashlight is the only real switch**, because
  `CameraManager.setTorchMode` is the one of these Android still lets an
  ordinary app call. Wi‑Fi, Bluetooth, Location, Do Not Disturb, Auto-rotate
  and Private DNS show their state, carry a corner arrow, and open Android's
  own screen — Wi‑Fi opens an inline Settings *panel* with a working switch on
  it. A line under the grid says all of this plainly.
- **Lock down** locks the screen through the optional accessibility service.
  It is not Android's own Lockdown, which also disables fingerprint unlock;
  that lives in the power menu and no app can start it. The panel says so.
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
- Search: auto keyboard, the most used row, clearing usage history, and
  contact search.
- Set as default home app: a shortcut into Android's home-app picker.

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

Build-time only, not shipped in the APK: Gradle 9.9.0, Android Gradle Plugin
9.5.1 and the Compose compiler plugin — all Apache 2.0. Since AGP 9.0 the
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
│   ├── emulator.sh            creates and boots the test emulator
│   └── install.sh             builds and installs on phone or emulator
└── app/
    ├── build.gradle.kts          the app module's build config
    ├── lint.xml                  one documented lint suppression
    └── src/main/
        ├── AndroidManifest.xml   HOME intent filter, permission removals
        ├── res/                  strings, window theme, launcher icon
        └── java/com/privacylauncher/
            ├── PrivacyLauncherApp.kt   app singleton (3 objects)
            ├── MainActivity.kt         the only Activity
            ├── data/
            │   ├── AppEntry.kt          one app + search matching
            │   ├── AppRepository.kt      reads installed apps, launches them
            │   ├── IconLoader.kt         background icon decode + LRU cache
            │   ├── LauncherSettings.kt   every preference, one data class
            │   └── SettingsRepository.kt local settings (DataStore)
            └── ui/
                ├── LauncherViewModel.kt state that survives rotation
                ├── HomeScreen.kt        clock, app grid, drag-to-reorder
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

Boot the test emulator (creates it on first run, ~2.5 GB download that once):

```bash
tools/emulator.sh
```

Add `--window` to watch it. It runs headless otherwise, which is faster and
still screenshots fine. Stop it with `adb emu kill`.

Build and install on whatever adb can see, phone or emulator:

```bash
tools/install.sh
```

On the emulator only, `tools/install.sh --home` also sets the launcher as the
home app, skipping the picker. On a real phone use the in-app button.

Take a screenshot of whatever is on screen:

```bash
adb exec-out screencap -p > shot.png
```

### Why an AOSP emulator image

The scripts use `system-images;android-35;default;arm64-v8a`, not the
`google_apis` variant. It is smaller, it runs ARM natively on Apple Silicon,
and it ships without Google Play Services — which makes it a closer match to
the de-Googled ROM this launcher is eventually for. It is not a substitute for
testing on the Samsung: One UI differs in icon shapes, where the home-app
picker lives, and gesture handling.

## Make it the home screen

The app cannot do this itself — only you can, in system settings. Either:

- open Privacy Launcher's settings button and tap **Set as default home app**, or
- **Settings → Apps → Choose default apps → Home app → Privacy Launcher**.

To go back to Samsung's launcher, pick **One UI Home** on that same screen.

## Uninstall

```bash
adb uninstall com.privacylauncher.debug
```

Note the `.debug` suffix: debug builds use a separate application ID so they
can sit alongside a release build.
