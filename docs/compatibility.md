# Which phones it runs on

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
| Android version | Every test on emulators at Android 11 and Android 15 | see [Tests](testing.md#tests) |
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
