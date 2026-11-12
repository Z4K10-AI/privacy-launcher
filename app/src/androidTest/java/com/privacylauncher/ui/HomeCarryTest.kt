package com.privacylauncher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.GlanceInfo
import com.privacylauncher.data.IconLoader
import com.privacylauncher.data.LauncherSettings
import com.privacylauncher.designsystem.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A finger carrying an icon across an edge: grid to dock, dock to grid, and
 * out of the drawer onto either.
 *
 * [HomeGridDragTest] covers a drag that starts and ends on the grid. These
 * drags leave the thing they started on, which is where they can go wrong
 * in ways a drag inside one grid cannot: the icon can be cut off at an
 * edge, land in a place nobody highlighted, or be left hanging when the
 * cell it came from disappears.
 *
 * The real home screen is on screen here, with its clock, grid, dock and
 * bottom bar, so the distances are real ones. The drawer is stood in for by
 * a single real drawer cell laid over the top-left corner, which removes
 * itself the moment its icon is carried away, as the drawer does.
 *
 * Every position a finger is sent to is read from what the screen reports
 * about itself to accessibility services, not from the numbers the code
 * under test keeps. If those numbers were wrong, a test that aimed with
 * them would miss in the same direction and pass.
 */
@RunWith(AndroidJUnit4::class)
class HomeCarryTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun entry(name: String) =
        AppEntry(packageName = "p.$name", activityName = "A", label = name)
    private fun app(name: String) = HomeTile.App(entry(name))
    private fun folder(id: String, vararg names: String) =
        HomeTile.Folder(id = id, name = id, entries = names.map { entry(it) })
    private fun id(name: String) = "a:p.$name/A"
    private fun key(name: String) = "p.$name/A"

    private var tiles by mutableStateOf<List<HomeTile>>(emptyList())
    private var dock by mutableStateOf<List<AppEntry>>(emptyList())
    private var editMode by mutableStateOf(false)
    private var drawerCell by mutableStateOf(false)
    private lateinit var drag: HomeDragState

    private val moves = mutableListOf<Pair<String, String>>()
    private val combines = mutableListOf<Pair<String, String>>()
    private val toDock = mutableListOf<Pair<String, String?>>()
    private val dockDrops = mutableListOf<HomeDrop>()
    private val drawerDrops = mutableListOf<HomeDrop>()
    private val launched = mutableListOf<String>()
    private val menus = mutableListOf<String>()
    private val removedFromDock = mutableListOf<String>()

    private fun show(
        grid: List<HomeTile>,
        dockApps: List<String>,
        editing: Boolean = true,
        withDrawerCell: Boolean = false,
    ) {
        tiles = grid
        dock = dockApps.map { entry(it) }
        editMode = editing
        drawerCell = withDrawerCell
        compose.setContent {
            LauncherTheme(settings = LauncherSettings()) {
                val state = remember { HomeDragState() }.also { drag = it }
                var origin by remember { mutableStateOf(Offset.Zero) }
                val loader = remember { IconLoader(context) }
                Box(
                    Modifier
                        .fillMaxSize()
                        .testTag(ROOT)
                        .onGloballyPositioned { origin = it.positionInRoot() }
                        .pointerInput(state) {
                            followCarry(state, origin = { origin }) { drawerDrops += it }
                        },
                ) {
                    HomeScreen(
                        tiles = tiles,
                        dock = dock,
                        glance = GlanceInfo(),
                        columns = 4,
                        iconSize = 48.dp,
                        iconLoader = loader,
                        editMode = editMode,
                        gesturesEnabled = !editMode,
                        onLaunch = { launched += it.label },
                        onOpenFolder = {},
                        onEnterEditMode = { editMode = true },
                        onExitEditMode = { editMode = false },
                        onRemoveTile = {},
                        onMove = { from, to -> moves += from to to },
                        onCombine = { from, onto -> combines += from to onto },
                        onMoveToDock = { from, before -> toDock += from to before },
                        onDockDrop = { dockDrops += it },
                        onRemoveFromDock = { removedFromDock += it.label },
                        onOpenDrawer = {},
                        onOpenSettings = {},
                        onOpenClock = {},
                        onDrawerDrag = {},
                        onDrawerRelease = {},
                        onQuickDragStart = {},
                        onQuickDrag = {},
                        onQuickRelease = {},
                        onSwipeDown = {},
                        onDoubleTap = {},
                        onPinchIn = {},
                        drag = state,
                    )
                    if (drawerCell) {
                        Box(Modifier.align(Alignment.TopStart).testTag(SOURCE)) {
                            DrawerAppCell(
                                entry = entry("X"),
                                iconLoader = loader,
                                iconSize = 48.dp,
                                onLaunch = { launched += it.label },
                                onMenu = { menus += it.label },
                                onDragOut = { carried ->
                                    state.pickUp(
                                        HomeTile.App(carried), DragSource.DRAWER, state.finger,
                                    )
                                    // The drawer closes, and its cells go
                                    // with it.
                                    drawerCell = false
                                },
                            )
                        }
                    }
                    HomeDragLayer(drag = state, iconSize = 48.dp, iconLoader = loader)
                }
            }
        }
        compose.waitForIdle()
    }

    /** Where a grid icon is, as the screen reports it. */
    private fun gridCell(name: String): Rect =
        compose.onNode(hasText(name) and !hasContentDescription(name))
            .fetchSemanticsNode().boundsInRoot

    /** Where a dock icon is. Dock icons have no visible label, only a spoken one. */
    private fun dockIcon(name: String): Rect =
        compose.onNodeWithContentDescription(name).fetchSemanticsNode().boundsInRoot

    private fun Rect.edge() = Offset(center.x + width * 0.38f, center.y)

    /**
     * Long-presses at [from] and carries to [to]. With [midway], pauses
     * after the long-press, finger still down, and aims at wherever
     * [midway] says, so the target can be read off the screen as it is
     * after the long-press has changed it.
     */
    private fun carry(from: Offset, to: Offset, lift: Boolean = true, midway: (() -> Offset)? = null) {
        val steps = 10
        val root = compose.onNodeWithTag(ROOT)
        root.performTouchInput {
            down(from)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 200)
            // An event after the wait, in the same place. Time in these
            // tests only moves when an event carries it forward, so a wait
            // with nothing after it is a wait that never happened and the
            // long-press would not have been recognised by the pause below.
            moveTo(from)
        }
        compose.waitForIdle()
        val target = midway?.invoke() ?: to
        root.performTouchInput {
            for (i in 1..steps) moveTo(from + (target - from) * (i / steps.toFloat()))
            if (lift) up()
        }
        compose.waitForIdle()
    }

    private fun TouchInjectionScope.release() = up()

    // --- grid to dock --------------------------------------------------------

    @Test
    fun aGridIconDroppedOnTheDockAsksToMoveThere() {
        show(listOf(app("A"), app("B"), app("C"), app("D")), dockApps = listOf("P", "Q"))
        val p = dockIcon("P")

        // To the left of P: in front of it.
        carry(from = gridCell("A").center, to = Offset(p.left - p.width * 0.4f, p.center.y))

        assertEquals(listOf(id("A") to key("P")), toDock)
        assertEquals("it must not also be reordered", emptyList<Pair<String, String>>(), moves)
        assertEquals(emptyList<Pair<String, String>>(), combines)
    }

    @Test
    fun aGridIconDroppedPastTheLastDockIconGoesToTheEndOfTheDock() {
        show(listOf(app("A"), app("B")), dockApps = listOf("P", "Q"))
        val q = dockIcon("Q")

        carry(from = gridCell("B").center, to = Offset(q.right + q.width * 0.4f, q.center.y))

        assertEquals(listOf<Pair<String, String?>>(id("B") to null), toDock)
    }

    @Test
    fun aFullDockDoesNotTakeAGridIcon() {
        show(listOf(app("A"), app("B")), dockApps = listOf("P", "Q", "R", "S", "T"))

        carry(from = gridCell("A").center, to = dockIcon("R").center)

        assertEquals(emptyList<Pair<String, String?>>(), toDock)
    }

    @Test
    fun aFolderCannotBeDroppedOnTheDock() {
        show(listOf(folder("f1", "X", "Y"), app("B")), dockApps = listOf("P"))

        carry(from = gridCell("f1").center, to = dockIcon("P").edge())

        assertEquals(emptyList<Pair<String, String?>>(), toDock)
    }

    @Test
    fun anAppAlreadyInTheDockIsNotTakenOffTheHomeScreenByDroppingItThere() {
        // A is on the home screen and in the dock. Carrying the home icon
        // to the dock could only remove it from the home screen.
        show(listOf(app("A"), app("B")), dockApps = listOf("A", "Q"))

        carry(from = gridCell("A").center, to = dockIcon("Q").edge())

        assertEquals(emptyList<Pair<String, String?>>(), toDock)
    }

    @Test
    fun theIconStaysUnderTheFingerWhenStartingADragMovesTheGrid() {
        // Not in edit mode to begin with. The long-press turns it on, the
        // bottom bar grows a line of help text, and the grid and dock both
        // move up under a finger that has not moved. The carried icon has
        // to stay with the finger, not with the cell it came from.
        show(listOf(app("A"), app("B"), app("C"), app("D")), dockApps = listOf("P", "Q"), editing = false)
        val start = gridCell("A").center
        var aim = Offset.Zero

        carry(from = start, to = Offset.Zero, lift = false) {
            assertTrue("the long-press should have started editing", editMode)
            val p = dockIcon("P")
            Offset(p.left - p.width * 0.4f, p.center.y).also { aim = it }
        }

        val shown = drag.position
        assertTrue(
            "the icon is drawn at $shown but the finger is at $aim",
            (shown - aim).getDistance() < 3f,
        )
        compose.onNodeWithTag(ROOT).performTouchInput { release() }
        compose.waitForIdle()
        assertEquals(listOf(id("A") to key("P")), toDock)
    }

    // --- dock to grid, and along the dock -----------------------------------

    @Test
    fun aDockIconDroppedOnTheEdgeOfAGridIconAsksForItsPlace() {
        show(listOf(app("A"), app("B"), app("C")), dockApps = listOf("P", "Q"))

        carry(from = dockIcon("P").center, to = gridCell("B").edge())

        assertEquals(listOf<HomeDrop>(HomeDrop.Place(key("P"), beforeId = id("B"))), dockDrops)
    }

    @Test
    fun aDockIconDroppedOnTheMiddleOfAGridIconAsksToJoinIt() {
        show(listOf(app("A"), app("B"), app("C")), dockApps = listOf("P", "Q"))

        carry(from = dockIcon("Q").center, to = gridCell("C").center)

        assertEquals(listOf<HomeDrop>(HomeDrop.Onto(key("Q"), ontoId = id("C"))), dockDrops)
    }

    @Test
    fun aDockIconDroppedOnEmptySpaceGoesToTheEndOfTheHomeScreen() {
        show(listOf(app("A"), app("B")), dockApps = listOf("P"))
        val b = gridCell("B")

        // Two cells to the right of B: an empty cell in the same row.
        carry(from = dockIcon("P").center, to = Offset(b.center.x + b.width * 2, b.center.y))

        assertEquals(listOf<HomeDrop>(HomeDrop.Place(key("P"), beforeId = null)), dockDrops)
    }

    @Test
    fun aDockIconCarriedAlongTheDockAsksToBeReordered() {
        show(listOf(app("A")), dockApps = listOf("P", "Q", "R"))
        val r = dockIcon("R")

        carry(from = dockIcon("P").center, to = Offset(r.right + r.width * 0.3f, r.center.y))

        assertEquals(listOf<HomeDrop>(HomeDrop.Dock(key("P"), beforeKey = null)), dockDrops)
    }

    @Test
    fun aDockIconDroppedOnTheBottomBarGoesBackWhereItWas() {
        show(listOf(app("A")), dockApps = listOf("P", "Q"))
        val p = dockIcon("P")
        val done = compose.onNodeWithContentDescription("Done").fetchSemanticsNode().boundsInRoot

        carry(from = p.center, to = Offset(p.center.x, done.center.y))

        assertEquals(emptyList<HomeDrop>(), dockDrops)
        assertNull("nothing should still be in the air", drag.carried)
    }

    @Test
    fun aDockIconHasANameAndCanBeOpenedWithoutTouchingTheScreen() {
        // The dock draws no labels, so the name has to be given to screen
        // readers some other way, and like the grid its taps are raw touch
        // handlers that offer no "click" unless one is declared.
        show(listOf(app("A")), dockApps = listOf("P", "Q"), editing = false)

        compose.onNode(hasContentDescription("Q") and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals(listOf("Q"), launched)
    }

    @Test
    fun whileEditingEachDockIconCanBeRemoved() {
        show(listOf(app("A")), dockApps = listOf("P", "Q"))

        compose.onAllNodesWithContentDescription("Remove from dock").assertCountEquals(2)
        compose.onAllNodesWithContentDescription("Remove from dock")[1]
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals(listOf("Q"), removedFromDock)
    }

    // --- out of the drawer ---------------------------------------------------

    private fun drawerCellCentre(): Offset =
        compose.onNodeWithTag(SOURCE).fetchSemanticsNode().boundsInRoot.center

    @Test
    fun anAppCarriedOutOfTheDrawerIsStillFollowedAfterItsCellHasGone() {
        show(listOf(app("A"), app("B"), app("C")), dockApps = listOf("P"), editing = false, withDrawerCell = true)

        carry(from = drawerCellCentre(), to = gridCell("B").edge())

        assertTrue("the drawer cell should have gone when its icon was carried off", !drawerCell)
        assertEquals(listOf<HomeDrop>(HomeDrop.Place(key("X"), beforeId = id("B"))), drawerDrops)
        assertEquals("a carry is not a long-press menu", emptyList<String>(), menus)
        assertEquals("nor a tap", emptyList<String>(), launched)
        assertNull("nothing should still be in the air", drag.carried)
    }

    @Test
    fun anAppCarriedOutOfTheDrawerCanBePutInTheDock() {
        show(listOf(app("A")), dockApps = listOf("P", "Q"), editing = false, withDrawerCell = true)
        val q = dockIcon("Q")

        carry(from = drawerCellCentre(), to = Offset(q.left - q.width * 0.3f, q.center.y))

        assertEquals(listOf<HomeDrop>(HomeDrop.Dock(key("X"), beforeKey = key("Q"))), drawerDrops)
    }

    @Test
    fun anAppCarriedOntoAnEmptyHomeScreenBecomesItsFirstIcon() {
        show(emptyList(), dockApps = listOf("P"), editing = false, withDrawerCell = true)
        val p = dockIcon("P")

        // Well above the dock, in the middle of the empty home screen.
        carry(from = drawerCellCentre(), to = Offset(p.center.x, p.top - p.height * 2))

        assertEquals(listOf<HomeDrop>(HomeDrop.Place(key("X"), beforeId = null)), drawerDrops)
    }

    @Test
    fun aLongPressInTheDrawerThatGoesNowhereOpensTheMenu() {
        show(listOf(app("A")), dockApps = emptyList(), editing = false, withDrawerCell = true)
        val at = drawerCellCentre()

        compose.onNodeWithTag(ROOT).performTouchInput {
            down(at)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 200)
            // A resting finger is never still. Less than the distance
            // Android counts as a deliberate movement must not start a
            // carry.
            moveTo(at + Offset(viewConfiguration.touchSlop * 0.4f, 0f))
            up()
        }
        compose.waitForIdle()

        assertEquals(listOf("X"), menus)
        assertEquals(emptyList<HomeDrop>(), drawerDrops)
        assertEquals(emptyList<String>(), launched)
        assertTrue("the cell should still be there", drawerCell)
    }

    @Test
    fun aTapInTheDrawerOpensTheApp() {
        show(listOf(app("A")), dockApps = emptyList(), editing = false, withDrawerCell = true)
        val at = drawerCellCentre()

        compose.onNodeWithTag(ROOT).performTouchInput {
            down(at)
            advanceEventTime(60)
            up()
        }
        compose.waitForIdle()

        assertEquals(listOf("X"), launched)
        assertEquals(emptyList<String>(), menus)
    }

    @Test
    fun aCarryThatIsTakenAwayPutsNothingDown() {
        // The phone rings, or the user presses home, with an icon in the
        // air. Android takes the gesture away rather than ending it. The
        // icon must not be dropped wherever it happened to be.
        show(listOf(app("A"), app("B")), dockApps = listOf("P"), editing = false, withDrawerCell = true)

        carry(from = drawerCellCentre(), to = gridCell("B").edge(), lift = false)
        assertTrue("the icon should be in the air", drag.carried != null)
        compose.onNodeWithTag(ROOT).performTouchInput { cancel() }
        compose.waitForIdle()

        assertEquals(emptyList<HomeDrop>(), drawerDrops)
        assertNull(drag.carried)
    }

    private companion object {
        const val ROOT = "launcher-root"
        const val SOURCE = "drawer-cell"
    }
}
