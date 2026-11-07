package com.privacylauncher.ui.focus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.privacylauncher.R
import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.IconLoader
import com.privacylauncher.designsystem.Spacing
import com.privacylauncher.ui.components.AppIconImage
import com.privacylauncher.ui.settings.ScreenScaffold

/**
 * Picks the apps that stay visible while focus mode is on.
 *
 * The mirror image of the hide-apps screen, and deliberately the same shape,
 * because the two are easy to confuse: hiding is permanent and about tidiness,
 * allowing is temporary and about attention.
 */
@Composable
fun FocusAppsScreen(
    apps: List<AppEntry>,
    allowed: Set<String>,
    iconLoader: IconLoader,
    onBack: () -> Unit,
    onToggle: (AppEntry, Boolean) -> Unit,
) {
    ScreenScaffold(title = stringResource(R.string.focus_allowed_apps), onBack = onBack) {
        Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs)) {
            Text(
                text = stringResource(R.string.focus_allowed_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.focus_allowed_count,
                    allowed.size,
                    allowed.size,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(items = apps, key = { it.key }) { entry ->
                val isAllowed = entry.key in allowed
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(entry, !isAllowed) }
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
                        Text(
                            text = entry.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Switch(
                        checked = isAllowed,
                        onCheckedChange = { onToggle(entry, it) },
                    )
                }
            }
        }
    }
}
