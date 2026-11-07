package com.privacylauncher.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.privacylauncher.R

/**
 * Explains the accessibility service before sending the user to enable it.
 *
 * Shown either from settings, or the first time a gesture that needs the
 * service is used. Accessibility is a powerful permission and a launcher
 * asking for it deserves suspicion, so the dialog says plainly what the
 * service can and cannot do rather than asking for trust.
 */
@Composable
fun GestureExplainerDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gesture_explainer_title)) },
        text = {
            Text(
                text = stringResource(R.string.gesture_explainer_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.gesture_explainer_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
