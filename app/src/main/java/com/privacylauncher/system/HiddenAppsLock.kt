package com.privacylauncher.system

/**
 * Something that can make the user prove it is them.
 *
 * In the app this is [DeviceUnlock], which shows Android's own PIN, pattern
 * or fingerprint screen. It is an interface so the rule below can be tested
 * with a stand-in that says yes or no on demand.
 */
fun interface Unlocker {
    /** Runs [onUnlocked] if, and only if, the user proves it is them. */
    fun confirm(title: String, description: String, onUnlocked: () -> Unit)
}

/**
 * What the hidden-apps lock stands in front of.
 *
 * Opening the list is the obvious one. The other three are the ways around
 * it, and a lock that leaves them open is a lock with the key left in it:
 *
 *  - turning the lock off, which used to take one tap and no PIN;
 *  - saving a backup, because the file lists the hidden apps in plain text;
 *  - restoring a backup, because that replaces both the list and the lock
 *    setting with whatever the file says.
 */
enum class Guarded { OPEN_HIDDEN_APPS, TURN_LOCK_OFF, SAVE_BACKUP, RESTORE_BACKUP }

object HiddenAppsLock {

    /**
     * Whether [action] has to wait for the user to unlock.
     *
     * A backup is only guarded while something is actually hidden. The lock
     * is on by default, and asking everyone with a PIN to enter it before
     * every backup, to protect a list with nothing on it, would be a cost
     * with no benefit.
     *
     * This says nothing about whether the phone *can* ask. On a phone with
     * no screen lock [DeviceUnlock.confirm] lets everything through,
     * because refusing would lock the user out of their own settings with
     * no way back; the Settings screen says so next to the switch.
     */
    fun requiresUnlock(action: Guarded, lockOn: Boolean, hiddenCount: Int): Boolean =
        when (action) {
            Guarded.OPEN_HIDDEN_APPS, Guarded.TURN_LOCK_OFF -> lockOn
            Guarded.SAVE_BACKUP, Guarded.RESTORE_BACKUP -> lockOn && hiddenCount > 0
        }
}

/** Runs [run], behind the phone's own lock when [HiddenAppsLock] says so. */
fun Unlocker.guard(
    action: Guarded,
    lockOn: Boolean,
    hiddenCount: Int,
    title: String,
    description: String,
    run: () -> Unit,
) {
    if (HiddenAppsLock.requiresUnlock(action, lockOn, hiddenCount)) {
        confirm(title, description, run)
    } else {
        run()
    }
}

/**
 * What the "Require unlock" switch does when it is moved to [wanted].
 *
 * Turning it on is free. Turning it off goes through the lock it is turning
 * off.
 */
fun Unlocker.moveLockSwitch(
    wanted: Boolean,
    lockOn: Boolean,
    title: String,
    description: String,
    set: (Boolean) -> Unit,
) {
    if (wanted) {
        set(true)
    } else {
        guard(Guarded.TURN_LOCK_OFF, lockOn, hiddenCount = 0, title, description) { set(false) }
    }
}
