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
     * True if [query] looks like a match for this app's name.
     *
     * Matches a plain substring ("tele" -> Telegram) and also initials
     * ("gm" -> Google Maps), which is how most launcher searches behave.
     */
    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim()
        if (label.contains(q, ignoreCase = true)) return true
        val initials = label
            .split(' ', '-', '_', '.')
            .filter { it.isNotEmpty() }
            .map { it.first() }
            .joinToString("")
        return initials.startsWith(q, ignoreCase = true)
    }
}
