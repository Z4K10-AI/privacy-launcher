package com.privacylauncher.data

/**
 * The offline "at a glance" line under the clock.
 *
 * Deliberately no weather. Weather needs the network and usually a location,
 * and this app has neither. Everything here is already on the device:
 *
 *  - the next alarm comes from AlarmManager, which needs no permission,
 *  - the battery level comes from the sticky ACTION_BATTERY_CHANGED
 *    broadcast, which also needs none.
 */
data class GlanceInfo(
    /** Formatted next alarm, e.g. "Mon 07:30". Null when none is set. */
    val nextAlarm: String? = null,
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
) {
    val isEmpty: Boolean get() = nextAlarm == null && batteryPercent == null
}
