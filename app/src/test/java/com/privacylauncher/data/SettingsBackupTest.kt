package com.privacylauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backup codec, including what it does with files it should not trust.
 *
 * A backup file is the one input to this app that a user can hand-edit, mail
 * to themselves, truncate by filling a disk, or pick by mistake from a
 * folder of unrelated JSON. Everything below the round-trip test is about
 * that: the rule is that a bad file costs you the settings it mangled and
 * never stops the launcher starting.
 */
class SettingsBackupTest {

    private val populated = LauncherSettings(
        themeMode = ThemeMode.DARK,
        accent = AccentColor.ROSE,
        dynamicColor = true,
        font = FontChoice.MONOSPACE,
        iconSize = IconSize.LARGE,
        drawerColumns = 5,
        homeColumns = 6,
        hiddenApps = setOf("p1/a1", "p2/a2"),
        homeItems = listOf(
            HomeItem.App("p1/a1"),
            HomeItem.Folder(id = "f1", name = "Tools", appKeys = listOf("p2/a2", "p3/a3")),
        ),
        dockApps = listOf("p4/a4", "p5/a5"),
        showGlance = false,
        autoKeyboard = false,
        showMostUsed = false,
        searchContacts = true,
        searchSettings = true,
        lockHiddenApps = false,
        focusAllowed = setOf("p6/a6"),
        focusSchedule = FocusSchedule(enabled = true, startMinute = 1290, endMinute = 390),
        minimalMode = true,
    )

    // --- round trip -------------------------------------------------------

    @Test
    fun `every backed-up field survives a round trip`() {
        val back = SettingsBackup.decode(SettingsBackup.encode(populated))
        assertNotNull(back)
        back!!
        assertEquals(populated.themeMode, back.themeMode)
        assertEquals(populated.accent, back.accent)
        assertEquals(populated.dynamicColor, back.dynamicColor)
        assertEquals(populated.font, back.font)
        assertEquals(populated.iconSize, back.iconSize)
        assertEquals(populated.drawerColumns, back.drawerColumns)
        assertEquals(populated.homeColumns, back.homeColumns)
        assertEquals(populated.hiddenApps, back.hiddenApps)
        assertEquals(populated.homeItems, back.homeItems)
        assertEquals(populated.dockApps, back.dockApps)
        assertEquals(populated.showGlance, back.showGlance)
        assertEquals(populated.autoKeyboard, back.autoKeyboard)
        assertEquals(populated.showMostUsed, back.showMostUsed)
        assertEquals(populated.searchContacts, back.searchContacts)
        assertEquals(populated.searchSettings, back.searchSettings)
        assertEquals(populated.lockHiddenApps, back.lockHiddenApps)
        assertEquals(populated.focusAllowed, back.focusAllowed)
        assertEquals(populated.focusSchedule, back.focusSchedule)
        assertEquals(populated.minimalMode, back.minimalMode)
    }

    @Test
    fun `defaults round-trip as defaults`() {
        val back = SettingsBackup.decode(SettingsBackup.encode(LauncherSettings()))
        assertEquals(LauncherSettings().copy(onboardingDone = false), back!!.copy(onboardingDone = false))
    }

    @Test
    fun `a folder keeps its name, id and order`() {
        val back = SettingsBackup.decode(SettingsBackup.encode(populated))!!
        val folder = back.homeItems.filterIsInstance<HomeItem.Folder>().single()
        assertEquals("f1", folder.id)
        assertEquals("Tools", folder.name)
        assertEquals(listOf("p2/a2", "p3/a3"), folder.appKeys)
    }

    // --- what is deliberately not in the file -----------------------------

    @Test
    fun `a running focus session is not written out`() {
        val json = SettingsBackup.encode(
            populated.copy(focusUntil = 999_999L, focusSnoozeUntil = 888_888L)
        )
        assertTrue("focusUntil leaked", !json.contains("999999"))
        assertTrue("snooze leaked", !json.contains("888888"))
        val back = SettingsBackup.decode(json)!!
        assertEquals(0L, back.focusUntil)
        assertEquals(0L, back.focusSnoozeUntil)
    }

    @Test
    fun `the onboarding flag is not written out`() {
        val json = SettingsBackup.encode(populated.copy(onboardingDone = true))
        assertTrue(!json.contains("onboarding"))
    }

    @Test
    fun `the file is a readable document, not a blob`() {
        val json = SettingsBackup.encode(populated)
        assertTrue("should be indented", json.contains("\n"))
        assertTrue(json.contains(SettingsBackup.FORMAT))
        assertTrue(json.contains("\"version\""))
    }

    // --- hostile and malformed input --------------------------------------

    @Test
    fun `rubbish is rejected rather than parsed`() {
        assertNull(SettingsBackup.decode(""))
        assertNull(SettingsBackup.decode("   "))
        assertNull(SettingsBackup.decode("not json at all"))
        assertNull(SettingsBackup.decode("[1,2,3]"))
        assertNull(SettingsBackup.decode("<html><body>nope</body></html>"))
    }

    @Test
    fun `someone else's json is rejected`() {
        assertNull(SettingsBackup.decode("""{"format":"some-other-app","version":1,"settings":{}}"""))
        // Right shape, no format marker.
        assertNull(SettingsBackup.decode("""{"version":1,"settings":{"themeMode":"DARK"}}"""))
    }

    @Test
    fun `a newer file version is refused rather than half-read`() {
        val json = """{"format":"${SettingsBackup.FORMAT}","version":${SettingsBackup.VERSION + 1},"settings":{"themeMode":"DARK"}}"""
        assertNull(SettingsBackup.decode(json))
    }

    @Test
    fun `a file with no settings object is refused`() {
        assertNull(SettingsBackup.decode("""{"format":"${SettingsBackup.FORMAT}","version":1}"""))
    }

    @Test
    fun `an empty settings object gives defaults, not a crash`() {
        val back = SettingsBackup.decode("""{"format":"${SettingsBackup.FORMAT}","version":1,"settings":{}}""")
        assertNotNull(back)
        assertEquals(LauncherSettings().themeMode, back!!.themeMode)
        assertEquals(LauncherSettings().drawerColumns, back.drawerColumns)
        assertTrue(back.homeItems.isEmpty())
    }

    @Test
    fun `unknown enum values fall back instead of throwing`() {
        val json = """{"format":"${SettingsBackup.FORMAT}","version":1,"settings":{
            "themeMode":"ULTRAVIOLET","accent":"BEIGE","font":"COMIC","iconSize":"ENORMOUS"}}"""
        val back = SettingsBackup.decode(json)!!
        assertEquals(ThemeMode.SYSTEM, back.themeMode)
        assertEquals(AccentColor.TEAL, back.accent)
        assertEquals(FontChoice.INTER, back.font)
        assertEquals(IconSize.MEDIUM, back.iconSize)
    }

    @Test
    fun `an absurd column count cannot produce a grid you cannot undo`() {
        for (bad in listOf(0, -4, 1, 2, 7, 99, Int.MAX_VALUE)) {
            val json = """{"format":"${SettingsBackup.FORMAT}","version":1,"settings":{
                "drawerColumns":$bad,"homeColumns":$bad}}"""
            val back = SettingsBackup.decode(json)!!
            assertTrue(
                "drawerColumns $bad became ${back.drawerColumns}",
                back.drawerColumns in LauncherSettings.COLUMN_CHOICES,
            )
            assertTrue(
                "homeColumns $bad became ${back.homeColumns}",
                back.homeColumns in LauncherSettings.COLUMN_CHOICES,
            )
        }
    }

    @Test
    fun `a dock longer than the maximum is trimmed`() {
        val many = (1..20).joinToString(",") { "\"p$it/a$it\"" }
        val json = """{"format":"${SettingsBackup.FORMAT}","version":1,"settings":{"dockApps":[$many]}}"""
        val back = SettingsBackup.decode(json)!!
        assertEquals(LauncherSettings.MAX_DOCK_APPS, back.dockApps.size)
    }

    @Test
    fun `schedule minutes outside a day are wrapped, not left to break the clock`() {
        val json = """{"format":"${SettingsBackup.FORMAT}","version":1,"settings":{
            "focusSchedule":{"enabled":true,"startMinute":-60,"endMinute":2000}}}"""
        val back = SettingsBackup.decode(json)!!
        assertTrue(back.focusSchedule.startMinute in 0 until FocusClock.MINUTES_PER_DAY)
        assertTrue(back.focusSchedule.endMinute in 0 until FocusClock.MINUTES_PER_DAY)
        // -60 wraps to 23:00, 2000 wraps to 09:20.
        assertEquals(23 * 60, back.focusSchedule.startMinute)
        assertEquals(2000 - FocusClock.MINUTES_PER_DAY, back.focusSchedule.endMinute)
    }

    @Test
    fun `wrong types where a list or object belongs do not throw`() {
        val json = """{"format":"${SettingsBackup.FORMAT}","version":1,"settings":{
            "hiddenApps":"not-a-list","dockApps":42,"homeItems":{"nope":true},
            "focusSchedule":"not-an-object","showGlance":"yes"}}"""
        val back = SettingsBackup.decode(json)
        assertNotNull("a wrong-typed field must not take the whole file down", back)
        assertTrue(back!!.hiddenApps.isEmpty())
        assertTrue(back.dockApps.isEmpty())
        assertTrue(back.homeItems.isEmpty())
    }

    @Test
    fun `blank entries inside a list are dropped`() {
        val json = """{"format":"${SettingsBackup.FORMAT}","version":1,"settings":{
            "hiddenApps":["a/b","","   ","c/d"],"dockApps":["","e/f"]}}"""
        val back = SettingsBackup.decode(json)!!
        assertEquals(setOf("a/b", "c/d"), back.hiddenApps)
        assertEquals(listOf("e/f"), back.dockApps)
    }

    @Test
    fun `a truncated file is refused rather than partly applied`() {
        val full = SettingsBackup.encode(populated)
        assertNull(SettingsBackup.decode(full.substring(0, full.length / 2)))
    }

    @Test
    fun `the suggested filename is dated and ends in json`() {
        val name = SettingsBackup.suggestedFileName()
        assertTrue(name, name.startsWith("privacy-launcher-settings-"))
        assertTrue(name, name.endsWith(".json"))
        assertTrue(name, Regex("""\d{4}-\d{2}-\d{2}""").containsMatchIn(name))
    }
}
