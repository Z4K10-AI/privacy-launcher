# Building the launcher into a ROM

This folder is what a ROM maintainer needs to ship Privacy Launcher as the
**default home screen of the operating system** rather than as an app someone
installs afterwards.

Nothing here flashes a phone. It is the integration glue: the pieces that go
into an Android source tree so the launcher comes out baked into the system
image.

## What changes when it is a system app

| | Installed app | System app |
|---|---|---|
| Where it lives | `/data/app` | `/system/priv-app` |
| Survives a factory reset | no | yes |
| Can the user uninstall it | yes | no, only disable |
| Default home on first boot | user must choose | yes, by build config |
| Counts against user storage | yes | no |

That last-but-one row is the one that matters: a system launcher is the home
screen from the first boot, with no "choose a home app" prompt, because the
build declares it.

## Files here

- `Android.bp` — a Soong prebuilt module. Drop the folder into
  `packages/apps/PrivacyLauncher/` in a LineageOS or AOSP tree.
- `privacy-launcher.mk` — the product makefile fragment. Include it from the
  device makefile, or add `PrivacyLauncher` to `PRODUCT_PACKAGES`.

Put the release APK next to them as `PrivacyLauncher.apk`.

## Making it the default home

Adding the package is not enough — Android still has to be told it is the
home screen. Two ways, and the second is the one that behaves:

1. `PRODUCT_PACKAGES += PrivacyLauncher` ships it, but if another launcher is
   also in the image the user is asked to choose on first boot.
2. Remove the other launcher from the product as well. In LineageOS that
   means dropping `Trebuchet` from `PRODUCT_PACKAGES` for the device. With a
   single home app present, Android picks it with no prompt.

## Signing

A system app in `priv-app` must be signed with a key the platform trusts.
For a personal build, sign with the ROM's own `platform` key, or set
`LOCAL_CERTIFICATE := PRESIGNED` in the prebuilt and keep the APK signed
with your own key — which is what the `Android.bp` here does, because it
keeps the launcher's signature independent of the ROM's.

## What this does not do

It does not build a ROM, and it cannot. Building LineageOS needs a Linux
machine, roughly 100 GB of source and 200 GB of free disk, and several hours
per build; macOS is not a supported build host for modern Android. These
files are the part that is portable — hand them, plus the APK, to whoever
builds the image.

It also says nothing about whether a given phone *can* run a custom ROM. See
the note on the Galaxy A30 in the main README.
