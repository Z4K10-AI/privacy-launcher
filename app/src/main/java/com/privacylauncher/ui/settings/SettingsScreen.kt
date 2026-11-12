package com.privacylauncher.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.privacylauncher.R
import com.privacylauncher.data.AccentColor
import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.BackupResult
import com.privacylauncher.data.FontChoice
import com.privacylauncher.data.IconLoader
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
import com.privacylauncher.system.Guarded
import com.privacylauncher.system.LockDownState

/**
 * Every launcher preference, in six sections.
 *
 * The grouping is by *where you will see the change*, not by which part of
 * the code owns it: icon size sits under Home with the grid it resizes, even
 * though it also changes the drawer, because the home screen is where anyone
 * notices. Appearance holds only what changes the whole app at once.
 */
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
    onShowMostUsed: (Boolean) -> Unit,
    onClearUsage: () -> Unit,
    onSearchContacts: (Boolean) -> Unit,
    onSearchSettings: (Boolean) -> Unit,
    onAppInfo: () -> Unit,
    onFont: (FontChoice) -> Unit,
    onIconSize: (IconSize) -> Unit,
    onDrawerColumns: (Int) -> Unit,
    onHomeColumns: (Int) -> Unit,
    onWallpaper: () -> Unit,
    onHiddenApps: () -> Unit,
    deviceSecure: Boolean,
    onLockHiddenApps: (Boolean) -> Unit,
    gestureServiceEnabled: Boolean,
    onGestureService: () -> Unit,
    lockDownState: LockDownState,
    onLockDown: () -> Unit,
    onDesignPreview: () -> Unit,
    onPrivacyDashboard: () -> Unit,
    onQuickPanel: () -> Unit,
    focusActive: Boolean,
    onFocus: () -> Unit,
    onMinimalMode: (Boolean) -> Unit,
    onSetDefaultHome: () -> Unit,
    /** A few real apps, for the live grid sample. */
    previewEntries: List<AppEntry>,
    iconLoader: IconLoader,
    appVersion: String,
    backupFileName: String,
    onBackupTo: (Uri) -> Unit,
    onRestoreFrom: (Uri) -> Unit,
    backupResult: BackupResult?,
    onDismissBackupResult: () -> Unit,
    /**
     * Runs something that the hidden-apps lock may stand in front of.
     *
     * A backup lists the hidden apps and a restore replaces the list, so
     * both go through here before Android's file picker opens. The default
     * lets everything through, for previews and tests.
     */
    guard: (Guarded, () -> Unit) -> Unit = { _, run -> run() },
) {
    val dark = settings.themeMode.resolveIsDark()
    val dynamicOn = settings.dynamicColor && dynamicColorAvailable
    var confirmClearUsage by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }

    // Android's own file picker. No storage permission is involved: the user
    // chooses the file and the system hands back a Uri for that one file.
    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(onBackupTo) }

    val openFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onRestoreFrom) }

    if (confirmClearUsage) {
        // Worth a confirmation even though nothing irreplaceable is lost: the
        // row sits next to ordinary switches, and a mis-tap that silently
        // wipes a record you cannot see is a bad surprise.
        AlertDialog(
            onDismissRequest = { confirmClearUsage = false },
            title = { Text(stringResource(R.string.settings_clear_usage)) },
            text = { Text(stringResource(R.string.settings_clear_usage_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearUsage = false
                        onClearUsage()
                    },
                ) { Text(stringResource(R.string.settings_clear_usage_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearUsage = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (confirmRestore) {
        // Restoring replaces the home layout, the dock and every preference
        // at once, and there is no undo. Saying exactly what goes and what
        // stays before the file picker opens is the only honest order.
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(stringResource(R.string.settings_restore)) },
            text = { Text(stringResource(R.string.settings_restore_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        guard(Guarded.RESTORE_BACKUP) {
                            openFile.launch(arrayOf("application/json", "text/plain", "*/*"))
                        }
                    },
                ) { Text(stringResource(R.string.settings_restore_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    backupResult?.let { result ->
        AlertDialog(
            onDismissRequest = onDismissBackupResult,
            title = {
                Text(
                    stringResource(
                        when (result) {
                            BackupResult.SAVED -> R.string.settings_backup_saved
                            BackupResult.SAVE_FAILED -> R.string.settings_backup_failed
                            BackupResult.RESTORED -> R.string.settings_restore_done
                            BackupResult.RESTORE_NOT_A_BACKUP,
                            BackupResult.RESTORE_UNREADABLE,
                            -> R.string.settings_restore_failed
                        }
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        when (result) {
                            BackupResult.SAVED -> R.string.settings_backup_saved_body
                            BackupResult.SAVE_FAILED -> R.string.settings_backup_failed_body
                            BackupResult.RESTORED -> R.string.settings_restore_done_body
                            BackupResult.RESTORE_NOT_A_BACKUP ->
                                R.string.settings_restore_not_backup_body
                            BackupResult.RESTORE_UNREADABLE ->
                                R.string.settings_restore_unreadable_body
                        }
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = onDismissBackupResult) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }

    ScreenScaffold(title = stringResource(R.string.settings_title), onBack = onBack) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {

            // --- Appearance: what changes the whole app at once ------------
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
                FontPreview()

                SettingsRow(
                    title = stringResource(R.string.settings_design_preview),
                    subtitle = stringResource(R.string.settings_design_preview_body),
                    onClick = onDesignPreview,
                )
            }

            // --- Home ------------------------------------------------------
            SettingsGroup(stringResource(R.string.settings_group_home)) {
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
                    title = stringResource(R.string.settings_home_columns),
                    options = LauncherSettings.COLUMN_CHOICES,
                    selected = settings.homeColumns,
                    label = { it.toString() },
                    onSelect = onHomeColumns,
                )
                // One sample under both controls rather than one each: they
                // describe the same grid, and two cards showing the same
                // thing would be noise.
                GridPreview(
                    entries = previewEntries,
                    iconLoader = iconLoader,
                    iconSize = settings.iconSize,
                    columns = settings.homeColumns,
                )

                SettingsSwitchRow(
                    title = stringResource(R.string.settings_glance),
                    subtitle = stringResource(R.string.settings_glance_body),
                    checked = settings.showGlance,
                    onCheckedChange = onShowGlance,
                )
                SettingsRow(
                    title = stringResource(R.string.settings_focus),
                    subtitle = if (focusActive) {
                        stringResource(R.string.settings_focus_on)
                    } else {
                        stringResource(R.string.settings_focus_body)
                    },
                    onClick = onFocus,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_minimal),
                    subtitle = stringResource(R.string.settings_minimal_body),
                    checked = settings.minimalMode,
                    onCheckedChange = onMinimalMode,
                )
                SettingsRow(
                    title = stringResource(R.string.settings_wallpaper),
                    subtitle = stringResource(R.string.settings_wallpaper_body),
                    onClick = onWallpaper,
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

            // --- Drawer ----------------------------------------------------
            SettingsGroup(stringResource(R.string.settings_group_drawer)) {
                ChoiceRow(
                    title = stringResource(R.string.settings_drawer_columns),
                    options = LauncherSettings.COLUMN_CHOICES,
                    selected = settings.drawerColumns,
                    label = { it.toString() },
                    onSelect = onDrawerColumns,
                )
                GridPreview(
                    entries = previewEntries,
                    iconLoader = iconLoader,
                    iconSize = settings.iconSize,
                    columns = settings.drawerColumns,
                )

                SettingsSwitchRow(
                    title = stringResource(R.string.settings_auto_keyboard),
                    subtitle = stringResource(R.string.settings_auto_keyboard_body),
                    checked = settings.autoKeyboard,
                    onCheckedChange = onAutoKeyboard,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_most_used),
                    subtitle = stringResource(R.string.settings_most_used_body),
                    checked = settings.showMostUsed,
                    onCheckedChange = onShowMostUsed,
                )
                SettingsRow(
                    title = stringResource(R.string.settings_clear_usage),
                    subtitle = stringResource(R.string.settings_clear_usage_body),
                    onClick = { confirmClearUsage = true },
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_search_settings),
                    subtitle = stringResource(R.string.settings_search_settings_body),
                    checked = settings.searchSettings,
                    onCheckedChange = onSearchSettings,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_contacts),
                    subtitle = stringResource(R.string.settings_contacts_body),
                    checked = settings.searchContacts,
                    onCheckedChange = onSearchContacts,
                )
                if (!settings.searchContacts) {
                    Text(
                        text = stringResource(R.string.settings_contacts_revoke),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = Spacing.lg,
                            vertical = Spacing.xxs,
                        ),
                    )
                }
            }

            // --- Gestures --------------------------------------------------
            SettingsGroup(stringResource(R.string.settings_group_gestures)) {
                SettingsRow(
                    title = stringResource(R.string.settings_quick_panel),
                    subtitle = stringResource(R.string.settings_quick_panel_body),
                    onClick = onQuickPanel,
                )
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
                SettingsRow(
                    title = stringResource(R.string.quick_lockdown),
                    subtitle = stringResource(
                        when (lockDownState) {
                            LockDownState.READY -> R.string.settings_lock_down_on
                            LockDownState.NEEDS_PERMISSION -> R.string.settings_lock_down_off
                            LockDownState.NEEDS_SCREEN_LOCK -> R.string.settings_lock_down_no_lock
                        },
                    ),
                    onClick = onLockDown,
                )
            }

            // --- Privacy ---------------------------------------------------
            SettingsGroup(stringResource(R.string.settings_group_privacy)) {
                SettingsRow(
                    title = stringResource(R.string.settings_privacy_dashboard),
                    subtitle = stringResource(R.string.settings_privacy_dashboard_body),
                    onClick = onPrivacyDashboard,
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
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_lock_hidden),
                    subtitle = stringResource(R.string.settings_lock_hidden_body),
                    checked = settings.lockHiddenApps,
                    onCheckedChange = onLockHiddenApps,
                )
                if (!deviceSecure) {
                    // Left switchable on purpose: the preference is still
                    // meaningful, and it starts working the moment a screen
                    // lock exists. Saying so beats a greyed-out row that
                    // gives no reason.
                    Text(
                        text = stringResource(R.string.settings_lock_hidden_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = Spacing.lg,
                            vertical = Spacing.xxs,
                        ),
                    )
                }
            }

            // --- About -----------------------------------------------------
            SettingsGroup(stringResource(R.string.settings_group_about)) {
                SettingsRow(
                    title = stringResource(R.string.settings_backup),
                    subtitle = stringResource(R.string.settings_backup_body),
                    onClick = { guard(Guarded.SAVE_BACKUP) { saveFile.launch(backupFileName) } },
                )
                SettingsRow(
                    title = stringResource(R.string.settings_restore),
                    subtitle = stringResource(R.string.settings_restore_body),
                    onClick = { confirmRestore = true },
                )
                SettingsRow(
                    title = stringResource(R.string.settings_app_info),
                    subtitle = stringResource(R.string.settings_app_info_body),
                    onClick = onAppInfo,
                )
                Text(
                    text = stringResource(R.string.settings_version, appVersion),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = Spacing.sm,
                    ),
                )
                Text(
                    text = stringResource(R.string.settings_licences),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = Spacing.xxs,
                        bottom = Spacing.sm,
                    ),
                )
            }

            Spacer(Modifier.height(Spacing.xl))
        }
    }
}
