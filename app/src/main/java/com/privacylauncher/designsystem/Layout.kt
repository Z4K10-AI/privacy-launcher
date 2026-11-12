package com.privacylauncher.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize

/**
 * The window's size in dp.
 *
 * Read from [LocalWindowInfo], not `LocalConfiguration.screenWidthDp`. The
 * Configuration values describe the screen rather than this window, so in
 * split-screen or a freeform window they report more space than the launcher
 * actually has — and the layout decisions below are exactly the ones that go
 * wrong when the number is too big.
 */
@Composable
fun windowSizeDp(): DpSize {
    val size = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) {
        DpSize(size.width.toDp(), size.height.toDp())
    }
}

/** True when the window is short enough that a 64sp clock crowds the grid. */
@Composable
fun isShortWindow(): Boolean = windowSizeDp().height < SHORT_WINDOW_HEIGHT

/**
 * The column count to actually use, given the user's preference.
 *
 * In portrait the preference is used as-is. In a wide window it is doubled,
 * because the space is roughly twice as wide and half as tall: keeping the
 * portrait count there produced very wide cells and pushed the second row of
 * the home grid off the bottom, where it was sliced through the middle.
 *
 * Capped at eight. Beyond that, cells get narrower than a comfortable touch
 * target and the labels truncate to nothing useful.
 */
@Composable
fun adaptiveColumns(preferred: Int): Int {
    val size = windowSizeDp()
    val wide = size.width > size.height
    return if (wide) (preferred * 2).coerceAtMost(MAX_COLUMNS) else preferred
}

/**
 * The size to draw an icon at so that it fits a slot [slotWidth] wide.
 *
 * Icon size is the user's choice, and so are the number of columns and how
 * many apps sit in the dock. On a wide phone any combination fits. On a
 * narrow one they can ask for more than the screen has: five dock apps at
 * the default size need 340 dp and most phones offer 312; six columns of
 * large icons need 384 dp and an Android Go phone is 320 dp wide in all.
 *
 * Left alone, a row deals with that by giving the first icons their full
 * size and crushing the last one into what remains, and a grid lets icons
 * spill over their neighbours. So every place that draws icons side by
 * side asks this first. The user's size is used whenever it fits, and
 * otherwise the largest size that does, with [breathing] left around it.
 *
 * Never smaller than 24 dp: below that an icon is a smudge, and a screen
 * too narrow for that is not one a launcher can do anything about.
 */
fun fittedIconSize(wanted: Dp, slotWidth: Dp, breathing: Dp = Spacing.xs): Dp =
    minOf(wanted, slotWidth - breathing).coerceAtLeast(SMALLEST_ICON)

private val SMALLEST_ICON: Dp = Dp(24f)
private val SHORT_WINDOW_HEIGHT: Dp = Dp(480f)
private const val MAX_COLUMNS = 8
