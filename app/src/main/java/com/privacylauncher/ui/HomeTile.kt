package com.privacylauncher.ui

import com.privacylauncher.data.AppEntry

/**
 * A home screen item with its apps already resolved to [AppEntry]s.
 *
 * The stored form (`HomeItem`) holds component keys; this is the drawn form.
 * Resolving once in the ViewModel keeps the lookup out of composition, where
 * it would run on every recomposition and every drag frame.
 */
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
