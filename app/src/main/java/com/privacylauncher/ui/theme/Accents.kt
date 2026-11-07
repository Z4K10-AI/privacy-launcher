package com.privacylauncher.ui.theme

import androidx.compose.ui.graphics.Color
import com.privacylauncher.data.AccentColor

/**
 * The colours behind each [AccentColor].
 *
 * Two tones per accent, not one. A single hue cannot serve both themes: a
 * colour dark enough to read as text on white is too dim to read on near
 * black, and vice versa. [onLight] is used in the light scheme, [onDark] in
 * the dark one, and each was chosen to clear roughly 4.5:1 contrast against
 * its own background.
 */
internal data class AccentPalette(
    val onLight: Color,
    val onDark: Color,
    /** A muted fill for selected rows and chips. */
    val containerLight: Color,
    val containerDark: Color,
)

internal fun AccentColor.palette(): AccentPalette = when (this) {
    AccentColor.TEAL -> AccentPalette(
        onLight = Color(0xFF1F7A68),
        onDark = Color(0xFF7DD3C0),
        containerLight = Color(0xFFCDEDE5),
        containerDark = Color(0xFF1B4F45),
    )
    AccentColor.BLUE -> AccentPalette(
        onLight = Color(0xFF2458C4),
        onDark = Color(0xFF9EC0FF),
        containerLight = Color(0xFFD6E2FF),
        containerDark = Color(0xFF1F3A6E),
    )
    AccentColor.VIOLET -> AccentPalette(
        onLight = Color(0xFF6A3FBE),
        onDark = Color(0xFFC9AEFF),
        containerLight = Color(0xFFE6DAFF),
        containerDark = Color(0xFF402A6B),
    )
    AccentColor.AMBER -> AccentPalette(
        onLight = Color(0xFF8A5A00),
        onDark = Color(0xFFF2C06B),
        containerLight = Color(0xFFFBE4BC),
        containerDark = Color(0xFF5A3E07),
    )
    AccentColor.ROSE -> AccentPalette(
        onLight = Color(0xFFB03355),
        onDark = Color(0xFFF2A3BA),
        containerLight = Color(0xFFFBD9E2),
        containerDark = Color(0xFF6B2238),
    )
    AccentColor.SLATE -> AccentPalette(
        onLight = Color(0xFF455066),
        onDark = Color(0xFFB4C0D8),
        containerLight = Color(0xFFDDE3EE),
        containerDark = Color(0xFF333C4D),
    )
}

/** The swatch shown in the settings picker, for the theme currently active. */
internal fun AccentColor.swatch(dark: Boolean): Color =
    palette().let { if (dark) it.onDark else it.onLight }
