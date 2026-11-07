package com.privacylauncher.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Pinching to about four-fifths counts as a deliberate pinch in. */
private const val PINCH_IN_THRESHOLD = 0.82f

/**
 * Every gesture the home screen listens for, in one modifier.
 *
 * Shared by the ordinary home screen and the text-only minimal one. They
 * draw nothing alike, but a swipe has to mean the same thing on both, and two
 * copies of this would drift apart the first time one was fixed.
 *
 * None of these consume the events they watch, so they coexist: a vertical
 * drag reaches the drawer detector, a horizontal one reaches the quick panel,
 * and the pinch detector ignores anything that is not two fingers.
 */
@Composable
fun Modifier.homeGestures(
    enabled: Boolean,
    /** Accumulated vertical drag, negative upwards, as the drawer follows. */
    onDrawerDrag: (Float) -> Unit,
    /** Drag released: vertical velocity in px/s, negative upwards. */
    onDrawerRelease: (Float) -> Unit,
    /** A horizontal drag has begun, so the quick panel's state can be read. */
    onQuickDragStart: () -> Unit,
    /** Accumulated horizontal drag, negative leftwards, for the quick panel. */
    onQuickDrag: (Float) -> Unit,
    /** Horizontal drag released: velocity in px/s, negative leftwards. */
    onQuickRelease: (Float) -> Unit,
    onSwipeDown: () -> Unit,
    onDoubleTap: () -> Unit,
    onPinchIn: () -> Unit,
): Modifier {
    val swipeThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
    return this
        .pointerInput(swipeThresholdPx, enabled) {
            if (!enabled) return@pointerInput
            // dragTotal is a plain local, NOT Compose state: state here
            // would recompose the whole screen on every drag frame. The
            // drawer's own position is an Animatable read inside a
            // graphicsLayer block, for the same reason.
            var dragTotal = 0f
            var velocity = VelocityTracker()
            detectVerticalDragGestures(
                onDragStart = {
                    dragTotal = 0f
                    velocity = VelocityTracker()
                },
                onDragEnd = {
                    if (dragTotal > swipeThresholdPx) {
                        // Downward past the threshold is the notification
                        // shade, not the drawer. Release it back to rest
                        // first in case the drag started upwards.
                        onDrawerRelease(0f)
                        onSwipeDown()
                    } else {
                        onDrawerRelease(velocity.calculateVelocity().y)
                    }
                },
                onDragCancel = { onDrawerRelease(0f) },
            ) { change, delta ->
                dragTotal += delta
                velocity.addPointerInputChange(change)
                onDrawerDrag(dragTotal)
            }
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            // Right-to-left drags the quick controls panel in from the
            // right. Deliberately a drag anywhere rather than one from the
            // edge: on a phone with gesture navigation the right edge
            // belongs to the system's Back gesture, and competing with it
            // is a fight this would lose.
            var dragTotal = 0f
            var velocity = VelocityTracker()
            detectHorizontalDragGestures(
                onDragStart = {
                    dragTotal = 0f
                    velocity = VelocityTracker()
                    // Read the switches now rather than when the panel
                    // composes: by then the sheet is already sliding in,
                    // and every tile would show "Unknown" for the first
                    // frames of the drag.
                    onQuickDragStart()
                },
                onDragEnd = { onQuickRelease(velocity.calculateVelocity().x) },
                onDragCancel = { onQuickRelease(0f) },
            ) { change, delta ->
                dragTotal += delta
                velocity.addPointerInputChange(change)
                onQuickDrag(dragTotal)
            }
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(onDoubleTap = { onDoubleTap() })
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            // Hand-rolled rather than detectTransformGestures, which also
            // reports single-finger pans and would fight the swipe-up
            // detector above. This only ever observes: it consumes nothing,
            // and ignores anything that is not two fingers.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var zoom = 1f
                var sawTwoFingers = false
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.size >= 2) {
                        sawTwoFingers = true
                        zoom *= event.calculateZoom()
                    }
                    if (event.changes.none { it.pressed }) break
                }
                if (sawTwoFingers && zoom < PINCH_IN_THRESHOLD) onPinchIn()
            }
        }
}
