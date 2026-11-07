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

private val SHORT_WINDOW_HEIGHT: Dp = Dp(480f)
private const val MAX_COLUMNS = 8
