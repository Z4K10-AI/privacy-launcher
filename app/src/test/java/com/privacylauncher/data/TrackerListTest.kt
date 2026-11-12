package com.privacylauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The tracker list, and recognising a tracker in the name of a class.
 *
 * The first group reads the real file that ships in the app, so a snapshot
 * that came out empty or garbled fails here and not on someone's phone.
 */
class TrackerListTest {

    private val shipped: TrackerList by lazy {
        // Unit tests run from the app module's own folder.
        File("src/main/assets/trackers.tsv").inputStream().use(TrackerList::parse)
    }

    private fun names(className: String) = shipped.matchName(className).map { it.name }

    private fun list(vararg lines: String) =
        TrackerList.parse(lines.joinToString("\n").byteInputStream())

    // --- the file that ships ---------------------------------------------------

    @Test
    fun theShippedListIsThereAndSaysWhenItWasTaken() {
        assertTrue("only ${shipped.trackers.size} trackers", shipped.trackers.size > 400)
        assertTrue("only ${shipped.signatureCount} signatures", shipped.signatureCount > 500)
        assertTrue("no snapshot date: '${shipped.snapshot}'", shipped.snapshot.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        assertEquals("ids must be unique", shipped.trackers.size, shipped.trackers.map { it.id }.toSet().size)
    }

    @Test
    fun wellKnownTrackersAreRecognised() {
        assertTrue(names("com.google.firebase.analytics.FirebaseAnalytics").any { "Firebase Analytics" in it })
        assertTrue(names("com.flurry.sdk.a").any { "Flurry" in it })
        assertTrue(names("com.facebook.ads.AdView").any { "Facebook" in it })
        assertTrue(names("com.google.android.gms.ads.AdView").any { "AdMob" in it })
        assertTrue(names("com.appsflyer.AppsFlyerLib").any { "AppsFlyer" in it })
    }

    @Test
    fun ordinaryCodeIsNotATracker() {
        for (name in listOf(
            "androidx.compose.ui.Modifier",
            "androidx.datastore.preferences.core.Preferences",
            "kotlin.collections.CollectionsKt",
            "kotlinx.coroutines.Job",
            "java.lang.String",
            "android.app.Activity",
            "com.privacylauncher.MainActivity",
            "com.privacylauncher.data.TrackerList",
        )) {
            assertEquals(name, emptyList<String>(), names(name))
        }
    }

    @Test
    fun aTrackerCanBeLookedUpByItsId() {
        val flurry = shipped.matchName("com.flurry.sdk.a").first()
        assertEquals(flurry, shipped.byId(flurry.id))
        assertNotNull(flurry.categories)
    }

    // --- how a name is matched -------------------------------------------------

    private val small = list(
        "# snapshot\t2026-01-02",
        "1\tBranch\tAnalytics\tio.branch.",
        "2\tSizmek\tAdvertisement\t.sizmek.",
        "3\tKit\tAnalytics,Profiling\tcom.kit.core|com.kit.extra.",
        "4\tCrash\tCrash reporting\tcom.crash.Reporter",
    )

    private fun found(className: String) = small.matchName(className).map { it.name }

    @Test
    fun aSignatureMatchesAtTheStartOfAName() {
        assertEquals(listOf("Branch"), found("io.branch.referral.Branch"))
    }

    @Test
    fun aSignatureDoesNotMatchInTheMiddleOfAWord() {
        // Not Branch's code. A reading that looked for the signature
        // anywhere in the name would report it.
        assertEquals(emptyList<String>(), found("radio.branch.Tuner"))
        assertEquals(emptyList<String>(), found("com.studio.branch.Tree"))
    }

    @Test
    fun aSignatureMayStartAtAnyPartOfTheName() {
        // The library repackaged under another name still gives itself away.
        assertEquals(listOf("Branch"), found("shaded.io.branch.referral.Branch"))
    }

    @Test
    fun aSignatureWithADotInFrontNeverMatchesAtTheVeryStart() {
        assertEquals(listOf("Sizmek"), found("com.sizmek.sdk.Ad"))
        assertEquals(emptyList<String>(), found("sizmek.sdk.Ad"))
        // And it is the whole part that must match, not the start of one.
        assertEquals(emptyList<String>(), found("com.sizmekish.Ad"))
    }

    @Test
    fun aSignatureWithoutAClosingDotMatchesWhateverFollows() {
        assertEquals(listOf("Kit"), found("com.kit.core.Engine"))
        assertEquals(listOf("Kit"), found("com.kit.coreplus.Engine"))
        assertEquals(listOf("Kit"), found("com.kit.extra.More"))
        assertEquals(emptyList<String>(), found("com.kit.other.Thing"))
    }

    @Test
    fun aSignatureCanNameOneClass() {
        assertEquals(listOf("Crash"), found("com.crash.Reporter"))
        assertEquals(listOf("Crash"), found("com.crash.Reporter\$Inner"))
        assertEquals(emptyList<String>(), found("com.crash.Other"))
    }

    @Test
    fun oneClassCanGiveAwayTwoTrackers() {
        val both = list(
            "1\tWide\t\tcom.vendor.",
            "2\tNarrow\t\tcom.vendor.ads.",
        )
        assertEquals(listOf("Wide", "Narrow"), both.matchName("com.vendor.ads.Banner").map { it.name })
    }

    @Test
    fun theDateAndCategoriesAreRead() {
        assertEquals("2026-01-02", small.snapshot)
        assertEquals(listOf("Analytics", "Profiling"), small.byId(3)?.categories)
        assertEquals(emptyList<String>(), list("9\tPlain\t\tcom.plain.").byId(9)?.categories)
    }

    // --- a file that has gone wrong --------------------------------------------

    @Test
    fun aBadLineIsSkippedAndTheRestStillWork() {
        val damaged = list(
            "not a line at all",
            "x\tNoNumber\t\tcom.nonumber.",
            "5\t\t\tcom.noname.",
            "6\tNoSignature\t\t",
            "7\tGood\t\tcom.good.",
            "",
        )
        assertEquals(listOf("Good"), damaged.trackers.map { it.name })
        assertEquals(listOf("Good"), damaged.matchName("com.good.Thing").map { it.name })
    }

    @Test
    fun aSignatureTooShortToMeanAnythingIsIgnored() {
        // "c." would match every class from every company with a .com.
        val greedy = list("1\tGreedy\t\tc.|com.greedy.")
        assertEquals(emptyList<String>(), greedy.matchName("c.other.Thing").map { it.name })
        assertEquals(listOf("Greedy"), greedy.matchName("com.greedy.Thing").map { it.name })
    }

    @Test
    fun anEmptyFileGivesAnEmptyListNotACrash() {
        val empty = list("")
        assertEquals(0, empty.trackers.size)
        assertEquals(emptyList<Tracker>(), empty.matchName("com.flurry.sdk.a"))
    }

    @Test
    fun aNameThatIsNotAClassNameMatchesNothing() {
        val found = java.util.BitSet()
        small.match(ByteArray(0), 0, found)
        small.match("io/branch/X".toByteArray(), 11, found) // no L in front
        small.match("L".toByteArray(), 1, found)
        assertFalse(found.nextSetBit(0) >= 0)
    }
}
