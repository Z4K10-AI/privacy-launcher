# Bundled third-party assets

## Inter (font)

- File: `app/src/main/res/font/inter_variable.ttf`
- Version: 4.1
- Source: https://github.com/rsms/inter
- Licence: SIL Open Font License 1.1 — full text in `Inter-OFL.txt`

The SIL OFL permits bundling in software, including commercial software,
provided the font is not sold by itself and the licence travels with it.
Both conditions are met: the font ships inside the APK and this directory
carries the licence.

The variable font is used rather than static weights. One 859 KB file covers
every weight from 100 to 900, where three static files (Light, Regular,
Medium) would have been about 1.2 MB and still capped the available weights.
Android has supported variable fonts since API 26, which is this app's
minimum.

## Material Design Icons (icon path data)

- File: `app/src/main/java/com/privacylauncher/ui/components/LauncherIcons.kt`
- Source: https://github.com/google/material-design-icons
- Licence: Apache License 2.0

Only the path data for four glyphs (search, close, settings, arrow back) is
used, transcribed into Compose `ImageVector`s. No library dependency.
