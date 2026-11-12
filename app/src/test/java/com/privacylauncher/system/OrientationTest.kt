package com.privacylauncher.system

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A phone's home screen stays upright; a tablet's or an opened folding
 * phone's may turn.
 */
class OrientationTest {

    @Test
    fun everyPhoneWidthStaysUpright() {
        // From the narrowest Android Go handset to the widest slab phone.
        for (width in listOf(320, 360, 384, 393, 411, 480, 599)) {
            assertEquals(
                "$width dp",
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                Orientation.forScreen(width),
            )
        }
    }

    @Test
    fun aTabletOrAnOpenedFoldingPhoneMayTurn() {
        for (width in listOf(600, 673, 800)) {
            assertEquals(
                "$width dp",
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                Orientation.forScreen(width),
            )
        }
    }
}
