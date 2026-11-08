package com.privacylauncher.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.FocusState
import com.privacylauncher.data.IconLoader
import com.privacylauncher.data.LauncherSettings
import com.privacylauncher.designsystem.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app drawer at the size a real phone reaches.
 *
 * The emulator has a dozen apps; a Galaxy A30 in use has well over a
 * hundred. Everything about the drawer that only matters at scale -- the
 * A-Z headers, the fast-scroll letter bar, scrolling a long list without
 * running out of memory -- has therefore never actually been rendered.
 * [DrawerListBuilderTest] checks the rows are built correctly at 120 apps;
 * this checks they can be drawn.
 */
@RunWith(AndroidJUnit4::class)
class AppDrawerScaleTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** 156 apps spread across the alphabet, as a busy phone looks. */
    private fun manyApps(n: Int = 156): List<AppEntry> =
        (0 until n).map { i ->
            val letter = 'A' + (i * 26 / n)
            AppEntry(
                packageName = "com.example.p$i",
                activityName = "Main",
                label = "$letter${"pp"} ${"%03d".format(i)}",
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

    private fun showDrawer(apps: List<AppEntry>, query: String = "") {
        val list = DrawerListBuilder.build(
            apps = apps,
            s = LauncherSettings(showMostUsed = false),
            rawQuery = query,
            counts = emptyMap(),
            focus = FocusState(),
        )
        compose.setContent {
            LauncherTheme(settings = LauncherSettings()) {
                AppDrawer(
                    list = list,
                    extraRows = emptyList(),
                    query = query,
                    columns = 4,
                    iconSize = 52.dp,
                    iconLoader = IconLoader(context),
                    isOnHome = { false },
                    isInDock = { false },
                    dockFull = false,
                    // False: the drawer opens the keyboard when it settles,
                    // and a keyboard racing the test makes it flaky.
                    isOpen = false,
                    autoKeyboard = false,
                    onHandleDrag = {},
                    onHandleRelease = {},
                    onQueryChange = {},
                    onLaunch = {},
                    onClose = {},
                    onAppInfo = {},
                    onUninstall = {},
                    onAddToHome = {},
                    onRemoveFromHome = {},
                    onAddToDock = {},
                    onRemoveFromDock = {},
                    onHide = {},
                    onOpenSettingsScreen = {},
                    onOpenContact = {},
                    onEnableContacts = {},
                    onOwnAppInfo = {},
                    onEndFocus = {},
                    onOpenFocus = {},
                )
            }
        }
    }

    @Test
    fun aHundredAndFiftySixAppsRender() {
        showDrawer(manyApps())
        compose.onNodeWithText("Search apps").assertIsDisplayed()
    }

    @Test
    fun theCountIsReportedToTheUser() {
        showDrawer(manyApps())
        compose.onNode(hasText("156", substring = true)).assertIsDisplayed()
    }

    @Test
    fun sectionHeadersAppearAtThisSize() {
        showDrawer(manyApps())
        // Two nodes legitimately read "A" at this size: the section header
        // and the fast-scroll strip's letter. Asserting a single node was
        // this test being wrong, not the drawer.
        val letterA = compose.onAllNodes(hasText("A")).fetchSemanticsNodes()
        assertEquals("header plus fast-scroll letter", 2, letterA.size)
    }

    @Test
    fun theGridScrollsToTheEnd() {
        val apps = manyApps()
        showDrawer(apps)
        val grid = compose.onAllNodes(hasScrollAction()).onFirst()
        // Scrolling to the last row is what would fail if the list were
        // eagerly composed rather than lazy.
        val rows = DrawerListBuilder.build(
            apps, LauncherSettings(showMostUsed = false), "", emptyMap(), FocusState(),
        ).rows
        grid.performScrollToIndex(rows.lastIndex)
        compose.waitForIdle()
        compose.onNodeWithText(apps.last().label).assertIsDisplayed()
    }

    @Test
    fun theFastScrollBarIsPresentAtScale() {
        showDrawer(manyApps())
        // One strip, with a letter for each section.
        compose.onNode(hasText("Z", substring = false)).assertExists()
    }

    @Test
    fun aSearchAcrossAllOfThemStillNarrows() {
        // Work the expected count out from the data rather than guessing
        // it: the labels cycle through the alphabet, so "App 0.." is six
        // apps, not the ten a quick glance suggests.
        val apps = manyApps()
        val query = "App 00"
        val expected = apps.count { it.label.contains(query) }
        assertEquals("fixture sanity", 6, expected)
        showDrawer(apps, query = query)
        // Exclude the search field: it contains the query as its own
        // editable text, so a plain text match counts the box along with
        // the results and reports one too many.
        val resultsOnly = hasText(query, substring = true) and
            SemanticsMatcher.keyNotDefined(SemanticsProperties.EditableText)
        compose.onAllNodes(resultsOnly).assertCountEquals(expected)
    }

    @Test
    fun anEmptyDrawerSaysSoRatherThanShowingNothing() {
        showDrawer(emptyList())
        // Not "no apps match that search" -- there is no search to blame.
        compose.onNode(hasText("hidden", substring = true)).assertExists()
    }
}
