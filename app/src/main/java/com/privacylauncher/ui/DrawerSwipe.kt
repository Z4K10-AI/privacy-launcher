package com.privacylauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.privacylauncher.designsystem.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * How far the sheet has to travel from where the drag started before
 * releasing commits to the new state. Deliberately well under half: a sheet
 * that has already moved a third of the way reads as going there.
 *
 * Measured from the starting state, not from the bottom of the screen. An
 * absolute threshold looks symmetric and is not: with one at 0.3, closing
 * would need a drag seven tenths of the way down the screen while opening
 * needed three.
 */
private const val COMMIT_FRACTION = 0.3f

/** A fast flick opens or closes regardless of distance, in px per second. */
private const val COMMIT_VELOCITY = 700f

/**
 * The app drawer's position, as a fraction: 0 is fully closed, 1 fully open.
 *
 * This exists so the drawer can follow the finger rather than playing a fixed
 * animation. The important detail is that [progress] is an [Animatable], whose
 * value is read inside `graphicsLayer` blocks only. Those re-run on the
 * animation thread without recomposing anything, so dragging the drawer costs
 * no recomposition at all -- which is what keeps it smooth on a 4 GB phone.
 *
 * Nothing here knows whether the drawer is "open" as far as the rest of the
 * app is concerned. That stays in [LauncherViewModel.panel], and this object
 * follows it; see the LaunchedEffect in MainActivity.
 */
@Stable
class DrawerSwipe(private val scope: CoroutineScope) {

    val progress = Animatable(0f)

    /** Window height in px, used to turn a drag distance into a fraction. */
    var heightPx: Float = 1f

    /**
     * Where the finger last put the sheet, updated synchronously.
     *
     * [progress] cannot be used to decide what a released drag meant. Writing
     * to an Animatable means suspending on its mutex, so the last frame of a
     * drag may still be queued when the finger lifts, and the release would
     * then be judged against a stale position. This plain field is written on
     * the spot, so the decision always sees the real one.
     */
    private var dragFraction = 0f

    /**
     * Follows a drag that started with the drawer closed. [totalPx] is the
     * accumulated vertical distance, negative upwards.
     */
    fun dragFromClosed(totalPx: Float) = snap(-totalPx / heightPx)

    /** Follows a drag that started with the drawer open; positive is down. */
    fun dragFromOpen(totalPx: Float) = snap(1f - totalPx / heightPx)

    private fun snap(target: Float) {
        val clamped = target.coerceIn(0f, 1f)
        dragFraction = clamped
        scope.launch { progress.snapTo(clamped) }
    }

    /**
     * Decides where a released drag should land.
     *
     * [velocityPx] is vertical velocity in px/s, negative upwards, and
     * [wasOpen] says which state the drag started from. Returns true if the
     * drawer should end up open.
     */
    fun shouldOpen(velocityPx: Float, wasOpen: Boolean): Boolean = when {
        velocityPx < -COMMIT_VELOCITY -> true
        velocityPx > COMMIT_VELOCITY -> false
        wasOpen -> dragFraction > 1f - COMMIT_FRACTION
        else -> dragFraction > COMMIT_FRACTION
    }

    /**
     * Springs to [target] (0 or 1), cancelling any animation already running.
     *
     * A no-overshoot spring on purpose: the sheet is full screen, so
     * overshooting past 1 would lift it above the top edge and flash the home
     * screen through the gap underneath.
     */
    fun settle(target: Float, spec: AnimationSpec<Float> = Motion.gesture()) {
        dragFraction = target
        scope.launch { progress.animateTo(target, spec) }
    }
}

@Composable
fun rememberDrawerSwipe(): DrawerSwipe {
    val scope = rememberCoroutineScope()
    return remember(scope) { DrawerSwipe(scope) }
}
