# Design system

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
