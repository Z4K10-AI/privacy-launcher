package com.privacylauncher.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The tracker scanner against real apps, on a real Android.
 *
 * The unit tests feed the scanner code files built by hand. These are built
 * by the Android tools and installed by the system, which is the only way
 * to find out whether the hand-built ones were a fair imitation, and
 * whether Android lets one app read another's file at all.
 *
 * Two apps are certain to be installed while this runs: the launcher, and
 * the test app that drives it. The test app carries two empty classes in
 * packages named like trackers' (see `TrackerScanFixture`), so one of the
 * two should be found clean and the other should not.
 */
@RunWith(AndroidJUnit4::class)
class TrackerScanDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val launcher = instrumentation.targetContext
    private val testApp = instrumentation.context

    private val scanner = TrackerScanner(launcher)
    private val list = scanner.list()

    private fun names(ids: List<Int>) = ids.mapNotNull { list.byId(it)?.name }

    @Test
    fun theListThatShipsInTheAppCanBeRead() {
        assertTrue("only ${list.trackers.size} trackers", list.trackers.size > 400)
        assertTrue(list.snapshot.isNotEmpty())
    }

    @Test
    fun trackerCodeInARealAppIsFound() {
        val (status, ids) = ApkScanner(list).scan(listOf(File(testApp.packageCodePath)))

        assertEquals(ScanStatus.SCANNED, status)
        val found = names(ids)
        assertTrue("Flurry's package was not recognised in $found", found.any { "Flurry" in it })
        assertTrue("a signature with a dot in front was not recognised in $found", found.any { "Sizmek" in it })
    }

    @Test
    fun theLauncherItselfContainsNoKnownTracker() {
        // The claim the whole project makes about itself, checked by its
        // own scanner against its own installed file.
        val (status, ids) = ApkScanner(list).scan(listOf(File(launcher.packageCodePath)))

        assertEquals("the launcher's own code could not be read", ScanStatus.SCANNED, status)
        assertEquals(emptyList<String>(), names(ids))
    }

    @Test
    fun aScanGoesThroughAndroidToFindEachAppsFileAndRemembersTheAnswer() = runBlocking {
        val packages = listOf(launcher.packageName, testApp.packageName)
        val seen = mutableListOf<String>()
        val results = mutableMapOf<String, AppScan>()

        scanner.scan(
            packages = packages,
            fresh = true,
            onApp = { _, name -> seen += name },
            onResult = { results[it.packageName] = it },
        )

        assertEquals(packages, seen)
        val own = results[launcher.packageName]
        assertNotNull("the launcher could not find its own package", own)
        assertEquals(ScanStatus.SCANNED, own!!.status)
        assertEquals(emptyList<Int>(), own.trackerIds)

        // Another app's file: Android has to let this one be read.
        val other = results[testApp.packageName]
        assertNotNull("Android did not let the launcher see the test app", other)
        assertEquals(ScanStatus.SCANNED, other!!.status)
        assertTrue(names(other.trackerIds).any { "Flurry" in it })

        // And the next visit to the dashboard costs one small file, not a scan.
        assertEquals(results, scanner.remembered(packages))
    }

    @Test
    fun everyAppWithAnIconGetsAnAnswerAndNoneBreaksTheScan() = runBlocking {
        // Whatever this emulator has installed: real files, made by other
        // people's builds, none of them written with this scanner in mind.
        val packages = AppRepository(launcher).loadApps().orEmpty()
            .map { it.packageName }.distinct()
        assertTrue("found only ${packages.size} apps to scan", packages.size >= 5)
        val results = mutableListOf<AppScan>()
        val runtime = Runtime.getRuntime()
        fun used() = runtime.totalMemory() - runtime.freeMemory()
        System.gc()
        val before = used()
        var peak = before
        val started = System.nanoTime()

        scanner.scan(
            packages,
            fresh = true,
            onApp = { _, _ -> peak = maxOf(peak, used()) },
            onResult = { results += it; peak = maxOf(peak, used()) },
        )

        val millis = (System.nanoTime() - started) / 1_000_000
        assertEquals("an app got no answer", packages.size, results.size)
        val byStatus = results.groupingBy { it.status }.eachCount()
        // Printed so a run records what this device's own apps look like
        // from here, how long reading them took, and how far the launcher's
        // memory rose while it did. Nothing is asserted about the mix or
        // the time: both belong to the device, not to the app.
        println(
            "TrackerScanDeviceTest: ${packages.size} apps: $byStatus, $millis ms, " +
                "memory up by at most ${(peak - before) / 1024} KB",
        )
        // This one is asserted. The scanner's whole design is to read apps
        // through one small buffer; if a scan ever needs tens of megabytes,
        // something is being held that should not be.
        assertTrue(
            "the scan raised memory use by ${(peak - before) / (1024 * 1024)} MB",
            peak - before < 24L * 1024 * 1024,
        )
        assertTrue(
            "at least the launcher should have been readable: $byStatus",
            (byStatus[ScanStatus.SCANNED] ?: 0) >= 1,
        )
    }
}
