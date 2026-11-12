package com.privacylauncher.data

import androidx.compose.runtime.Immutable
import java.io.InputStream
import java.util.BitSet

/** One known tracker: a company's advertising, analytics or profiling code. */
@Immutable
data class Tracker(
    /** Exodus Privacy's number for it, which is what the cache stores. */
    val id: Int,
    val name: String,
    /** What Exodus files it under: "Analytics", "Advertisement" and so on. */
    val categories: List<String>,
)

/**
 * The list of known trackers the launcher carries, and the machine that
 * recognises them in the name of a class.
 *
 * **Where the list comes from.** Exodus Privacy publish it under the Open
 * Database License 1.0. `tools/trackers.py` cuts their file down to the
 * four things needed here and writes `assets/trackers.tsv`, which is read
 * by [parse]. The file is inside the app. Nothing is fetched on the phone,
 * and nothing could be: there is no network permission.
 *
 * **What a signature is.** A tracker's code lives in packages its maker
 * named, and the list says which: `com.flurry.` for Flurry, for instance.
 * Every app's code carries the names of its own classes, so a class called
 * `com.flurry.sdk.Something` gives Flurry away. A signature is the start of
 * such a name.
 *
 * **How a name is matched.** Exodus's own tool treats a signature as a
 * pattern that may occur anywhere in a class name. This reads it a little
 * more strictly: a signature has to start where a part of the name starts,
 * the beginning or just after a dot. So `io.branch.` is found in
 * `io.branch.referral.Branch` but not in `radio.branch.Tuner`, which is not
 * Branch's code and which the looser reading would report. A signature
 * written with a dot in front, such as `.sizmek.`, means "this part, but
 * not at the very beginning", and is read that way.
 *
 * **Why a tree rather than a loop.** There are close to six hundred
 * signatures, and a large app defines a hundred thousand classes. Trying
 * every signature against every class is sixty million comparisons per
 * app. Here the signatures are folded into one tree of letters, a class
 * name is walked through it once from each of its dots, and the cost is a
 * few dozen steps a class. The tree is three small arrays, a little over a
 * hundred kilobytes, and it is only built when a scan is asked for.
 */
class TrackerList private constructor(
    /** The date the list was taken from Exodus, as written in the file. */
    val snapshot: String,
    /** Every tracker, in the order of the file. Positions index the result sets. */
    val trackers: List<Tracker>,
    private val label: ByteArray,
    private val firstChild: IntArray,
    private val nextSibling: IntArray,
    /** Trackers, by position, one of whose signatures ends at this node. */
    private val ends: Array<IntArray?>,
    /** The same, for signatures that may not start at the beginning of a name. */
    private val innerEnds: Array<IntArray?>,
) {
    private val byId: Map<Int, Tracker> = trackers.associateBy { it.id }

    /** How many signatures were read, across all trackers. */
    val signatureCount: Int =
        ends.sumOf { it?.size ?: 0 } + innerEnds.sumOf { it?.size ?: 0 }

    fun byId(id: Int): Tracker? = byId[id]

    /**
     * Marks in [found] every tracker recognised in one class name.
     *
     * [descriptor] is the name as a dex file stores it: `Lcom/flurry/sdk/a;`
     * with slashes for dots, an `L` in front and a semicolon behind.
     * Only the first [length] bytes are looked at. Working on the stored
     * bytes means no text is built for a class that matches nothing, which
     * is nearly all of them.
     */
    fun match(descriptor: ByteArray, length: Int, found: BitSet) {
        if (length < 3 || descriptor[0] != 'L'.code.toByte()) return
        var start = 1
        var first = true
        while (start < length) {
            walk(descriptor, start, length, first, found)
            // On to the next part of the name: just past the next slash.
            var next = start
            while (next < length && descriptor[next] != SLASH && descriptor[next] != END) next++
            if (next >= length || descriptor[next] == END) return
            start = next + 1
            first = false
        }
    }

    private fun walk(d: ByteArray, from: Int, length: Int, first: Boolean, found: BitSet) {
        var node = 0
        var i = from
        while (i < length) {
            val b = d[i]
            if (b == END) return
            var child = firstChild[node]
            while (child != NONE && label[child] != b) child = nextSibling[child]
            if (child == NONE) return
            node = child
            ends[node]?.forEach { found.set(it) }
            if (!first) innerEnds[node]?.forEach { found.set(it) }
            i++
        }
    }

    /** The trackers recognised in a class name written the ordinary way, with dots. */
    fun matchName(className: String): List<Tracker> {
        val bytes = ("L" + className.replace('.', '/') + ";").toByteArray(Charsets.ISO_8859_1)
        val found = BitSet(trackers.size)
        match(bytes, bytes.size, found)
        return trackersIn(found)
    }

    /** The trackers whose positions are set in [found], in list order. */
    fun trackersIn(found: BitSet): List<Tracker> = buildList {
        var i = found.nextSetBit(0)
        while (i >= 0 && i < trackers.size) {
            add(trackers[i])
            i = found.nextSetBit(i + 1)
        }
    }

    private class Builder {
        val label = ArrayList<Byte>().apply { add(0) }
        val firstChild = ArrayList<Int>().apply { add(NONE) }
        val nextSibling = ArrayList<Int>().apply { add(NONE) }
        val ends = HashMap<Int, MutableList<Int>>()
        val innerEnds = HashMap<Int, MutableList<Int>>()

        fun add(signature: String, tracker: Int) {
            val inner = signature.startsWith(".")
            val text = signature.removePrefix(".")
            if (text.isEmpty()) return
            var node = 0
            for (c in text) {
                // A character that cannot be one byte cannot be in a class
                // name a signature could describe. Skip the signature.
                if (c.code > 0x7f) return
                val b = (if (c == '.') '/' else c).code.toByte()
                var child = firstChild[node]
                while (child != NONE && label[child] != b) child = nextSibling[child]
                if (child == NONE) {
                    child = label.size
                    label.add(b)
                    firstChild.add(NONE)
                    nextSibling.add(firstChild[node])
                    firstChild[node] = child
                }
                node = child
            }
            val into = if (inner) innerEnds else ends
            val list = into.getOrPut(node) { mutableListOf() }
            if (tracker !in list) list += tracker
        }

        fun build(snapshot: String, trackers: List<Tracker>): TrackerList {
            val size = label.size
            return TrackerList(
                snapshot = snapshot,
                trackers = trackers,
                label = ByteArray(size) { label[it] },
                firstChild = IntArray(size) { firstChild[it] },
                nextSibling = IntArray(size) { nextSibling[it] },
                ends = Array(size) { ends[it]?.toIntArray() },
                innerEnds = Array(size) { innerEnds[it]?.toIntArray() },
            )
        }
    }

    companion object {
        private const val NONE = -1
        private const val SLASH = '/'.code.toByte()
        private const val END = ';'.code.toByte()

        /** A signature shorter than this would match half the phone. */
        private const val SHORTEST_SIGNATURE = 4

        /**
         * Reads the file `tools/trackers.py` writes.
         *
         * A line it cannot make sense of is skipped, not fatal. The file is
         * part of the app and is checked when it is made, so this should
         * never matter; but a scanner that cannot start because of one bad
         * line would be a poor trade for strictness.
         */
        fun parse(input: InputStream): TrackerList {
            val builder = Builder()
            val trackers = ArrayList<Tracker>()
            var snapshot = ""
            input.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                if (line.startsWith("#")) {
                    val parts = line.removePrefix("#").trim().split('\t')
                    if (parts.size == 2 && parts[0] == "snapshot") snapshot = parts[1].trim()
                    return@forEachLine
                }
                val fields = line.split('\t')
                if (fields.size != 4) return@forEachLine
                val id = fields[0].toIntOrNull() ?: return@forEachLine
                val name = fields[1].trim()
                val signatures = fields[3].split('|')
                    .map { it.trim() }
                    .filter { it.trim('.').length >= SHORTEST_SIGNATURE }
                if (name.isEmpty() || signatures.isEmpty()) return@forEachLine

                val position = trackers.size
                trackers += Tracker(
                    id = id,
                    name = name,
                    categories = fields[2].split(',').map { it.trim() }.filter { it.isNotEmpty() },
                )
                signatures.forEach { builder.add(it, position) }
            }
            return builder.build(snapshot, trackers)
        }
    }
}
