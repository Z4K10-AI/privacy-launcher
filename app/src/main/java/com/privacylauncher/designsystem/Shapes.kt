package com.privacylauncher.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * One corner-radius scale.
 *
 * Three values, each with a job. Mixing radii at random is the fastest way to
 * make a layout look assembled from parts rather than designed.
 */
object Radii {
    /** 8dp — small controls, inline buttons. */
    val small = 8.dp
    /** 12dp — cards and list rows. */
    val card = 12.dp
    /** 20dp — sheets, dialogs, anything that overlays. */
    val sheet = 20.dp
    /** Fully round — chips, pills, swatches. */
    val pill = 1000.dp
}

internal val LauncherShapes = Shapes(
    extraSmall = RoundedCornerShape(Radii.small),
    small = RoundedCornerShape(Radii.small),
    medium = RoundedCornerShape(Radii.card),
    large = RoundedCornerShape(Radii.sheet),
    extraLarge = RoundedCornerShape(Radii.sheet),
)
