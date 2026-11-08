package com.privacylauncher.ui.focus

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.privacylauncher.R
import com.privacylauncher.data.FocusClock
import com.privacylauncher.data.FocusDuration
import com.privacylauncher.data.FocusSource
import com.privacylauncher.data.FocusState
import com.privacylauncher.data.LauncherSettings
import com.privacylauncher.designsystem.Radii
import com.privacylauncher.designsystem.Spacing
import com.privacylauncher.designsystem.cardSurface
import com.privacylauncher.ui.components.ChoiceRow
import com.privacylauncher.ui.components.SettingsGroup
import com.privacylauncher.ui.components.SettingsRow
import com.privacylauncher.ui.components.SettingsSwitchRow
import com.privacylauncher.ui.settings.ScreenScaffold
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Focus mode's own screen: start or end a session, choose what stays
 * visible, and set the daily window.
 *
 * The honesty block at the bottom is not decoration. Focus looks like a
 * blocker and is not one, and someone relying on it to keep them off an app
 * should know exactly how far it goes before they rely on it.
 */
@Composable
fun FocusScreen(
    settings: LauncherSettings,
    focus: FocusState,
    allowedCount: Int,
    onBack: () -> Unit,
    onStart: (FocusDuration) -> Unit,
    onEnd: () -> Unit,
    onPickApps: () -> Unit,
    onScheduleEnabled: (Boolean) -> Unit,
    /** Steps for the start and end of the window, in 15-minute units. */
    onNudgeWindow: (Int, Int) -> Unit,
) {
    val context = LocalContext.current
    val use24Hour = DateFormat.is24HourFormat(context)
    var duration by remember { mutableStateOf(FocusDuration.OPEN_ENDED) }

    ScreenScaffold(title = stringResource(R.string.focus_title), onBack = onBack) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md)
                    .clip(RoundedCornerShape(Radii.card))
                    .cardSurface(level = 1)
                    .padding(Spacing.md),
            ) {
                Text(
                    text = if (focus.active) {
                        stringResource(R.string.focus_state_on)
                    } else {
                        stringResource(R.string.focus_state_off)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = focusSubtitle(focus, use24Hour),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.sm))

                if (focus.active) {
                    Button(onClick = onEnd) { Text(stringResource(R.string.focus_end)) }
                } else {
                    // Starting focus with nothing allowed would empty the home
                    // screen and the drawer in one go, which reads as a crash
                    // rather than a feature. The button says what to do first.
                    Button(
                        onClick = { onStart(duration) },
                        enabled = allowedCount > 0,
                    ) {
                        Text(stringResource(R.string.focus_start))
                    }
                    if (allowedCount == 0) {
                        Text(
                            text = stringResource(R.string.focus_needs_apps),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
            }

            if (!focus.active) {
                SettingsGroup(stringResource(R.string.focus_group_session)) {
                    ChoiceRow(
                        title = stringResource(R.string.focus_length),
                        options = FocusDuration.entries,
                        selected = duration,
                        label = { stringResource(it.labelRes()) },
                        onSelect = { duration = it },
                    )
                }
            }

            SettingsGroup(stringResource(R.string.focus_group_apps)) {
                SettingsRow(
                    title = stringResource(R.string.focus_allowed_apps),
                    subtitle = pluralStringResource(
                        R.plurals.focus_allowed_count,
                        allowedCount,
                        allowedCount,
                    ),
                    onClick = onPickApps,
                )
            }

            SettingsGroup(stringResource(R.string.focus_group_schedule)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.focus_schedule),
                    subtitle = scheduleSubtitle(settings, use24Hour),
                    checked = settings.focusSchedule.enabled,
                    onCheckedChange = onScheduleEnabled,
                )
                TimeStepper(
                    title = stringResource(R.string.focus_schedule_start),
                    minute = settings.focusSchedule.startMinute,
                    use24Hour = use24Hour,
                    enabled = settings.focusSchedule.enabled,
                    onStep = { onNudgeWindow(it, 0) },
                )
                TimeStepper(
                    title = stringResource(R.string.focus_schedule_end),
                    minute = settings.focusSchedule.endMinute,
                    use24Hour = use24Hour,
                    enabled = settings.focusSchedule.enabled,
                    onStep = { onNudgeWindow(0, it) },
                )
            }

            SettingsGroup(stringResource(R.string.focus_group_truth)) {
                Text(
                    text = stringResource(R.string.focus_truth_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }

            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

/**
 * A time, stepped a quarter of an hour at a time.
 *
 * Not Material3's TimePicker, which is still experimental API, and not the
 * platform's TimePickerDialog, which arrives wearing the system's theme
 * rather than this one. Two taps gets you half an hour, and the window is a
 * thing people nudge rather than set once.
 */
@Composable
private fun TimeStepper(
    title: String,
    minute: Int,
    use24Hour: Boolean,
    enabled: Boolean,
    onStep: (Int) -> Unit,
) {
    // Both steppers on this screen are a "-" and a "+" with no text, so a
    // screen reader met four buttons announced as "minus sign" and "plus
    // sign" with nothing to say which row they belonged to or what they
    // changed. The row's own title goes into each label.
    val stepEarlier = pluralStringResource(
        R.plurals.focus_step_earlier,
        LauncherSettings.FOCUS_TIME_STEP,
        title,
        LauncherSettings.FOCUS_TIME_STEP,
    )
    val stepLater = pluralStringResource(
        R.plurals.focus_step_later,
        LauncherSettings.FOCUS_TIME_STEP,
        title,
        LauncherSettings.FOCUS_TIME_STEP,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { onStep(-1) },
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = stepEarlier },
        ) {
            // U+2212 MINUS SIGN, not a hyphen: a hyphen next to a "+" at the
            // same size reads as a dash in a sentence. Cleared for the screen
            // reader so the glyph is not read out after the label.
            Text(
                text = "−",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        Text(
            text = formatMinuteOfDay(minute, use24Hour),
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            // A minimum, not a fixed width: 80dp holds "10:00 PM" at the
            // default font size and clips it at 1.3x. The minimum is what
            // keeps the two steppers from shifting as the time changes.
            modifier = Modifier.defaultMinSize(minWidth = 80.dp),
            textAlign = TextAlign.Center,
        )
        IconButton(
            onClick = { onStep(1) },
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = stepLater },
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

@Composable
private fun focusSubtitle(focus: FocusState, use24Hour: Boolean): String = when (focus.source) {
    FocusSource.OFF -> stringResource(R.string.focus_state_off_body)
    FocusSource.SCHEDULE -> stringResource(
        R.string.focus_state_scheduled_body,
        formatMinuteOfDay(focus.scheduleEndMinute ?: 0, use24Hour),
    )
    FocusSource.MANUAL ->
        if (focus.manualEndsAt == FocusClock.NO_END) {
            stringResource(R.string.focus_state_open_body)
        } else {
            stringResource(
                R.string.focus_state_until_body,
                formatInstant(focus.manualEndsAt, use24Hour),
            )
        }
}

@Composable
private fun scheduleSubtitle(settings: LauncherSettings, use24Hour: Boolean): String {
    val schedule = settings.focusSchedule
    if (!schedule.enabled) return stringResource(R.string.focus_schedule_off_body)
    val start = formatMinuteOfDay(schedule.startMinute, use24Hour)
    val end = formatMinuteOfDay(schedule.endMinute, use24Hour)
    return if (schedule.startMinute > schedule.endMinute) {
        stringResource(R.string.focus_schedule_overnight_body, start, end)
    } else {
        stringResource(R.string.focus_schedule_on_body, start, end)
    }
}

/** A label for the picker; the enum itself holds no strings. */
private fun FocusDuration.labelRes(): Int = when (this) {
    FocusDuration.OPEN_ENDED -> R.string.focus_length_open
    FocusDuration.HALF_HOUR -> R.string.focus_length_30
    FocusDuration.HOUR -> R.string.focus_length_60
    FocusDuration.TWO_HOURS -> R.string.focus_length_120
    FocusDuration.FOUR_HOURS -> R.string.focus_length_240
}

private fun timeFormatter(use24Hour: Boolean): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a")

internal fun formatMinuteOfDay(minute: Int, use24Hour: Boolean): String =
    LocalTime.of(minute / 60, minute % 60).format(timeFormatter(use24Hour))

private fun formatInstant(millis: Long, use24Hour: Boolean): String =
    java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalTime()
        .format(timeFormatter(use24Hour))
