package com.privacylauncher.data

import androidx.compose.runtime.Immutable
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.annotation.StringRes
import com.privacylauncher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A system settings screen that drawer search can jump to.
 *
 * Labels are our own strings rather than the system's. Asking Android for
 * them means calling `loadLabel` on each activity, and many settings
 * activities report a generic "Settings" -- which would give a search result
 * list of sixteen identical rows.
 */
@Immutable
data class SettingsTarget(
    @param:StringRes val labelRes: Int,
    val action: String,
) {
    val id: String get() = "set:$action"
}

/**
 * The settings screens that match [query], best first, and none at all
 * until the user has switched this on.
 *
 * [label] is passed in because the names are string resources, and a test
 * on the build machine has no resources to read them from.
 */
internal fun rankSettingsTargets(
    enabled: Boolean,
    query: String,
    targets: List<SettingsTarget>,
    max: Int,
    label: (SettingsTarget) -> String,
): List<SettingsTarget> {
    if (!enabled || query.isBlank()) return emptyList()
    return targets
        .map { it to Fuzzy.score(label(it), query) }
        .filter { it.second != Fuzzy.NO_MATCH }
        .sortedByDescending { it.second }
        .take(max)
        .map { it.first }
}

/**
 * The table of settings screens, and the one place that checks which of them
 * this device actually has.
 *
 * Every action is resolved once and the misses are dropped, because the list
 * below is deliberately optimistic: some of these screens do not exist on
 * every Android version or every manufacturer's build, and a search result
 * that opens nothing is worse than no result.
 */
object SettingsTargets {

    private val ALL = listOf(
        SettingsTarget(R.string.target_wifi, Settings.ACTION_WIFI_SETTINGS),
        SettingsTarget(R.string.target_bluetooth, Settings.ACTION_BLUETOOTH_SETTINGS),
        SettingsTarget(R.string.target_network, Settings.ACTION_WIRELESS_SETTINGS),
        SettingsTarget(R.string.target_display, Settings.ACTION_DISPLAY_SETTINGS),
        SettingsTarget(R.string.target_sound, Settings.ACTION_SOUND_SETTINGS),
        SettingsTarget(R.string.target_battery, Intent.ACTION_POWER_USAGE_SUMMARY),
        SettingsTarget(R.string.target_storage, Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
        SettingsTarget(R.string.target_apps, Settings.ACTION_APPLICATION_SETTINGS),
        SettingsTarget(R.string.target_notifications, ACTION_NOTIFICATION_SETTINGS),
        SettingsTarget(R.string.target_location, Settings.ACTION_LOCATION_SOURCE_SETTINGS),
        SettingsTarget(R.string.target_privacy, Settings.ACTION_PRIVACY_SETTINGS),
        SettingsTarget(R.string.target_security, Settings.ACTION_SECURITY_SETTINGS),
        SettingsTarget(R.string.target_accessibility, Settings.ACTION_ACCESSIBILITY_SETTINGS),
        SettingsTarget(R.string.target_default_apps, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
        SettingsTarget(R.string.target_vpn, Settings.ACTION_VPN_SETTINGS),
        SettingsTarget(R.string.target_date_time, Settings.ACTION_DATE_SETTINGS),
        SettingsTarget(R.string.target_languages, Settings.ACTION_LOCALE_SETTINGS),
        SettingsTarget(
            R.string.target_developer,
            Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
        ),
        SettingsTarget(R.string.target_about, Settings.ACTION_DEVICE_INFO_SETTINGS),
    )

    /**
     * The subset this device can actually open.
     *
     * Done on an IO dispatcher and once per process: each entry is a call
     * into the package manager, and nineteen of them on the main thread the
     * first time someone types a letter would be visible as a stutter.
     */
    suspend fun available(context: Context): List<SettingsTarget> =
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            ALL.filter { target ->
                packageManager.resolveActivity(
                    Intent(target.action),
                    PackageManager.MATCH_DEFAULT_ONLY,
                ) != null
            }
        }

    /**
     * Not a Settings constant: there is no public one for the notifications
     * screen, but the action string has been stable across Android versions.
     * It is resolve-checked like everything else, so a build without it
     * simply drops the entry.
     */
    private const val ACTION_NOTIFICATION_SETTINGS = "android.settings.NOTIFICATION_SETTINGS"
}
