package com.privacylauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guard in front of the one operation that can destroy the user's work.
 *
 * `pruneMissing` deletes, from disk and with no undo, every reference to an
 * app that is not in the set it is handed: the home screen, the dock, the
 * hidden list and the focus list. It is handed the result of asking the
 * system what is installed.
 *
 * Before [HomeLayoutOps.prunable] existed, those two facts met like this:
 *
 *     val apps = appRepository.loadApps()          // empty when the query failed
 *     settingsRepository.pruneMissing(apps.keys)   // so: delete everything
 *
 * and a failed query read as a phone with no apps on it. The user would
 * press home and find a bare screen, with the layout already overwritten.
 *
 * Nothing crashed, nothing logged, and there was no way back. This is the
 * test that holds that shut.
 */
class PruneGuardTest {

    private fun layout() = listOf(
        HomeItem.App("com.phone/Main"),
        HomeItem.App("com.camera/Main"),
        HomeItem.Folder(id = "f1", name = "Tools", appKeys = listOf("com.files/Main")),
    )

    @Test
    fun `an empty installed set is never believed`() {
        // A phone with no launchable apps has no Settings app either, so
        // this is not a phone, it is a question that failed.
        assertFalse(HomeLayoutOps.prunable(emptySet()))
    }

    @Test
    fun `one app is enough to believe the answer`() {
        assertTrue(HomeLayoutOps.prunable(setOf("com.phone/Main")))
    }

    @Test
    fun `prune itself still filters, because that is its job`() {
        // The guard belongs in the caller. prune is a filter and asserting
        // otherwise here would be testing the wrong thing -- the damage is
        // done by trusting the input, not by filtering it.
        assertTrue(HomeLayoutOps.prune(layout(), emptySet()).isEmpty())
    }

    @Test
    fun `the guard is what stands between a failed query and the layout`() {
        // Spelled out as the two-step sequence the ViewModel performs, so
        // the test reads as the bug it prevents.
        val installed = emptySet<String>()
        val before = layout()
        val after = if (HomeLayoutOps.prunable(installed)) {
            HomeLayoutOps.prune(before, installed)
        } else {
            before
        }
        assertEquals(before, after)
    }

    @Test
    fun `a real uninstall is still cleaned up`() {
        // The guard must not stop the thing pruning is for.
        val installed = setOf("com.phone/Main", "com.files/Main")
        assertTrue(HomeLayoutOps.prunable(installed))
        val after = HomeLayoutOps.prune(layout(), installed)
        val keys = after.flatMap {
            when (it) {
                is HomeItem.App -> listOf(it.key)
                is HomeItem.Folder -> it.appKeys
            }
        }
        assertEquals(listOf("com.phone/Main", "com.files/Main"), keys)
    }
}
