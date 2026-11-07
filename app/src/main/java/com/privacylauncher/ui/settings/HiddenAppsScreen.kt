package com.privacylauncher.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(items = apps, key = { it.key }) { entry ->
                val isHidden = entry.key in hidden
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(entry, !isHidden) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIconImage(entry = entry, loader = iconLoader, size = 36.dp)
                        Spacer(Modifier.width(16.dp))
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
                    Switch(
                        checked = isHidden,
                        onCheckedChange = { onToggle(entry, it) },
                    )
                }
            }
        }
    }
}
