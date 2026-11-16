# Building the launcher into a ROM

This folder is what a ROM maintainer needs to ship Privacy Launcher as the
**default home screen of the operating system** rather than as an app someone
installs afterwards.

Nothing here flashes a phone. It is the integration glue: the pieces that go
into an Android source tree so the launcher comes out baked into the system
image.

The same arrangement can be tried without building anything:
`tools/os-preview.sh` puts the launcher into the system partition of an
Android 15 emulator and checks how Android treats it. What is written below
was corrected by doing that. [docs/os.md](../docs/os.md) has the results.

## What changes when it is a system app

| | Installed app | System app |
|---|---|---|
| Where it lives | `/data/app` | `/system/priv-app` |
| Survives a factory reset | no | yes |
| Can the user uninstall it | yes | no |
| Default home on first boot | user must choose | yes, with no question |
| "Modify system settings" | off until the user allows it | allowed from the first boot |
| Counts against user storage | yes | no |

The first four rows were checked in the emulator. The fifth was found there:
Android grants that permission to every app in the system image without
asking. The launcher still only uses it for the brightness slider.

## Files here

- `Android.bp` — a Soong prebuilt module. Drop the folder into
  `packages/apps/PrivacyLauncher/` in a LineageOS or AOSP tree.
- `privacy-launcher.mk` — the product makefile fragment. Include it from the
  device makefile, or add `PrivacyLauncher` to `PRODUCT_PACKAGES`.

Put the release APK next to them as `PrivacyLauncher.apk`. It has to be a
build whose manifest carries the home-screen priority described below;
`aapt2 dump xmltree --file AndroidManifest.xml PrivacyLauncher.apk` shows
`android:priority` on the home filter if it does.

## Making it the default home

Adding the package is enough, as long as it is a **privileged** system app.

The launcher's manifest gives its home screen a priority of 1. Android sets
that back to 0 for an ordinary installed app, so nothing changes for people
who install the APK. For a privileged app in the system image Android keeps
it, and a home screen with a higher priority than the others is used from
the first boot without asking.

**Leave Android's own launcher in the image.** Two other ways of ending up
with a single home app look simpler and both break the system on Android 15:

1. Removing the stock launcher (`Trebuchet` on LineageOS, `Launcher3QuickStep`
   on AOSP). The recent-apps screen and the navigation buttons are a service
   inside that package. Without it there is neither.
2. Keeping the package but switching off its home screen, with a
   `component-override` in a sysconfig file. The service then crashes as it
   starts, because it looks up its own home screen and finds nothing, and
   the result is the same.

The stock launcher's home screen stays in the list under Settings, Apps,
Default apps, Home app, and is not shown unless the user picks it there.

## Signing

A system app keeps whatever signature its APK has when `Android.bp` says
`PRESIGNED`, which this one does. That keeps the launcher's signature
independent of the ROM's, so an APK signed with the same key can later be
installed over it without rebuilding the image.

## What this does not do

It does not build a ROM, and it cannot. Building Android from source needs a
Linux machine with, by Google's own figures, at least 400 GB of free disk
and 64 GB of memory, and several hours per build; macOS is not a supported
build host for modern Android.
These files are the part that is portable: hand them, plus the APK, to
whoever builds the image.

It also says nothing about whether a given phone *can* run a custom ROM.
That depends on the phone's maker and model, and
[docs/os.md](../docs/os.md) says what is involved.
