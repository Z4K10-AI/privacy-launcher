package com.privacylauncher.data

/**
 * Focus mode: the user picks a handful of apps, and everything else stops
 * being shown until focus ends.
 *
 * Two things are worth being plain about, and the screen says both:
 *
 *  - This hides, it does not block. A hidden app is still installed, still
 *    running whatever it was running, and can still be opened from a
 *    notification, a link, or another launcher. Only this launcher's home
 *    screen and drawer stop offering it.
 *  - The launcher's own screens are never hidden. Settings, the drawer and
 *    this screen stay reachable, so focus can always be switched off.
 *
 * Nothing here runs in the background. A focus session is not an event with
 * an alarm behind it; it is a question about the current time, answered by
 * [resolveFocus] whenever anything needs to know. See
 * [nextFocusChangeMillis] for the one timer involved, which exists only so
 * the screen redraws at the moment the answer changes while you are looking
 * at it.
 */

/** Why focus is on, if it is. */
enum class FocusSource { OFF, MANUAL, SCHEDULE }

/** A daily focus window, in minutes from midnight. */
data class FocusSchedule(
    val enabled: Boolean = false,
    /** 22:00 by default. */
    val startMinute: Int = 22 * 60,
    /** 07:00 by default, so the default window runs overnight. */
    val endMinute: Int = 7 * 60,
)

/** How long a hand-started focus session lasts. */
enum class FocusDuration(val minutes: Int) {
    /** No end time: it runs until it is switched off. */
    OPEN_ENDED(0),
    HALF_HOUR(30),
    HOUR(60),
    TWO_HOURS(120),
    FOUR_HOURS(240),
}

object FocusClock {
    const val MINUTES_PER_DAY = 24 * 60

    /** Stored in `focusUntil` for a session with no end time. */
    const val NO_END = Long.MAX_VALUE

    /**
     * The longest this will sleep before looking at the clock again.
     *
     * The maths below works out the exact moment focus next changes, so in
     * normal use this never comes into play. It is a backstop: the phone's
     * clock can jump, for a time zone, a daylight-saving change or the user
     * simply setting it, and a single long sleep computed before the jump
     * would leave the screen showing a stale answer for hours.
     */
    const val MAX_SLEEP_MS = 60L * 60L * 1000L

    const val MIN_SLEEP_MS = 1_000L
}

/** Whether a minute of the day falls inside the window. */
fun FocusSchedule.coversMinute(minute: Int): Boolean {
    // Equal start and end is an empty window, not an all-day one. Someone
    // stepping the end time past the start should not find the whole day
    // swallowed at the moment the two meet.
    if (!enabled || startMinute == endMinute) return false
    return if (startMinute < endMinute) {
        minute >= startMinute && minute < endMinute
    } else {
        // Overnight: 22:00 to 07:00 covers both sides of midnight.
        minute >= startMinute || minute < endMinute
    }
}

/** Minutes until this window next opens or closes, or null if it never does. */
fun FocusSchedule.minutesUntilChange(minute: Int): Int? {
    if (!enabled || startMinute == endMinute) return null
    val target = if (coversMinute(minute)) endMinute else startMinute
    val diff = (target - minute + FocusClock.MINUTES_PER_DAY) % FocusClock.MINUTES_PER_DAY
    // 0 means we are standing exactly on the boundary; the next one is a day
    // away, and returning 0 would spin.
    return if (diff == 0) FocusClock.MINUTES_PER_DAY else diff
}

/** What focus is doing right now. */
data class FocusState(
    val source: FocusSource = FocusSource.OFF,
    /** Component keys ("package/activity") that stay visible. */
    val allowed: Set<String> = emptySet(),
    /** When a hand-started session ends, or [FocusClock.NO_END]. */
    val manualEndsAt: Long = 0L,
    /** The running window's end, in minutes from midnight. */
    val scheduleEndMinute: Int? = null,
) {
    val active: Boolean get() = source != FocusSource.OFF

    fun allows(key: String): Boolean = !active || key in allowed
}

/**
 * Works out whether focus is on, from the stored settings and the clock.
 *
 * A hand-started session wins over the schedule, in both directions: it can
 * start focus inside a window that is not running, and `focusSnoozeUntil`
 * can end a session the schedule started without having to switch the whole
 * schedule off.
 */
fun resolveFocus(settings: LauncherSettings, nowMillis: Long, minuteOfDay: Int): FocusState {
    // 0 means "no hand-started session". It is also safely in the past, so
    // the comparison below rejects it without a special case.
    val manualRunning = settings.focusUntil == FocusClock.NO_END ||
        nowMillis < settings.focusUntil
    if (manualRunning) {
        return FocusState(
            source = FocusSource.MANUAL,
            allowed = settings.focusAllowed,
            manualEndsAt = settings.focusUntil,
        )
    }
    val scheduleRunning = settings.focusSchedule.coversMinute(minuteOfDay) &&
        nowMillis >= settings.focusSnoozeUntil
    if (scheduleRunning) {
        return FocusState(
            source = FocusSource.SCHEDULE,
            allowed = settings.focusAllowed,
            scheduleEndMinute = settings.focusSchedule.endMinute,
        )
    }
    return FocusState(allowed = settings.focusAllowed)
}

/**
 * How long to wait before the answer from [resolveFocus] could change.
 *
 * Deliberately not "one minute". The state only moves at three instants --
 * the end of a timed session, the end of a snooze, and the next edge of the
 * schedule -- so the launcher sleeps until the nearest of them instead of
 * waking sixty times an hour to ask a question whose answer it already knows.
 * Clamped by [FocusClock.MAX_SLEEP_MS]; see there for why.
 */
fun nextFocusChangeMillis(
    settings: LauncherSettings,
    nowMillis: Long,
    minuteOfDay: Int,
    /** Milliseconds already elapsed in the current minute. */
    secondOfMinuteMillis: Long,
): Long {
    val candidates = mutableListOf<Long>()

    if (settings.focusUntil != 0L && settings.focusUntil != FocusClock.NO_END) {
        candidates += settings.focusUntil - nowMillis
    }
    if (settings.focusSnoozeUntil > nowMillis) {
        candidates += settings.focusSnoozeUntil - nowMillis
    }
    settings.focusSchedule.minutesUntilChange(minuteOfDay)?.let { minutes ->
        candidates += minutes * 60_000L - secondOfMinuteMillis
    }

    val soonest = candidates.filter { it > 0 }.minOrNull() ?: FocusClock.MAX_SLEEP_MS
    return soonest.coerceIn(FocusClock.MIN_SLEEP_MS, FocusClock.MAX_SLEEP_MS)
}
