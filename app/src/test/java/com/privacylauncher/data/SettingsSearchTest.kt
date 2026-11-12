package com.privacylauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Android's settings screens in drawer search: only once the user asks.
 *
 * They used to be listed for everyone, with no switch. The rule for this
 * launcher is that search finds apps, and anything more is a choice.
 */
class SettingsSearchTest {

    private val wifi = SettingsTarget(labelRes = 1, action = "wifi")
    private val display = SettingsTarget(labelRes = 2, action = "display")
    private val developer = SettingsTarget(labelRes = 3, action = "developer")
    private val all = listOf(wifi, display, developer)
    private val names = mapOf(1 to "Wi-Fi", 2 to "Display", 3 to "Developer options")
    private fun label(t: SettingsTarget) = names.getValue(t.labelRes)

    private fun search(query: String, enabled: Boolean, max: Int = 4) =
        rankSettingsTargets(enabled, query, all, max, ::label)

    @Test
    fun itIsOffUntilTheUserTurnsItOn() {
        assertFalse(LauncherSettings().searchSettings)
    }

    @Test
    fun whileOffNothingIsListedHoweverWellItMatches() {
        assertEquals(emptyList<SettingsTarget>(), search("display", enabled = false))
        assertEquals(emptyList<SettingsTarget>(), search("wi", enabled = false))
    }

    @Test
    fun whileOnMatchesAreListedBestFirst() {
        // "d" starts both; the shorter name is the closer match.
        assertEquals(listOf(display, developer), search("d", enabled = true))
        assertEquals(listOf(wifi), search("wi", enabled = true))
    }

    @Test
    fun anEmptySearchListsNothing() {
        assertEquals(emptyList<SettingsTarget>(), search("", enabled = true))
        assertEquals(emptyList<SettingsTarget>(), search("   ", enabled = true))
    }

    @Test
    fun settingsNeverCrowdOutTheApps() {
        assertEquals(1, search("d", enabled = true, max = 1).size)
    }

    @Test
    fun theChoiceSurvivesABackup() {
        val saved = SettingsBackup.encode(LauncherSettings(searchSettings = true))
        assertEquals(true, SettingsBackup.decode(saved)?.searchSettings)
    }
}
