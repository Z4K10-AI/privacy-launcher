package com.privacylauncher.ui

import com.privacylauncher.data.AppEntry

/**
 * One slot in the drawer grid.
 *
 * The grid is a single flat list so that a row's position in it is also its
 * index in the LazyVerticalGrid, which is what the fast-scroll letter bar
 * needs in order to jump. Headers take a whole line; apps take one cell.
 *
 * [id] rather than the app key, because the most-used row shows apps that
 * also appear further down under their letter, and a LazyVerticalGrid's keys
 * have to be unique.
 */
sealed interface DrawerRow {
    val id: String

    /** An A-Z section heading. */
    data class Header(val letter: String) : DrawerRow {
        override val id: String get() = "h:$letter"
    }

    /**
     * The "most used" heading. A separate type rather than a Header carrying
     * a title, so the wording stays in strings.xml where it can be
     * translated, instead of being built in the view model.
     */
    data object MostUsedHeader : DrawerRow {
        override val id: String get() = "h:most-used"
    }

    data class App(val entry: AppEntry, val section: String) : DrawerRow {
        override val id: String get() = "$section:${entry.key}"
    }
}

/** A letter in the fast-scroll bar, and the row it scrolls to. */
data class LetterJump(val letter: String, val rowIndex: Int)

/**
 * Everything the drawer draws, built in one pass.
 *
 * Kept as one object rather than several flows so the rows and the letter
 * bar can never disagree about which index a letter sits at.
 */
data class DrawerList(
    val rows: List<DrawerRow> = emptyList(),
    val letters: List<LetterJump> = emptyList(),
    /** How many apps are shown. With no search, the whole drawer. */
    val matchCount: Int = 0,
    /** How many apps the drawer would show with no search. */
    val total: Int = 0,
    /** Best match, for the keyboard's Search key. */
    val topMatch: AppEntry? = null,
    val searching: Boolean = false,
)
