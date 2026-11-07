package com.privacylauncher.designsystem

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tonal layers.
 *
 * Depth is expressed by stepping the surface colour, never by a drop shadow.
 * Hard shadows are the look this design system is avoiding, and they read
 * badly over an arbitrary wallpaper.
 */
@Immutable
data class SurfaceTones(
    val level0: Color,
    val level1: Color,
    val level2: Color,
    val level3: Color,
)

internal val LocalSurfaceTones = staticCompositionLocalOf {
    SurfaceTones(Neutral.Background, Neutral.Surface1, Neutral.Surface2, Neutral.Surface3)
}

/**
 * Whether this device can blur a composable's contents (API 31+ RenderEffect).
 */
internal val LocalContentBlurSupported = staticCompositionLocalOf { blurAvailable }

/** The three status tones, resolved for the theme currently active. */
@Immutable
data class SignalColors(val good: Color, val fair: Color, val attention: Color)

val LocalSignalColors = staticCompositionLocalOf {
    SignalColors(Signal.GoodLight, Signal.FairLight, Signal.AttentionLight)
}

internal val blurAvailable: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * A full-screen panel that floats over the home screen.
 *
 * **On why this is opaque rather than frosted glass.** The obvious way to
 * blur the wallpaper behind a panel is `Window.setBackgroundBlurRadius`, and
 * it does not work here. That API blurs the windows *behind* yours, but a
 * launcher declares `windowShowWallpaper`, so the wallpaper belongs to our
 * own window — there is nothing behind it to blur. It was implemented, tested
 * on a device, and produced a translucent panel over a perfectly sharp
 * wallpaper, which is worse than either extreme.
 *
 * The alternative is to read the wallpaper bitmap and blur it ourselves, via
 * `WallpaperManager.getDrawable()`. That needs a storage permission, and this
 * app requests none at all. Readable panels are worth more than a glass
 * effect, so the panels are opaque tonal surfaces.
 *
 * [blurBehind] does work, because it blurs our own composables, and is what
 * an in-app sheet (an open folder, say) should sit on top of.
 */
@Composable
fun Modifier.panelSurface(tone: Color = LocalSurfaceTones.current.level0): Modifier =
    background(tone)

/** An opaque card surface for rows and groups inside a panel. */
@Composable
fun Modifier.cardSurface(level: Int = 1): Modifier {
    val tones = LocalSurfaceTones.current
    val tone = when (level) {
        0 -> tones.level0
        1 -> tones.level1
        2 -> tones.level2
        else -> tones.level3
    }
    return background(tone)
}

/**
 * Blurs this composable's own content while [active].
 *
 * Real frosted glass, for an overlay sitting above the launcher's own UI —
 * apply it to the content underneath. Unlike window blur this genuinely
 * works, because the pixels being blurred are ours.
 *
 * A no-op below API 31, where RenderEffect does not exist. Callers should
 * dim or fade the content as well, so the fallback still reads as "behind
 * something" rather than as nothing having happened.
 */
fun Modifier.blurBehind(active: Boolean, radius: Dp = 24.dp): Modifier {
    if (!active || !blurAvailable) return this
    return graphicsLayer {
        val px = radius.toPx().coerceAtLeast(0.1f)
        renderEffect = RenderEffect
            .createBlurEffect(px, px, Shader.TileMode.CLAMP)
            .asComposeRenderEffect()
    }
}
