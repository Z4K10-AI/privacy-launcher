package com.privacylauncher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.GlanceInfo
import com.privacylauncher.data.IconLoader
import com.privacylauncher.data.IconSize
import com.privacylauncher.data.LauncherSettings
import com.privacylauncher.designsystem.LauncherTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The home screen has to fit the phone it is on, whatever the phone.
 *
 * Icon size and the number of apps in the dock are both the user's choice,
 * and a choice that fits a wide screen can ask a narrow one for more room
 * than it has. Five dock apps at the default icon size need 340 dp; a phone
 * 360 dp wide, which is most phones, has 312 dp for them. A row laid out
 * without thinking about that gives the first four icons their full size
 * and squeezes the fifth into what is left.
 *
 * `tools/shapes.sh` runs this on screens from 320 dp wide upwards. On the
 * emulator's own 411 dp screen it only bites at the largest icon size.
 */
@RunWith(AndroidJUnit4::class)
class HomeFitTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val density = context.resources.displayMetrics.density

    private fun entry(name: String) =
        AppEntry(packageName = "p.${name.lowercase()}", activityName = "A", label = name)

    private val dockApps = listOf("Phone", "Messages", "Camera", "Browser", "Music").map(::entry)
    private var iconSize by mutableStateOf(52.dp)

    private fun show() {
        compose.setContent {
            LauncherTheme(settings = LauncherSettings()) {
                HomeScreen(
                    tiles = listOf("Clock", "Maps", "Notes").map { HomeTile.App(entry(it)) },
                    dock = dockApps,
                    glance = GlanceInfo(),
                    columns = 4,
                    iconSize = iconSize,
                    iconLoader = IconLoader(context),
                    editMode = false,
                    gesturesEnabled = true,
                    onLaunch = {}, onOpenFolder = {}, onEnterEditMode = {}, onExitEditMode = {},
                    onRemoveTile = {}, onMove = { _, _ -> }, onCombine = { _, _ -> },
                    onMoveToDock = { _, _ -> }, onDockDrop = {}, onRemoveFromDock = {},
                    onOpenDrawer = {}, onOpenSettings = {}, onOpenClock = {},
                    onDrawerDrag = {}, onDrawerRelease = {}, onQuickDragStart = {},
                    onQuickDrag = {}, onQuickRelease = {}, onSwipeDown = {}, onDoubleTap = {},
                    onPinchIn = {},
                )
            }
        }
        compose.waitForIdle()
    }

    private fun dp(px: Int): Dp = (px / density).dp

    @Test
    fun aFullDockGivesAllFiveIconsTheSameRoomAtEveryIconSize() {
        show()
        val screen = compose.onRoot().fetchSemanticsNode().size.width
        for (size in IconSize.entries) {
            iconSize = size.dp.dp
            compose.waitForIdle()

            val slots = dockApps.map { app ->
                compose.onNodeWithContentDescription(app.label).fetchSemanticsNode()
            }
            val widths = slots.map { it.size.width }
            val what = "${size.name.lowercase()} icons on a ${dp(screen)} wide screen: " +
                dockApps.map { it.label }.zip(widths.map { dp(it) }).joinToString { "${it.first} ${it.second}" }

            assertTrue(
                "the dock's icons are not all the same width with $what",
                widths.max() - widths.min() <= 1,
            )
            assertTrue(
                "a dock icon is too narrow to press with $what",
                widths.min() >= 48 * density - 1,
            )
            val left = slots.first().positionInRoot.x
            val right = slots.last().positionInRoot.x + slots.last().size.width
            assertTrue(
                "the dock runs off the screen with $what",
                left >= 0f && right <= screen.toFloat(),
            )
        }
    }
}
