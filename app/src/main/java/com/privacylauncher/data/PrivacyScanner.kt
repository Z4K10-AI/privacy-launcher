package com.privacylauncher.data

import androidx.compose.runtime.Immutable
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One app that holds something in a permission group. */
@Immutable
data class PermissionHolder(
    val packageName: String,
    val label: String,
    /** Came with the phone, rather than being installed by the user. */
    val preinstalled: Boolean,
    /** This launcher itself. Shown like any other app, and counted like one. */
    val isSelf: Boolean,
)

/** One group's findings. */
@Immutable
data class GroupReport(
    val group: PermissionGroup,
    val holders: List<PermissionHolder>,
) {
    val installedHolders: List<PermissionHolder> = holders.filterNot { it.preinstalled }
    val preinstalledHolders: List<PermissionHolder> = holders.filter { it.preinstalled }

    /** Only apps the user installed are scored. See [PrivacyReport]. */
    val penalty: Int = group.penaltyFor(installedHolders.size)
}

/** The whole dashboard, computed in one pass. */
@Immutable
data class PrivacyReport(
    val groups: List<GroupReport> = emptyList(),
    val score: Int = 100,
    /** Apps the score looked at: visible, and installed by the user. */
    val installedAppsScanned: Int = 0,
    /** Apps that came with the phone, listed but not scored. */
    val preinstalledAppsScanned: Int = 0,
    /** True once a scan has finished, so the UI can tell empty from pending. */
    val ready: Boolean = false,
) {
    val band: ScoreBand = ScoreBand.of(score)

    /** Groups with something to show, worst first. */
    val rankedGroups: List<GroupReport> =
        groups.sortedWith(
            compareByDescending<GroupReport> { it.penalty }
                .thenByDescending { it.holders.size }
                .thenBy { it.group.ordinal },
        )
}

enum class ScoreBand { GOOD, FAIR, ATTENTION;
    companion object {
        fun of(score: Int): ScoreBand = when {
            score >= 80 -> GOOD
            score >= 50 -> FAIR
            else -> ATTENTION
        }
    }
}

/**
 * Builds the privacy dashboard's picture of the phone.
 *
 * **What it can see, and what it cannot.** This app does not hold
 * QUERY_ALL_PACKAGES, which is a sensitive permission Android grants
 * grudgingly and which would undercut the point of the app. It therefore sees
 * the packages its `<queries>` block makes visible: everything with a launcher
 * icon, plus whatever the platform makes visible to all apps. In practice that
 * is every app the user can see and open, which is also every app they can do
 * anything about. Background-only packages with no launcher entry are not
 * listed, and the screen says so rather than implying the list is exhaustive.
 *
 * **What "holds" means.** Only runtime permissions that are actually granted,
 * read from PackageInfo's requested-permission flags. An app that declares
 * CAMERA in its manifest but has never been granted it does not appear.
 *
 * **Scoring counts apps the user installed.** Preinstalled apps are listed
 * under their group but excluded from the score. Two reasons: the user cannot
 * uninstall them, so counting them is a number they cannot move; and every
 * phone of a given model ships the same set, which would turn the score into
 * a measure of the manufacturer rather than of anything the user has done.
 */
class PrivacyScanner(private val context: Context) {

    suspend fun scan(): PrivacyReport = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val packages = runCatching { installedPackages(packageManager) }
            .getOrDefault(emptyList())

        val byGroup = PermissionGroup.entries.associateWith { mutableListOf<PermissionHolder>() }
        var installedCount = 0
        var preinstalledCount = 0

        for (info in packages) {
            val appInfo = info.applicationInfo ?: continue
            val granted = grantedPermissions(info)
            if (granted.isEmpty()) continue

            val preinstalled = appInfo.isPreinstalled()
            val holder = PermissionHolder(
                packageName = info.packageName,
                label = runCatching {
                    packageManager.getApplicationLabel(appInfo).toString()
                }.getOrDefault(info.packageName),
                preinstalled = preinstalled,
                // The launcher is listed among the rest on purpose. A privacy
                // dashboard that quietly leaves out the app showing it has
                // answered the wrong question.
                isSelf = info.packageName == context.packageName,
            )

            var counted = false
            for (group in PermissionGroup.entries) {
                if (group.permissions.any { it in granted }) {
                    byGroup.getValue(group) += holder
                    counted = true
                }
            }
            if (counted) {
                if (preinstalled) preinstalledCount++ else installedCount++
            }
        }

        val reports = PermissionGroup.entries.map { group ->
            GroupReport(
                group = group,
                holders = byGroup.getValue(group).sortedWith(
                    compareBy<PermissionHolder> { it.preinstalled }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label },
                ),
            )
        }

        PrivacyReport(
            groups = reports,
            score = (100 - reports.sumOf { it.penalty }).coerceIn(0, 100),
            installedAppsScanned = installedCount,
            preinstalledAppsScanned = preinstalledCount,
            ready = true,
        )
    }

    // Lint's QueryPermissionsNeeded: since Android 11 this returns only the
    // packages we can see rather than all of them. That is the documented,
    // intended behaviour here -- the alternative is QUERY_ALL_PACKAGES, which
    // this app will not ask for. The class comment and the dashboard itself
    // both say the list is limited.
    @SuppressLint("QueryPermissionsNeeded")
    private fun installedPackages(packageManager: PackageManager): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(
                    PackageManager.GET_PERMISSIONS.toLong(),
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }

    /**
     * The permissions this package has actually been granted.
     *
     * requestedPermissions lists what the manifest asks for; the parallel
     * flags array says which of those are currently granted. Reading only the
     * first would report every app that has ever thought about the camera.
     */
    private fun grantedPermissions(info: PackageInfo): Set<String> {
        val requested = info.requestedPermissions ?: return emptySet()
        val flags = info.requestedPermissionsFlags ?: return emptySet()
        val result = mutableSetOf<String>()
        for (i in requested.indices) {
            if (i >= flags.size) break
            if (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0) {
                result += requested[i]
            }
        }
        return result
    }

    private fun ApplicationInfo.isPreinstalled(): Boolean =
        (flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

    // --- jumping to Android's own screens ---------------------------------

    /**
     * Opens Android's App info page for one app, where Permissions lives.
     *
     * **Why not the permission list itself.** The permission controller does
     * expose `android.intent.action.MANAGE_APP_PERMISSIONS`, which lands
     * straight on the list, and it was tried here first. Starting it from an
     * ordinary app is refused:
     *
     *     Permission Denial: starting Intent
     *     { act=android.intent.action.MANAGE_APP_PERMISSIONS ... }
     *     requires android.permission.GRANT_RUNTIME_PERMISSIONS
     *
     * That permission is signature|privileged, so no installable app can hold
     * it. App info is as close as Android allows, and the screen says so
     * rather than labelling the row as something it is not.
     *
     * Worth recording for the ROM build this launcher is eventually for: as a
     * privileged system app it could hold GRANT_RUNTIME_PERMISSIONS, and the
     * direct route would then work.
     */
    fun openPermissions(packageName: String): Boolean {
        val details = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(details) }.isSuccess
    }

    /**
     * Opens Android's own privacy screen, which is where the recent-usage
     * timeline this app cannot read lives.
     *
     * Where it lands has moved: on Android 12 and 13 this is the Privacy
     * settings page with the dashboard on it, and on 14 and later the same
     * action opens Safety Center, which holds the dashboard a level down.
     * The on-screen wording says "privacy settings" for that reason rather
     * than naming a screen that is not always what appears.
     */
    fun openSystemPrivacy(): Boolean {
        val intent = Intent(Settings.ACTION_PRIVACY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    companion object {
        /**
         * Android 12 added the system Privacy dashboard, which shows which
         * app used the camera, microphone or location and when.
         *
         * No ordinary app can read that history: it sits behind
         * GET_APP_OPS_STATS, which is signature|privileged. So this app does
         * not pretend to have it. Where the system screen exists we link to
         * it; where it does not, we say so.
         */
        val systemUsageTimelineAvailable: Boolean
            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    }
}
