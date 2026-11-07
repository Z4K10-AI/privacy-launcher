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
import androidx.compose.ui.unit.dp
import com.privacylauncher.R
import com.privacylauncher.data.AccentColor
import com.privacylauncher.data.FontChoice
import com.privacylauncher.data.IconSize
import com.privacylauncher.data.LauncherSettings
import com.privacylauncher.data.ThemeMode
import com.privacylauncher.ui.components.ChoiceRow
import com.privacylauncher.ui.components.SettingsGroup
import com.privacylauncher.ui.components.SettingsRow
import com.privacylauncher.ui.components.SwatchRow
import com.privacylauncher.ui.theme.resolveIsDark
import com.privacylauncher.ui.theme.swatch

@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    isDefaultHome: Boolean,
    onBack: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onAccent: (AccentColor) -> Unit,
    onFont: (FontChoice) -> Unit,
    onIconSize: (IconSize) -> Unit,
    onDrawerColumns: (Int) -> Unit,
    onHomeColumns: (Int) -> Unit,
    onWallpaper: () -> Unit,
    onHiddenApps: () -> Unit,
    onSetDefaultHome: () -> Unit,
) {
    val dark = settings.themeMode.resolveIsDark()

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

                SwatchRow(
                    title = stringResource(R.string.settings_accent),
                    swatches = AccentColor.entries.map { it.name to it.swatch(dark) },
                    selectedLabel = settings.accent.name,
                    onSelect = { name ->
                        AccentColor.entries
                            .firstOrNull { it.name == name }
                            ?.let(onAccent)
                    },
                )

                ChoiceRow(
                    title = stringResource(R.string.settings_font),
                    options = FontChoice.entries,
                    selected = settings.font,
                    label = {
                        stringResource(
                            when (it) {
                                FontChoice.SYSTEM -> R.string.settings_font_system
                                FontChoice.SANS_SERIF -> R.string.settings_font_sans
                                FontChoice.SERIF -> R.string.settings_font_serif
                                FontChoice.MONOSPACE -> R.string.settings_font_mono
                            }
                        )
                    },
                    onSelect = onFont,
                )
            }

            SettingsGroup(stringResource(R.string.settings_group_layout)) {
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
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                } else {
                    SettingsRow(
                        title = stringResource(R.string.settings_default_home),
                        subtitle = stringResource(R.string.settings_default_home_body),
                        onClick = onSetDefaultHome,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
