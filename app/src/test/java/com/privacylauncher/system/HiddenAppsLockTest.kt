package com.privacylauncher.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hidden-apps lock, and the ways round it.
 *
 * The lock used to guard one door: opening the list. The switch that turns
 * the lock off was beside it and took one tap. These tests hold the other
 * doors shut. The stand-in below plays the part of Android's PIN screen:
 * it is asked, and it answers yes or no.
 */
class HiddenAppsLockTest {

    /** Says yes or no, and counts how often it was asked. */
    private class FakeUnlocker(private val userConfirms: Boolean) : Unlocker {
        var asked = 0
        val reasons = mutableListOf<String>()
        override fun confirm(title: String, description: String, onUnlocked: () -> Unit) {
            asked++
            reasons += description
            if (userConfirms) onUnlocked()
        }
    }

    // --- the switch that turns the lock off ---------------------------------

    @Test
    fun turningTheLockOffAsksFirst() {
        val unlocker = FakeUnlocker(userConfirms = true)
        val set = mutableListOf<Boolean>()

        unlocker.moveLockSwitch(wanted = false, lockOn = true, "t", "why", set::add)

        assertEquals("the user was not asked", 1, unlocker.asked)
        assertEquals(listOf(false), set)
    }

    @Test
    fun refusingLeavesTheLockOn() {
        val unlocker = FakeUnlocker(userConfirms = false)
        val set = mutableListOf<Boolean>()

        unlocker.moveLockSwitch(wanted = false, lockOn = true, "t", "why", set::add)

        assertEquals(1, unlocker.asked)
        assertEquals("the lock was turned off without the PIN", emptyList<Boolean>(), set)
    }

    @Test
    fun turningTheLockOnIsFree() {
        val unlocker = FakeUnlocker(userConfirms = false)
        val set = mutableListOf<Boolean>()

        unlocker.moveLockSwitch(wanted = true, lockOn = false, "t", "why", set::add)

        assertEquals("nobody should be asked for a PIN to add a lock", 0, unlocker.asked)
        assertEquals(listOf(true), set)
    }

    @Test
    fun thePromptSaysWhatItIsFor() {
        val unlocker = FakeUnlocker(userConfirms = true)
        unlocker.moveLockSwitch(false, lockOn = true, "t", "before turning this lock off") {}
        assertEquals(listOf("before turning this lock off"), unlocker.reasons)
    }

    // --- everything else behind it ------------------------------------------

    @Test
    fun withTheLockOnTheListAndTheSwitchAreAlwaysBehindIt() {
        for (hidden in listOf(0, 3)) {
            assertTrue(HiddenAppsLock.requiresUnlock(Guarded.OPEN_HIDDEN_APPS, lockOn = true, hidden))
            assertTrue(HiddenAppsLock.requiresUnlock(Guarded.TURN_LOCK_OFF, lockOn = true, hidden))
        }
    }

    @Test
    fun aBackupIsBehindTheLockOnlyWhileSomethingIsHidden() {
        // The file lists the hidden apps, and a restore replaces the list.
        assertTrue(HiddenAppsLock.requiresUnlock(Guarded.SAVE_BACKUP, lockOn = true, hiddenCount = 1))
        assertTrue(HiddenAppsLock.requiresUnlock(Guarded.RESTORE_BACKUP, lockOn = true, hiddenCount = 1))
        // With nothing hidden there is nothing in the file to protect, and
        // the lock is on by default: asking would cost every user a PIN
        // entry per backup for no benefit.
        assertFalse(HiddenAppsLock.requiresUnlock(Guarded.SAVE_BACKUP, lockOn = true, hiddenCount = 0))
        assertFalse(HiddenAppsLock.requiresUnlock(Guarded.RESTORE_BACKUP, lockOn = true, hiddenCount = 0))
    }

    @Test
    fun withTheLockOffNothingAsks() {
        for (action in Guarded.entries) {
            assertFalse("$action", HiddenAppsLock.requiresUnlock(action, lockOn = false, hiddenCount = 5))
        }
    }

    @Test
    fun aGuardedActionRunsOnlyAfterTheUserConfirms() {
        var ran = 0
        FakeUnlocker(userConfirms = false)
            .guard(Guarded.SAVE_BACKUP, lockOn = true, hiddenCount = 2, "t", "d") { ran++ }
        assertEquals("ran although the user refused", 0, ran)

        FakeUnlocker(userConfirms = true)
            .guard(Guarded.SAVE_BACKUP, lockOn = true, hiddenCount = 2, "t", "d") { ran++ }
        assertEquals(1, ran)
    }

    @Test
    fun anUnguardedActionRunsWithoutAsking() {
        val unlocker = FakeUnlocker(userConfirms = false)
        var ran = 0
        unlocker.guard(Guarded.SAVE_BACKUP, lockOn = true, hiddenCount = 0, "t", "d") { ran++ }
        assertEquals(0, unlocker.asked)
        assertEquals(1, ran)
    }
}
