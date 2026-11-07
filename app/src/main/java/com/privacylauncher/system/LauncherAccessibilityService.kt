package com.privacylauncher.system

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/**
 * An accessibility service used for exactly two things: pulling down the
 * notification shade, and locking the screen.
 *
 * **Why a launcher needs one at all.** Android gives no ordinary app a way to
 * open the notification panel or lock the device. `expandNotificationsPanel`
 * is system-only, and there is no lock API. Accessibility services can do
 * both through `performGlobalAction`, and that is the only route available.
 *
 * **Why this one is safe to enable.** Accessibility is a powerful permission,
 * and in a privacy launcher it deserves scrutiny rather than a shrug. This
 * service is configured to be as close to inert as the platform allows:
 *
 *  - The config declares no `accessibilityEventTypes` at all. That attribute
 *    is a flags value with no "none" constant, so omitting it is how a
 *    service subscribes to zero event types: the system never tells it
 *    anything that happens on screen.
 *  - `canRetrieveWindowContent="false"` — it cannot read window content. Not
 *    "does not": the system refuses it the capability.
 *  - It declares no `canPerformGestures`, no key filtering, and no
 *    `packageNames` beyond the default.
 *
 * [onAccessibilityEvent] is empty because there is nothing to handle, and
 * nothing is logged or stored. The whole service is the two methods below.
 *
 * It is entirely optional: the launcher works without it, and the gestures
 * that use it simply do nothing until the user turns it on in Android's own
 * accessibility settings.
 */
class LauncherAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /** No events are subscribed to, so this is never called with anything. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: LauncherAccessibilityService? = null

        /** True only while the user has the service switched on. */
        val isEnabled: Boolean get() = instance != null

        /** Pulls down the notification shade. No-op when not enabled. */
        fun openNotifications(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) ?: false

        /**
         * Locks the screen. Needs Android 9, where the action was added.
         */
        fun lockScreen(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            return instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false
        }

        val lockScreenSupported: Boolean
            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    }
}
