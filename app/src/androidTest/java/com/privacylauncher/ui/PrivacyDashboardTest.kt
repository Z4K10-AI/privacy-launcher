package com.privacylauncher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.privacylauncher.data.GroupReport
import com.privacylauncher.data.IconLoader
import com.privacylauncher.data.LauncherSettings
import com.privacylauncher.data.PermissionGroup
import com.privacylauncher.data.PermissionHolder
import com.privacylauncher.data.PrivacyReport
import com.privacylauncher.data.ScanStatus
import com.privacylauncher.data.Tracker
import com.privacylauncher.data.TrackerFinding
import com.privacylauncher.data.TrackerReport
import com.privacylauncher.designsystem.LauncherTheme
import com.privacylauncher.ui.privacy.PrivacyDashboardScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.test.performScrollTo

/**
 * The privacy dashboard, as someone using it meets it.
 *
 * Two things on this screen are promises about limits, and promises of that
 * kind are the first to go when a screen is tidied: what a tracker result
 * does and does not mean, and why the launcher links to Android's screens
 * instead of showing what it cannot read. These tests hold the words in
 * place. The rest walks the tracker card through each of its states: never
 * scanned, scanning, and the three shapes a result can take.
 */
@RunWith(AndroidJUnit4::class)
class PrivacyDashboardTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private var permissions by mutableStateOf(PrivacyReport(ready = true))
    private var trackers by mutableStateOf(TrackerReport())

    private val scans = mutableListOf<Boolean>()
    private var stops = 0
    private val opened = mutableListOf<String>()

    private fun show() {
        compose.setContent {
            LauncherTheme(settings = LauncherSettings()) {
                PrivacyDashboardScreen(
                    report = permissions,
                    iconLoader = IconLoader(context),
                    onBack = {},
                    onOpenPermissions = { opened += it },
                    onOpenSystemPrivacy = {},
                    trackers = trackers,
                    onScanTrackers = { scans += it },
                    onStopTrackerScan = { stops++ },
                )
            }
        }
        compose.waitForIdle()
    }

    /**
     * Scrolls the page until [text] is on screen, then returns it.
     *
     * Two scrolls, because the first only promises that the card holding
     * the text is somewhere in view. A tall card on a short screen can be
     * in view with its last button still below the edge, and a tap aimed
     * there lands on nothing. The second brings the text itself up.
     */
    private fun find(text: String, whole: Boolean = false) = compose.run {
        onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = !whole))
        onNodeWithText(text, substring = !whole).performScrollTo()
    }

    private val analytics = Tracker(1, "Acme Analytics", listOf("Analytics"))
    private val ads = Tracker(2, "Banner Ads", listOf("Advertisement", "Profiling"))

    private fun results(vararg findings: TrackerFinding, waiting: Int = 0) = TrackerReport(
        findings = findings.toList(),
        total = findings.size + waiting,
        waiting = waiting,
        snapshot = "2026-01-02",
        listSize = 428,
    )

    // --- the tracker card ------------------------------------------------------

    @Test
    fun beforeAnyScanItExplainsItselfAndOffersOne() {
        trackers = TrackerReport(total = 3, snapshot = "2026-01-02", listSize = 428)
        show()

        find("Many apps carry code").assertIsDisplayed()
        find("Scan 3 apps").performClick()

        assertEquals("a first scan reads only what has not been read", listOf(false), scans)
    }

    @Test
    fun aScanIsNeverStartedByOpeningTheScreen() {
        trackers = TrackerReport(total = 3)
        show()
        find("Trackers inside apps").assertIsDisplayed()

        assertEquals(emptyList<Boolean>(), scans)
    }

    @Test
    fun whileScanningItSaysHowFarItHasGotAndCanBeStopped() {
        trackers = TrackerReport(running = true, done = 1, total = 3, current = "Maps")
        show()

        find("Reading app 2 of 3").assertIsDisplayed()
        find("Maps").assertIsDisplayed()
        find("Stop").performClick()

        assertEquals(1, stops)
    }

    @Test
    fun appsWithTrackersAreListedAndOpenToShowWhich() {
        trackers = results(
            TrackerFinding("p.games", "Games", ScanStatus.SCANNED, listOf(analytics, ads)),
            TrackerFinding("p.notes", "Notes", ScanStatus.SCANNED, emptyList()),
            TrackerFinding("p.news", "News", ScanStatus.SCANNED, listOf(analytics)),
        )
        show()

        find("2 apps of the 3 read contain a known tracker").assertIsDisplayed()
        find("2 trackers").assertIsDisplayed()
        find("Games").performClick()
        find("Banner Ads").assertIsDisplayed()
        find("Advertisement · Profiling").assertIsDisplayed()
        // The way to do something about it: Android's page for that app.
        // The whole text, because the words "App info" are also in the
        // paragraph further down that explains what tapping an app does,
        // and on some screen sizes both are on screen at once.
        find("App info", whole = true).performClick()

        assertEquals(listOf("p.games"), opened)
    }

    @Test
    fun aCleanResultSaysWhatWasReadNotThatThePhoneIsClean() {
        trackers = results(
            TrackerFinding("p.notes", "Notes", ScanStatus.SCANNED, emptyList()),
            TrackerFinding("p.clock", "Clock", ScanStatus.SCANNED, emptyList()),
        )
        show()

        find("No known tracker in the 2 apps read").assertIsDisplayed()
    }

    @Test
    fun appsThatCouldNotBeReadAreCountedApartAndExplained() {
        trackers = results(
            TrackerFinding("p.notes", "Notes", ScanStatus.SCANNED, emptyList()),
            TrackerFinding("p.system", "Phone", ScanStatus.NO_CODE, emptyList()),
            TrackerFinding("p.broken", "Broken", ScanStatus.UNREADABLE, emptyList()),
        )
        show()

        // One app read, not three: the two that could not be read are not
        // quietly counted as clean.
        find("No known tracker in the 1 app read").assertIsDisplayed()
        find("2 apps could not be read").assertIsDisplayed()
    }

    @Test
    fun newAppsSinceTheLastScanAreMentionedAndCanBeReadOnTheirOwn() {
        trackers = results(
            TrackerFinding("p.notes", "Notes", ScanStatus.SCANNED, emptyList()),
            waiting = 2,
        )
        show()

        find("2 apps are new or updated").assertIsDisplayed()
        find("Read them now").performClick()
        find("Read every app again").performClick()

        assertEquals(listOf(false, true), scans)
    }

    @Test
    fun anAppReadOnlyInPartSaysThereMayBeMore() {
        trackers = results(
            TrackerFinding("p.half", "Half", ScanStatus.PARTLY, listOf(analytics)),
        )
        show()

        find("Half").performClick()
        find("Part of this app could not be read").assertIsDisplayed()
    }

    @Test
    fun whatAResultDoesAndDoesNotMeanIsAlwaysOnTheCard() {
        // With results up, then before any scan, then during one.
        trackers = results(TrackerFinding("p.notes", "Notes", ScanStatus.SCANNED, emptyList()))
        show()
        find("does not show that the code runs").assertIsDisplayed()
        find("code can be disguised").assertIsDisplayed()
        find("Exodus Privacy, 2026-01-02: 428 trackers").assertIsDisplayed()

        trackers = TrackerReport(total = 3, listSize = 428, snapshot = "2026-01-02")
        compose.waitForIdle()
        find("does not show that the code runs").assertIsDisplayed()

        trackers = TrackerReport(running = true, done = 0, total = 3, listSize = 428, snapshot = "2026-01-02")
        compose.waitForIdle()
        find("does not show that the code runs").assertIsDisplayed()
    }

    // --- the permissions half ----------------------------------------------------

    @Test
    fun whileThePermissionsAreStillBeingReadItSaysSo() {
        permissions = PrivacyReport(ready = false)
        show()

        compose.onNodeWithText("Reading permissions", substring = true).assertIsDisplayed()
    }

    @Test
    fun itSaysWhyTappingAnAppOpensAppInfoAndNotThePermissionList() {
        show()

        find("Only a system app may open the permission list directly").assertIsDisplayed()
    }

    @Test
    fun itSaysItCannotShowRecentUseRatherThanShowingNothing() {
        show()

        find("Recent use").assertIsDisplayed()
    }

    @Test
    fun anAppHoldingAPermissionOpensThatAppsPageInAndroid() {
        val camera = PermissionGroup.entries.first()
        permissions = PrivacyReport(
            groups = listOf(
                GroupReport(
                    group = camera,
                    holders = listOf(
                        PermissionHolder("p.snap", "Snap", preinstalled = false, isSelf = false),
                    ),
                ),
            ),
            score = 90,
            installedAppsScanned = 1,
            ready = true,
        )
        show()

        // The group's one-line description is the only text that belongs
        // to its card alone: its name is also in the list of what came off
        // the score. Tapping it opens the group; the app is inside.
        find(context.getString(camera.bodyRes)).performClick()
        find("Snap").performClick()

        assertEquals(listOf("p.snap"), opened)
    }
}
