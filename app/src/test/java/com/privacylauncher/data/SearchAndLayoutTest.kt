package com.privacylauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drawer search ranking, and the home layout codec.
 *
 * Search is judged by ordering rather than by score: the numbers are an
 * implementation detail and will drift, but "typing `ca` puts Calculator
 * above Camera" is the thing a user would notice breaking.
 */
class SearchAndLayoutTest {

    private fun entry(label: String) =
        AppEntry(packageName = "p.${label.lowercase().replace(" ", "")}", activityName = "A", label = label)

    private fun rank(query: String, vararg labels: String): List<String> =
        labels.map { entry(it) }
            .map { it to it.score(query) }
            .filter { it.second != Fuzzy.NO_MATCH }
            .sortedByDescending { it.second }
            .map { it.first.label }

    // --- search -----------------------------------------------------------

    @Test
    fun `an exact name beats a prefix, which beats a word, which beats a substring`() {
        val order = rank("photo", "Photo", "Photos", "Google Photo Editor", "Telephoto")
        assertEquals(listOf("Photo", "Photos", "Google Photo Editor", "Telephoto"), order)
    }

    @Test
    fun `a shorter name wins when both match the same way`() {
        // Both start with "ca"; the shorter is more likely the one meant.
        val order = rank("ca", "Cat", "Calculator")
        assertEquals(listOf("Cat", "Calculator"), order)
    }

    @Test
    fun `initials find a multi-word app`() {
        assertNotEquals(Fuzzy.NO_MATCH, entry("Google Play Music").score("gpm"))
        assertNotEquals(Fuzzy.NO_MATCH, entry("Device Policy Manager").score("dpm"))
    }

    @Test
    fun `camel case counts as a word boundary`() {
        // "WebView Browser Tester" is a real entry on the test emulator.
        assertNotEquals(Fuzzy.NO_MATCH, entry("WebView Browser Tester").score("browser"))
        assertNotEquals(Fuzzy.NO_MATCH, entry("VLCPlayer").score("player"))
    }

    @Test
    fun `search ignores case and surrounding spaces`() {
        val plain = entry("Calendar").score("cal")
        assertEquals(plain, entry("Calendar").score("CAL"))
        assertEquals(plain, entry("Calendar").score("  cal  "))
    }

    @Test
    fun `nonsense matches nothing`() {
        assertEquals(Fuzzy.NO_MATCH, entry("Calendar").score("zzzzz"))
        assertEquals(Fuzzy.NO_MATCH, entry("Calendar").score("xqj"))
    }

    @Test
    fun `an empty query is neutral rather than a match or a miss`() {
        // The drawer shows everything when the box is empty; a NO_MATCH here
        // would empty the list the moment the user cleared their search.
        assertEquals(0, entry("Calendar").score(""))
        assertEquals(0, entry("Calendar").score("   "))
    }

    @Test
    fun `apps whose names are not letters still get a section`() {
        assertEquals("#", entry("1Password").sectionLetter)
        assertEquals("#", entry("+Message").sectionLetter)
        assertEquals("C", entry("calendar").sectionLetter)
        assertEquals("C", entry("Calendar").sectionLetter)
    }

    @Test
    fun `an empty label does not blow up the section letter`() {
        assertEquals("#", entry("").sectionLetter)
    }

    // --- home layout codec ------------------------------------------------

    @Test
    fun `layout survives a round trip with folders intact`() {
        val items = listOf(
            HomeItem.App("p1/a1"),
            HomeItem.Folder(id = "f1", name = "Work", appKeys = listOf("p2/a2", "p3/a3")),
            HomeItem.App("p4/a4"),
        )
        assertEquals(items, HomeLayoutCodec.decode(HomeLayoutCodec.encode(items)))
    }

    @Test
    fun `an empty layout round-trips as empty`() {
        assertTrue(HomeLayoutCodec.decode(HomeLayoutCodec.encode(emptyList())).isEmpty())
    }

    @Test
    fun `a corrupt layout costs the layout, not the launcher`() {
        // The one rule: the launcher is the app that cannot fail to start.
        assertTrue(HomeLayoutCodec.decode(null).isEmpty())
        assertTrue(HomeLayoutCodec.decode("").isEmpty())
        assertTrue(HomeLayoutCodec.decode("   ").isEmpty())
        assertTrue(HomeLayoutCodec.decode("{not json").isEmpty())
        assertTrue(HomeLayoutCodec.decode("{\"an\":\"object\"}").isEmpty())
    }

    @Test
    fun `unparseable entries are skipped and the rest kept`() {
        // The stored keys are deliberately one character each -- "t" type,
        // "k" app key, "i" folder id, "n" name, "a" apps -- because this
        // string goes into DataStore on every layout change.
        val json = """[
            {"t":"app","k":"p1/a1"},
            {"t":"nonsense"},
            {"nothing":"useful"},
            {"t":"app","k":""},
            {"t":"folder","i":"f1","n":"Keep","a":["p2/a2"]},
            {"t":"folder","n":"No id","a":["p3/a3"]}
        ]"""
        val items = HomeLayoutCodec.decode(json)
        assertEquals(2, items.size)
        assertEquals(HomeItem.App("p1/a1"), items[0])
        assertEquals("Keep", (items[1] as HomeItem.Folder).name)
    }

    @Test
    fun `item ids are stable and distinguish apps from folders`() {
        assertEquals(HomeItem.App("p/a").id, HomeItem.App("p/a").id)
        assertNotEquals(HomeItem.App("p/a").id, HomeItem.App("p/b").id)
        assertNotEquals(
            HomeItem.App("p/a").id,
            HomeItem.Folder(id = "p/a", name = "n", appKeys = emptyList()).id,
        )
    }
}
