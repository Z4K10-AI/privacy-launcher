package com.privacylauncher.ui

import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.FocusSource
import com.privacylauncher.data.FocusState
import com.privacylauncher.data.HomeItem
import com.privacylauncher.data.HomeLayoutOps
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Dragging an icon while the home screen is showing less than is stored.
 *
 * The home screen does not always draw everything in the saved layout. Focus
 * mode hides the apps it is not letting through, and an app that was just
 * uninstalled is skipped for the moment before it is cleaned up. So the
 * third icon on screen can be the fifth item on disk.
 *
 * The grid used to report a drag as "position 1 to position 2" and those
 * numbers were applied straight to the saved layout. With nothing hidden the
 * two lists line up and it works, which is why it survived every manual
 * check. With focus on, the drag moved a *different* icon -- quite possibly
 * one the user could not even see -- and the icon under their finger stayed
 * put. Dropping onto another icon was worse: it could put the wrong app in
 * a folder.
 *
 * Every test here builds both lists the way the app does and then performs
 * the drag the way the app does, so it fails if that wiring ever goes back
 * to positions.
 */
class HomeReorderTest {

    private fun entry(k: String) = AppEntry(packageName = k, activityName = "A", label = k)
    private fun key(k: String) = entry(k).key
    private fun app(k: String) = HomeItem.App(key(k))

    private val installed = listOf("a", "b", "c", "d", "e").map(::entry)

    /** Focus on, letting through only the named apps. */
    private fun focusOn(vararg allowed: String) =
        FocusState(source = FocusSource.MANUAL, allowed = allowed.map(::key).toSet())

    private fun names(tiles: List<HomeTile>) = tiles.map {
        when (it) {
            is HomeTile.App -> it.entry.packageName
            is HomeTile.Folder -> "[${it.entries.joinToString("+") { e -> e.packageName }}]"
        }
    }

    private fun stored(items: List<HomeItem>) = items.map {
        when (it) {
            is HomeItem.App -> it.key.substringBefore('/')
            is HomeItem.Folder ->
                "[${it.appKeys.joinToString("+") { k -> k.substringBefore('/') }}]"
        }
    }

    /**
     * A drag from one drawn position to another, done the way the app does
     * it. This is the line under test.
     */
    private fun drag(
        items: List<HomeItem>,
        tiles: List<HomeTile>,
        from: Int,
        to: Int,
    ): List<HomeItem> = HomeLayoutOps.moveById(items, tiles[from].id, tiles[to].id)

    /** A drop of one drawn icon onto another, done the way the app does it. */
    private fun drop(
        items: List<HomeItem>,
        tiles: List<HomeTile>,
        from: Int,
        onto: Int,
    ): List<HomeItem> =
        HomeLayoutOps.combineById(items, tiles[from].id, tiles[onto].id, "f:new", "Folder")

    // --- reordering -------------------------------------------------------

    @Test
    fun `with focus on, the icon that was dragged is the one that moves`() {
        val items = listOf(app("a"), app("b"), app("c"), app("d"))
        val focus = focusOn("a", "c", "d")
        val tiles = resolveHomeTiles(items, installed, focus)
        assertEquals("what is on screen", listOf("a", "c", "d"), names(tiles))

        // The user drags "c" (second on screen) to where "d" is (third).
        val after = drag(items, tiles, from = 1, to = 2)

        assertEquals(listOf("a", "d", "c"), names(resolveHomeTiles(after, installed, focus)))
    }

    @Test
    fun `the hidden icon keeps its place among the ones that did not move`() {
        val items = listOf(app("a"), app("b"), app("c"), app("d"))
        val focus = focusOn("a", "c", "d")
        val tiles = resolveHomeTiles(items, installed, focus)

        val after = drag(items, tiles, from = 1, to = 2)

        // "b" was never touched, so it is still right after "a" when focus
        // ends and everything comes back.
        assertEquals(listOf("a", "b", "d", "c"), stored(after))
    }

    @Test
    fun `dragging backward past a hidden icon lands where it was dropped`() {
        val items = listOf(app("a"), app("b"), app("c"), app("d"))
        val focus = focusOn("a", "c", "d")
        val tiles = resolveHomeTiles(items, installed, focus)

        // "d" (third on screen) to the front.
        val after = drag(items, tiles, from = 2, to = 0)

        assertEquals(listOf("d", "a", "c"), names(resolveHomeTiles(after, installed, focus)))
    }

    @Test
    fun `an uninstalled app still in the layout does not shift the drag`() {
        // "gone" has been uninstalled but not yet cleaned out of storage.
        val items = listOf(app("gone"), app("a"), app("b"), app("c"))
        val tiles = resolveHomeTiles(items, installed, FocusState())
        assertEquals(listOf("a", "b", "c"), names(tiles))

        val after = drag(items, tiles, from = 0, to = 2)

        assertEquals(
            listOf("b", "c", "a"),
            names(resolveHomeTiles(after, installed, FocusState())),
        )
    }

    @Test
    fun `with nothing hidden a drag does what it always did`() {
        val items = listOf(app("a"), app("b"), app("c"), app("d"))
        val tiles = resolveHomeTiles(items, installed, FocusState())

        assertEquals(listOf("b", "c", "a", "d"), stored(drag(items, tiles, 0, 2)))
        assertEquals(listOf("c", "a", "b", "d"), stored(drag(items, tiles, 2, 0)))
    }

    // --- dropping one icon onto another -----------------------------------

    @Test
    fun `with focus on, a folder is made from the two icons that were touched`() {
        val items = listOf(app("a"), app("b"), app("c"), app("d"))
        val focus = focusOn("a", "c", "d")
        val tiles = resolveHomeTiles(items, installed, focus)

        // "d" (third on screen) dropped onto "c" (second on screen).
        val after = drop(items, tiles, from = 2, onto = 1)

        assertEquals(listOf("a", "b", "[c+d]"), stored(after))
    }

    @Test
    fun `a hidden app is never swept into a folder by a drag it was not part of`() {
        val items = listOf(app("a"), app("b"), app("c"), app("d"))
        val focus = focusOn("a", "c", "d")
        val tiles = resolveHomeTiles(items, installed, focus)

        val after = drop(items, tiles, from = 2, onto = 1)

        val inFolders = after.filterIsInstance<HomeItem.Folder>().flatMap { it.appKeys }
        assertEquals(false, key("b") in inFolders)
    }

    @Test
    fun `dropping onto a folder that focus has thinned adds to that folder`() {
        val items = listOf(
            app("a"),
            HomeItem.Folder(id = "f1", name = "Tools", appKeys = listOf(key("b"), key("c"))),
            app("d"),
            app("e"),
        )
        // "a" is hidden, and so is "b" inside the folder.
        val focus = focusOn("c", "d", "e")
        val tiles = resolveHomeTiles(items, installed, focus)
        assertEquals(listOf("[c]", "d", "e"), names(tiles))

        // "e" (third on screen) onto the folder (first on screen).
        val after = drop(items, tiles, from = 2, onto = 0)

        assertEquals(listOf("a", "[b+c+e]", "d"), stored(after))
    }
}
