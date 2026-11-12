package com.privacylauncher.ui.quick

import androidx.annotation.StringRes
import com.privacylauncher.R
import com.privacylauncher.data.PrivateDnsMode
import com.privacylauncher.data.QuickControlsState
import com.privacylauncher.data.Toggle

/**
 * The eight tiles, in the order they appear.
 *
 * [opensSettings] is the honest flag: true for everything Android will not
 * let an ordinary app change, which is everything except the flashlight and
 * the lock-screen action. The panel draws a corner arrow on those so the tap
 * target does not pretend to be a switch.
 */
enum class QuickTile(
    @param:StringRes val labelRes: Int,
    val opensSettings: Boolean,
) {
    // The order here is the order on the panel, and the two that act come
    // first. Lock down used to be last of the eight, which on a short
    // screen with large text put it below the bottom edge: the one tile a
    // person reaches for in a hurry was the one they had to scroll to.
    LOCKDOWN(R.string.quick_lockdown, opensSettings = false),
    FLASHLIGHT(R.string.quick_flashlight, opensSettings = false),
    WIFI(R.string.quick_wifi, opensSettings = true),
    BLUETOOTH(R.string.quick_bluetooth, opensSettings = true),
    LOCATION(R.string.quick_location, opensSettings = true),
    DND(R.string.quick_dnd, opensSettings = true),
    ROTATION(R.string.quick_rotation, opensSettings = true),
    PRIVATE_DNS(R.string.quick_private_dns, opensSettings = true),
    ;

    fun toggle(state: QuickControlsState): Toggle = when (this) {
        WIFI -> state.wifi
        BLUETOOTH -> state.bluetooth
        LOCATION -> state.location
        FLASHLIGHT -> state.torch
        DND -> state.dnd
        ROTATION -> state.autoRotate
        PRIVATE_DNS -> when (state.privateDns) {
            PrivateDnsMode.OFF -> Toggle.OFF
            PrivateDnsMode.AUTOMATIC, PrivateDnsMode.HOSTNAME -> Toggle.ON
            PrivateDnsMode.UNKNOWN -> Toggle.UNKNOWN
        }
        // Not a state, an action. It is never drawn as "on".
        LOCKDOWN -> Toggle.OFF
    }

    fun isActive(state: QuickControlsState): Boolean = toggle(state) == Toggle.ON
}
