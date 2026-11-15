# Accessibility

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
[`tools/contrast.py`](../tools/contrast.py), which reads the colours out of
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
