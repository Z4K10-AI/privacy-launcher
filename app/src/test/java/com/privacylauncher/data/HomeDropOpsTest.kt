package com.privacylauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * An icon carried in from the drawer or the dock, or out to the dock.
 *
 * [HomeLayoutOpsTest] covers icons rearranged within the home screen. These
 * are the edits where an icon crosses a boundary, which is where an app can
 * end up on the home screen twice, vanish from both places, or leave a
 * one-app folder behind.
 */
class HomeDropOpsTest {

    private fun app(name: String) = HomeItem.App("p.$name/A")
    private fun key(name: String) = "p.$name/A"
    private fun id(name: String) = "a:p.$name/A"
    private fun folder(id: String, vararg names: String) =
        HomeItem.Folder(id = id, name = id, appKeys = names.map { key(it) })

    private val abc = listOf(app("A"), app("B"), app("C"))

    // --- placing an app on the home screen --------------------------------

    @Test
    fun aNewAppTakesThePlaceOfTheIconItWasDroppedOn() {
        assertEquals(
            listOf(app("A"), app("X"), app("B"), app("C")),
            HomeLayoutOps.placeApp(abc, key("X"), beforeId = id("B")),
        )
    }

    @Test
    fun aNewAppDroppedOnEmptySpaceGoesToTheEnd() {
        assertEquals(abc + app("X"), HomeLayoutOps.placeApp(abc, key("X"), beforeId = null))
    }

    @Test
    fun aNewAppOnAnEmptyHomeScreenIsTheFirstIcon() {
        assertEquals(listOf(app("X")), HomeLayoutOps.placeApp(emptyList(), key("X"), null))
    }

    @Test
    fun aTargetThatHasGoneMeansTheEndRatherThanNothing() {
        // The icon it was aimed at was removed while the finger was down.
        // The user still let go over the home screen, so the app is added.
        assertEquals(abc + app("X"), HomeLayoutOps.placeApp(abc, key("X"), beforeId = id("Z")))
    }

    @Test
    fun anAppAlreadyOnTheHomeScreenIsMovedNotAddedTwice() {
        val out = HomeLayoutOps.placeApp(abc, key("C"), beforeId = id("A"))
        assertEquals(listOf(app("C"), app("A"), app("B")), out)
        assertEquals("C must be there exactly once", 1, out.count { it == app("C") })
    }

    @Test
    fun anAppAlreadyOnTheHomeScreenDroppedOnEmptySpaceMovesToTheEnd() {
        assertEquals(
            listOf(app("B"), app("C"), app("A")),
            HomeLayoutOps.placeApp(abc, key("A"), beforeId = null),
        )
    }

    @Test
    fun anAppDroppedOnItsOwnIconChangesNothing() {
        assertSame(abc, HomeLayoutOps.placeApp(abc, key("B"), beforeId = id("B")))
    }

    @Test
    fun anAppInsideAFolderComesOutOfItWhenPlaced() {
        val items = listOf(app("A"), folder("f1", "X", "Y", "Z"))
        assertEquals(
            listOf(app("X"), app("A"), folder("f1", "Y", "Z")),
            HomeLayoutOps.placeApp(items, key("X"), beforeId = id("A")),
        )
    }

    @Test
    fun aFolderLeftWithOneAppCollapsesToThatApp() {
        val items = listOf(app("A"), folder("f1", "X", "Y"))
        assertEquals(
            listOf(app("X"), app("A"), app("Y")),
            HomeLayoutOps.placeApp(items, key("X"), beforeId = id("A")),
        )
    }

    @Test
    fun anAppCanBePlacedWhereTheFolderItIsLeavingWas() {
        // The target is named by the folder's id, and the folder is about
        // to collapse into a plain icon with a different id. The place has
        // to be found before that happens.
        val items = listOf(app("A"), folder("f1", "X", "Y"), app("B"))
        assertEquals(
            listOf(app("A"), app("X"), app("Y"), app("B")),
            HomeLayoutOps.placeApp(items, key("X"), beforeId = "f1"),
        )
    }

    // --- dropping an app onto an icon -------------------------------------

    @Test
    fun aNewAppDroppedOnAnAppMakesAFolderOfTheTwo() {
        assertEquals(
            listOf(app("A"), HomeItem.Folder("new", "Folder", listOf(key("B"), key("X"))), app("C")),
            HomeLayoutOps.dropAppOnto(abc, key("X"), id("B"), "new", "Folder"),
        )
    }

    @Test
    fun aNewAppDroppedOnAFolderJoinsIt() {
        val items = listOf(app("A"), folder("f1", "P", "Q"))
        assertEquals(
            listOf(app("A"), folder("f1", "P", "Q", "X")),
            HomeLayoutOps.dropAppOnto(items, key("X"), "f1", "new", "Folder"),
        )
    }

    @Test
    fun anAppAlreadyOnTheHomeScreenIsNotLeftBehindWhenDroppedOnAnother() {
        val out = HomeLayoutOps.dropAppOnto(abc, key("A"), id("C"), "new", "Folder")
        assertEquals(
            listOf(app("B"), HomeItem.Folder("new", "Folder", listOf(key("C"), key("A")))),
            out,
        )
    }

    @Test
    fun anAppMovesFromOneFolderToAnother() {
        val items = listOf(folder("f1", "X", "Y", "Z"), folder("f2", "P", "Q"))
        assertEquals(
            listOf(folder("f1", "Y", "Z"), folder("f2", "P", "Q", "X")),
            HomeLayoutOps.dropAppOnto(items, key("X"), "f2", "new", "Folder"),
        )
    }

    @Test
    fun aDropOntoSomethingThatHasGoneChangesNothing() {
        // Unlike a drop on empty space this had one specific meaning, "put
        // it with that icon", and that icon is not there to be put with.
        val items = listOf(app("A"), folder("f1", "X", "Y"))
        assertSame(items, HomeLayoutOps.dropAppOnto(items, key("X"), id("Z"), "new", "Folder"))
    }

    @Test
    fun anAppDroppedOnItselfChangesNothing() {
        assertSame(abc, HomeLayoutOps.dropAppOnto(abc, key("B"), id("B"), "new", "Folder"))
    }

    // --- the dock ----------------------------------------------------------

    private val dock = listOf(key("P"), key("Q"), key("R"))

    @Test
    fun aNewDockIconGoesWhereItWasDropped() {
        assertEquals(
            listOf(key("P"), key("X"), key("Q"), key("R")),
            HomeLayoutOps.placeInDock(dock, key("X"), beforeKey = key("Q"), max = 5),
        )
        assertEquals(dock + key("X"), HomeLayoutOps.placeInDock(dock, key("X"), null, 5))
    }

    @Test
    fun aDockIconCanBeReordered() {
        assertEquals(
            listOf(key("R"), key("P"), key("Q")),
            HomeLayoutOps.placeInDock(dock, key("R"), beforeKey = key("P"), max = 5),
        )
        assertEquals(
            listOf(key("Q"), key("R"), key("P")),
            HomeLayoutOps.placeInDock(dock, key("P"), beforeKey = null, max = 5),
        )
    }

    @Test
    fun aFullDockRefusesANewIconButStillReorders() {
        assertSame(dock, HomeLayoutOps.placeInDock(dock, key("X"), key("P"), max = 3))
        assertEquals(
            listOf(key("Q"), key("P"), key("R")),
            HomeLayoutOps.placeInDock(dock, key("Q"), key("P"), max = 3),
        )
    }

    // --- across the boundary -----------------------------------------------

    @Test
    fun aHomeIconCarriedToTheDockLeavesTheHomeScreen() {
        val out = HomeLayoutOps.homeToDock(HomeAndDock(abc, dock), id("B"), key("P"), max = 5)
        assertEquals(listOf(app("A"), app("C")), out.home)
        assertEquals(listOf(key("B"), key("P"), key("Q"), key("R")), out.dock)
    }

    @Test
    fun nothingIsLostWhenTheDockCannotTakeAHomeIcon() {
        val full = HomeAndDock(abc, dock)
        assertSame("a full dock", full, HomeLayoutOps.homeToDock(full, id("B"), null, max = 3))

        val already = HomeAndDock(abc, listOf(key("B")))
        assertSame(
            "an app already in the dock must not vanish from the home screen",
            already,
            HomeLayoutOps.homeToDock(already, id("B"), null, max = 5),
        )

        val withFolder = HomeAndDock(listOf(folder("f1", "X", "Y")), dock)
        assertSame(
            "a folder has no place in the dock",
            withFolder,
            HomeLayoutOps.homeToDock(withFolder, "f1", null, max = 5),
        )
    }

    @Test
    fun aDockIconCarriedHomeLeavesTheDock() {
        val out = HomeLayoutOps.dockToHome(HomeAndDock(abc, dock), key("Q"), beforeId = id("A"))
        assertEquals(listOf(app("Q"), app("A"), app("B"), app("C")), out.home)
        assertEquals(listOf(key("P"), key("R")), out.dock)
    }

    @Test
    fun aDockIconForAnAppAlreadyOnTheHomeScreenDoesNotAppearTwice() {
        val both = HomeAndDock(abc, listOf(key("C"), key("P")))
        val out = HomeLayoutOps.dockToHome(both, key("C"), beforeId = id("A"))
        assertEquals(listOf(app("C"), app("A"), app("B")), out.home)
        assertEquals(listOf(key("P")), out.dock)
    }

    @Test
    fun aDockIconDroppedOnAHomeIconMakesAFolderAndLeavesTheDock() {
        val out = HomeLayoutOps.dockOntoHome(
            HomeAndDock(abc, dock), key("Q"), id("B"), "new", "Folder",
        )
        assertEquals(
            listOf(app("A"), HomeItem.Folder("new", "Folder", listOf(key("B"), key("Q"))), app("C")),
            out.home,
        )
        assertEquals(listOf(key("P"), key("R")), out.dock)
    }

    @Test
    fun aDockIconDroppedOnSomethingThatHasGoneStaysInTheDock() {
        val layout = HomeAndDock(abc, dock)
        assertSame(layout, HomeLayoutOps.dockOntoHome(layout, key("Q"), id("Z"), "new", "Folder"))
    }

    @Test
    fun onlyAnIconThatIsReallyInTheDockCanLeaveIt() {
        val layout = HomeAndDock(abc, dock)
        assertSame(layout, HomeLayoutOps.dockToHome(layout, key("X"), null))
    }
}
