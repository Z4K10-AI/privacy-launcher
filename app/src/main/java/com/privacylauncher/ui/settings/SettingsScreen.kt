package com.privacylauncher.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.privacylauncher.R
import com.privacylauncher.data.AccentColor
import com.privacylauncher.data.FontChoice
import com.privacylauncher.data.IconSize
import com.privacylauncher.data.LauncherSettings
import com.privacylauncher.data.ThemeMode
import com.privacylauncher.designsystem.Spacing
import com.privacylauncher.designsystem.dynamicColorAvailable
import com.privacylauncher.designsystem.resolveIsDark
import com.privacylauncher.designsystem.swatch
import com.privacylauncher.ui.components.ChoiceRow
import com.privacylauncher.ui.components.SettingsGroup
import com.privacylauncher.ui.components.SettingsRow
import com.privacylauncher.ui.components.SettingsSwitchRow
import com.privacylauncher.ui.components.SwatchRow

@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    isDefaultHome: Boolean,
    onBack: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onAccent: (AccentColor) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onShowGlance: (Boolean) -> Unit,
    onAutoKeyboard: (Boolean) -> Unit,
    onFont: (FontChoice) -> Unit,
    onIconSize: (IconSize) -> Unit,
    onDrawerColumns: (Int) -> Unit,
    onHomeColumns: (Int) -> Unit,
    onWallpaper: () -> Unit,
    onHiddenApps: () -> Unit,
    gestureServiceEnabled: Boolean,
    onGestureService: () -> Unit,
    onDesignPreview: () -> Unit,
    onSetDefaultHome: () -> Unit,
) {
    val dark = settings.themeMode.resolveIsDark()
    val dynamicOn = settings.dynamicColor && dynamicColorAvailable

    ScreenScaffold(title = stringResource(R.string.settings_title), onBack = onBack) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {

            SettingsGroup(stringResource(R.string.settings_group_appearance)) {
                ChoiceRow(
                    title = stringResource(R.string.settings_theme),
                    options = ThemeMode.entries,
                    selected = settings.themeMode,
                    label = {
                        stringResource(
                            when (it) {
                                ThemeMode.SYSTEM -> R.string.settings_theme_system
                                ThemeMode.LIGHT -> R.string.settings_theme_light
                                ThemeMode.DARK -> R.string.settings_theme_dark
                            }
                        )
                    },
                    onSelect = onThemeMode,
                )

                SettingsSwitchRow(
                    title = stringResource(R.string.settings_material_you),
                    subtitle = if (dynamicColorAvailable) {
                        stringResource(R.string.settings_material_you_body)
                    } else {
                        stringResource(R.string.settings_material_you_unavailable)
                    },
                    checked = dynamicOn,
                    enabled = dynamicColorAvailable,
                    onCheckedChange = onDynamicColor,
                )

                SwatchRow(
                    title = stringResource(R.string.settings_accent),
                    swatches = AccentColor.entries.map { it.name to it.swatch(dark) },
                    selectedLabel = settings.accent.name,
                    // Material You supplies its own colours, so the manual
                    // accent has nothing to do while it is on. Greyed rather
                    // than hidden, so the setting does not seem to vanish.
                    enabled = !dynamicOn,
                    onSelect = { name ->
                        AccentColor.entries.firstOrNull { it.name == name }?.let(onAccent)
                    },
                )

                ChoiceRow(
                    title = stringResource(R.string.settings_font),
                    options = FontChoice.entries,
                    selected = settings.font,
                    label = {
                        stringResource(
                            when (it) {
                                FontChoice.INTER -> R.string.settings_font_inter
                                FontChoice.SYSTEM -> R.string.settings_font_system
                                FontChoice.SANS_SERIF -> R.string.settings_font_sans
                                FontChoice.SERIF -> R.string.settings_font_serif
                                FontChoice.MONOSPACE -> R.string.settings_font_mono
                            }
                        )
                    },
                    onSelect = onFont,
                )

                SettingsRow(
                    title = stringResource(R.string.settings_design_preview),
                    subtitle = stringResource(R.string.settings_design_preview_body),
                    onClick = onDesignPreview,
                )
            }

            SettingsGroup(stringResource(R.string.settings_group_layout)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_glance),
                    subtitle = stringResource(R.string.settings_glance_body),
                    checked = settings.showGlance,
                    onCheckedChange = onShowGlance,
                )
                ChoiceRow(
                    title = stringResource(R.string.settings_icon_size),
                    options = IconSize.entries,
                    selected = settings.iconSize,
                    label = {
                        stringResource(
                            when (it) {
                                IconSize.SMALL -> R.string.settings_icon_small
                                IconSize.MEDIUM -> R.string.settings_icon_medium
                                IconSize.LARGE -> R.string.settings_icon_large
                            }
                        )
                    },
                    onSelect = onIconSize,
                )
                ChoiceRow(
                    title = stringResource(R.string.settings_drawer_columns),
                    options = LauncherSettings.COLUMN_CHOICES,
                    selected = settings.drawerColumns,
                    label = { it.toString() },
                    onSelect = onDrawerColumns,
                )
                ChoiceRow(
                    title = stringResource(R.string.settings_home_columns),
                    options = LauncherSettings.COLUMN_CHOICES,
                    selected = settings.homeColumns,
                    label = { it.toString() },
                    onSelect = onHomeColumns,
                )
            }

            SettingsGroup(stringResource(R.string.settings_group_search)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_auto_keyboard),
                    subtitle = stringResource(R.string.settings_auto_keyboard_body),
                    checked = settings.autoKeyboard,
                    onCheckedChange = onAutoKeyboard,
                )
            }

            SettingsGroup(stringResource(R.string.settings_group_gestures)) {
                SettingsRow(
                    title = stringResource(R.string.settings_gestures_enable),
                    subtitle = stringResource(R.string.settings_gestures_body),
                    onClick = onGestureService,
                )
                Text(
                    text = stringResource(
                        if (gestureServiceEnabled) {
                            R.string.settings_gestures_on
                        } else {
                            R.string.settings_gestures_off
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (gestureServiceEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(
                        horizontal = Spacing.lg,
                        vertical = Spacing.xxs,
                    ),
                )
            }

            SettingsGroup(stringResource(R.string.settings_group_system)) {
                SettingsRow(
                    title = stringResource(R.string.settings_wallpaper),
                    subtitle = stringResource(R.string.settings_wallpaper_body),
                    onClick = onWallpaper,
                )
                SettingsRow(
                    title = stringResource(R.string.settings_hidden_apps),
                    subtitle = if (settings.hiddenApps.isEmpty()) {
                        stringResource(R.string.settings_hidden_apps_none)
                    } else {
                        pluralStringResource(
                            R.plurals.settings_hidden_apps_count,
                            settings.hiddenApps.size,
                            settings.hiddenApps.size,
                        )
                    },
                    onClick = onHiddenApps,
                )
                if (isDefaultHome) {
                    Text(
                        text = stringResource(R.string.settings_default_home_already),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = Spacing.lg,
                            vertical = Spacing.sm,
                        ),
                    )
                } else {
                    SettingsRow(
                        title = stringResource(R.string.settings_default_home),
                        subtitle = stringResource(R.string.settings_default_home_body),
                        onClick = onSetDefaultHome,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xl))
        }
    }
}
