package com.privacylauncher.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * Standard durations and curves.
 *
 * Two rules behind these numbers. Larger movements need longer to stay
 * legible, so duration scales with how far something travels. And anything
 * the finger is dragging uses a spring rather than a duration, because a
 * fixed duration feels detached from the gesture that caused it.
 */
object Motion {

    /** 150ms — press states, small fades, chip selection. */
    const val SHORT = 150

    /** 250ms — most transitions: sheets fading, content swapping. */
    const val MEDIUM = 250

    /** 350ms — full-screen panels entering or leaving. */
    const val LONG = 350

    /**
     * Standard easing: quick to start, settles gently.
     *
     * Deliberately not linear and not a symmetric ease-in-out, both of which
     * read as mechanical.
     */
    val Easing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** For things leaving the screen, which should not linger. */
    val ExitEasing: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    fun <T> short(): FiniteAnimationSpec<T> = tween(SHORT, easing = Easing)

    fun <T> medium(): FiniteAnimationSpec<T> = tween(MEDIUM, easing = Easing)

    fun <T> long(): FiniteAnimationSpec<T> = tween(LONG, easing = Easing)

    fun <T> exit(): FiniteAnimationSpec<T> = tween(MEDIUM, easing = ExitEasing)

    /** A spring with no overshoot, for anything that follows a finger. */
    fun <T> gesture(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** A spring with a little life, for items settling into place. */
    fun <T> settle(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Panel slide, used by the drawer and full-screen screens. */
    fun panelSlide(): FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}
