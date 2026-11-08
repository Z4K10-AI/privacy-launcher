package com.privacylauncher.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Backup and restore against the real preferences file.
 *
 * [SettingsBackupTest] covers the codec in isolation; this covers the other
 * half, which is the half that rots. `importBackup` writes about twenty
 * keys by hand, so adding a setting means remembering to add it there too.
 * Forget, and the new setting is silently dropped on every restore --
 * nothing throws, nothing logs, and the only symptom is one preference
 * quietly reverting months later.
 *
 * [everySettingSurvivesARoundTrip] is written to fail in exactly that case:
 * it sets every field to something that is not its default, so any field
 * the restore does not carry comes back as the default and the comparison
 * fails naming it.
 *
 * These run against the app's own DataStore on the device, so the test
 * leaves the launcher's settings changed. That is fine on an emulator and
 * is why this is an instrumentation test rather than a unit test.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private lateinit var repo: SettingsRepository

    /** Every field different from its default, so a dropped one shows up. */
    private val populated = LauncherSettings(
        themeMode = ThemeMode.DARK,
        accent = AccentColor.ROSE,
        dynamicColor = true,
        font = FontChoice.MONOSPACE,
        iconSize = IconSize.LARGE,
        drawerColumns = 6,
        homeColumns = 3,
        hiddenApps = setOf("h1/a", "h2/a"),
        homeItems = listOf(
            HomeItem.App("p1/a1"),
            HomeItem.Folder(id = "f1", name = "Tools", appKeys = listOf("p2/a2", "p3/a3")),
        ),
        dockApps = listOf("d1/a", "d2/a"),
        showGlance = false,
        autoKeyboard = false,
        showMostUsed = false,
        searchContacts = true,
        lockHiddenApps = false,
        focusAllowed = setOf("f1/a"),
        focusSchedule = FocusSchedule(enabled = true, startMinute = 1290, endMinute = 390),
        minimalMode = true,
    )

    @Before
    fun setUp() {
        repo = SettingsRepository(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun everySettingSurvivesARoundTrip() = runBlocking {
        // Start from defaults so nothing left over can make a dropped field
        // look like it was restored.
        assertTrue(repo.importBackup(SettingsBackup.encode(LauncherSettings())))

        assertTrue(repo.importBackup(SettingsBackup.encode(populated)))
        val back = repo.settings.first()

        assertEquals("themeMode", populated.themeMode, back.themeMode)
        assertEquals("accent", populated.accent, back.accent)
        assertEquals("dynamicColor", populated.dynamicColor, back.dynamicColor)
        assertEquals("font", populated.font, back.font)
        assertEquals("iconSize", populated.iconSize, back.iconSize)
        assertEquals("drawerColumns", populated.drawerColumns, back.drawerColumns)
        assertEquals("homeColumns", populated.homeColumns, back.homeColumns)
        assertEquals("hiddenApps", populated.hiddenApps, back.hiddenApps)
        assertEquals("homeItems", populated.homeItems, back.homeItems)
        assertEquals("dockApps", populated.dockApps, back.dockApps)
        assertEquals("showGlance", populated.showGlance, back.showGlance)
        assertEquals("autoKeyboard", populated.autoKeyboard, back.autoKeyboard)
        assertEquals("showMostUsed", populated.showMostUsed, back.showMostUsed)
        assertEquals("searchContacts", populated.searchContacts, back.searchContacts)
        assertEquals("lockHiddenApps", populated.lockHiddenApps, back.lockHiddenApps)
        assertEquals("focusAllowed", populated.focusAllowed, back.focusAllowed)
        assertEquals("focusSchedule", populated.focusSchedule, back.focusSchedule)
        assertEquals("minimalMode", populated.minimalMode, back.minimalMode)
    }

    @Test
    fun exportingWhatWasJustRestoredGivesTheSameFile() = runBlocking {
        // If export and import disagree about any field, two passes diverge.
        assertTrue(repo.importBackup(SettingsBackup.encode(populated)))
        val first = repo.exportBackup()
        assertTrue(repo.importBackup(first))
        val second = repo.exportBackup()
        // savedAt is a timestamp and will differ; everything else must not.
        fun withoutTimestamp(s: String) =
            s.lines().filterNot { it.contains("\"savedAt\"") }.joinToString("\n")
        assertEquals(withoutTimestamp(first), withoutTimestamp(second))
    }

    @Test
    fun aRunningFocusSessionIsNotCarriedAcross() = runBlocking {
        // The backup deliberately omits it: restoring last week's end time
        // would switch focus on for no reason, or off mid-use.
        repo.importBackup(SettingsBackup.encode(populated))
        repo.startFocus(until = Long.MAX_VALUE / 2)
        val json = repo.exportBackup()

        repo.endFocus(snoozeUntil = 0L)
        assertTrue(repo.importBackup(json))
        assertEquals(0L, repo.settings.first().focusUntil)
    }

    @Test
    fun theUsageRecordIsNotCarriedAcross() = runBlocking {
        // The most revealing thing the app stores, and not a setting.
        repo.recordLaunch("counted/app")
        val json = repo.exportBackup()
        assertFalse("usage leaked into the backup", json.contains("counted/app"))
    }

    @Test
    fun restoringDoesNotWipeTheUsageRecord() = runBlocking {
        // Left alone rather than reset -- the screen says so.
        repo.recordLaunch("kept/app")
        val before = repo.launchCounts.first()["kept/app"]
        assertTrue(repo.importBackup(SettingsBackup.encode(populated)))
        assertEquals(before, repo.launchCounts.first()["kept/app"])
    }

    @Test
    fun aFileThatIsNotABackupChangesNothing() = runBlocking {
        assertTrue(repo.importBackup(SettingsBackup.encode(populated)))
        val before = repo.settings.first()
        assertFalse(repo.importBackup("{\"format\":\"something-else\",\"version\":1}"))
        assertFalse(repo.importBackup("not json"))
        assertEquals(before, repo.settings.first())
    }
}
