package com.privacylauncher.system

import android.content.pm.ActivityInfo

/**
 * Which way up the launcher may be.
 *
 * **On a phone it stays upright.** Turned on its side a phone's screen is
 * about 400 dp tall. The home screen fits in that, but the drawer does not
 * once the keyboard is up: the search box and the keyboard take the whole
 * height between them and the results are behind the keys. Every mainstream
 * launcher makes the same choice for the same reason, and it is what people
 * expect a home screen to do when the phone is put down on a table.
 *
 * **On a tablet or an opened folding phone it turns freely.** There is room
 * either way up, and those are held sideways as often as not.
 *
 * The line between the two is Android's own: 600 dp across the shorter
 * side, the width at which it starts calling a screen "large".
 */
object Orientation {

    const val LARGE_SCREEN_DP = 600

    /** The `requestedOrientation` for a screen whose shorter side is [smallestWidthDp]. */
    fun forScreen(smallestWidthDp: Int): Int =
        if (smallestWidthDp < LARGE_SCREEN_DP) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
}
