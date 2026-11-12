package com.privacylauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "What is under this point?", for an icon being carried across the home
 * screen.
 *
 * The screen here is made of round numbers: a grid 400 wide in four columns
 * of 100, rows 100 tall, starting 200 down; a dock under it. A point is
 * then easy to read: (250, 350) is the third column of the second row.
 */
class HomeDropTargetTest {

    private fun id(name: String) = "a:p.$name/A"
    private fun key(name: String) = "p.$name/A"

    /** Five icons, A to E: one full row and one icon on the second. */
    private fun screen(
        tiles: List<String> = listOf("A", "B", "C", "D", "E"),
        dock: List<String> = listOf("P", "Q", "R"),
    ) = HomeGeometry().apply {
        home = Bounds(0f, 0f, 400f, 600f)
        grid = if (tiles.isEmpty()) null else Bounds(0f, 200f, 400f, 400f)
        cellW = 100f
        cellH = 100f
        columns = 4
        tileIds = tiles.map { id(it) }
        this.dock = if (dock.isEmpty()) null else Bounds(0f, 600f, 400f, 700f)
        dockKeys = dock.map { key(it) }
        // Spread evenly across the dock, as the dock draws them.
        dockCentres = dock.indices.map { 400f * (it + 1) / (dock.size + 1) }
    }

    // --- the grid, in its own coordinates ----------------------------------

    @Test
    fun aPointNearTheEdgeOfACellIsAReorder() {
        val hit = gridHit(x = 290f, y = 50f, cellW = 100f, cellH = 100f, columns = 4, count = 5)
        assertEquals(2, hit.index)
        assertFalse(hit.overCentre)
        assertFalse(hit.pastEnd)
    }

    @Test
    fun aPointInTheMiddleOfACellIsOnTheIcon() {
        val hit = gridHit(x = 250f, y = 50f, cellW = 100f, cellH = 100f, columns = 4, count = 5)
        assertEquals(2, hit.index)
        assertTrue(hit.overCentre)
    }

    @Test
    fun theMiddleOfAnEmptyCellIsNotTheMiddleOfTheLastIcon() {
        // Second row, third column: two cells past E, dead centre of an
        // empty cell. The nearest icon is still E, but nothing here says
        // "put it with E".
        val hit = gridHit(x = 250f, y = 150f, cellW = 100f, cellH = 100f, columns = 4, count = 5)
        assertEquals(4, hit.index)
        assertTrue(hit.pastEnd)
        assertFalse("empty space was read as the middle of the last icon", hit.overCentre)
    }

    @Test
    fun aPointOutsideTheGridIsPulledToTheNearestCell() {
        assertEquals(0, gridHit(-30f, -80f, 100f, 100f, 4, 5).index)
        assertEquals(3, gridHit(900f, 20f, 100f, 100f, 4, 5).index)
        assertEquals(4, gridHit(50f, 900f, 100f, 100f, 4, 5).index)
    }

    @Test
    fun inARightToLeftLanguageTheFirstColumnIsOnTheRight() {
        // The rightmost cell of the top row holds the first icon.
        assertEquals(0, gridHit(350f, 50f, 100f, 100f, 4, 5, rtl = true).index)
        assertEquals(3, gridHit(50f, 50f, 100f, 100f, 4, 5, rtl = true).index)
        // And the middle of that cell is still its middle.
        assertTrue(gridHit(350f, 50f, 100f, 100f, 4, 5, rtl = true).overCentre)
        // The second row's only icon, E, is at the right; the cell to its
        // left is empty.
        assertFalse(gridHit(350f, 150f, 100f, 100f, 4, 5, rtl = true).pastEnd)
        assertTrue(gridHit(250f, 150f, 100f, 100f, 4, 5, rtl = true).pastEnd)
    }

    // --- an icon carried in from the drawer or the dock --------------------

    @Test
    fun overTheEdgeOfAnIconItTakesThatIconsPlace() {
        assertEquals(DropTarget.Cell(1), screen().targetFor(190f, 250f, key("X")))
    }

    @Test
    fun overTheMiddleOfAnIconItJoinsIt() {
        assertEquals(DropTarget.Onto(1), screen().targetFor(150f, 250f, key("X")))
    }

    @Test
    fun overItsOwnIconItNeverJoinsItself() {
        // B is already on the home screen and is being carried in again
        // from the dock. Over its own icon, the only sensible meaning is
        // "leave it there".
        assertEquals(DropTarget.Cell(1), screen().targetFor(150f, 250f, key("B")))
    }

    @Test
    fun overEmptySpaceItGoesToTheEnd() {
        val s = screen()
        assertEquals("an empty cell", DropTarget.End, s.targetFor(250f, 350f, key("X")))
        assertEquals("above the grid, over the clock", DropTarget.End, s.targetFor(200f, 80f, key("X")))
        assertEquals("below the grid", DropTarget.End, s.targetFor(200f, 500f, key("X")))
    }

    @Test
    fun onAnEmptyHomeScreenAnywhereIsTheEnd() {
        assertEquals(DropTarget.End, screen(tiles = emptyList()).targetFor(200f, 300f, key("X")))
    }

    @Test
    fun offTheHomeScreenItLandsNowhere() {
        val s = screen()
        assertEquals("under the dock, on the bottom bar", DropTarget.None, s.targetFor(200f, 750f, key("X")))
        s.home = null
        assertEquals("the home screen is not showing", DropTarget.None, s.targetFor(200f, 300f, key("X")))
    }

    // --- the dock -----------------------------------------------------------

    @Test
    fun aNewIconGoesBetweenTheDockIconsEitherSideOfTheFinger() {
        val s = screen() // P at 100, Q at 200, R at 300
        assertEquals(DropTarget.Dock(0, key("P")), s.targetFor(40f, 650f, key("X")))
        assertEquals(DropTarget.Dock(1, key("Q")), s.targetFor(150f, 650f, key("X")))
        assertEquals(DropTarget.Dock(3, null), s.targetFor(380f, 650f, key("X")))
    }

    @Test
    fun aDockIconBeingReorderedIsNotCountedAmongTheOthers() {
        val s = screen()
        // Q carried to the far right: after R, the last of the other two.
        assertEquals(DropTarget.Dock(2, null), s.targetFor(380f, 650f, key("Q")))
        // Q carried to the far left: in front of P.
        assertEquals(DropTarget.Dock(0, key("P")), s.targetFor(20f, 650f, key("Q")))
        // Q hovering over its own place: in front of R, where it already is.
        assertEquals(DropTarget.Dock(1, key("R")), s.targetFor(200f, 650f, key("Q")))
    }

    @Test
    fun aFullDockTakesNothingNewButCanStillBeReordered() {
        val s = screen(dock = listOf("P", "Q", "R", "S", "T"))
        assertEquals(DropTarget.None, s.targetFor(200f, 650f, key("X")))
        assertTrue(s.targetFor(20f, 650f, key("T")) is DropTarget.Dock)
    }

    @Test
    fun aHomeIconAlreadyInTheDockIsNotOfferedTheDock() {
        // From the grid, the trip to the dock removes the icon from the
        // home screen. If the dock already has the app, that is all it
        // would do.
        val s = screen()
        assertNull(s.dockTarget(200f, 650f, key("Q"), newOnly = true))
        assertEquals(DropTarget.Dock(1, key("Q")), s.dockTarget(150f, 650f, key("X"), newOnly = true))
    }

    @Test
    fun aFolderIsNeverOfferedTheDock() {
        // A folder has no app key, which is how "this is a folder" arrives.
        assertNull(screen().dockTarget(200f, 650f, key = null))
    }

    @Test
    fun withNoDockOnScreenThereIsNoDockTarget() {
        val s = screen(dock = emptyList())
        assertNull(s.dockTarget(200f, 650f, key("X")))
    }

    @Test
    fun inARightToLeftLanguageTheDockCountsFromTheRight() {
        val s = screen().apply {
            rtl = true
            // Drawn right to left: P at 300, Q at 200, R at 100.
            dockCentres = listOf(300f, 200f, 100f)
        }
        assertEquals(DropTarget.Dock(0, key("P")), s.targetFor(380f, 650f, key("X")))
        assertEquals(DropTarget.Dock(3, null), s.targetFor(20f, 650f, key("X")))
    }

    // --- what a drop asks for ------------------------------------------------

    @Test
    fun aDropNamesIconsByIdNotByWhereTheyWere() {
        val s = screen()
        assertEquals(HomeDrop.Place(key("X"), id("B")), s.dropFor(DropTarget.Cell(1), key("X")))
        assertEquals(HomeDrop.Onto(key("X"), id("B")), s.dropFor(DropTarget.Onto(1), key("X")))
        assertEquals(HomeDrop.Place(key("X"), null), s.dropFor(DropTarget.End, key("X")))
        assertEquals(
            HomeDrop.Dock(key("X"), key("Q")),
            s.dropFor(DropTarget.Dock(1, key("Q")), key("X")),
        )
        assertNull(s.dropFor(DropTarget.None, key("X")))
    }

    @Test
    fun aDropOnACellThatHasEmptiedIsTreatedAsTheEndOrAsNothing() {
        // The icons changed between the last movement and the finger
        // lifting, and there is now nothing in the cell.
        val s = screen(tiles = listOf("A"))
        assertEquals(HomeDrop.Place(key("X"), null), s.dropFor(DropTarget.Cell(3), key("X")))
        assertNull("nothing left to join", s.dropFor(DropTarget.Onto(3), key("X")))
    }

    // --- the icon in the air -------------------------------------------------

    @Test
    fun aCarryThatHasEndedCannotClearTheNextOne() {
        val drag = HomeDragState()
        val tile = HomeTile.App(
            com.privacylauncher.data.AppEntry(packageName = "p.X", activityName = "A", label = "X"),
        )
        val first = drag.pickUp(tile, DragSource.DOCK, androidx.compose.ui.geometry.Offset.Zero)
        drag.clear(first)
        assertNull(drag.carried)

        val second = drag.pickUp(tile, DragSource.DRAWER, androidx.compose.ui.geometry.Offset.Zero)
        drag.clear(first) // a late callback from the gesture that already ended
        assertEquals("the new carry was dropped by the old gesture", tile, drag.carried)
        drag.clear(second)
        assertNull(drag.carried)
    }
}
