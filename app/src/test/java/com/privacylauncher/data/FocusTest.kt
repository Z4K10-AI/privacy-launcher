package com.privacylauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Focus mode's clock arithmetic.
 *
 * This is the part of the app most worth testing and least possible to test
 * by hand: an overnight window is wrong for only a few minutes a day, and
 * those minutes are at 02:00. Everything here is a pure function of the
 * stored settings and a minute of the day, which is exactly why it was
 * written that way.
 */
class FocusTest {

    private fun at(hour: Int, minute: Int = 0) = hour * 60 + minute

    private fun settings(
        schedule: FocusSchedule = FocusSchedule(),
        until: Long = 0L,
        snooze: Long = 0L,
    ) = LauncherSettings(
        focusSchedule = schedule,
        focusUntil = until,
        focusSnoozeUntil = snooze,
        focusAllowed = setOf("a/b"),
    )

    // --- coversMinute -----------------------------------------------------

    @Test
    fun `daytime window covers only the hours inside it`() {
        val s = FocusSchedule(enabled = true, startMinute = at(9), endMinute = at(17))
        assertFalse(s.coversMinute(at(8, 59)))
        assertTrue(s.coversMinute(at(9)))
        assertTrue(s.coversMinute(at(12)))
        assertTrue(s.coversMinute(at(16, 59)))
        // The end minute itself is outside: a 09:00-17:00 window ends at 17:00.
        assertFalse(s.coversMinute(at(17)))
    }

    @Test
    fun `overnight window covers both sides of midnight`() {
        val s = FocusSchedule(enabled = true, startMinute = at(22), endMinute = at(7))
        assertFalse(s.coversMinute(at(21, 59)))
        assertTrue(s.coversMinute(at(22)))
        assertTrue(s.coversMinute(at(23, 59)))
        assertTrue(s.coversMinute(0))
        assertTrue(s.coversMinute(at(3)))
        assertTrue(s.coversMinute(at(6, 59)))
        assertFalse(s.coversMinute(at(7)))
        assertFalse(s.coversMinute(at(12)))
    }

    @Test
    fun `equal start and end is an empty window, not an all-day one`() {
        val s = FocusSchedule(enabled = true, startMinute = at(9), endMinute = at(9))
        // Stepping the end back onto the start must not swallow the day.
        assertFalse(s.coversMinute(at(9)))
        assertFalse(s.coversMinute(at(3)))
        assertFalse(s.coversMinute(at(21)))
    }

    @Test
    fun `a disabled schedule covers nothing however it is set`() {
        val s = FocusSchedule(enabled = false, startMinute = at(22), endMinute = at(7))
        assertFalse(s.coversMinute(at(23)))
        assertFalse(s.coversMinute(at(3)))
    }

    // --- minutesUntilChange ----------------------------------------------

    @Test
    fun `counts down to the start when outside, to the end when inside`() {
        val s = FocusSchedule(enabled = true, startMinute = at(22), endMinute = at(7))
        assertEquals(60, s.minutesUntilChange(at(21)))
        assertEquals(60, s.minutesUntilChange(at(6)))
        assertEquals(7 * 60, s.minutesUntilChange(at(0)))
    }

    @Test
    fun `on a boundary it counts to the other edge`() {
        val s = FocusSchedule(enabled = true, startMinute = at(22), endMinute = at(7))
        // 22:00 is inside the window, so the next change is the 07:00 end.
        assertEquals(9 * 60, s.minutesUntilChange(at(22)))
        // 07:00 is outside it, so the next change is the 22:00 start.
        assertEquals(15 * 60, s.minutesUntilChange(at(7)))
    }

    @Test
    fun `never returns zero for any minute of any window`() {
        // The property the caller actually depends on: a zero would make the
        // focus clock sleep for no time and ask again, in a tight loop.
        //
        // This holds structurally rather than by luck. The only way to get
        // zero is for the target edge to be the current minute, and that
        // cannot happen: coversMinute(start) is always true, so the start is
        // never the target while standing on it, and coversMinute(end) is
        // always false, so the end never is either. The guard inside
        // minutesUntilChange is therefore unreachable, and this test is what
        // says so -- if a future change makes it reachable, this fails.
        val windows = listOf(
            FocusSchedule(enabled = true, startMinute = at(22), endMinute = at(7)),
            FocusSchedule(enabled = true, startMinute = at(9), endMinute = at(17)),
            FocusSchedule(enabled = true, startMinute = 0, endMinute = 1),
            FocusSchedule(enabled = true, startMinute = 1439, endMinute = 0),
            FocusSchedule(enabled = true, startMinute = 0, endMinute = 1439),
        )
        for (w in windows) {
            for (m in 0 until FocusClock.MINUTES_PER_DAY) {
                val d = w.minutesUntilChange(m)!!
                assertTrue(
                    "window $w at minute $m gave $d",
                    d in 1..FocusClock.MINUTES_PER_DAY,
                )
            }
        }
    }

    @Test
    fun `every minute of the day is inside exactly one side of a window`() {
        // A window and its complement must partition the day with no minute
        // counted twice and none missed -- the bug an overnight window
        // invites is a minute that is both or neither.
        val w = FocusSchedule(enabled = true, startMinute = at(22), endMinute = at(7))
        val inside = (0 until FocusClock.MINUTES_PER_DAY).count { w.coversMinute(it) }
        assertEquals(9 * 60, inside)
    }

    @Test
    fun `a window that never changes reports null`() {
        assertNull(FocusSchedule(enabled = false).minutesUntilChange(at(12)))
        assertNull(
            FocusSchedule(enabled = true, startMinute = at(9), endMinute = at(9))
                .minutesUntilChange(at(12))
        )
    }

    // --- resolveFocus -----------------------------------------------------

    @Test
    fun `focus is off with no session and no schedule`() {
        val state = resolveFocus(settings(), nowMillis = 1_000L, minuteOfDay = at(12))
        assertFalse(state.active)
        assertEquals(FocusSource.OFF, state.source)
    }

    @Test
    fun `a zero focusUntil is not treated as a session`() {
        // 0 is "none", and is also in the past, so it must not read as running.
        val state = resolveFocus(settings(until = 0L), nowMillis = 9_999L, minuteOfDay = at(12))
        assertFalse(state.active)
    }

    @Test
    fun `an expired timed session is over`() {
        val state = resolveFocus(settings(until = 500L), nowMillis = 1_000L, minuteOfDay = at(12))
        assertFalse(state.active)
    }

    @Test
    fun `an open-ended session stays on regardless of the clock`() {
        val state = resolveFocus(
            settings(until = FocusClock.NO_END),
            nowMillis = Long.MAX_VALUE - 1,
            minuteOfDay = at(12),
        )
        assertTrue(state.active)
        assertEquals(FocusSource.MANUAL, state.source)
    }

    @Test
    fun `a hand-started session wins inside a window that is not running`() {
        val state = resolveFocus(
            settings(schedule = FocusSchedule(enabled = false), until = 5_000L),
            nowMillis = 1_000L,
            minuteOfDay = at(12),
        )
        assertEquals(FocusSource.MANUAL, state.source)
    }

    @Test
    fun `the schedule turns focus on inside its window`() {
        val state = resolveFocus(
            settings(FocusSchedule(enabled = true, startMinute = at(22), endMinute = at(7))),
            nowMillis = 1_000L,
            minuteOfDay = at(23),
        )
        assertTrue(state.active)
        assertEquals(FocusSource.SCHEDULE, state.source)
        assertEquals(at(7), state.scheduleEndMinute)
    }

    @Test
    fun `a snooze suppresses the schedule until it expires`() {
        val s = settings(
            schedule = FocusSchedule(enabled = true, startMinute = at(22), endMinute = at(7)),
            snooze = 10_000L,
        )
        // Inside the window, but snoozed: ending focus must actually end it.
        assertFalse(resolveFocus(s, nowMillis = 5_000L, minuteOfDay = at(23)).active)
        // Once the snooze passes, the schedule takes over again.
        assertTrue(resolveFocus(s, nowMillis = 10_000L, minuteOfDay = at(23)).active)
    }

    @Test
    fun `allows lets everything through when focus is off`() {
        val off = FocusState(source = FocusSource.OFF, allowed = setOf("a/b"))
        assertTrue(off.allows("anything/at-all"))

        val on = FocusState(source = FocusSource.MANUAL, allowed = setOf("a/b"))
        assertTrue(on.allows("a/b"))
        assertFalse(on.allows("c/d"))
    }

    // --- nextFocusChangeMillis -------------------------------------------

    @Test
    fun `sleeps until the soonest of the things that could change`() {
        val s = settings(
            schedule = FocusSchedule(enabled = true, startMinute = at(22), endMinute = at(7)),
            until = 60_000L,
        )
        // The timed session ends in 30s; the schedule edge is an hour away.
        val sleep = nextFocusChangeMillis(s, nowMillis = 30_000L, minuteOfDay = at(21), secondOfMinuteMillis = 0)
        assertEquals(30_000L, sleep)
    }

    @Test
    fun `never sleeps longer than the cap, even with nothing to wait for`() {
        val sleep = nextFocusChangeMillis(settings(), 0L, at(12), 0)
        assertEquals(FocusClock.MAX_SLEEP_MS, sleep)
    }

    @Test
    fun `never busy-spins when a deadline has just passed`() {
        // focusUntil in the past would give a negative or zero delay; the
        // floor is what stops the loop burning the battery.
        val s = settings(until = 1_000L)
        val sleep = nextFocusChangeMillis(s, nowMillis = 1_000L, minuteOfDay = at(12), secondOfMinuteMillis = 0)
        assertTrue("sleep was $sleep", sleep >= FocusClock.MIN_SLEEP_MS)
    }

    @Test
    fun `subtracts the elapsed part of the current minute`() {
        val s = settings(FocusSchedule(enabled = true, startMinute = at(22), endMinute = at(7)))
        // One minute to the edge, 20s already gone: wake in 40s, not 60.
        val sleep = nextFocusChangeMillis(s, 0L, at(21, 59), secondOfMinuteMillis = 20_000L)
        assertEquals(40_000L, sleep)
    }

    @Test
    fun `an open-ended session contributes no deadline`() {
        // NO_END is Long.MAX_VALUE; subtracting now from it must not be
        // mistaken for a real, enormous deadline or overflow into a negative.
        val sleep = nextFocusChangeMillis(
            settings(until = FocusClock.NO_END),
            nowMillis = 1_000L,
            minuteOfDay = at(12),
            secondOfMinuteMillis = 0,
        )
        assertEquals(FocusClock.MAX_SLEEP_MS, sleep)
    }
}
