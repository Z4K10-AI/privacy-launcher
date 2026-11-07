package com.privacylauncher.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.privacylauncher.R
import com.privacylauncher.data.FontChoice

/**
 * Inter, as one variable font.
 *
 * A single 859 KB file covers every weight. Three static files (Light,
 * Regular, Medium) would have been about 1.2 MB and still fixed the choice of
 * weights at three. Variable fonts have worked since API 26, which is this
 * app's minimum, so there is no fallback to maintain.
 */
private fun inter(weight: Int) = Font(
    resId = R.font.inter_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

internal val InterFamily = FontFamily(
    inter(300),
    inter(400),
    inter(500),
    inter(600),
)

private fun FontChoice.family(): FontFamily = when (this) {
    FontChoice.INTER -> InterFamily
    FontChoice.SYSTEM -> FontFamily.Default
    FontChoice.SANS_SERIF -> FontFamily.SansSerif
    FontChoice.SERIF -> FontFamily.Serif
    FontChoice.MONOSPACE -> FontFamily.Monospace
}

/**
 * The type scale: display, title, body, label.
 *
 * Two deliberate choices. Display sizes use Light (300) and negative letter
 * spacing — large text set at normal weight and tracking looks heavy and
 * cramped, which is the single most common thing that makes an interface feel
 * like a default rather than a design. And no screen uses more than two
 * weights, so hierarchy comes from size and colour instead of from piling on
 * boldness.
 */
internal fun launcherTypography(font: FontChoice): Typography {
    val f = font.family()
    return Typography(
        displayLarge = TextStyle(
            fontFamily = f,
            fontWeight = FontWeight.Light,
            fontSize = 64.sp,
            lineHeight = 68.sp,
            letterSpacing = (-1.6).sp,
        ),
        displayMedium = TextStyle(
            fontFamily = f,
            fontWeight = FontWeight.Light,
            fontSize = 44.sp,
            lineHeight = 50.sp,
            letterSpacing = (-1.0).sp,
        ),
        displaySmall = TextStyle(
            fontFamily = f,
            fontWeight = FontWeight.Light,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            letterSpacing = (-0.6).sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = f,
            fontWeight = FontWeight.Medium,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            letterSpacing = (-0.4).sp,
        ),
        titleLarge = TextStyle(
            fontFamily = f,
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            letterSpacing = (-0.2).sp,
        ),
        titleMedium = TextStyle(
            fontFamily = f,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = f,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = f,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = f,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 18.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = f,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = f,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        ),
    )
}
