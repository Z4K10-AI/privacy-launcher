package com.privacylauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How user data is written into the preferences file and read back.
 *
 * The dock, the hidden set and the usage record all go through these. A
 * parsing bug here does not crash anything -- it quietly loses a list, which
 * is worse, because nobody notices until the dock is empty one morning.
 */
class PrefsCodecTest {

    // --- lists ------------------------------------------------------------

    @Test
    fun `a list survives a round trip in order`() {
        val list = listOf("p1/a1", "p2/a2", "p3/a3")
        assertEquals(list, list.encodeList().decodeList())
    }

    @Test
    fun `an empty list round-trips as empty`() {
        assertEquals(emptyList<String>(), emptyList<String>().encodeList().decodeList())
        assertEquals(emptyList<String>(), "".decodeList())
    }

    @Test
    fun `blank lines are dropped rather than becoming empty entries`() {
        // An empty entry would be a dock slot holding no app.
        assertEquals(listOf("a", "b"), "a\n\nb\n".decodeList())
        assertEquals(listOf("a", "b"), "\n  \na\nb".decodeList())
    }

    @Test
    fun `a single entry needs no separator`() {
        assertEquals(listOf("only/one"), listOf("only/one").encodeList().decodeList())
    }

    // --- launch counts ----------------------------------------------------

    @Test
    fun `counts survive a round trip`() {
        val counts = mapOf("p1/a1" to 3, "p2/a2" to 17, "p3/a3" to 1)
        assertEquals(counts, decodeCounts(encodeCounts(counts)))
    }

    @Test
    fun `an empty record round-trips as empty`() {
        assertEquals(emptyMap<String, Int>(), decodeCounts(encodeCounts(emptyMap())))
        assertEquals(emptyMap<String, Int>(), decodeCounts(null))
        assertEquals(emptyMap<String, Int>(), decodeCounts(""))
    }

    @Test
    fun `a zero count is kept rather than treated as absent`() {
        assertEquals(mapOf("p/a" to 0), decodeCounts(encodeCounts(mapOf("p/a" to 0))))
    }

    @Test
    fun `a large count survives`() {
        val counts = mapOf("p/a" to Int.MAX_VALUE)
        assertEquals(counts, decodeCounts(encodeCounts(counts)))
    }

    @Test
    fun `a corrupt record costs the mangled lines and keeps the rest`() {
        // The rule the comment states: a bad count costs one drawer row, not
        // the launcher starting.
        val raw = listOf(
            "3\tp1/a1",      // fine
            "notanumber\tp2/a2",
            "nokeyhere",      // no tab at all
            "\tp3/a3",        // tab first, so no count
            "7\t",            // count but no key
            "5\tp4/a4",      // fine
            "",               // blank
        ).joinToString("\n")
        assertEquals(mapOf("p1/a1" to 3, "p4/a4" to 5), decodeCounts(raw))
    }

    @Test
    fun `rubbish decodes to nothing rather than throwing`() {
        assertEquals(emptyMap<String, Int>(), decodeCounts("just some text"))
        assertEquals(emptyMap<String, Int>(), decodeCounts("\n\n\n"))
        assertEquals(emptyMap<String, Int>(), decodeCounts("\t\t\t"))
    }

    @Test
    fun `a key containing a slash and dots is preserved exactly`() {
        // Real component keys look like this and must come back byte for byte,
        // because they are matched against the installed app list.
        val key = "com.example.app/com.example.app.ui.MainActivity"
        assertEquals(mapOf(key to 9), decodeCounts(encodeCounts(mapOf(key to 9))))
    }

    @Test
    fun `the separator assumption holds for real component keys`() {
        // The codec skips escaping on the grounds that neither a package nor
        // an activity name can contain a tab or a newline. That is true of
        // Java identifiers, and this is the test that says so out loud.
        val key = "com.example.app/com.example.app.MainActivity"
        assertTrue(!key.contains('\t'))
        assertTrue(!key.contains('\n'))
    }

    @Test
    fun `many counts round-trip without collisions`() {
        val counts = (1..200).associate { "pkg$it/Activity$it" to it }
        assertEquals(counts, decodeCounts(encodeCounts(counts)))
    }
}
