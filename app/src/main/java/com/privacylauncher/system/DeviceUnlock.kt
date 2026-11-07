package com.privacylauncher.system

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Asks the user to prove it is them, using the phone's own lock.
 *
 * Built on [KeyguardManager.createConfirmDeviceCredentialIntent] rather than
 * `androidx.biometric`. That library is the usual answer and it is a fine
 * library, but it is a dependency of some size for one dialog, and this
 * project is explicitly meant to stay small enough for a 4 GB phone and to
 * ship with as little third-party code as can be managed. The platform call
 * costs nothing, needs no permission -- not even USE_BIOMETRIC -- and shows
 * the same system sheet: fingerprint or face where they are enrolled, PIN,
 * pattern or password otherwise. That satisfies "biometrics or PIN" exactly,
 * and it is the system's own decision which to offer rather than ours.
 *
 * The API is marked deprecated in favour of `BiometricPrompt`, which is the
 * platform class the androidx library wraps. It still works, and swapping to
 * it means either the dependency or a minSdk above this project's 26.
 */
@Stable
class DeviceUnlock(
    context: Context,
    private val start: (Intent, () -> Unit) -> Unit,
) {
    private val keyguard: KeyguardManager? =
        context.getSystemService(KeyguardManager::class.java)

    /** Whether a PIN, pattern or password is set at all. */
    val isSecure: Boolean
        get() = keyguard?.isDeviceSecure == true

    /**
     * Runs [onUnlocked] once the user has confirmed, or straight away if
     * there is nothing to confirm against.
     *
     * A phone with no screen lock cannot challenge anybody, so refusing to
     * open the screen would lock the user out of their own setting with no
     * way back. The Settings row says as much rather than pretending.
     */
    @Suppress("DEPRECATION")
    fun confirm(title: String, description: String, onUnlocked: () -> Unit) {
        val manager = keyguard
        if (manager == null || !manager.isDeviceSecure) {
            onUnlocked()
            return
        }
        val intent = manager.createConfirmDeviceCredentialIntent(title, description)
        if (intent == null) {
            onUnlocked()
            return
        }
        start(intent, onUnlocked)
    }
}

@Composable
fun rememberDeviceUnlock(): DeviceUnlock {
    val context = LocalContext.current
    // The callback is fixed when the launcher is registered, so what to do on
    // success is parked here rather than baked into it.
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val action = pending
        pending = null
        if (result.resultCode == Activity.RESULT_OK) action?.invoke()
    }
    return remember(context, launcher) {
        DeviceUnlock(context) { intent, onUnlocked ->
            pending = onUnlocked
            launcher.launch(intent)
        }
    }
}
