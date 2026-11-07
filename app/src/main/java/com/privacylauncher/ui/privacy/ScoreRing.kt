package com.privacylauncher.ui.privacy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.privacylauncher.designsystem.Motion
import kotlin.math.roundToInt

/** Long enough to read as a sweep rather than a jump, short enough not to wait. */
private const val SWEEP_MS = 900

/**
 * The privacy score, as a ring that sweeps up to its value.
 *
 * Two things here are about cost rather than looks. The sweep is an
 * [Animatable] read inside the Canvas draw lambda, which re-runs on the
 * animation thread without recomposing anything. And the number beside it
 * goes through [derivedStateOf], so the one Text that shows it invalidates
 * when the rounded integer changes -- at most a hundred times over the whole
 * sweep -- rather than on every frame.
 */
@Composable
fun ScoreRing(
    score: Int,
    label: String,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 184.dp,
    thickness: Dp = 14.dp,
) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(score) {
        sweep.animateTo(
            targetValue = score.coerceIn(0, 100) / 100f,
            animationSpec = tween(SWEEP_MS, easing = Motion.Easing),
        )
    }
    val shown by remember { derivedStateOf { (sweep.value * 100f).roundToInt() } }

    Box(
        modifier = modifier
            .size(diameter)
            // One label for the whole thing. Without this a screen reader
            // reads a number with no units and then an unrelated word.
            .semantics { contentDescription = "$score. $label" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = thickness.toPx()
            val inset = stroke / 2f
            val arc = Size(size.width - stroke, size.height - stroke)
            val style = Stroke(width = stroke, cap = StrokeCap.Round)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arc,
                style = style,
            )
            // From the top, clockwise. A round cap on a zero-length arc still
            // paints a dot, so a score of 0 is drawn as nothing at all.
            if (sweep.value > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * sweep.value,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arc,
                    style = style,
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = shown.toString(),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
            )
        }
    }
}
