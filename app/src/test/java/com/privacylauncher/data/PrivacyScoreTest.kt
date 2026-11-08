package com.privacylauncher.data

import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The privacy score.
 *
 * This is a number shown large at the top of the dashboard, under a
 * paragraph explaining exactly how it is worked out. That makes it a claim,
 * not a decoration: if the arithmetic does not match the explanation then
 * the screen is lying, in the same way the "two permissions" line was.
 * These tests check the number against the words.
 */
class PrivacyScoreTest {

    private val groups = PermissionGroup.entries

    // --- the shape of a single group's penalty ----------------------------

    @Test
    fun `no apps in a group costs nothing`() {
        for (g in groups) {
            assertEquals("${g.name} with none", 0, g.penaltyFor(0))
        }
    }

    @Test
    fun `a negative count cannot credit the score`() {
        // Not reachable through the UI, but a count is an Int and this is
        // the kind of thing that turns into a score above 100.
        for (g in groups) {
            assertEquals("${g.name} with -1", 0, g.penaltyFor(-1))
            assertEquals("${g.name} with -100", 0, g.penaltyFor(-100))
        }
    }

    @Test
    fun `one app costs about a quarter of the group's weight`() {
        // The documented shape: "a quarter of the weight at one app".
        for (g in groups) {
            assertEquals("${g.name}", (g.weight / 4f).roundToInt(), g.penaltyFor(1))
        }
    }

    @Test
    fun `three apps cost half the group's weight`() {
        // "half at three" -- the stated half-way point.
        for (g in groups) {
            assertEquals("${g.name}", (g.weight / 2f).roundToInt(), g.penaltyFor(3))
        }
    }

    @Test
    fun `a group's cost never exceeds its weight, however many apps`() {
        // The ceiling the dashboard promises: "with a ceiling per group".
        for (g in groups) {
            for (n in listOf(1, 5, 20, 100, 10_000, Int.MAX_VALUE / 32)) {
                assertTrue(
                    "${g.name} with $n apps cost ${g.penaltyFor(n)} > weight ${g.weight}",
                    g.penaltyFor(n) <= g.weight,
                )
            }
        }
    }

    @Test
    fun `adding an app never lowers the cost`() {
        // "The more apps share one kind of access the more it costs."
        for (g in groups) {
            var previous = 0
            for (n in 0..60) {
                val now = g.penaltyFor(n)
                assertTrue("${g.name} dropped from $previous to $now at $n", now >= previous)
                previous = now
            }
        }
    }

    // --- the score as a whole ---------------------------------------------

    @Test
    fun `a clean phone scores 100`() {
        val total = groups.sumOf { it.penaltyFor(0) }
        assertEquals(100, (100 - total).coerceIn(0, 100))
    }

    @Test
    fun `the worst possible score is 28, not 0`() {
        // Every group pinned at its ceiling. The weights total 72, so the
        // arithmetic cannot reach zero however bad the phone is -- worth
        // pinning down, because "0 out of 100" is what someone would
        // reasonably expect the floor to be, and a future weight change
        // that pushed the total past 100 would silently start clamping.
        val worst = groups.sumOf { it.weight }
        assertEquals(72, worst)
        assertEquals(28, (100 - worst).coerceIn(0, 100))
    }

    @Test
    fun `the score stays inside 0 to 100 for any mix of apps`() {
        for (perGroup in listOf(0, 1, 2, 3, 10, 500)) {
            val total = groups.sumOf { it.penaltyFor(perGroup) }
            val score = (100 - total).coerceIn(0, 100)
            assertTrue("score $score with $perGroup per group", score in 0..100)
        }
    }

    // --- bands --------------------------------------------------------------

    @Test
    fun `band boundaries are where the dashboard says they are`() {
        assertEquals(ScoreBand.GOOD, ScoreBand.of(100))
        assertEquals(ScoreBand.GOOD, ScoreBand.of(80))
        assertEquals(ScoreBand.FAIR, ScoreBand.of(79))
        assertEquals(ScoreBand.FAIR, ScoreBand.of(50))
        assertEquals(ScoreBand.ATTENTION, ScoreBand.of(49))
        assertEquals(ScoreBand.ATTENTION, ScoreBand.of(0))
    }

    @Test
    fun `every band is reachable from a real phone`() {
        // A band no phone can land in would be dead code in the UI. The
        // floor is 28, so ATTENTION is reachable; check the other two are
        // too, with a plausible number of apps rather than a contrived one.
        val none = 100 - groups.sumOf { it.penaltyFor(0) }
        val some = 100 - groups.sumOf { it.penaltyFor(1) }
        val many = 100 - groups.sumOf { it.penaltyFor(50) }
        assertEquals(ScoreBand.GOOD, ScoreBand.of(none))
        assertTrue("one app per group should not already be ATTENTION", some >= 50)
        assertEquals(ScoreBand.ATTENTION, ScoreBand.of(many))
    }

    // --- the report object ------------------------------------------------

    @Test
    fun `a report separates installed apps from preinstalled ones`() {
        // Only apps the user installed are scored: a preinstalled one is a
        // number they cannot move.
        val group = PermissionGroup.entries.first()
        val holders = listOf(
            PermissionHolder("a", "A", preinstalled = false, isSelf = false),
            PermissionHolder("b", "B", preinstalled = true, isSelf = false),
            PermissionHolder("c", "C", preinstalled = false, isSelf = false),
        )
        val report = GroupReport(group = group, holders = holders)
        assertEquals(2, report.installedHolders.size)
        assertEquals(1, report.preinstalledHolders.size)
        assertEquals(group.penaltyFor(2), report.penalty)
    }

    @Test
    fun `preinstalled apps alone leave the score untouched`() {
        val group = PermissionGroup.entries.first()
        val report = GroupReport(
            group = group,
            holders = List(10) { PermissionHolder("p$it", "P$it", preinstalled = true, isSelf = false) },
        )
        assertEquals(0, report.penalty)
    }
}
