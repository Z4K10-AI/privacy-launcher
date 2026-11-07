package com.privacylauncher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.privacylauncher.data.LauncherSettings
import com.privacylauncher.data.ThemeMode

/**
 * Wraps the launcher in a colour scheme and typography built from the user's
 * settings.
 *
 * There is deliberately no Material You / dynamic colour. It derives colours
 * from the wallpaper, which is a local operation and harmless for privacy,
 * but gives unpredictable contrast over an arbitrary wallpaper. A fixed set
 * of hand-checked accents is more reliable.
 */
@Composable
fun PrivacyLauncherTheme(
    settings: LauncherSettings,
    content: @Composable () -> Unit,
) {
    val dark = settings.themeMode.resolveIsDark()
    val palette = remember(settings.accent) { settings.accent.palette() }

    val colorScheme = remember(settings.accent, dark) {
        if (dark) {
            darkColorScheme(
                primary = palette.onDark,
                onPrimary = Ink900,
                primaryContainer = palette.containerDark,
                onPrimaryContainer = Ink100,
                secondary = palette.onDark,
                onSecondary = Ink900,
                background = Ink900,
                onBackground = Ink100,
                surface = Ink800,
                onSurface = Ink100,
                surfaceVariant = Ink700,
                onSurfaceVariant = Ink100,
                outline = Ink700,
            )
        } else {
            lightColorScheme(
                primary = palette.onLight,
                onPrimary = Color.White,
                primaryContainer = palette.containerLight,
                onPrimaryContainer = Ink900,
                secondary = palette.onLight,
                onSecondary = Color.White,
                background = Paper,
                onBackground = Ink900,
                surface = Paper,
                onSurface = Ink900,
                surfaceVariant = PaperDim,
                onSurfaceVariant = Ink800,
                outline = PaperDim,
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = remember(settings.font) { launcherTypography(settings.font) },
        content = content,
    )
}

/**
 * Turns a [ThemeMode] into a plain "is it dark right now" answer.
 *
 * The Activity needs this as well as the theme does, to decide whether the
 * status and navigation bar icons should be drawn light or dark.
 */
@Composable
fun ThemeMode.resolveIsDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
