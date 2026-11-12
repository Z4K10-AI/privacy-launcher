package com.privacylauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Scanning an app's files, and remembering what was found.
 *
 * The APKs here are made for the test: real zip archives holding small
 * hand-built code files. See [DexFixture].
 */
class TrackerScanTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val list = TrackerList.parse(
        listOf(
            "# snapshot\t2026-01-02",
            "10\tFlurry\tAnalytics\tcom.flurry.",
            "20\tAdCo\tAdvertisement\tcom.adco.ads.",
            "30\tNever\t\tcom.never.",
        ).joinToString("\n").byteInputStream(),
    )
    private val scanner = ApkScanner(list)

    private fun apk(name: String, entries: Map<String, ByteArray>): File =
        DexFixture.apk(folder.newFile(name), entries)

    private val app = DexFixture.dex(listOf("com.example.app.Main", "com.flurry.sdk.Agent"))
    private val ads = DexFixture.dex(listOf("com.adco.ads.Banner"))
    private val clean = DexFixture.dex(listOf("com.example.app.Main", "com.example.app.Other"))

    // --- one app ---------------------------------------------------------------

    @Test
    fun trackersInEveryCodeFileAreFound() {
        val file = apk("two.apk", mapOf("classes.dex" to app, "classes2.dex" to ads, "res/x.png" to ByteArray(9)))
        assertEquals(ScanStatus.SCANNED to listOf(10, 20), scanner.scan(listOf(file)))
    }

    @Test
    fun anAppWithNoTrackersIsReadAndFoundClean() {
        val file = apk("clean.apk", mapOf("classes.dex" to clean))
        assertEquals(ScanStatus.SCANNED to emptyList<Int>(), scanner.scan(listOf(file)))
    }

    @Test
    fun anAppDeliveredInPiecesIsReadPieceByPiece() {
        val base = apk("base.apk", mapOf("classes.dex" to clean))
        val feature = apk("feature.apk", mapOf("classes.dex" to ads))
        val language = apk("lang.apk", mapOf("res/strings" to ByteArray(4)))
        assertEquals(
            ScanStatus.SCANNED to listOf(20),
            scanner.scan(listOf(base, feature, language)),
        )
    }

    @Test
    fun anAppWithNoCodeInItsFileSaysSoRatherThanClean() {
        // What a preinstalled app often looks like from here.
        val file = apk("system.apk", mapOf("AndroidManifest.xml" to ByteArray(20)))
        assertEquals(ScanStatus.NO_CODE to emptyList<Int>(), scanner.scan(listOf(file)))
    }

    @Test
    fun whenPartOfTheCodeCannotBeReadWhatWasFoundStillCounts() {
        val file = apk("partly.apk", mapOf("classes.dex" to app, "classes2.dex" to ByteArray(300)))
        assertEquals(ScanStatus.PARTLY to listOf(10), scanner.scan(listOf(file)))
    }

    @Test
    fun aFileThatIsNotAnArchiveIsUnreadableNotClean() {
        val notZip = folder.newFile("broken.apk").apply { writeBytes(ByteArray(64) { 7 }) }
        assertEquals(ScanStatus.UNREADABLE to emptyList<Int>(), scanner.scan(listOf(notZip)))
        val missing = File(folder.root, "gone.apk")
        assertEquals(ScanStatus.UNREADABLE to emptyList<Int>(), scanner.scan(listOf(missing)))
    }

    @Test
    fun codeFilesAfterAGapInTheNumberingAreNotLoadedByAndroidAndNotReadHere() {
        // Android stops at the first missing number, so code in classes3
        // with no classes2 never runs. Reporting it would be reporting
        // something the app cannot do.
        val file = apk("gap.apk", mapOf("classes.dex" to clean, "classes3.dex" to ads))
        assertEquals(ScanStatus.SCANNED to emptyList<Int>(), scanner.scan(listOf(file)))
    }

    @Test
    fun theSameScannerCanBeUsedForOneAppAfterAnother() {
        val first = apk("a.apk", mapOf("classes.dex" to app))
        val second = apk("b.apk", mapOf("classes.dex" to clean))
        assertEquals(listOf(10), scanner.scan(listOf(first)).second)
        assertEquals("the first app's findings leaked into the second", emptyList<Int>(), scanner.scan(listOf(second)).second)
    }

    // --- remembering -------------------------------------------------------------

    private val scans = listOf(
        AppScan("com.example.one", ScanStatus.SCANNED, listOf(10, 20), versionCode = 7, updatedAt = 1000),
        AppScan("com.example.two", ScanStatus.NO_CODE, emptyList(), versionCode = 1, updatedAt = 2000),
        AppScan("com.example.three", ScanStatus.PARTLY, listOf(30), versionCode = 12345678901, updatedAt = 3000),
    )

    @Test
    fun whatWasFoundIsRememberedExactly() {
        val text = TrackerCache.encode("2026-01-02", scans)
        assertEquals(scans.associateBy { it.packageName }, TrackerCache.decode(text, "2026-01-02"))
    }

    @Test
    fun resultsFromAnOlderListAreNotBelieved() {
        // "Nothing found" by a list that did not know a tracker yet says
        // nothing about that tracker.
        val text = TrackerCache.encode("2026-01-02", scans)
        assertEquals(emptyMap<String, AppScan>(), TrackerCache.decode(text, "2026-06-30"))
    }

    @Test
    fun aDamagedMemoryCostsARescanNotACrash() {
        assertEquals(emptyMap<String, AppScan>(), TrackerCache.decode("", "x"))
        assertEquals(emptyMap<String, AppScan>(), TrackerCache.decode("rubbish\nmore rubbish", "x"))
        val text = TrackerCache.encode("x", scans) +
            "com.bad.one\tnot-a-number\t1\tSCANNED\t\n" +
            "com.bad.two\t1\t1\tINVENTED\t\n" +
            "com.bad.three\t1\t1\tSCANNED\t1,two,3\n" +
            "too\tfew\n"
        assertEquals(scans.map { it.packageName }.toSet(), TrackerCache.decode(text, "x").keys)
    }

    @Test
    fun anUpdatedAppIsNoLongerDescribedByItsOldScan() {
        val scan = scans.first()
        assertTrue(scan.describes(versionCode = 7, updatedAt = 1000))
        assertFalse("a new version", scan.describes(versionCode = 8, updatedAt = 1000))
        assertFalse("reinstalled", scan.describes(versionCode = 7, updatedAt = 1001))
    }

    // --- what the dashboard is told ------------------------------------------------

    @Test
    fun theReportCountsOnlyAppsItCouldRead() {
        val t = Tracker(1, "T", emptyList())
        val report = TrackerReport(
            findings = listOf(
                TrackerFinding("a", "Zebra", ScanStatus.SCANNED, listOf(t)),
                TrackerFinding("b", "apple", ScanStatus.SCANNED, listOf(t, t.copy(id = 2))),
                TrackerFinding("c", "Clean", ScanStatus.SCANNED, emptyList()),
                TrackerFinding("d", "System", ScanStatus.NO_CODE, emptyList()),
                TrackerFinding("e", "Broken", ScanStatus.UNREADABLE, emptyList()),
                TrackerFinding("f", "Half", ScanStatus.PARTLY, listOf(t)),
            ),
        )
        assertEquals("most trackers first, then by name", listOf("apple", "Half", "Zebra"), report.withTrackers.map { it.label })
        assertEquals(4, report.read)
        assertEquals("unread apps must not be counted as clean", 2, report.unread)
        assertTrue(report.hasResults)
        assertFalse(TrackerReport().hasResults)
    }
}
