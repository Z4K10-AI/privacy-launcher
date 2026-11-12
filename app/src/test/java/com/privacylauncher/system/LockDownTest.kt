package com.privacylauncher.system

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What stands between the user and a working Lock down.
 *
 * Small, but it decides what the tile says and whether tapping it locks the
 * phone or opens an explanation, so the order of the questions is pinned
 * down here.
 */
class LockDownTest {

    @Test
    fun allowedAndWithAScreenLockItIsReady() {
        assertEquals(LockDownState.READY, LockDown.state(allowed = true, screenLockSet = true))
    }

    @Test
    fun withoutPermissionItAsksForIt() {
        assertEquals(
            LockDownState.NEEDS_PERMISSION,
            LockDown.state(allowed = false, screenLockSet = true),
        )
    }

    @Test
    fun withNoScreenLockThatComesFirstWhateverElseIsTrue() {
        // Locking a phone that has no PIN shuts nothing. Saying "ready"
        // would be a promise the phone cannot keep, and asking for the
        // permission first would be asking for something that does not
        // help yet.
        assertEquals(
            LockDownState.NEEDS_SCREEN_LOCK,
            LockDown.state(allowed = true, screenLockSet = false),
        )
        assertEquals(
            LockDownState.NEEDS_SCREEN_LOCK,
            LockDown.state(allowed = false, screenLockSet = false),
        )
    }
}
