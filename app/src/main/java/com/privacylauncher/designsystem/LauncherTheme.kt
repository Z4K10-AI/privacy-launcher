package com.privacylauncher.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.privacylauncher.data.LauncherSettings
import com.privacylauncher.data.ThemeMode

/** True when the launcher should be drawing its dark scheme right now. */
@Composable
fun ThemeMode.resolveIsDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Whether Material You can be offered on this device at all. */
val dynamicColorAvailable: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * The root of the design system.
 *
 * Provides colour, type, shape, tonal layers and the window-blur capability
 * flag. Everything else in the app reads from here or from the [Spacing],
 * [Radii] and [Motion] objects.
 */
@Composable
fun LauncherTheme(
    settings: LauncherSettings,
    content: @Composable () -> Unit,
) {
    val dark = settings.themeMode.resolveIsDark()
    val context = LocalContext.current
    val useDynamic = settings.dynamicColor && dynamicColorAvailable

    val colorScheme: ColorScheme = remember(settings.accent, dark, useDynamic, context) {
        when {
            // Material You: colours derived from the wallpaper by the system.
            // This happens entirely on-device; no wallpaper data leaves the
            // phone and no permission is involved.
            useDynamic && dark -> dynamicDarkColorScheme(context)
            useDynamic -> dynamicLightColorScheme(context)
            dark -> darkScheme(settings.accent.palette())
            else -> lightScheme(settings.accent.palette())
        }
    }

    val tones = remember(dark, useDynamic, colorScheme) {
        if (useDynamic) {
            SurfaceTones(
                level0 = colorScheme.surface,
                level1 = colorScheme.surfaceContainerLow,
                level2 = colorScheme.surfaceContainer,
                level3 = colorScheme.surfaceContainerHigh,
            )
        } else if (dark) {
            SurfaceTones(
                Neutral.BackgroundDark,
                Neutral.Surface1Dark,
                Neutral.Surface2Dark,
                Neutral.Surface3Dark,
            )
        } else {
            SurfaceTones(
                Neutral.Background,
                Neutral.Surface1,
                Neutral.Surface2,
                Neutral.Surface3,
            )
        }
    }

    CompositionLocalProvider(
        LocalSurfaceTones provides tones,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = remember(settings.font) { launcherTypography(settings.font) },
            shapes = LauncherShapes,
            content = content,
        )
    }
}

private fun darkScheme(p: AccentPalette) = darkColorScheme(
    primary = p.onDark,
    onPrimary = Neutral.BackgroundDark,
    primaryContainer = p.containerDark,
    onPrimaryContainer = Neutral.OnBackgroundDark,
    secondary = p.onDark,
    onSecondary = Neutral.BackgroundDark,
    background = Neutral.BackgroundDark,
    onBackground = Neutral.OnBackgroundDark,
    surface = Neutral.Surface1Dark,
    onSurface = Neutral.OnBackgroundDark,
    surfaceVariant = Neutral.Surface2Dark,
    onSurfaceVariant = Neutral.OnVariantDark,
    outline = Neutral.OutlineDark,
    outlineVariant = Neutral.OutlineDark,
    error = Color(0xFFE08C8C),
    onError = Neutral.BackgroundDark,
)

private fun lightScheme(p: AccentPalette) = lightColorScheme(
    primary = p.onLight,
    onPrimary = Color.White,
    primaryContainer = p.containerLight,
    onPrimaryContainer = Neutral.OnBackground,
    secondary = p.onLight,
    onSecondary = Color.White,
    background = Neutral.Background,
    onBackground = Neutral.OnBackground,
    surface = Neutral.Surface1,
    onSurface = Neutral.OnBackground,
    surfaceVariant = Neutral.Surface2,
    onSurfaceVariant = Neutral.OnVariant,
    outline = Neutral.Outline,
    outlineVariant = Neutral.Outline,
    error = Color(0xFFA33A3A),
    onError = Color.White,
)
