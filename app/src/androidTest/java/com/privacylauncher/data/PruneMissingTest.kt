package com.privacylauncher.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `pruneMissing` against the real preferences file.
 *
 * [PruneGuardTest] checks the rule; this checks the thing that does the
 * deleting actually obeys it, against a real DataStore on a real device,
 * because that is where the loss happened. All four lists are checked
 * separately: the home screen, the dock, the hidden apps and the focus
 * allow-list are pruned by four separate pieces of code in one `edit`
 * block, and a guard that covered three of them would be no guard at all.
 */
@RunWith(AndroidJUnit4::class)
class PruneMissingTest {

    private lateinit var repo: SettingsRepository

    private val phone = "com.phone/Main"
    private val camera = "com.camera/Main"
    private val files = "com.files/Main"
    private val gone = "com.uninstalled/Main"

    private val layout = LauncherSettings(
        homeItems = listOf(
            HomeItem.App(phone),
            HomeItem.App(gone),
            HomeItem.Folder(id = "f1", name = "Tools", appKeys = listOf(files, camera)),
        ),
        dockApps = listOf(phone, gone),
        hiddenApps = setOf(camera, gone),
        focusAllowed = setOf(phone, gone),
    )

    @Before
    fun setUp() {
        repo = SettingsRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        // Not `= runBlocking { ... }`: that returns the block's value, so the
        // method is not void and JUnit refuses the whole class.
        runBlocking { repo.importBackup(SettingsBackup.encode(layout)) }
    }

    @Test
    fun anEmptyInstalledSetChangesNothing() = runBlocking {
        val before = repo.settings.first()

        // This is the call that used to wipe the phone's home screen when a
        // package query came back empty.
        repo.pruneMissing(emptySet())

        val after = repo.settings.first()
        assertEquals("home screen", before.homeItems, after.homeItems)
        assertEquals("dock", before.dockApps, after.dockApps)
        assertEquals("hidden apps", before.hiddenApps, after.hiddenApps)
        assertEquals("focus allow-list", before.focusAllowed, after.focusAllowed)
    }

    @Test
    fun aRealUninstallIsStillRemovedFromAllFourLists() = runBlocking {
        repo.pruneMissing(setOf(phone, camera, files))
        val after = repo.settings.first()

        assertEquals(
            listOf(
                HomeItem.App(phone),
                HomeItem.Folder(id = "f1", name = "Tools", appKeys = listOf(files, camera)),
            ),
            after.homeItems,
        )
        assertEquals(listOf(phone), after.dockApps)
        assertEquals(setOf(camera), after.hiddenApps)
        assertEquals(setOf(phone), after.focusAllowed)
    }

    @Test
    fun anUnchangedLayoutIsLeftExactlyAsItWas() = runBlocking {
        // Every app still installed, so nothing should move -- including the
        // folder, which dissolveThinFolders would collapse if it lost one of
        // its two apps.
        val before = repo.settings.first()
        repo.pruneMissing(setOf(phone, camera, files, gone))
        assertEquals(before.homeItems, repo.settings.first().homeItems)
    }

    @Test
    fun theDeviceItselfNeverReportsAnEmptyAppList() = runBlocking {
        // The assumption the guard is built on, checked against a real
        // Android system rather than asserted in a comment: asking what is
        // installed returns something, and it is not null.
        val apps = AppRepository(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).loadApps()
        org.junit.Assert.assertNotNull("the query itself failed", apps)
        org.junit.Assert.assertTrue(
            "a running phone with no launchable apps is not a phone",
            apps!!.isNotEmpty(),
        )
    }
}
