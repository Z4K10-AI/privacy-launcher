package com.privacylauncher.ui

import androidx.compose.runtime.Immutable
import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.FocusState
import com.privacylauncher.data.HomeItem

/**
 * A home screen item with its apps already resolved to [AppEntry]s.
 *
 * The stored form (`HomeItem`) holds component keys; this is the drawn form.
 * Resolving once in the ViewModel keeps the lookup out of composition, where
 * it would run on every recomposition and every drag frame.
 */
@Immutable
sealed interface HomeTile {
    val id: String

    data class App(val entry: AppEntry) : HomeTile {
        override val id: String get() = "a:${entry.key}"
    }

    data class Folder(
        override val id: String,
        val name: String,
        val entries: List<AppEntry>,
    ) : HomeTile
}

/**
 * Turns the stored layout into what the home screen draws.
 *
 * Two things are left out: keys that do not resolve to an installed app, and
 * anything focus mode is not letting through. A folder is filtered rather
 * than hidden whole -- with one allowed app inside it, hiding the folder
 * would put that app out of reach for no reason -- and vanishes only when
 * nothing in it is left.
 *
 * The consequence that matters: **the drawn list can be shorter than the
 * stored one, so a position in one is not a position in the other.** The
 * third icon on screen may be the fifth item on disk. Anything that edits
 * the layout from something the user did on screen must name tiles by
 * [HomeTile.id], never by where they were drawn. [HomeReorderTest] holds
 * that in place.
 *
 * A function rather than a lambda inside the ViewModel so that test can call
 * it: the bug it guards against lived in the gap between this list and the
 * stored one, and could not be shown without building both.
 */
internal fun resolveHomeTiles(
    items: List<HomeItem>,
    apps: List<AppEntry>,
    focus: FocusState,
): List<HomeTile> {
    val byKey = apps.associateBy { it.key }
    return items.mapNotNull { item ->
        when (item) {
            is HomeItem.App ->
                byKey[item.key]?.takeIf { focus.allows(it.key) }?.let { HomeTile.App(it) }
            is HomeItem.Folder -> {
                val entries = item.appKeys.mapNotNull { byKey[it] }
                    .filter { focus.allows(it.key) }
                if (entries.isEmpty()) null
                else HomeTile.Folder(item.id, item.name, entries)
            }
        }
    }
}
