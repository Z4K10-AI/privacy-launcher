package com.privacylauncher.data

import android.Manifest
import android.annotation.SuppressLint
import androidx.annotation.StringRes
import com.privacylauncher.R
import kotlin.math.roundToInt

/**
 * The sensitive permission groups the privacy dashboard reports on.
 *
 * Grouped the way Android's own permission UI groups them, because that is
 * what the user will see when they tap through to change something. Granting
 * any one permission in a group is what counts: an app with RECORD_AUDIO can
 * hear you whether or not it also has anything else.
 *
 * [weight] drives the score. It is a judgement, not a measurement, and the
 * dashboard says so on screen and shows its working. The ordering behind the
 * numbers: the microphone, your location and your messages are the things you
 * cannot tell are being read, so they cost most; the camera has a mandatory
 * indicator on modern Android; files and phone state are sensitive but far
 * more often legitimately needed.
 *
 * **Why the cost is not simply so-much-per-app.** It was, at first, and the
 * arithmetic made the whole thing useless: an ordinary phone with a browser,
 * a messenger, a camera app and a maps app ran straight past every cap and
 * floored at zero. A score every phone scores zero on measures nothing. So
 * each group's cost rises towards [weight] and levels off -- the tenth app
 * with camera access matters, but less than the first did. The weights sum to
 * 72, so the worst reachable score is 28 rather than 0: a phone with apps on
 * it cannot be perfectly private, and the number should not pretend there is
 * a floor you are about to hit.
 */
@SuppressLint("InlinedApi")
// ACCESS_BACKGROUND_LOCATION and the READ_MEDIA_* constants were added after
// this app's minSdk of 26, and lint says so. They are compile-time string
// constants and nothing here calls an API: the strings are compared against
// what a package's manifest declares. On an older phone no package declares
// them, the comparison simply never matches, and nothing breaks.
enum class PermissionGroup(
    @param:StringRes val labelRes: Int,
    @param:StringRes val bodyRes: Int,
    /** The most this group can take off the score, however many apps hold it. */
    val weight: Int,
    val permissions: List<String>,
) {
    LOCATION(
        labelRes = R.string.perm_location,
        bodyRes = R.string.perm_location_body,
        weight = 14,
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ),
    ),
    MICROPHONE(
        labelRes = R.string.perm_microphone,
        bodyRes = R.string.perm_microphone_body,
        weight = 14,
        permissions = listOf(Manifest.permission.RECORD_AUDIO),
    ),
    SMS(
        labelRes = R.string.perm_sms,
        bodyRes = R.string.perm_sms_body,
        weight = 10,
        permissions = listOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.RECEIVE_MMS,
        ),
    ),
    CAMERA(
        labelRes = R.string.perm_camera,
        bodyRes = R.string.perm_camera_body,
        weight = 12,
        permissions = listOf(Manifest.permission.CAMERA),
    ),
    CONTACTS(
        labelRes = R.string.perm_contacts,
        bodyRes = R.string.perm_contacts_body,
        weight = 8,
        permissions = listOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
        ),
    ),
    PHONE(
        labelRes = R.string.perm_phone,
        bodyRes = R.string.perm_phone_body,
        weight = 8,
        permissions = listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.ANSWER_PHONE_CALLS,
        ),
    ),
    FILES(
        labelRes = R.string.perm_files,
        bodyRes = R.string.perm_files_body,
        weight = 6,
        // MANAGE_EXTERNAL_STORAGE ("all files access") is deliberately not
        // here. It is an app-op permission, not a runtime one, so the granted
        // flag on PackageInfo does not reliably say whether the user actually
        // allowed it -- and a dashboard that reports a permission as held when
        // it is not is worse than one that stays quiet.
        permissions = listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        ),
    ),
    ;

    /**
     * Score cost of [count] apps holding something in this group.
     *
     * weight * count / (count + SOFTNESS): zero at none, a quarter of the
     * weight at one app, half at three, and approaching the weight without
     * ever quite reaching it.
     */
    fun penaltyFor(count: Int): Int {
        if (count <= 0) return 0
        return ((weight * count).toFloat() / (count + SOFTNESS)).roundToInt()
    }

    companion object {
        /**
         * How fast a group's cost approaches its weight. Three apps with the
         * same access is the half-way point, which matches how an ordinary
         * phone actually looks.
         */
        private const val SOFTNESS = 3
    }
}
