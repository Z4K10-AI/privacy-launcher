package com.privacylauncher.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.privacylauncher.data.FontChoice

/**
 * Maps a [FontChoice] to a family Android already ships.
 *
 * No font files are bundled, so choosing a font costs nothing in APK size.
 * `Default` is whatever the phone's own UI font is, which on a Samsung means
 * the user's pick in Settings > Display > Font.
 */
private fun FontChoice.family(): FontFamily = when (this) {
    FontChoice.SYSTEM -> FontFamily.Default
    FontChoice.SANS_SERIF -> FontFamily.SansSerif
    FontChoice.SERIF -> FontFamily.Serif
    FontChoice.MONOSPACE -> FontFamily.Monospace
}

internal fun launcherTypography(font: FontChoice): Typography {
    val family = font.family()
    return Typography(
        displayLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Light,
            fontSize = 64.sp,
            lineHeight = 68.sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 22.sp,
            lineHeight = 28.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp,
            lineHeight = 26.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            lineHeight = 14.sp,
        ),
    )
}
