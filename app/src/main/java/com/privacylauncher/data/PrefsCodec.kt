package com.privacylauncher.data

/**
 * How lists and launch counts are written into the one preferences file.
 *
 * Top-level rather than tucked inside [SettingsRepository]'s companion, so
 * they can be tested without a device. They hold user data -- the dock, the
 * hidden set, the usage record -- and a parsing bug here loses it silently.
 */

/** Lines, because neither a component key nor a folder name can contain one. */
internal const val PREF_SEPARATOR = "\n"

internal fun String.decodeList(): List<String> =
    split(PREF_SEPARATOR).filter { it.isNotBlank() }

internal fun List<String>.encodeList(): String = joinToString(PREF_SEPARATOR)

/**
 * Launch counts as "count<tab>key" lines.
 *
 * A component key is "package/activity"; neither part can contain a tab or
 * a newline, so no escaping is needed. Unparseable lines are skipped rather
 * than throwing: a corrupt count costs you one row in the drawer, and the
 * launcher is the one app that cannot fail to start.
 */
internal fun encodeCounts(counts: Map<String, Int>): String =
    counts.entries.joinToString(PREF_SEPARATOR) { "${it.value}\t${it.key}" }

internal fun decodeCounts(raw: String?): Map<String, Int> {
    if (raw.isNullOrEmpty()) return emptyMap()
    return raw.split(PREF_SEPARATOR).mapNotNull { line ->
        val tab = line.indexOf('\t')
        if (tab <= 0) return@mapNotNull null
        val count = line.substring(0, tab).toIntOrNull() ?: return@mapNotNull null
        val key = line.substring(tab + 1)
        if (key.isEmpty()) null else key to count
    }.toMap()
}
