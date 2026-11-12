package com.privacylauncher.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.privacylauncher.R
import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.IconLoader
import com.privacylauncher.ui.components.AppIconImage
import com.privacylauncher.designsystem.Spacing
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role

/**
 * Lists every installed app with a switch to keep it out of the drawer.
 *
 * Worth being clear about what this does and does not do, which the body text
 * on screen also says: hiding is purely cosmetic. The app stays installed, can
 * still run, and keeps whatever permissions it holds. Only this launcher stops
 * showing it.
 */
@Composable
fun HiddenAppsScreen(
    apps: List<AppEntry>,
    hidden: Set<String>,
    iconLoader: IconLoader,
    onBack: () -> Unit,
    onToggle: (AppEntry, Boolean) -> Unit,
) {
    ScreenScaffold(title = stringResource(R.string.hidden_apps_title), onBack = onBack) {
        Text(
            text = stringResource(R.string.hidden_apps_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(items = apps, key = { it.key }) { entry ->
                val isHidden = entry.key in hidden
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // The whole row is the switch. With a second, live
                        // switch inside it a screen reader found two things
                        // per app: a row with a name and no state, and a
                        // switch with a state and no name.
                        .toggleable(
                            value = isHidden,
                            role = Role.Switch,
                            onValueChange = { onToggle(entry, it) },
                        )
                        .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                        .defaultMinSize(minHeight = Spacing.touchTarget),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIconImage(entry = entry, loader = iconLoader, size = 36.dp)
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = entry.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = entry.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Switch(checked = isHidden, onCheckedChange = null)
                }
            }
        }
    }
}
