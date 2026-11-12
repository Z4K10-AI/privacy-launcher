package com.privacylauncher.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.IconLoader
import com.privacylauncher.data.LauncherSettings
import com.privacylauncher.designsystem.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction

/**
 * A finger dragging an icon across the real home grid.
 *
 * [HomeReorderTest] checks that a drag, once reported, edits the right item.
 * This checks the step before it: that the grid reports the right two icons
 * in the first place. Nothing else in the project puts a finger on the
 * grid, and this is the code that decides which icon a drag was about.
 *
 * It matters most on the *second* drag. The gesture handler on each cell is
 * long-lived -- Compose only rebuilds it when the things it is keyed on
 * change -- and it used to hold on to the list of icons as it was when the
 * handler was made. Reordering keeps the same number of icons in the same
 * number of cells, so nothing told it to let go: after one rearrangement it
 * was still deciding what was under the finger from the previous layout.
 *
 * Each test therefore changes something first and drags second.
 */
@RunWith(AndroidJUnit4::class)
class HomeGridDragTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun app(name: String) =
        HomeTile.App(AppEntry(packageName = "p.$name", activityName = "A", label = name))

    private fun folder(id: String, vararg names: String) =
        HomeTile.Folder(id = id, name = id, entries = names.map { app(it).entry })

    private var tiles by mutableStateOf<List<HomeTile>>(emptyList())
    private var iconSize by mutableStateOf(48.dp)
    private var editMode by mutableStateOf(false)
    private var columns = 4

    /** A right-to-left language: the first column is drawn on the right. */
    private var rtl = false

    private val moves = mutableListOf<Pair<String, String>>()
    private val combines = mutableListOf<Pair<String, String>>()
    private val launched = mutableListOf<String>()
    private val opened = mutableListOf<String>()

    private fun show(
        initial: List<HomeTile>,
        columns: Int = 4,
        iconSize: Dp = 48.dp,
        rtl: Boolean = false,
    ) {
        tiles = initial
        this.columns = columns
        this.iconSize = iconSize
        this.rtl = rtl
        compose.setContent {
            LauncherTheme(settings = LauncherSettings()) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides
                        if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                HomeGrid(
                    tiles = tiles,
                    columns = columns,
                    iconSize = this.iconSize,
                    iconLoader = IconLoader(context),
                    editMode = editMode,
                    onLaunch = { launched += it.label },
                    onOpenFolder = { opened += it },
                    onEnterEditMode = { editMode = true },
                    onRemoveTile = {},
                    onMove = { from, to -> moves += from to to },
                    onCombine = { from, onto -> combines += from to onto },
                    modifier = Modifier.fillMaxWidth().testTag(GRID),
                )
                }
            }
        }
        compose.waitForIdle()
    }

    /**
     * Long-presses the icon in cell [from] and drags it to cell [to].
     *
     * [edge] drops it off-centre, which the grid reads as "reorder to
     * here"; without it the drop is on the middle of the icon, which means
     * "make a folder".
     *
     * [midway], when given, runs with the icon in the air, half way there
     * and the finger still down: the place to change something under a
     * drag that is already happening.
     */
    private fun drag(from: Int, to: Int, edge: Boolean, midway: (() -> Unit)? = null) {
        val cols = columns
        // Mirrors the grid's own cell height: the icon plus room for a
        // label. If the grid changes that sum this must change with it,
        // and the first test below fails loudly if they disagree.
        val cellHeightDp = iconSize + LABEL_SPACE
        val steps = 10

        /** Where the finger is after [step] of [steps] moves. */
        fun TouchInjectionScope.fingerAt(step: Int): Offset {
            val cellW = width.toFloat() / cols
            val cellH = cellHeightDp.toPx()
            fun centre(index: Int): Offset {
                val column = index % cols
                val drawnColumn = if (rtl) cols - 1 - column else column
                return Offset(
                    x = drawnColumn * cellW + cellW / 2,
                    y = (index / cols) * cellH + cellH / 2,
                )
            }
            val start = centre(from)
            val end = centre(to) + if (edge) Offset(cellW * 0.38f, 0f) else Offset.Zero
            return start + (end - start) * (step / steps.toFloat())
        }

        val grid = compose.onNodeWithTag(GRID)
        if (midway == null) {
            grid.performTouchInput {
                down(fingerAt(0))
                advanceEventTime(viewConfiguration.longPressTimeoutMillis + 200)
                for (i in 1..steps) moveTo(fingerAt(i))
                up()
            }
        } else {
            grid.performTouchInput {
                down(fingerAt(0))
                advanceEventTime(viewConfiguration.longPressTimeoutMillis + 200)
                for (i in 1..steps / 2) moveTo(fingerAt(i))
            }
            compose.waitForIdle()
            midway()
            compose.waitForIdle()
            grid.performTouchInput {
                for (i in steps / 2 + 1..steps) moveTo(fingerAt(i))
                up()
            }
        }
        compose.waitForIdle()
    }

    // --- one drag ---------------------------------------------------------

    @Test
    fun aDragToAnEdgeReportsAMoveBetweenThoseTwoIcons() {
        show(listOf(app("A"), app("B"), app("C"), app("D")))

        drag(from = 0, to = 2, edge = true)

        assertEquals("should not have made a folder", emptyList<Pair<String, String>>(), combines)
        assertEquals(listOf("a:p.A/A" to "a:p.C/A"), moves)
    }

    @Test
    fun aDropOnTheMiddleOfAnIconReportsAFolder() {
        show(listOf(app("A"), app("B"), app("C"), app("D")))

        drag(from = 0, to = 2, edge = false)

        assertEquals(listOf("a:p.A/A" to "a:p.C/A"), combines)
        assertEquals(emptyList<Pair<String, String>>(), moves)
    }

    // --- a drag after something changed -----------------------------------
    //
    // Each of these touches the cell *before* the change. That is the whole
    // point: a cell's gesture handler starts the first time a finger lands
    // on it and then lives on, and it is only a handler that is already
    // running that can be holding on to something out of date.

    @Test
    fun afterAReorderTheNextDragIsAboutTheIconsThatAreThereNow() {
        show(listOf(app("A"), app("B"), app("C"), app("D")))
        drag(from = 0, to = 2, edge = true)
        assertEquals("the first drag", listOf("a:p.A/A" to "a:p.C/A"), moves)

        // The app now does what was asked: A lands after C. Same four
        // cells, different occupants.
        tiles = listOf(app("B"), app("C"), app("A"), app("D"))
        moves.clear()
        compose.waitForIdle()

        // The first cell now holds B and the third holds A.
        drag(from = 0, to = 2, edge = true)

        assertEquals(listOf("a:p.B/A" to "a:p.A/A"), moves)
    }

    @Test
    fun anAppThatTookAFoldersPlaceCanStillBeDroppedOntoAnother() {
        // Only an app can be dropped onto something to make a folder. The
        // grid checks what kind of thing is being dragged -- and used to
        // check it against the old layout, so an app sitting where a folder
        // had been was refused for as long as the icon count stayed the
        // same.
        show(listOf(folder("f1", "X", "Y"), app("A"), app("B"), app("C")))
        drag(from = 0, to = 1, edge = true)
        assertEquals("the first drag", listOf("f1" to "a:p.A/A"), moves)

        tiles = listOf(app("A"), folder("f1", "X", "Y"), app("B"), app("C"))
        moves.clear()
        compose.waitForIdle()

        drag(from = 0, to = 2, edge = false)

        assertEquals(listOf("a:p.A/A" to "a:p.B/A"), combines)
        assertEquals(emptyList<Pair<String, String>>(), moves)
    }

    @Test
    fun afterTheIconSizeChangesADragStillLandsOnTheRowUnderTheFinger() {
        // Two columns, so six icons make three rows. Bigger icons make
        // taller rows, and the row under the finger is worked out by
        // dividing by the row height: a height remembered from before the
        // change puts a drop two rows down on a row that does not exist.
        show(
            listOf(app("A"), app("B"), app("C"), app("D"), app("E"), app("F")),
            columns = 2,
            iconSize = 40.dp,
        )
        // A long-press that goes nowhere: enough to wake the handler.
        drag(from = 0, to = 0, edge = false)

        iconSize = 72.dp
        compose.waitForIdle()

        // A, top-left, down onto E, bottom-left.
        drag(from = 0, to = 4, edge = true)

        assertEquals(listOf("a:p.A/A" to "a:p.E/A"), moves)
    }

    @Test
    fun aDragPausedHalfWayIsStillOneDrag() {
        // The control for the test below, which expects *nothing* to be
        // reported -- and nothing is also what a drag that never happened
        // reports. This is the same paused drag with nothing changed in
        // the pause, and it must report the move. If pausing ever breaks
        // the gesture, this fails and the test below stops being trusted.
        show(listOf(app("A"), app("B"), app("C"), app("D")))

        drag(from = 2, to = 0, edge = true) { /* nothing changes */ }

        assertEquals(listOf("a:p.C/A" to "a:p.A/A"), moves)
        assertEquals(emptyList<Pair<String, String>>(), combines)
    }

    @Test
    fun ifTheIconsShiftUnderADragTheDropMovesNothing() {
        // The price of a handler that always sees the newest icons. C is
        // picked up from the third cell and carried left. While it is in
        // the air B disappears -- uninstalled, or hidden because a focus
        // schedule has just started -- and everything after it moves one
        // cell left. The third cell, whose handler still has the finger,
        // now belongs to D. A handler that reports "whatever is in my cell
        // now" moves D, which the user never touched. There is no sensible
        // reading of a drop like that, so it must do nothing at all.
        show(listOf(app("A"), app("B"), app("C"), app("D")))

        drag(from = 2, to = 0, edge = true) {
            tiles = listOf(app("A"), app("C"), app("D"))
        }

        assertEquals(emptyList<Pair<String, String>>(), moves)
        assertEquals(emptyList<Pair<String, String>>(), combines)
    }

    @Test
    fun aDragThatEndsWhereItStartedReportsNothing() {
        show(listOf(app("A"), app("B"), app("C"), app("D")))

        drag(from = 1, to = 1, edge = false)

        assertEquals(emptyList<Pair<String, String>>(), moves)
        assertEquals(emptyList<Pair<String, String>>(), combines)
    }

    @Test
    fun aDropOnEmptySpaceAfterTheLastIconNeverMakesAFolder() {
        // Five icons in four columns: the second row is one icon and three
        // empty cells. A is dropped on the middle of an empty one, two
        // cells to the right of E. That is "put it at the end". The grid
        // used to judge "is the finger on the middle of something?" against
        // the empty cell, then apply the answer to the last icon, so the
        // drop made a folder out of A and E -- an icon the finger was
        // never near.
        show(listOf(app("A"), app("B"), app("C"), app("D"), app("E")))

        drag(from = 0, to = 6, edge = false)

        assertEquals(
            "made a folder with an icon the finger was nowhere near",
            emptyList<Pair<String, String>>(),
            combines,
        )
        assertEquals(listOf("a:p.A/A" to "a:p.E/A"), moves)
    }

    @Test
    fun inARightToLeftLanguageADragGoesWhereTheFingerGoes() {
        // In Arabic, Hebrew, Persian or Urdu the first icon is drawn on the
        // right and the row runs leftwards. The grid used to work out the
        // column under the finger as if the row always ran left to right,
        // so the finger went one way and the answer went the other: this
        // drag, two cells to the left, was read as two cells off the right
        // edge and reported nothing at all.
        show(listOf(app("A"), app("B"), app("C"), app("D")), rtl = true)

        drag(from = 0, to = 2, edge = true)

        assertEquals(listOf("a:p.A/A" to "a:p.C/A"), moves)
        assertEquals(emptyList<Pair<String, String>>(), combines)
    }

    @Test
    fun anIconCanBeOpenedWithoutTouchingTheScreen() {
        // TalkBack, Switch Access and a keyboard do not touch the screen:
        // they ask an item to perform its "click" action. The grid's taps
        // are raw touch handlers, which offer no such action, so an icon
        // had a name a screen reader could read out and no way to open it.
        show(listOf(app("A"), folder("f1", "X", "Y")))

        compose.onNode(hasText("A") and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.onNode(hasText("f1") and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals(listOf("A"), launched)
        assertEquals(listOf("f1"), opened)
    }

    private companion object {
        const val GRID = "home-grid"

        /** Spacing.xl + Spacing.sm at normal font size: see HomeGrid. */
        val LABEL_SPACE = 44.dp
    }
}
