package com.privacylauncher.data

/**
 * Fuzzy matching for the drawer search box.
 *
 * Runs over every app on every keystroke, so it is plain string work with no
 * allocation per comparison beyond what Kotlin forces: the per-item pieces it
 * needs (lowercased label, words, initials) are computed once when the app
 * list is built and handed back in as [Terms].
 *
 * Scoring is tiered rather than a single distance measure. What someone means
 * by "ca" is almost always "an app whose name starts with ca", and a tiered
 * score keeps Camera above Calculator above Vocabulary Trainer without any
 * tuning. The tiers, highest first:
 *
 *  - the whole name, exactly
 *  - the name starts with the query
 *  - some word in the name starts with it ("maps" -> Google Maps)
 *  - the initials start with it ("gm" -> Google Maps)
 *  - the name contains it anywhere
 *  - the letters appear in order with gaps ("gmp" -> Google Maps)
 *
 * Within a tier, shorter names win, because a short name that matches is
 * usually the one that was meant.
 */
object Fuzzy {

    /** Returned when the query does not match at all. */
    const val NO_MATCH = -1

    /** The pre-computed pieces of one searchable name. */
    class Terms(label: String) {
        val lower: String = label.lowercase()

        // Split on the original casing, then lowercase. Lowercasing first
        // would throw away the camel-case boundary the splitter relies on.
        val words: List<String> = splitWords(label).map { it.lowercase() }
        val initials: String = words.joinToString("") { it.take(1) }
    }

    fun score(terms: Terms, query: String): Int {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return 0

        val lower = terms.lower
        if (lower == q) return 10_000
        if (lower.startsWith(q)) return 9_000 - lower.length
        if (terms.words.any { it.startsWith(q) }) return 8_000 - lower.length
        if (terms.initials.startsWith(q)) return 7_500 - lower.length
        if (terms.initials.contains(q)) return 7_000 - lower.length

        val at = lower.indexOf(q)
        if (at >= 0) return 6_000 - at * 10 - lower.length

        // Only worth trying for two letters or more. For one letter a
        // subsequence match is the same thing as "contains", already handled
        // above, and would otherwise match practically everything.
        if (q.length < 2) return NO_MATCH
        return subsequence(lower, q)
    }

    /** Convenience for short, one-off lists such as settings screens. */
    fun score(label: String, query: String): Int = score(Terms(label), query)

    /**
     * Scores a run of the query's letters appearing in order.
     *
     * Gaps cost, and letters that land next to each other earn some of it
     * back, so "gmp" prefers "Google Maps" to a name where g, m and p happen
     * to be scattered far apart.
     */
    private fun subsequence(target: String, query: String): Int {
        var from = 0
        var gaps = 0
        var run = 0
        var bestRun = 0
        for (c in query) {
            val found = target.indexOf(c, from)
            if (found < 0) return NO_MATCH
            if (found == from && from > 0) {
                run++
                if (run > bestRun) bestRun = run
            } else {
                run = 0
                gaps += found - from
            }
            from = found + 1
        }
        return (5_000 - gaps.coerceAtMost(400) + bestRun * 20 - target.length)
            .coerceAtLeast(1)
    }

    /**
     * Splits a name into words.
     *
     * Separators plus a lower-to-upper transition, so "WebView Browser"
     * yields web, view, browser and its initials are "wvb". That is how
     * someone would actually type it.
     */
    private fun splitWords(label: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var previous: Char? = null
        for (c in label) {
            when {
                !c.isLetterOrDigit() -> {
                    if (current.isNotEmpty()) {
                        out += current.toString()
                        current.clear()
                    }
                }
                previous != null && previous.isLowerCase() && c.isUpperCase() -> {
                    if (current.isNotEmpty()) {
                        out += current.toString()
                        current.clear()
                    }
                    current.append(c)
                }
                else -> current.append(c)
            }
            previous = c
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }
}
