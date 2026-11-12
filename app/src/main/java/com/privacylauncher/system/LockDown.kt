package com.privacylauncher.system

import android.app.KeyguardManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * The part of the launcher Android knows as a "device admin app".
 *
 * It is empty, and has to exist: this class is what the user switches on
 * and off, and what Android checks before letting the launcher lock the
 * screen. `res/xml/lock_down_admin.xml` lists what it may do, which is one
 * thing: lock the screen. It cannot erase the phone, set or change the PIN,
 * disable the camera, or see anything, and Android enforces that, not this
 * code.
 */
class LockDownAdminReceiver : DeviceAdminReceiver()

/** What stands between the user and a working Lock down, if anything. */
enum class LockDownState {
    /** Allowed, and the phone has a screen lock: the tile locks at once. */
    READY,

    /** The phone has no PIN, pattern or password, so there is nothing to lock it with. */
    NEEDS_SCREEN_LOCK,

    /** The user has not yet allowed the launcher to lock the screen. */
    NEEDS_PERMISSION,
}

/**
 * Lock down: shut the phone so that only the PIN, pattern or password opens
 * it again.
 *
 * **Why it is more than "lock the screen".** A phone locked the ordinary
 * way opens to a fingerprint or a face, and a finger can be pressed to a
 * sensor by someone else. Android's own Lockdown, in the power menu, turns
 * those off until the PIN has been typed once. No app can start that. But
 * `DevicePolicyManager.lockNow()` does the part that matters: Android's
 * documentation says that after it "the device must be unlocked using
 * strong authentication (PIN, pattern, or password)", and its source sets
 * the flag that makes the lock screen refuse biometrics. `tools/soak.sh`
 * reads that flag back from a locked emulator.
 *
 * **What it costs.** Calling it needs the launcher to be a device admin
 * app, which the user grants on one of Android's own screens and can take
 * back at any time. While it is granted Android will not uninstall the
 * launcher; that is Android's rule for every device admin app, and the
 * dialog says so before the user agrees.
 *
 * **What it is not.** Android's Lockdown also hides notifications on the
 * lock screen. This cannot, and the panel says so.
 *
 * The launcher used to offer "Lock down" through its accessibility service,
 * which locks the screen and nothing more: a fingerprint still opened it.
 * That is still what double-tap-to-lock does, where it is the right thing.
 */
object LockDown {

    /** The decision, apart from Android, so it can be tested. */
    fun state(allowed: Boolean, screenLockSet: Boolean): LockDownState = when {
        !screenLockSet -> LockDownState.NEEDS_SCREEN_LOCK
        !allowed -> LockDownState.NEEDS_PERMISSION
        else -> LockDownState.READY
    }

    fun component(context: Context) = ComponentName(context, LockDownAdminReceiver::class.java)

    private fun policy(context: Context): DevicePolicyManager? =
        context.getSystemService(DevicePolicyManager::class.java)

    /** Whether the user has allowed the launcher to lock the screen. */
    fun isAllowed(context: Context): Boolean =
        runCatching { policy(context)?.isAdminActive(component(context)) == true }
            .getOrDefault(false)

    /** Whether the phone has a PIN, pattern or password at all. */
    fun screenLockSet(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    fun state(context: Context): LockDownState =
        state(allowed = isAllowed(context), screenLockSet = screenLockSet(context))

    /**
     * Locks now. Returns false if Android refused, which it does when the
     * permission was taken away since the launcher last looked.
     */
    fun lockNow(context: Context): Boolean =
        try {
            policy(context)?.lockNow()
            true
        } catch (e: SecurityException) {
            false
        }

    /** Android's own screen for granting it, with [explanation] shown on it. */
    fun allowIntent(context: Context, explanation: String): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, explanation)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Gives the permission back. After this the launcher can be uninstalled again. */
    fun giveUp(context: Context) {
        runCatching { policy(context)?.removeActiveAdmin(component(context)) }
    }
}
