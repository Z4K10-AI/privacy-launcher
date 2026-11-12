package com.privacylauncher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.privacylauncher.data.LauncherSettings
import com.privacylauncher.designsystem.LauncherTheme
import com.privacylauncher.system.LockDownState
import com.privacylauncher.ui.settings.LockDownDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the user is told before Lock down is granted, and where it is undone.
 *
 * Becoming a device admin app has a cost Android does not explain well:
 * the launcher cannot be uninstalled while it is one. The dialog has to say
 * so before the user is sent to Android's screen, not after.
 */
@RunWith(AndroidJUnit4::class)
class LockDownDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(LockDownState.NEEDS_PERMISSION)
    private val pressed = mutableListOf<String>()

    private fun show(initial: LockDownState) {
        state = initial
        compose.setContent {
            LauncherTheme(settings = LauncherSettings()) {
                LockDownDialog(
                    state = state,
                    onDismiss = { pressed += "dismiss" },
                    onAllow = { pressed += "allow" },
                    onOpenSecurity = { pressed += "security" },
                    onTurnOff = { pressed += "turn off" },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun says(text: String) =
        compose.onNodeWithText(text, substring = true).assertIsDisplayed()

    @Test
    fun beforeAskingItSaysWhatItWantsWhatItDoesNotAndWhatItCosts() {
        show(LockDownState.NEEDS_PERMISSION)

        says("only your PIN, pattern or password opens it")
        says("asks to lock the screen and nothing else")
        says("cannot erase the phone")
        says("Android will not uninstall the launcher")
        says("You can take it back at any time")

        compose.onNodeWithText("Continue").performClick()
        assertEquals(listOf("allow"), pressed)
    }

    @Test
    fun withNoScreenLockItSendsTheUserToSetOneAndDoesNotAskForThePermission() {
        show(LockDownState.NEEDS_SCREEN_LOCK)

        says("This phone has none set")
        compose.onNodeWithText("Open security settings").performClick()

        assertEquals(listOf("security"), pressed)
    }

    @Test
    fun onceAllowedTheSameDialogIsWhereItIsTurnedOff() {
        show(LockDownState.READY)

        says("Lock down is allowed")
        says("Android will not uninstall the launcher")
        compose.onNodeWithText("Turn off").performClick()

        assertEquals(listOf("turn off"), pressed)
    }

    @Test
    fun cancelGrantsNothing() {
        show(LockDownState.NEEDS_PERMISSION)

        compose.onNodeWithText("Cancel").performClick()

        assertEquals(listOf("dismiss"), pressed)
    }
}
