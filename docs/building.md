# Building, installing and the helper scripts

Built with the tooling current in November 2026: Android Gradle Plugin
9.5.1, Kotlin 2.4.21, Compose BOM 2026.09.00, Gradle 9.9.0, compileSdk 37,
JDK 17. The launcher is written so it can later be built into an
Android-based system image (a LineageOS-based ROM) as the default home
screen; `rom/` holds the start of that.

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

This has not been tried yet: the project has only run on emulators so far.
The steps are the standard ones for any Android phone.

On the phone: **Settings → About phone**, find **Build number** (on some
phones it is one level further in, under Software information) and tap it
seven times to unlock Developer options. Then **Settings → Developer
options → USB debugging** on. Plug in the USB cable and accept the "Allow
USB debugging?" prompt that appears on the phone.

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

## Make it the home screen

The app cannot do this itself — only you can, in system settings. Either:

- open Privacy Launcher's settings button and tap **Set as default home app**, or
- in Android's Settings, find **Default apps** (usually under Apps) and then
  **Home app**, and choose Privacy Launcher. Phone makers put this screen in
  slightly different places; searching Settings for "home app" finds it.

To go back to the launcher the phone came with, pick it on that same
screen. On a Pixel it is called Pixel Launcher, on a Samsung One UI Home.

## Uninstall

If you allowed **Lock down**, turn it off first: Settings → Gestures → Lock
down → Turn off. Android will not uninstall an app while it is allowed to
lock the screen, and says "this app is a device admin app" if you try.

```bash
adb uninstall com.privacylauncher.debug
```

Note the `.debug` suffix: debug builds use a separate application ID so they
can sit alongside a release build.

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
[docs/performance.md](performance.md) before reading the numbers):

```bash
tools/bench.sh a30
```

Record the start-up profile again, after changing what the launcher does at
start-up or on its main screens. It checks the recording went through the
screens it should have before telling you to commit it:

```bash
tools/profile.sh a30
```

Check every colour pair in the palette against WCAG AA (no device needed):

```bash
python3 tools/contrast.py
```

Take a screenshot of whatever is on screen:

```bash
adb exec-out screencap -p > shot.png
```

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
│   ├── profile.sh             records the start-up profile again
│   ├── shapes.sh              the device tests on seven screen shapes
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

## Continuous integration

Three workflows in `.github/workflows/`. Every outside action they use is
pinned to an exact commit, with its version in a comment beside it, so what
runs cannot change unless the file does.

| Workflow | When | What |
|---|---|---|
| `ci.yml` | every push and pull request | build debug and release, lint, the JVM tests, `tools/apkcheck.sh` on the release APK, the colour-contrast check, the licence check, the link check |
| `emulator-tests.yml` | Mondays, and on demand from the Actions tab | every device test, on emulators running Android 11 and Android 15 |
| `release.yml` | a tag named `launcher-v…` | builds, signs and publishes the APK; see below |

`tools/apkcheck.sh` checks the promises that can be read straight off an
APK: exactly the expected permissions, no emoji-font start-up helper, no
dependency-information block (an encrypted list of libraries the Android
build adds for Google Play, which F-Droid rejects), and the size budget.

## Releasing

A release is built by GitHub from a tag, never on someone's own machine, so
anyone can see exactly what went into it. The signing key exists in two
places only: in four repository secrets, and in a backup that the project's
owner keeps somewhere safe and offline. It is never in the repository.

**Before the first release, once:**

1. Make the release key. Not the development key described above: a new
   one, made only for releases.

   ```bash
   keytool -genkeypair -v -keystore ~/.privacy-launcher-signing/release.p12 -storetype PKCS12 -alias privacy-launcher -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Back it up, with its password, somewhere safe and offline. **If this key
   is lost, no update can ever be installed over an existing copy of the
   app**: Android refuses an update signed with a different key.

3. Put it in the repository's secrets (Settings, Secrets and variables,
   Actions), or with the GitHub command-line tool. `gh secret set` without
   a value asks for it without showing it, so the passwords stay out of the
   shell's history:

   ```bash
   base64 -i ~/.privacy-launcher-signing/release.p12 | gh secret set PL_KEYSTORE_BASE64
   gh secret set PL_KEYSTORE_PASSWORD
   gh secret set PL_KEY_ALIAS --body privacy-launcher
   gh secret set PL_KEY_PASSWORD
   ```

   For a `.p12` key, the key password is the same as the store password.

4. In the repository's Settings, under Code security, turn on **Private
   vulnerability reporting**. `SECURITY.md` and `CODE_OF_CONDUCT.md` send
   private reports there.

**For each release:**

1. Set `versionName` in `app/build.gradle.kts` to the new version and raise
   `versionCode` by one.
2. Write `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, at
   most 500 characters: it becomes the release notes and F-Droid's.
3. Tag and push:

   ```bash
   git tag launcher-v1.1
   git push origin launcher-v1.1
   ```

The workflow then:

- stops if any of the four secrets is missing, or if the tag does not match
  `versionName`;
- writes the key to a temporary folder outside the project and points the
  build at it with `PL_KEYSTORE_PROPERTIES`;
- builds the release from scratch, with nothing reused from earlier builds;
- runs `tools/apkcheck.sh` on the exact file it is about to publish;
- verifies the signature, and writes the file's SHA-256 and the signing
  certificate's SHA-256 into the release notes;
- publishes the APK and a `SHA256SUMS` file as a GitHub release;
- deletes the key from the machine, whether or not anything failed.

These steps were rehearsed on this project's Mac, with the development key
standing in for the secrets and everything except publishing and the final
deletion run as written. The key came back from its encoded form byte for
byte, the build was signed with it, every check in `tools/apkcheck.sh`
passed, and the notes came out with both fingerprints. It has not yet run
on GitHub itself.
