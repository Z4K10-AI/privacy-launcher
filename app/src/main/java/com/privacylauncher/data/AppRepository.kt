package com.privacylauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the list of installed, launchable apps from the system.
 *
 * Everything here is a local query against [PackageManager]. No network, no
 * remote catalogue, no telemetry.
 */
class AppRepository(private val context: Context) {

    private val packageManager: PackageManager get() = context.packageManager

    /**
     * Every app that has a home-screen icon, sorted by name.
     *
     * Runs on a background thread: on a phone with 150+ apps this takes long
     * enough to drop frames if done on the main thread.
     */
    suspend fun loadApps(): List<AppEntry> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        packageManager.queryIntentActivities(intent, 0)
            .asSequence()
            .mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                // Our own launcher should not appear in its own drawer.
                if (activityInfo.packageName == context.packageName) return@mapNotNull null

                AppEntry(
                    packageName = activityInfo.packageName,
                    activityName = activityInfo.name,
                    label = resolveInfo.loadLabel(packageManager).toString(),
                )
            }
            .distinctBy { it.key }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }

    /** Starts an app. Returns false if it vanished since the list was built. */
    fun launch(entry: AppEntry): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(entry.packageName, entry.activityName))
            .setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
            )
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** Opens the system "App info" page, where permissions can be changed. */
    fun openAppInfo(packageName: String): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** Asks the system to uninstall an app. Android shows its own confirmation. */
    fun requestUninstall(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_DELETE)
            .setData(Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** True when this app is currently the system's home screen. */
    fun isDefaultHome(): Boolean {
        val resolved = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /**
     * Opens Android's home-app picker.
     *
     * An app can never make itself the launcher; only the user can, in system
     * settings. We can only take them to the right screen.
     */
    fun openHomeAppPicker(): Boolean {
        val preferred = Intent(Settings.ACTION_HOME_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(preferred) }.isSuccess) return true

        // Some vendor builds (including a few Samsung ones) hide that screen.
        // Falling back to the general settings list still gets the user there.
        val fallback = Intent(Settings.ACTION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(fallback) }.isSuccess
    }
}
