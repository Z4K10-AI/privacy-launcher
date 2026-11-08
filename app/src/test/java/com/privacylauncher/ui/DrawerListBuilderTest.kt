package com.privacylauncher.ui

import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.FocusSource
import com.privacylauncher.data.FocusState
import com.privacylauncher.data.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the app drawer is made of.
 *
 * The most-used row, the A-Z headers, the focus notice and the fast-scroll
 * letter bar are all built in one pass, and the letter bar's indices point
 * into the row list. An off-by-one there scrolls to the wrong letter and
 * nothing crashes to announce it, which is exactly the kind of bug that
 * survives being looked at.
 */
class DrawerListBuilderTest {

    private fun app(label: String) =
        AppEntry(packageName = "p.${label.lowercase().filter { it.isLetterOrDigit() }}",
            activityName = "A", label = label)

    /** Apps arrive sorted by label; AppRepository does that before this runs. */
    private fun apps(vararg labels: String) =
        labels.map(::app).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

    private fun manyApps(n: Int): List<AppEntry> =
        (0 until n).map { app("${('A' + it % 26)}pp ${"%03d".format(it)}") }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

    private fun build(
        apps: List<AppEntry>,
        settings: LauncherSettings = LauncherSettings(showMostUsed = false),
        query: String = "",
        counts: Map<String, Int> = emptyMap(),
        focus: FocusState = FocusState(),
    ) = DrawerListBuilder.build(apps, settings, query, counts, focus)

    private fun List<DrawerRow>.appLabels() =
        filterIsInstance<DrawerRow.App>().map { it.entry.label }

    // --- the letter bar ---------------------------------------------------

    @Test
    fun `every letter points at its own header row`() {
        // The property the fast-scroll bar depends on.
        val list = build(manyApps(80))
        assertTrue("expected sections", list.letters.isNotEmpty())
        for (jump in list.letters) {
            val row = list.rows.getOrNull(jump.rowIndex)
            assertTrue(
                "letter ${jump.letter} -> index ${jump.rowIndex} is ${row?.javaClass?.simpleName}",
                row is DrawerRow.Header,
            )
            assertEquals(jump.letter, (row as DrawerRow.Header).letter)
        }
    }

    @Test
    fun `the focus notice shifts every letter index by one`() {
        // The notice is prepended, so indices recorded afterwards must
        // already account for it. Building the same list with and without
        // focus is the cleanest way to say that.
        val all = manyApps(80)
        val plain = build(all)
        val focused = build(
            all,
            focus = FocusState(
                source = FocusSource.MANUAL,
                allowed = all.map { it.key }.toSet(),
            ),
        )
        assertTrue(focused.rows.first() is DrawerRow.FocusNotice)
        assertEquals(plain.letters.size, focused.letters.size)
        plain.letters.zip(focused.letters).forEach { (a, b) ->
            assertEquals(a.letter, b.letter)
            assertEquals("letter ${a.letter}", a.rowIndex + 1, b.rowIndex)
        }
        // And the shifted index still lands on the right header.
        for (jump in focused.letters) {
            assertEquals(jump.letter, (focused.rows[jump.rowIndex] as DrawerRow.Header).letter)
        }
    }

    @Test
    fun `sections are emitted once per letter`() {
        // Holds only while the input is sorted, which is the assumption the
        // builder documents.
        val list = build(manyApps(80))
        val letters = list.rows.filterIsInstance<DrawerRow.Header>().map { it.letter }
        assertEquals(letters.distinct(), letters)
        assertEquals(letters, list.letters.map { it.letter })
    }

    @Test
    fun `a short list gets no headers and no letter bar`() {
        val list = build(apps("Alpha", "Beta", "Gamma"))
        assertTrue(list.rows.none { it is DrawerRow.Header })
        assertTrue(list.letters.isEmpty())
        assertEquals(listOf("Alpha", "Beta", "Gamma"), list.rows.appLabels())
    }

    @Test
    fun `sections appear once the list is long enough`() {
        val justUnder = build(manyApps(DrawerListBuilder.MIN_APPS_FOR_SECTIONS - 1))
        val atTheLimit = build(manyApps(DrawerListBuilder.MIN_APPS_FOR_SECTIONS))
        assertTrue(justUnder.letters.isEmpty())
        assertTrue(atTheLimit.letters.isNotEmpty())
    }

    // --- hiding and focus -------------------------------------------------

    @Test
    fun `hidden apps are not in the drawer`() {
        val all = apps("Alpha", "Beta", "Gamma")
        val list = build(all, settings = LauncherSettings(
            showMostUsed = false,
            hiddenApps = setOf(all.first { it.label == "Beta" }.key),
        ))
        assertEquals(listOf("Alpha", "Gamma"), list.rows.appLabels())
        assertEquals(2, list.total)
    }

    @Test
    fun `focus hides the apps it does not allow and counts them`() {
        val all = apps("Alpha", "Beta", "Gamma")
        val allowed = all.first { it.label == "Alpha" }.key
        val list = build(all, focus = FocusState(FocusSource.MANUAL, allowed = setOf(allowed)))
        assertEquals(listOf("Alpha"), list.rows.appLabels())
        val notice = list.rows.first() as DrawerRow.FocusNotice
        assertEquals(2, notice.setAside)
    }

    @Test
    fun `an app set aside by focus does not come back when searched for`() {
        // Typing its name must not defeat focus.
        val all = apps("Alpha", "Beta")
        val list = build(
            all,
            query = "Beta",
            focus = FocusState(FocusSource.MANUAL, allowed = setOf(all.first().key)),
        )
        assertTrue(list.rows.appLabels().none { it == "Beta" })
        assertEquals(0, list.matchCount)
    }

    // --- search -----------------------------------------------------------

    @Test
    fun `searching drops the letter bar and the headers`() {
        val list = build(manyApps(80), query = "app")
        assertTrue(list.letters.isEmpty())
        assertTrue(list.rows.none { it is DrawerRow.Header })
        assertTrue(list.searching)
    }

    @Test
    fun `a search with no matches is empty but still reports the total`() {
        val list = build(apps("Alpha", "Beta"), query = "zzzzz")
        assertEquals(0, list.matchCount)
        assertEquals(2, list.total)
        assertNull(list.topMatch)
        assertTrue(list.rows.none { it is DrawerRow.App })
    }

    @Test
    fun `whitespace alone is not a search`() {
        val list = build(apps("Alpha", "Beta"), query = "   ")
        assertTrue(!list.searching)
        assertEquals(2, list.rows.appLabels().size)
    }

    @Test
    fun `the top match is the first row, for the keyboard's search key`() {
        val list = build(apps("Camera", "Calculator", "Calendar"), query = "cal")
        assertEquals(list.rows.appLabels().first(), list.topMatch?.label)
    }

    // --- most used --------------------------------------------------------

    @Test
    fun `one most-used app is not a row`() {
        // "One icon under a heading is not a row, it is a mistake."
        val all = apps("Alpha", "Beta", "Gamma")
        val list = build(
            all,
            settings = LauncherSettings(showMostUsed = true),
            counts = mapOf(all.first().key to 5),
        )
        assertTrue(list.rows.none { it is DrawerRow.MostUsedHeader })
    }

    @Test
    fun `two or more most-used apps get a heading, capped and ordered`() {
        val all = apps("Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta")
        val counts = all.associate { it.key to all.indexOf(it) + 1 }
        val list = build(all, settings = LauncherSettings(showMostUsed = true), counts = counts)

        assertTrue(list.rows.first() is DrawerRow.MostUsedHeader)
        val mostUsed = list.rows
            .filterIsInstance<DrawerRow.App>()
            .filter { it.section == DrawerListBuilder.SECTION_MOST_USED }
        assertEquals(LauncherSettings.MOST_USED_COUNT, mostUsed.size)
        // Highest count first.
        val used = mostUsed.map { counts.getValue(it.entry.key) }
        assertEquals(used.sortedDescending(), used)
    }

    @Test
    fun `switching most-used off removes the row entirely`() {
        val all = apps("Alpha", "Beta", "Gamma")
        val counts = all.associate { it.key to 5 }
        val list = build(all, settings = LauncherSettings(showMostUsed = false), counts = counts)
        assertTrue(list.rows.none { it is DrawerRow.MostUsedHeader })
    }

    @Test
    fun `a most-used app that focus set aside is not promoted`() {
        val all = apps("Alpha", "Beta", "Gamma")
        val counts = all.associate { it.key to 5 }
        val list = build(
            all,
            settings = LauncherSettings(showMostUsed = true),
            counts = counts,
            focus = FocusState(FocusSource.MANUAL, allowed = setOf(all.first().key)),
        )
        val mostUsed = list.rows
            .filterIsInstance<DrawerRow.App>()
            .filter { it.section == DrawerListBuilder.SECTION_MOST_USED }
        assertTrue("set-aside apps must not reappear as most used", mostUsed.isEmpty())
    }

    // --- empty ------------------------------------------------------------

    @Test
    fun `no apps gives an empty drawer rather than an error`() {
        val list = build(emptyList())
        assertTrue(list.rows.isEmpty())
        assertTrue(list.letters.isEmpty())
        assertEquals(0, list.total)
        assertNull(list.topMatch)
    }

    @Test
    fun `every row id is unique, because Compose keys on it`() {
        // A duplicate key makes LazyColumn throw at runtime.
        val list = build(manyApps(120), settings = LauncherSettings(showMostUsed = true),
            counts = manyApps(120).take(5).associate { it.key to 3 })
        val ids = list.rows.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }
}
