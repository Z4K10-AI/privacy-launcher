package com.privacylauncher.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.util.lerp
import com.privacylauncher.data.AccentColor
import androidx.annotation.StringRes
import com.privacylauncher.R

/**
 * Neutrals.
 *
 * Nothing is pure #FFFFFF or #000000. Pure white glares and pure black makes
 * every colour placed on it look slightly radioactive; both read as "default"
 * rather than chosen. These are warm off-white and a near-black with a trace
 * of blue, which is what expensive hardware interfaces tend to use.
 *
 * The three surface steps are tonal layers: each is a little lighter than the
 * one beneath in dark mode, a little darker in light mode. Depth comes from
 * these, never from a drop shadow.
 */
internal object Neutral {
    // Light
    val Background = Color(0xFFFAFAF8)
    val Surface1 = Color(0xFFF3F3F0)
    val Surface2 = Color(0xFFEAEAE6)
    val Surface3 = Color(0xFFE1E1DC)
    val OnBackground = Color(0xFF16161A)
    val OnVariant = Color(0xFF5B5B64)
    val Outline = Color(0xFFDCDCD6)

    // Dark
    val BackgroundDark = Color(0xFF0E0E11)
    val Surface1Dark = Color(0xFF16161A)
    val Surface2Dark = Color(0xFF1E1E24)
    val Surface3Dark = Color(0xFF26262E)
    val OnBackgroundDark = Color(0xFFECECEF)
    val OnVariantDark = Color(0xFF9A9AA5)
    val OutlineDark = Color(0xFF2C2C34)
}

/**
 * The colours behind each [AccentColor].
 *
 * Two tones per accent, not one. A single hue cannot serve both themes: a
 * colour dark enough to read as text on off-white is too dim to read on
 * near-black, and vice versa. Each was chosen to clear roughly 4.5:1 against
 * its own background.
 *
 * All are desaturated relative to Material's defaults. Saturated accent
 * colours are the other half of why a stock Android layout looks cheap.
 */
internal data class AccentPalette(
    val onLight: Color,
    val onDark: Color,
    val containerLight: Color,
    val containerDark: Color,
)

internal fun AccentColor.palette(): AccentPalette = when (this) {
    AccentColor.TEAL -> AccentPalette(
        onLight = Color(0xFF1C6B5C),
        onDark = Color(0xFF86CFBE),
        containerLight = Color(0xFFDCEDE8),
        containerDark = Color(0xFF17443B),
    )
    AccentColor.BLUE -> AccentPalette(
        onLight = Color(0xFF2B55A8),
        onDark = Color(0xFFA6C2F0),
        containerLight = Color(0xFFDEE7F8),
        containerDark = Color(0xFF1D3156),
    )
    AccentColor.VIOLET -> AccentPalette(
        onLight = Color(0xFF61459E),
        onDark = Color(0xFFC3AEE8),
        containerLight = Color(0xFFE7DFF6),
        containerDark = Color(0xFF362A52),
    )
    AccentColor.AMBER -> AccentPalette(
        onLight = Color(0xFF8A5E1C),
        onDark = Color(0xFFE2BC80),
        containerLight = Color(0xFFF6E7CF),
        containerDark = Color(0xFF50391A),
    )
    AccentColor.ROSE -> AccentPalette(
        onLight = Color(0xFF9E3F58),
        onDark = Color(0xFFE5A8B8),
        containerLight = Color(0xFFF6DEE4),
        containerDark = Color(0xFF53242F),
    )
    AccentColor.SLATE -> AccentPalette(
        onLight = Color(0xFF4A5363),
        onDark = Color(0xFFB2BCCB),
        containerLight = Color(0xFFE3E6EB),
        containerDark = Color(0xFF2F353F),
    )
}

/**
 * Three status tones, for the privacy score and nothing else.
 *
 * Deliberately not Material's error/warning/success: those are saturated
 * enough to fight the accent and look like a different app's palette dropped
 * in. These are the same desaturation as the accents above, so a red score
 * ring still belongs to this design system.
 */
internal object Signal {
    val GoodLight = Color(0xFF2F6B50)
    val GoodDark = Color(0xFF8CCCAB)
    val FairLight = Color(0xFF8A6210)
    val FairDark = Color(0xFFE0C182)
    val AttentionLight = Color(0xFF9A3B3B)
    val AttentionDark = Color(0xFFE2A1A1)
}

/** What the colour is called, for the picker's screen-reader labels. */
@StringRes
internal fun AccentColor.nameRes(): Int = when (this) {
    AccentColor.TEAL -> R.string.accent_teal
    AccentColor.BLUE -> R.string.accent_blue
    AccentColor.VIOLET -> R.string.accent_violet
    AccentColor.AMBER -> R.string.accent_amber
    AccentColor.ROSE -> R.string.accent_rose
    AccentColor.SLATE -> R.string.accent_slate
}

/** The swatch shown in the picker, for the theme currently active. */
internal fun AccentColor.swatch(dark: Boolean): Color =
    palette().let { if (dark) it.onDark else it.onLight }

/**
 * How far focus mode drains the colour out of the interface.
 *
 * Not 1.0. Pure grey loses the difference between a selected chip and an
 * unselected one, and between a switch that is on and one that is off, which
 * turns a calm screen into an unreadable one. At 0.82 the accent is a
 * barely-there warm grey, and the contrast that carries meaning survives.
 */
private const val CALM_AMOUNT = 0.82f

/**
 * Pulls a colour towards its own brightness, leaving a trace of the hue.
 *
 * The weights are the Rec. 709 luminance coefficients -- the eye is far more
 * sensitive to green than to blue, so a plain average of the channels turns
 * a saturated blue into a grey that looks much lighter than the original.
 */
internal fun Color.greyed(amount: Float = CALM_AMOUNT): Color {
    val grey = 0.2126f * red + 0.7152f * green + 0.0722f * blue
    return Color(
        red = lerp(red, grey, amount),
        green = lerp(green, grey, amount),
        blue = lerp(blue, grey, amount),
        alpha = alpha,
    )
}

/** The accent, drained of colour, for focus mode. */
internal fun AccentPalette.calmed(): AccentPalette = AccentPalette(
    onLight = onLight.greyed(),
    onDark = onDark.greyed(),
    containerLight = containerLight.greyed(),
    containerDark = containerDark.greyed(),
)

/** The score ring's three tones, drained of colour, for focus mode. */
internal fun SignalColors.calmed(): SignalColors = SignalColors(
    good = good.greyed(),
    fair = fair.greyed(),
    attention = attention.greyed(),
)
