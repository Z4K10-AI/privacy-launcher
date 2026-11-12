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

## Exodus Privacy tracker signatures (data)

- File: `app/src/main/assets/trackers.tsv`
- Source: https://reports.exodus-privacy.eu.org/api/trackers, the list kept
  by Exodus Privacy (https://exodus-privacy.eu.org)
- Snapshot: 12 November 2026. 428 trackers, 588 code signatures.
- Licence: Open Database License 1.0,
  https://opendatacommons.org/licenses/odbl/1-0/

This is data, not code. The launcher's tracker scan compares the names of
the classes inside an app with this list.

The file is a cut-down copy of what Exodus publish: the id, name, categories
and code signatures of each tracker, and nothing else. Their descriptions,
web addresses and network signatures are left out, as are four trackers
that have no code signature to look for. `tools/trackers.py` makes it, and
its header says where it came from and under what licence.

What the licence asks, and how it is met:

- **Say where the data came from.** The file's own header does, this note
  does, and the scan's screen in the launcher says "List from Exodus
  Privacy" with the date, next to every result.
- **Keep it open.** This cut-down copy is offered under the same Open
  Database License. Anyone may take it, use it and change it on those terms.
- **Point to the licence.** By its address, above and in the file.

The licence covers the list. It does not reach the launcher's own code,
which only reads it.

Exodus ask that apps do not call their online service to look trackers up.
The launcher could not if it wanted to: it has no network permission. The
list is fetched once, by hand, on a developer's machine, and shipped inside
the app.

## Material Design Icons (icon path data)

- File: `app/src/main/java/com/privacylauncher/ui/components/LauncherIcons.kt`
- Source: https://github.com/google/material-design-icons
- Licence: Apache License 2.0

Only the path data for four glyphs (search, close, settings, arrow back) is
used, transcribed into Compose `ImageVector`s. No library dependency.
