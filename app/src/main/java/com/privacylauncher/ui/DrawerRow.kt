package com.privacylauncher.ui

import androidx.compose.runtime.Immutable
import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.ContactMatch
import com.privacylauncher.data.SettingsTarget

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
@Immutable
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

    /** Heading over the non-app results. */
    data object SettingsSearchHeader : DrawerRow {
        override val id: String get() = "h:settings"
    }

    data object ContactsHeader : DrawerRow {
        override val id: String get() = "h:contacts"
    }

    data class SettingsScreen(val target: SettingsTarget) : DrawerRow {
        override val id: String get() = target.id
    }

    data class Contact(val match: ContactMatch) : DrawerRow {
        override val id: String get() = match.id
    }

    /**
     * The banner at the top of the drawer while focus mode is on.
     *
     * It carries the way out as well as the explanation. Focus hides most of
     * the drawer, so an "end focus" that lived only in Settings would be
     * found by reading, when it should be found by looking.
     */
    data class FocusNotice(val setAside: Int) : DrawerRow {
        override val id: String get() = "focus-notice"
    }

    /**
     * The row that offers contact search, shown only while searching and
     * only while the feature is off. Tapping it is the one thing in this app
     * that asks for a permission.
     */
    data class ContactPrompt(val permanentlyDenied: Boolean) : DrawerRow {
        override val id: String get() = "contact-prompt"
    }
}

/** A letter in the fast-scroll bar, and the row it scrolls to. */
@Immutable
data class LetterJump(val letter: String, val rowIndex: Int)

/**
 * Everything the drawer draws, built in one pass.
 *
 * Kept as one object rather than several flows so the rows and the letter
 * bar can never disagree about which index a letter sits at.
 */
@Immutable
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
