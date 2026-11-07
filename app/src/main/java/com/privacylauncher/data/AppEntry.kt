package com.privacylauncher.data

/**
 * One launchable app, as shown in the drawer.
 *
 * This holds only text. The icon is a [android.graphics.drawable.Drawable] that
 * costs memory to decode, so it is fetched separately and on demand by
 * [IconLoader] when a row actually scrolls into view.
 */
data class AppEntry(
    val packageName: String,
    /** Fully-qualified name of the activity with the LAUNCHER intent filter. */
    val activityName: String,
    val label: String,
) {
    /** Stable identity. One package can expose more than one launcher icon. */
    val key: String = "$packageName/$activityName"

    /**
     * Pre-chewed pieces of the label for searching.
     *
     * Built once when the app list is loaded, not per keystroke. Search runs
     * over every installed app each time a letter is typed, so anything
     * derivable from the label alone belongs here.
     */
    val terms: Fuzzy.Terms = Fuzzy.Terms(label)

    /** The A-Z section this app belongs in. Non-letters all share "#". */
    val sectionLetter: String =
        label.firstOrNull()?.uppercaseChar()
            ?.takeIf { it.isLetter() }?.toString()
            ?: "#"

    /** Higher is a better match; [Fuzzy.NO_MATCH] means no match at all. */
    fun score(query: String): Int = Fuzzy.score(terms, query)
}
