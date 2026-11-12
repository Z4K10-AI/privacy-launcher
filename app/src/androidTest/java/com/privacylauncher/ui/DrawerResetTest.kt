package com.privacylauncher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.FocusState
import com.privacylauncher.data.IconLoader
import com.privacylauncher.data.LauncherSettings
import com.privacylauncher.designsystem.LauncherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A drawer that is closed and opened again starts afresh.
 *
 * It used to do that for free: closing it threw the whole thing away, and
 * the next opening built a new one. It is now kept between openings, which
 * is what makes an opening smooth, and so "starts afresh" has become
 * something the drawer must do on purpose. These are the two things a
 * person would notice if it did not.
 */
@RunWith(AndroidJUnit4::class)
class DrawerResetTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Enough apps that the last of them is well below the first screenful. */
    private val apps = (0 until 120).map { i ->
        val letter = 'A' + (i * 26 / 120)
        AppEntry("com.example.p$i", "Main", "$letter app ${"%03d".format(i)}")
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

    private var open by mutableStateOf(true)

    private fun show() {
        val list = DrawerListBuilder.build(
            apps = apps,
            s = LauncherSettings(showMostUsed = false),
            rawQuery = "",
            counts = emptyMap(),
            focus = FocusState(),
        )
        compose.setContent {
            LauncherTheme(settings = LauncherSettings()) {
                AppDrawer(
                    list = list, extraRows = emptyList(), query = "", columns = 4,
                    iconSize = 52.dp, iconLoader = IconLoader(context),
                    isOnHome = { false }, isInDock = { false }, dockFull = false,
                    isOpen = open,
                    // Off: a keyboard racing the test makes it flaky.
                    autoKeyboard = false,
                    onHandleDrag = {}, onHandleRelease = {}, onQueryChange = {},
                    onLaunch = {}, onClose = {}, onAppInfo = {}, onUninstall = {},
                    onAddToHome = {}, onRemoveFromHome = {}, onAddToDock = {},
                    onRemoveFromDock = {}, onHide = {}, onOpenSettingsScreen = {},
                    onOpenContact = {}, onEnableContacts = {}, onOwnAppInfo = {},
                    onEndFocus = {}, onOpenFocus = {},
                )
            }
        }
        compose.waitForIdle()
    }

    private fun closeAndReopen() {
        open = false
        compose.waitForIdle()
        open = true
        compose.waitForIdle()
    }

    @Test
    fun itOpensAtTheTopHoweverFarDownItWasLeft() {
        show()
        val first = apps.first().label
        val last = apps.last().label
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(last))
        compose.onNodeWithText(last).assertIsDisplayed()

        closeAndReopen()

        compose.onNodeWithText(first).assertIsDisplayed()
    }

    @Test
    fun anAppsMenuLeftOpenIsGoneWhenTheDrawerCloses() {
        show()
        compose.onNodeWithText(apps.first().label).performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Add to home screen").assertIsDisplayed()

        closeAndReopen()

        // The menu belonged to the drawer as it was. It must not be found
        // floating over the home screen, or over the drawer opened again.
        compose.onNodeWithText("Add to home screen").assertDoesNotExist()
    }
}
