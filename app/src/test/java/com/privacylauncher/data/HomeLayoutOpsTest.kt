package com.privacylauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rearranging the home screen.
 *
 * This is index arithmetic that has to stay right while a finger drags an
 * icon across a grid, and until these tests existed it was the least
 * verifiable code in the app: every one of these cases was previously
 * checked, if at all, by dragging an icon and looking.
 */
class HomeLayoutOpsTest {

    private fun app(k: String) = HomeItem.App(k)
    private fun folder(id: String, vararg keys: String) =
        HomeItem.Folder(id = id, name = "F", appKeys = keys.toList())

    private val abcd = listOf(app("a"), app("b"), app("c"), app("d"))

    private fun keys(items: List<HomeItem>) = items.map {
        when (it) {
            is HomeItem.App -> it.key
            is HomeItem.Folder -> "[${it.appKeys.joinToString("+")}]"
        }
    }

    // --- move -------------------------------------------------------------

    @Test
    fun `moving forward lands at the requested position`() {
        assertEquals(listOf("b", "c", "a", "d"), keys(HomeLayoutOps.move(abcd, 0, 2)))
    }

    @Test
    fun `moving backward lands at the requested position`() {
        assertEquals(listOf("c", "a", "b", "d"), keys(HomeLayoutOps.move(abcd, 2, 0)))
    }

    @Test
    fun `first to last and last to first both work`() {
        assertEquals(listOf("b", "c", "d", "a"), keys(HomeLayoutOps.move(abcd, 0, 3)))
        assertEquals(listOf("d", "a", "b", "c"), keys(HomeLayoutOps.move(abcd, 3, 0)))
    }

    @Test
    fun `moving onto itself changes nothing`() {
        assertEquals(keys(abcd), keys(HomeLayoutOps.move(abcd, 2, 2)))
    }

    @Test
    fun `a drag past either end is clamped, not dropped`() {
        // A finger can go past the grid; the item should land at the edge.
        assertEquals(listOf("b", "c", "d", "a"), keys(HomeLayoutOps.move(abcd, 0, 99)))
        assertEquals(listOf("d", "a", "b", "c"), keys(HomeLayoutOps.move(abcd, 3, -5)))
    }

    @Test
    fun `an impossible source index is ignored`() {
        assertSame(abcd, HomeLayoutOps.move(abcd, 9, 0))
        assertSame(abcd, HomeLayoutOps.move(abcd, -1, 0))
        assertSame(emptyList<HomeItem>(), HomeLayoutOps.move(emptyList(), 0, 0))
    }

    @Test
    fun `moving never loses or duplicates an item`() {
        // The property that matters most: whatever the drag, the same set of
        // apps is on the screen afterwards.
        for (from in abcd.indices) {
            for (to in -2..5) {
                val out = HomeLayoutOps.move(abcd, from, to)
                assertEquals("from=$from to=$to", abcd.size, out.size)
                assertEquals("from=$from to=$to", abcd.toSet(), out.toSet())
            }
        }
    }

    // --- combine ----------------------------------------------------------

    @Test
    fun `app onto app makes a folder holding the target then the dragged`() {
        val out = HomeLayoutOps.combine(abcd, from = 0, onto = 1, newFolderId = "f1", folderName = "New")
        assertEquals(listOf("[b+a]", "c", "d"), keys(out))
        val f = out.first() as HomeItem.Folder
        assertEquals("f1", f.id)
        assertEquals("New", f.name)
    }

    @Test
    fun `dragging backward onto an earlier item puts the folder in the right place`() {
        // from > onto: the folder stays where the target was.
        val out = HomeLayoutOps.combine(abcd, from = 3, onto = 1, newFolderId = "f1", folderName = "New")
        assertEquals(listOf("a", "[b+d]", "c"), keys(out))
    }

    @Test
    fun `dragging forward onto a later item puts the folder in the right place`() {
        // from < onto: removing the dragged item shifts the folder down one.
        val out = HomeLayoutOps.combine(abcd, from = 0, onto = 2, newFolderId = "f1", folderName = "New")
        assertEquals(listOf("b", "[c+a]", "d"), keys(out))
    }

    @Test
    fun `app onto folder appends to that folder`() {
        val items = listOf(app("a"), folder("f1", "x", "y"))
        val out = HomeLayoutOps.combine(items, from = 0, onto = 1, newFolderId = "ignored", folderName = "New")
        assertEquals(listOf("[x+y+a]"), keys(out))
        assertEquals("f1", (out.single() as HomeItem.Folder).id)
    }

    @Test
    fun `a folder dragged onto anything does nothing`() {
        // Merging folders would silently destroy one of the two names.
        val items = listOf(folder("f1", "x"), app("a"), folder("f2", "y"))
        assertSame(items, HomeLayoutOps.combine(items, 0, 1, "n", "New"))
        assertSame(items, HomeLayoutOps.combine(items, 2, 0, "n", "New"))
    }

    @Test
    fun `combining with itself or out of range does nothing`() {
        assertSame(abcd, HomeLayoutOps.combine(abcd, 1, 1, "n", "New"))
        assertSame(abcd, HomeLayoutOps.combine(abcd, 0, 9, "n", "New"))
        assertSame(abcd, HomeLayoutOps.combine(abcd, -1, 0, "n", "New"))
    }

    @Test
    fun `combining never loses an app`() {
        for (from in abcd.indices) {
            for (onto in abcd.indices) {
                val out = HomeLayoutOps.combine(abcd, from, onto, "f", "New")
                val flat = out.flatMap {
                    when (it) {
                        is HomeItem.App -> listOf(it.key)
                        is HomeItem.Folder -> it.appKeys
                    }
                }
                assertEquals("from=$from onto=$onto", setOf("a", "b", "c", "d"), flat.toSet())
            }
        }
    }

    // --- dissolve ---------------------------------------------------------

    @Test
    fun `a folder of one collapses to a plain icon and an empty one disappears`() {
        val items = listOf(folder("f0"), folder("f1", "x"), folder("f2", "y", "z"), app("a"))
        assertEquals(listOf("x", "[y+z]", "a"), keys(HomeLayoutOps.dissolveThinFolders(items)))
    }

    // --- removeFromFolder -------------------------------------------------

    @Test
    fun `taking the second-to-last app out of a folder dissolves it`() {
        val items = listOf(app("a"), folder("f1", "x", "y"))
        assertEquals(listOf("a", "y"), keys(HomeLayoutOps.removeFromFolder(items, "f1", "x")))
    }

    @Test
    fun `removing from a folder leaves other folders alone`() {
        val items = listOf(folder("f1", "x", "y", "z"), folder("f2", "x", "y"))
        val out = HomeLayoutOps.removeFromFolder(items, "f1", "x")
        assertEquals(listOf("[y+z]", "[x+y]"), keys(out))
    }

    // --- rename -----------------------------------------------------------

    @Test
    fun `renaming trims and touches only the named folder`() {
        val items = listOf(folder("f1", "x", "y"), folder("f2", "p", "q"))
        val out = HomeLayoutOps.renameFolder(items, "f1", "  Work  ")
        assertEquals("Work", (out[0] as HomeItem.Folder).name)
        assertEquals("F", (out[1] as HomeItem.Folder).name)
    }

    // --- prune ------------------------------------------------------------

    @Test
    fun `uninstalled apps go, installed ones stay`() {
        val items = listOf(app("a"), app("gone"), folder("f1", "b", "gone", "c"))
        val out = HomeLayoutOps.prune(items, setOf("a", "b", "c"))
        assertEquals(listOf("a", "[b+c]"), keys(out))
    }

    @Test
    fun `a folder thinned by pruning dissolves too`() {
        val items = listOf(folder("f1", "b", "gone"))
        assertEquals(listOf("b"), keys(HomeLayoutOps.prune(items, setOf("b"))))
    }

    @Test
    fun `pruning with everything installed changes nothing`() {
        val items = listOf(app("a"), folder("f1", "b", "c"))
        assertEquals(items, HomeLayoutOps.prune(items, setOf("a", "b", "c")))
    }

    @Test
    fun `pruning everything leaves an empty screen rather than an error`() {
        val items = listOf(app("a"), folder("f1", "b", "c"))
        assertTrue(HomeLayoutOps.prune(items, emptySet()).isEmpty())
    }

    // --- dock -------------------------------------------------------------

    @Test
    fun `the dock takes an app, refuses a duplicate, and stops when full`() {
        val max = LauncherSettings.MAX_DOCK_APPS
        var dock = emptyList<String>()
        repeat(max) { dock = HomeLayoutOps.addToDock(dock, "app$it", max) }
        assertEquals(max, dock.size)

        // Full: one more is refused, and the same list comes back.
        assertSame(dock, HomeLayoutOps.addToDock(dock, "extra", max))
        // Duplicate: also refused.
        assertSame(dock, HomeLayoutOps.addToDock(dock, "app0", max))
    }
}
