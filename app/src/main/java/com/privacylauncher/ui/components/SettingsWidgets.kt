package com.privacylauncher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.privacylauncher.designsystem.Motion
import com.privacylauncher.designsystem.Radii
import com.privacylauncher.designsystem.Spacing
import com.privacylauncher.designsystem.rememberHaptics
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.contentDescription

/** A titled block of settings rows. */
@Composable
fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = Spacing.sm)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            // Marked as a heading so TalkBack's "next heading" gesture steps
            // between sections. Settings is six sections of rows, and without
            // this the only way through it with a screen reader is one row at
            // a time from the top.
            modifier = Modifier
                .semantics { heading() }
                .padding(
                    start = Spacing.lg,
                    top = Spacing.lg,
                    bottom = Spacing.xs,
                ),
        )
        content()
    }
}

/** A tappable row with a title and an explanatory second line. */
@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null && enabled) {
                    Modifier.clickable(role = Role.Button) {
                        haptics.tick()
                        onClick()
                    }
                } else {
                    Modifier
                }
            )
            // Keeps the row at or above the 48dp minimum touch target.
            .defaultMinSize(minHeight = Spacing.touchTarget)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowBody(title = title, subtitle = subtitle, trailing = trailing)
    }
}

/**
 * A settings row whose control is a switch.
 *
 * The whole row is one `toggleable` with `Role.Switch`, and the switch
 * inside it is not interactive. That arrangement matters for anyone using
 * TalkBack, and it is easy to get wrong -- this row previously did.
 *
 * It used to be a [SettingsRow] with `onClick` plus a `Switch` that had its
 * own `onCheckedChange`. Sighted use was fine, but the screen reader saw two
 * separate controls for one setting: the row announced as "Button" with no
 * on/off state at all, and then the switch announced again right after it.
 * One setting, two stops, and the state only on the second.
 *
 * With `toggleable` on the row and `onCheckedChange = null` on the switch,
 * the switch's semantics merge into the row's: one stop, announced as a
 * switch, with its state and the subtitle read as part of it. It also makes
 * the entire row the target rather than just the switch at the far edge.
 */
@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = {
                    haptics.tick()
                    onCheckedChange(it)
                },
            )
            .defaultMinSize(minHeight = Spacing.touchTarget)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowBody(title = title, subtitle = subtitle) {
            // Not interactive: the row above owns the gesture and the
            // semantics. A switch with its own onCheckedChange here would
            // put the setting back on the screen twice.
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    }
}

/**
 * The shared insides of both rows.
 *
 * Extracted rather than duplicated: the two rows differ only in which
 * modifier they carry, and a second copy of this layout would drift from
 * the first the moment either was adjusted.
 */
@Composable
private fun RowScope.RowBody(
    title: String,
    subtitle: String?,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.weight(1f)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (trailing != null) {
        Box(Modifier.padding(start = Spacing.md)) { trailing() }
    }
}

/**
 * A label plus a row of mutually exclusive chips.
 *
 * Used instead of a dropdown because every one of these settings has at most
 * six options, and showing them all makes the effect of each obvious without
 * an extra tap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    /** @Composable so callers can resolve a string resource per option. */
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        // FlowRow, not Row: with five font options, equal-weight chips in a
        // single row squeezed "Monospace" until it broke mid-word. Chips now
        // size to their text and wrap to a second line when they need to.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            options.forEach { option ->
                Chip(
                    text = label(option),
                    selected = option == selected,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}

@Composable
private fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    // Animated so selection does not jump between states.
    val bg by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = Motion.short(),
        label = "chipBackground",
    )
    val border by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        },
        animationSpec = Motion.short(),
        label = "chipBorder",
    )
    val fg = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radii.pill))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(Radii.pill))
            .clickable(role = Role.RadioButton) {
                haptics.tick()
                onClick()
            }
            .defaultMinSize(minWidth = 72.dp, minHeight = Spacing.touchTarget)
            .padding(vertical = Spacing.sm, horizontal = Spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** A row of colour circles, one per accent. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SwatchRow(
    title: String,
    swatches: List<Pair<String, Color>>,
    selectedLabel: String,
    enabled: Boolean = true,
    /**
     * What a screen reader calls each swatch, by its label. A dot of colour
     * says nothing by itself: without this every swatch was an unnamed
     * button, and choosing an accent without seeing the screen was a guess.
     */
    spokenName: (String) -> String = { it },
    onSelect: (String) -> Unit,
) {
    val haptics = rememberHaptics()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(bottom = Spacing.sm),
        )
        // FlowRow, not Row, for the same reason ChoiceRow uses one.
        //
        // Six 48dp touch targets with 12dp between them need 348dp, and a
        // 360dp-wide phone has 312dp once this column's own insets are
        // taken off. On a 411dp test device they fit with 15dp to spare; on
        // a Galaxy A30 the sixth swatch was simply cut off at the screen
        // edge, with no hint that a colour was missing. A Row overflows
        // silently, which is the worst way for a layout to fail.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            swatches.forEach { (label, color) ->
                val isSelected = enabled && label == selectedLabel
                Box(
                    modifier = Modifier
                        // 48dp hit area around a 36dp dot: the visible circle
                        // can stay small without the target being too small.
                        .size(Spacing.touchTarget)
                        .clip(CircleShape)
                        // selectable, not clickable: one of several, and a
                        // screen reader should say which one is chosen.
                        .selectable(
                            selected = isSelected,
                            enabled = enabled,
                            role = Role.RadioButton,
                        ) {
                            haptics.tick()
                            onSelect(label)
                        }
                        .semantics { contentDescription = spokenName(label) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (enabled) color else color.copy(alpha = 0.3f))
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onBackground,
                                shape = CircleShape,
                            ),
                    )
                }
            }
        }
    }
}
