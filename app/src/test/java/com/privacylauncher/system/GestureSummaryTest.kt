package com.privacylauncher.system

import com.privacylauncher.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What Settings says about the gesture service, on old Android and new.
 *
 * The launcher runs on Android 8.0 and later. Locking the screen from the
 * gesture service only became possible in Android 9, so on 8.0 and 8.1
 * "double-tap to lock" is something the phone cannot do. None of the test
 * emulators is that old, so the choice of sentence is pinned down here,
 * where the Android version can be pretended.
 */
class GestureSummaryTest {

    @Test
    fun offIsOffOnAnyAndroid() {
        assertEquals(
            R.string.settings_gestures_off,
            LauncherAccessibilityService.summary(enabled = false, canLock = true),
        )
        assertEquals(
            R.string.settings_gestures_off,
            LauncherAccessibilityService.summary(enabled = false, canLock = false),
        )
    }

    @Test
    fun onAndroidNineAndLaterItPromisesBothGestures() {
        assertEquals(
            R.string.settings_gestures_on,
            LauncherAccessibilityService.summary(enabled = true, canLock = true),
        )
    }

    @Test
    fun onAndroidEightItDoesNotPromiseALockItCannotDo() {
        assertEquals(
            R.string.settings_gestures_on_no_lock,
            LauncherAccessibilityService.summary(enabled = true, canLock = false),
        )
    }
}
