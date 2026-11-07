package com.privacylauncher.designsystem

import androidx.compose.ui.unit.dp

/**
 * The only spacing values allowed in this app.
 *
 * One 4dp-based scale. If a layout seems to need 14dp or 18dp, the answer is
 * to pick the nearer step, not to add a value here. Arbitrary padding is what
 * makes an interface feel unconsidered, and it is invisible in code review
 * precisely because each individual number looks reasonable.
 */
object Spacing {
    /** 4dp — hairline gaps, icon-to-badge offsets. */
    val xxs = 4.dp
    /** 8dp — between tightly related items. */
    val xs = 8.dp
    /** 12dp — inside chips and small controls. */
    val sm = 12.dp
    /** 16dp — the default gutter and row padding. */
    val md = 16.dp
    /** 24dp — screen side margins, between groups. */
    val lg = 24.dp
    /** 32dp — above and below major sections. */
    val xl = 32.dp
    /** 48dp — large breathing room, empty states. */
    val xxl = 48.dp

    /** Minimum touch target. Anything tappable must reach this. */
    val touchTarget = 48.dp
}
