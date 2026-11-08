package com.privacylauncher.ui

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.privacylauncher.data.LauncherSettings
import com.privacylauncher.designsystem.LauncherTheme
import com.privacylauncher.ui.components.SettingsSwitchRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a screen reader actually sees on a settings switch.
 *
 * This exists because of a bug that a static read of the code did not
 * catch and clicking around could not have caught either. The row was a
 * `clickable` with `Role.Button` wrapping an interactive `Switch`, so
 * TalkBack met every setting twice: first as a button with no on/off
 * state, then as the switch. Sighted use looked perfect.
 *
 * Semantics are the only place that difference is visible, so this is the
 * only kind of test that can hold the fix in place.
 */
@RunWith(AndroidJUnit4::class)
class SettingsRowSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun switchRow(
        checked: Boolean = false,
        onChange: (Boolean) -> Unit = {},
    ) {
        compose.setContent {
            LauncherTheme(settings = LauncherSettings()) {
                SettingsSwitchRow(
                    title = "Minimal mode",
                    subtitle = "A text-only home screen",
                    checked = checked,
                    onCheckedChange = onChange,
                )
            }
        }
    }

    @Test
    fun theRowIsOneNodeNotTwo() {
        switchRow()
        // The title, the subtitle and the switch merge into a single
        // focusable node. Two toggleable nodes would mean the setting is
        // announced twice, which is exactly what was wrong before.
        compose.onAllNodes(isToggleable()).assertCountEquals(1)
    }

    @Test
    fun theRowAnnouncesItselfAsASwitch() {
        switchRow()
        // Role.Button was the bug: a screen reader said "button" and gave
        // no state at all.
        compose.onNodeWithText("Minimal mode").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
        )
    }

    @Test
    fun theRowCarriesTheOnOffState() {
        switchRow(checked = true)
        compose.onNodeWithText("Minimal mode").assertIsOn()
    }

    @Test
    fun theRowCarriesTheOffState() {
        switchRow(checked = false)
        compose.onNodeWithText("Minimal mode").assertIsOff()
    }

    @Test
    fun theSubtitleIsReadAsPartOfTheRow() {
        switchRow()
        // Merged, so it is spoken with the setting rather than skipped or
        // landed on separately.
        val tree = compose.onRoot().printToString()
        assertTrue("subtitle missing from the tree", tree.contains("A text-only home screen"))
        compose.onNode(hasText("A text-only home screen", substring = true))
            .assertHasClickAction()
    }

    @Test
    fun tappingAnywhereOnTheRowToggles() {
        var latest: Boolean? = null
        switchRow(checked = false) { latest = it }
        // Tapping the title, not the switch at the far edge: the whole row
        // is the target, which is the other half of the fix.
        compose.onNodeWithText("Minimal mode").performClick()
        compose.waitForIdle()
        assertEquals(true, latest)
    }
}
