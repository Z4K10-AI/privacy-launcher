package com.privacylauncher.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.privacylauncher.R
import com.privacylauncher.system.LockDownState

/**
 * Says what Lock down is, and what it needs, before anything is granted.
 *
 * "Device admin app" is an alarming phrase and it should be: such an app
 * can be given the power to erase a phone. This one asks for the power to
 * lock the screen and no other, and there is a cost the user must hear
 * first: Android will not uninstall the launcher while it is granted. So
 * the dialog says what is asked for, what is not, what it costs and how to
 * undo it, and only then offers Android's own screen.
 *
 * The same dialog is where it is turned off again.
 */
@Composable
fun LockDownDialog(
    state: LockDownState,
    onDismiss: () -> Unit,
    /** Open Android's screen for allowing it. */
    onAllow: () -> Unit,
    /** Open Android's security settings, to set a screen lock. */
    onOpenSecurity: () -> Unit,
    /** Give the permission back. */
    onTurnOff: () -> Unit,
) {
    val (body, action, onAction) = when (state) {
        LockDownState.NEEDS_SCREEN_LOCK -> Triple(
            R.string.lock_down_needs_lock,
            R.string.lock_down_open_security,
            onOpenSecurity,
        )
        LockDownState.NEEDS_PERMISSION -> Triple(
            R.string.lock_down_explainer,
            R.string.lock_down_continue,
            onAllow,
        )
        LockDownState.READY -> Triple(
            R.string.lock_down_allowed,
            R.string.lock_down_turn_off,
            onTurnOff,
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_lockdown)) },
        text = {
            Text(
                text = stringResource(body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onAction) { Text(stringResource(action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
