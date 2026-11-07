package com.privacylauncher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.privacylauncher.data.ThemeMode

private val DarkScheme = darkColorScheme(
    primary = Teal200,
    onPrimary = Ink900,
    primaryContainer = Teal700,
    onPrimaryContainer = Ink100,
    secondary = Teal200,
    onSecondary = Ink900,
    background = Ink900,
    onBackground = Ink100,
    surface = Ink800,
    onSurface = Ink100,
    surfaceVariant = Ink700,
    onSurfaceVariant = Ink100,
    outline = Ink700,
)

private val LightScheme = lightColorScheme(
    primary = Teal400,
    onPrimary = Color.White,
    primaryContainer = Teal200,
    onPrimaryContainer = Ink900,
    secondary = Teal400,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink900,
    surface = Paper,
    onSurface = Ink900,
    surfaceVariant = PaperDim,
    onSurfaceVariant = Ink800,
    outline = PaperDim,
)

/**
 * Wraps the whole launcher in a colour scheme.
 *
 * Note there is no "dynamic colour" / Material You here. That feature reads
 * the wallpaper to pick colours, which is a local operation and harmless, but
 * it pulls in extra code and gives inconsistent contrast over arbitrary
 * wallpapers. Feature 2 adds a manual accent picker instead.
 */
@Composable
fun PrivacyLauncherTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = LauncherTypography,
        content = content,
    )
}
