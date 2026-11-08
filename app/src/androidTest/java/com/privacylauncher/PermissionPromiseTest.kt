package com.privacylauncher

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app's central claim, checked by machine instead of by hand.
 *
 * The README and the onboarding screen both tell the user exactly which
 * permissions this launcher asks for, and the onboarding page says a promise
 * is only worth what can be checked. That promise was wrong once already: it
 * said two permissions while the built app asked for three, because a
 * library had added one during manifest merge and nobody looked at the
 * merged result.
 *
 * That is the failure this guards. A permission can arrive without anyone
 * writing it down -- a dependency upgrade is enough -- and the manifest in
 * the source tree will still look innocent. This reads the permission list
 * out of the *installed* package, which is the only list that is true.
 *
 * It asserts an exact set rather than a denylist, so a new permission fails
 * here whether or not anyone thought to forbid it in advance.
 */
@RunWith(AndroidJUnit4::class)
class PermissionPromiseTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Added automatically by AndroidX when a receiver is registered as
     * not-exported, and named after the application id so it differs between
     * the debug and release builds. It is a signature-level permission the
     * app grants to itself and it guards nothing outside this process, so it
     * is accounted for by name rather than listed to the user as a
     * permission the launcher "asks for".
     */
    private val selfGranted = "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"

    private fun requested(): List<String> =
        context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.toList()
            .orEmpty()

    @Test
    fun theLauncherAsksForExactlyTheTwoPermissionsItTellsTheUserAbout() {
        val declared = requested().filterNot { it == selfGranted }.sorted()
        assertEquals(
            listOf(
                // Searching contacts from the drawer. Optional, and the
                // launcher works fully without it.
                "android.permission.READ_CONTACTS",
                // Changing screen brightness from the quick panel. An app-op
                // permission, granted on a settings screen, not a dialog.
                "android.permission.WRITE_SETTINGS",
            ),
            declared,
        )
    }

    @Test
    fun thereIsNoWayForThisAppToReachTheNetwork() {
        // The one promise the whole design rests on. Without INTERNET, no
        // dependency can phone home however much it would like to -- which
        // is why it is worth asserting rather than trusting.
        val all = requested()
        assertFalse(
            "INTERNET was added to the merged manifest",
            all.contains("android.permission.INTERNET"),
        )
        assertFalse(
            "ACCESS_NETWORK_STATE was added to the merged manifest",
            all.contains("android.permission.ACCESS_NETWORK_STATE"),
        )
    }

    @Test
    fun nothingTrackingShapedSlippedIn() {
        // Belt as well as braces: the exact-set test above would already
        // catch these, but naming them says what the project is refusing.
        val all = requested().joinToString(" ")
        for (forbidden in listOf(
            "AD_ID",
            "ACCESS_ADSERVICES",
            "ACCESS_FINE_LOCATION",
            "ACCESS_COARSE_LOCATION",
            "READ_PHONE_STATE",
            "GET_ACCOUNTS",
            "PACKAGE_USAGE_STATS",
            "QUERY_ALL_PACKAGES",
        )) {
            assertFalse("found $forbidden", all.contains(forbidden))
        }
    }

    @Test
    fun theOnlyPermissionGrantedAtInstallIsTheOneWeGrantOurselves() {
        // Both real permissions must be ones the user chooses later, so a
        // fresh install genuinely starts with no access to anything. If a
        // future permission were install-time ("normal"), it would be live
        // the moment the app lands, and this says so.
        val granted = requested().filter {
            context.packageManager.checkPermission(it, context.packageName) ==
                PackageManager.PERMISSION_GRANTED
        }
        assertEquals(listOf(selfGranted), granted)
    }
}
