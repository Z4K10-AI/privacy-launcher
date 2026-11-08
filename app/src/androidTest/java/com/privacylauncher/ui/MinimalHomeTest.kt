package com.privacylauncher.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.GlanceInfo
import com.privacylauncher.data.LauncherSettings
import com.privacylauncher.designsystem.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The text-only home screen.
 *
 * Its list is not simply the home tiles: folders are flattened, dock apps
 * are appended, and duplicates are dropped. That happens inside the
 * composable, in a `remember`, so a plain unit test cannot reach it — the
 * only way to check it is to compose the screen and read what is on it.
 */
@RunWith(AndroidJUnit4::class)
class MinimalHomeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun app(label: String) =
        AppEntry(packageName = "p.${label.lowercase()}", activityName = "A", label = label)

    private fun show(
        tiles: List<HomeTile> = emptyList(),
        dock: List<AppEntry> = emptyList(),
        focusActive: Boolean = false,
        onLaunch: (AppEntry) -> Unit = {},
    ) {
        compose.setContent {
            LauncherTheme(settings = LauncherSettings()) {
                MinimalHome(
                    tiles = tiles,
                    dock = dock,
                    glance = GlanceInfo(),
                    focusActive = focusActive,
                    gesturesEnabled = true,
                    onLaunch = onLaunch,
                    onOpenDrawer = {},
                    onOpenSettings = {},
                    onOpenClock = {},
                    onOpenFocus = {},
                    onDrawerDrag = {},
                    onDrawerRelease = {},
                    onQuickDragStart = {},
                    onQuickDrag = {},
                    onQuickRelease = {},
                    onSwipeDown = {},
                    onDoubleTap = {},
                    onPinchIn = {},
                )
            }
        }
    }

    @Test
    fun anEmptyHomeSaysSoRatherThanShowingNothing() {
        show()
        compose.onNode(hasText("Long-press", substring = true)).assertIsDisplayed()
    }

    @Test
    fun appTilesAreListedByName() {
        show(tiles = listOf(HomeTile.App(app("Clock")), HomeTile.App(app("Phone"))))
        compose.onNodeWithText("Clock").assertIsDisplayed()
        compose.onNodeWithText("Phone").assertIsDisplayed()
    }

    @Test
    fun foldersAreFlattenedIntoTheirApps() {
        // "A folder is a picture of a pile of icons, and just app names
        // cannot show one, so the apps inside are listed on their own."
        show(
            tiles = listOf(
                HomeTile.App(app("Clock")),
                HomeTile.Folder(id = "f1", name = "Tools", entries = listOf(app("Files"), app("Calculator"))),
            ),
        )
        compose.onNodeWithText("Files").assertIsDisplayed()
        compose.onNodeWithText("Calculator").assertIsDisplayed()
        // The folder's own name is not a row: it has nothing to open.
        compose.onAllNodes(hasText("Tools")).assertCountEquals(0)
    }

    @Test
    fun dockAppsJoinTheList() {
        // Dock apps are usually not also on the home grid, so dropping the
        // dock without them would put several apps out of reach.
        show(tiles = listOf(HomeTile.App(app("Clock"))), dock = listOf(app("Phone")))
        compose.onNodeWithText("Clock").assertIsDisplayed()
        compose.onNodeWithText("Phone").assertIsDisplayed()
    }

    @Test
    fun anAppInBothTheGridAndTheDockIsListedOnce() {
        val phone = app("Phone")
        show(tiles = listOf(HomeTile.App(phone)), dock = listOf(phone))
        compose.onAllNodes(hasText("Phone")).assertCountEquals(1)
    }

    @Test
    fun anAppInBothAFolderAndTheDockIsListedOnce() {
        val files = app("Files")
        show(
            tiles = listOf(HomeTile.Folder(id = "f1", name = "Tools", entries = listOf(files))),
            dock = listOf(files),
        )
        compose.onAllNodes(hasText("Files")).assertCountEquals(1)
    }

    @Test
    fun tappingANameLaunchesThatApp() {
        var launched: AppEntry? = null
        show(tiles = listOf(HomeTile.App(app("Clock")))) { launched = it }
        compose.onNodeWithText("Clock").performClick()
        compose.waitForIdle()
        assertEquals("Clock", launched?.label)
    }

    @Test
    fun theFocusBannerOnlyAppearsWhenFocusIsOn() {
        show(tiles = listOf(HomeTile.App(app("Clock"))), focusActive = false)
        compose.onAllNodes(hasText("Focus is on")).assertCountEquals(0)
    }

    @Test
    fun theFocusBannerAppearsWhenFocusIsOn() {
        show(tiles = listOf(HomeTile.App(app("Clock"))), focusActive = true)
        compose.onNodeWithText("Focus is on").assertIsDisplayed()
    }

    @Test
    fun theWayOutIsAlwaysOnScreen() {
        // Minimal mode hides almost everything; the two ways off it must
        // not be among the things hidden.
        show()
        compose.onNodeWithText("Apps").assertIsDisplayed()
        compose.onNodeWithText("Settings").assertIsDisplayed()
    }
}
