package com.privacylauncher.ui

import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.FocusState
import com.privacylauncher.data.Fuzzy
import com.privacylauncher.data.LauncherSettings

/**
 * Builds everything the app drawer draws, in one pass.
 *
 * Pulled out of [LauncherViewModel] so it can be tested: it was already a
 * pure function of its five arguments, just not reachable from a test. The
 * part most worth pinning down is the fast-scroll letter bar, whose indices
 * point into the row list -- an off-by-one there sends a letter to the
 * wrong place and nothing crashes to say so.
 *
 * It assumes [apps] arrives sorted by label, case-insensitively, which is
 * how AppRepository hands it over. Unsorted input would emit the same
 * section letter more than once; `sections are emitted once per letter`
 * in DrawerListBuilderTest is what holds that assumption in place.
 */
internal object DrawerListBuilder {

    const val SECTION_SEARCH = "q"
    const val SECTION_MOST_USED = "mu"
    const val SECTION_PLAIN = "a"

    /**
     * Below this many apps the drawer is a plain alphabetical grid.
     *
     * A heading takes a whole line, so sectioning a short list spreads a
     * screenful of icons over several screens of mostly empty space. The
     * headings only start paying for themselves once each one has a few
     * icons under it, and at that point the list is long enough to be
     * worth skipping through.
     */
    const val MIN_APPS_FOR_SECTIONS = 24

fun build(
    apps: List<AppEntry>,
    s: LauncherSettings,
    rawQuery: String,
    counts: Map<String, Int>,
    focus: FocusState,
): DrawerList {
    val notHidden = apps.filterNot { it.key in s.hiddenApps }
    // Focus filters the same list the rest of the drawer is built from,
    // search included: an app you have set aside should not come back the
    // moment you type its name.
    val visible = if (focus.active) {
        notHidden.filter { focus.allows(it.key) }
    } else {
        notHidden
    }
    val setAside = notHidden.size - visible.size
    val total = visible.size
    val query = rawQuery.trim()

    val rows = mutableListOf<DrawerRow>()
    // First, so the letter-bar indices recorded below already account for
    // it, and so it is the first thing read out by a screen reader.
    if (focus.active) rows += DrawerRow.FocusNotice(setAside)

    if (query.isNotEmpty()) {
        val scored = visible
            .map { it to it.score(query) }
            .filter { it.second != Fuzzy.NO_MATCH }
            .sortedWith(
                compareByDescending<Pair<AppEntry, Int>> { it.second }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.first.label },
            )
        scored.forEach { rows += DrawerRow.App(it.first, SECTION_SEARCH) }
        return DrawerList(
            rows = rows,
            letters = emptyList(),
            matchCount = scored.size,
            total = total,
            topMatch = scored.firstOrNull()?.first,
            searching = true,
        )
    }

    val letters = mutableListOf<LetterJump>()

    if (s.showMostUsed) {
        val top = visible
            .filter { counts.containsKey(it.key) }
            .sortedByDescending { counts[it.key] ?: 0 }
            .take(LauncherSettings.MOST_USED_COUNT)
        // One icon under a heading is not a row, it is a mistake.
        if (top.size >= 2) {
            rows += DrawerRow.MostUsedHeader
            top.forEach { rows += DrawerRow.App(it, SECTION_MOST_USED) }
        }
    }

    if (visible.size < MIN_APPS_FOR_SECTIONS) {
        visible.forEach { rows += DrawerRow.App(it, SECTION_PLAIN) }
    } else {
        var section: String? = null
        for (entry in visible) {
            if (entry.sectionLetter != section) {
                section = entry.sectionLetter
                letters += LetterJump(section, rows.size)
                rows += DrawerRow.Header(section)
            }
            rows += DrawerRow.App(entry, section)
        }
    }

    return DrawerList(
        rows = rows,
        letters = letters,
        matchCount = total,
        total = total,
        topMatch = visible.firstOrNull(),
        searching = false,
    )
}
}
