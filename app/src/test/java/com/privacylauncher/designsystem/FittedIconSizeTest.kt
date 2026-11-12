package com.privacylauncher.designsystem

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Icons have to fit the phone, not the other way round.
 *
 * The arithmetic of what fits where, for the screen widths phones really
 * come in. `HomeFitTest` checks the result on a real layout; this checks
 * the rule itself at widths no emulator here happens to have.
 */
class FittedIconSizeTest {

    private val medium = 52.dp
    private val large = 64.dp

    @Test
    fun whenThereIsRoomTheUsersSizeIsUsedUntouched() {
        assertEquals(medium, fittedIconSize(medium, slotWidth = 100.dp))
        assertEquals(large, fittedIconSize(large, slotWidth = 72.dp))
    }

    @Test
    fun whenThereIsNotTheIconShrinksToItsSlot() {
        // Six columns on a 320 dp Android Go phone: 50 dp a column.
        assertEquals(42.dp, fittedIconSize(large, slotWidth = 50.dp))
        assertEquals(42.dp, fittedIconSize(medium, slotWidth = 50.dp))
    }

    @Test
    fun fiveDockAppsFitOnEveryCommonPhoneWidth() {
        // The dock: 16 dp of margin each side, 8 dp of its own padding each
        // side, and each icon wants 8 dp around it.
        for (screen in listOf(320, 360, 384, 393, 411, 480)) {
            val slot = (screen.dp - 32.dp - 16.dp) / 5
            for (wanted in listOf(40.dp, medium, large)) {
                val icon = fittedIconSize(wanted, slot, breathing = 16.dp)
                val used = (icon + 16.dp) * 5
                assertTrue(
                    "on a $screen dp screen five ${wanted.value.toInt()} dp icons take ${used.value} dp",
                    used <= screen.dp - 48.dp,
                )
                assertTrue("each slot must still be 48 dp to press", icon + 16.dp >= 48.dp)
            }
        }
    }

    @Test
    fun itNeverShrinksToNothing() {
        assertEquals(24.dp, fittedIconSize(medium, slotWidth = 10.dp))
        assertEquals(24.dp, fittedIconSize(medium, slotWidth = 0.dp))
    }
}
