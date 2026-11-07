package com.privacylauncher.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.privacylauncher.R
import com.privacylauncher.designsystem.LocalSurfaceTones
import com.privacylauncher.designsystem.blurAvailable
import com.privacylauncher.designsystem.Motion
import com.privacylauncher.designsystem.Radii
import com.privacylauncher.designsystem.Spacing
import com.privacylauncher.designsystem.cardSurface
import com.privacylauncher.designsystem.rememberHaptics
import com.privacylauncher.ui.components.ChoiceRow
import com.privacylauncher.ui.components.SettingsGroup
import com.privacylauncher.ui.components.SettingsRow
import com.privacylauncher.ui.components.SettingsSwitchRow

/**
 * Every token in the design system, on one screen.
 *
 * This exists to be looked at. A design system that lives only as constants
 * scattered through screens cannot be reviewed as a whole, and drift goes
 * unnoticed until something already looks wrong.
 */
@Composable
fun DesignPreviewScreen(onBack: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.preview_title), onBack = onBack) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.xl),
        ) {
            TypeSection()
            ColourSection()
            SurfaceSection()
            ControlSection()
            SpacingSection()
            MotionSection()
            BlurStatus()
        }
    }
}

@Composable
private fun TypeSection() {
    SettingsGroup(stringResource(R.string.preview_type)) {
        Column(Modifier.padding(horizontal = Spacing.lg)) {
            val t = MaterialTheme.typography
            Sample("displayLarge  64 / Light", t.displayLarge, "12:45")
            Sample("displayMedium 44 / Light", t.displayMedium, "Saturday")
            Sample("headlineSmall 24 / Medium", t.headlineSmall, "Settings")
            Sample("titleLarge    20 / Medium", t.titleLarge, "Appearance")
            Sample("bodyLarge     16 / Regular", t.bodyLarge, "Change wallpaper")
            Sample("bodyMedium    14 / Regular", t.bodyMedium, "Opens Android's picker.")
            Sample("labelLarge    14 / Medium", t.labelLarge, "Medium")
            Sample("labelSmall    11 / Regular", t.labelSmall, "Calendar")
        }
    }
}

@Composable
private fun Sample(caption: String, style: TextStyle, text: String) {
    Column(Modifier.padding(bottom = Spacing.md)) {
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = style,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun ColourSection() {
    val c = MaterialTheme.colorScheme
    SettingsGroup(stringResource(R.string.preview_colour)) {
        Column(Modifier.padding(horizontal = Spacing.lg)) {
            Swatches(
                listOf(
                    "primary" to c.primary,
                    "primaryContainer" to c.primaryContainer,
                    "background" to c.background,
                    "surface" to c.surface,
                    "surfaceVariant" to c.surfaceVariant,
                    "error" to c.error,
                )
            )
        }
    }
}

@Composable
private fun Swatches(items: List<Pair<String, Color>>) {
    Column {
        items.chunked(3).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                row.forEach { (name, colour) ->
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(Spacing.xxl)
                                .clip(RoundedCornerShape(Radii.card))
                                .background(colour)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(Radii.card),
                                ),
                        )
                        Spacer(Modifier.height(Spacing.xxs))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SurfaceSection() {
    val tones = LocalSurfaceTones.current
    SettingsGroup(stringResource(R.string.preview_surfaces)) {
        Column(Modifier.padding(horizontal = Spacing.lg)) {
            listOf(
                "level0  background" to tones.level0,
                "level1  cards" to tones.level1,
                "level2  raised" to tones.level2,
                "level3  highest" to tones.level3,
            ).forEach { (name, colour) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.xs)
                        .clip(RoundedCornerShape(Radii.card))
                        .background(colour)
                        .padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            Text(
                text = "Depth is these steps, never a drop shadow.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
        }
    }
}

@Composable
private fun ControlSection() {
    var chip by remember { mutableStateOf("Medium") }
    var toggle by remember { mutableStateOf(true) }

    SettingsGroup(stringResource(R.string.preview_controls)) {
        ChoiceRow(
            title = stringResource(R.string.preview_sample_chip),
            options = listOf("Small", "Medium", "Large"),
            selected = chip,
            label = { it },
            onSelect = { chip = it },
        )
        SettingsSwitchRow(
            title = "Switch row",
            subtitle = "48dp minimum height, haptic on change",
            checked = toggle,
            onCheckedChange = { toggle = it },
        )
        SettingsRow(
            title = "Tappable row",
            subtitle = "Title plus supporting line",
            onClick = {},
        )
        Box(
            Modifier
                .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.card))
                .cardSurface(level = 2)
                .padding(Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.preview_sample_card),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun SpacingSection() {
    SettingsGroup(stringResource(R.string.preview_spacing)) {
        Column(Modifier.padding(horizontal = Spacing.lg)) {
            listOf(
                "xxs  4" to Spacing.xxs,
                "xs   8" to Spacing.xs,
                "sm   12" to Spacing.sm,
                "md   16" to Spacing.md,
                "lg   24" to Spacing.lg,
                "xl   32" to Spacing.xl,
                "xxl  48" to Spacing.xxl,
            ).forEach { (name, value) -> SpacingBar(name, value) }
        }
    }
}

@Composable
private fun SpacingBar(name: String, value: Dp) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(Spacing.xxl + Spacing.md),
        )
        Box(
            Modifier
                .width(value)
                .height(Spacing.sm)
                .clip(RoundedCornerShape(Radii.pill))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun MotionSection() {
    val haptics = rememberHaptics()
    var toggled by remember { mutableStateOf(false) }

    SettingsGroup(stringResource(R.string.preview_motion)) {
        Column(Modifier.padding(horizontal = Spacing.lg)) {
            Text(
                text = stringResource(R.string.preview_motion_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
            listOf(
                "short  150ms" to Motion.short<Float>(),
                "medium 250ms" to Motion.medium<Float>(),
                "long   350ms" to Motion.long<Float>(),
                "settle spring" to Motion.settle<Float>(),
            ).forEach { (name, spec) ->
                val offset by animateFloatAsState(
                    targetValue = if (toggled) 1f else 0f,
                    animationSpec = spec,
                    label = name,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.xs)
                        .clickable {
                            haptics.tick()
                            toggled = !toggled
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(Spacing.xxl + Spacing.xl),
                    )
                    Box(Modifier.weight(1f).height(Spacing.lg)) {
                        Box(
                            Modifier
                                .graphicsLayer {
                                    translationX = offset * (size.width - 24.dp.toPx())
                                }
                                .size(Spacing.lg)
                                .clip(RoundedCornerShape(Radii.pill))
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BlurStatus() {
    Text(
        text = stringResource(
            if (blurAvailable) R.string.preview_blur_on else R.string.preview_blur_off
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
    )
}
