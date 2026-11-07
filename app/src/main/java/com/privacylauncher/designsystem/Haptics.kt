package com.privacylauncher.designsystem

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Light haptic feedback.
 *
 * Uses `View.performHapticFeedback`, which routes through the window manager
 * and respects the user's own haptics setting. It needs no permission, unlike
 * driving the Vibrator directly — which matters here, because this app
 * requests none at all.
 */
@Immutable
class Haptics internal constructor(private val view: View) {

    /** A small tick: toggles, chip selection, crossing a reorder boundary. */
    fun tick() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** The firmer bump that confirms a long-press has registered. */
    fun longPress() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /** Picking an item up to drag it. */
    fun dragStart() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /** Dropping an item. */
    fun dragEnd() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
