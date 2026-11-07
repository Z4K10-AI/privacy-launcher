package com.privacylauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.privacylauncher.R
import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.IconLoader
import com.privacylauncher.ui.components.AppCell
import com.privacylauncher.ui.components.LauncherIcons

/**
 * The app drawer: a searchable grid of every installed, non-hidden app.
 *
 * Uses [LazyVerticalGrid], so only cells on screen exist. That plus the
 * on-demand icon loading in AppIconImage is what keeps this usable on a 4 GB
 * phone with a couple of hundred apps.
 */
@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    totalAppCount: Int,
    query: String,
    columns: Int,
    iconSize: Dp,
    iconLoader: IconLoader,
    isOnHome: (AppEntry) -> Boolean,
    onQueryChange: (String) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onClose: () -> Unit,
    onAppInfo: (AppEntry) -> Unit,
    onUninstall: (AppEntry) -> Unit,
    onAddToHome: (AppEntry) -> Unit,
    onRemoveFromHome: (AppEntry) -> Unit,
    onHide: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Fully opaque. At 0.97 the home screen's clock and Apps button
            // ghosted through the drawer, which read as a rendering fault
            // rather than as deliberate translucency.
            .background(MaterialTheme.colorScheme.background)
            // safeDrawing is the union of system bars, display cutout AND the
            // IME, so adding imePadding() here would shift the content up by
            // the keyboard height twice.
            .safeDrawingPadding(),
    ) {
        val closeLabel = stringResource(R.string.drawer_close_handle)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClose)
                .semantics { contentDescription = closeLabel }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.drawer_search_hint)) },
            leadingIcon = { Icon(LauncherIcons.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            LauncherIcons.Close,
                            contentDescription = stringResource(R.string.drawer_search_clear),
                        )
                    }
                }
            },
            keyboardActions = KeyboardActions(
                onSearch = { apps.firstOrNull()?.let(onLaunch) },
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )

        Text(
            text = if (query.isBlank()) {
                pluralStringResource(R.plurals.app_count, totalAppCount, totalAppCount)
            } else {
                stringResource(R.string.app_count_filtered, apps.size, totalAppCount)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, bottom = 4.dp),
        )

        if (apps.isEmpty()) {
            // weight(1f), not fillMaxSize(): inside a Column the latter asks for
            // the full parent height rather than what is left after the search
            // field, which pushes the bottom of the list off screen.
            Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.drawer_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                items(items = apps, key = { it.key }) { entry ->
                    AppCell(
                        entry = entry,
                        iconLoader = iconLoader,
                        iconSize = iconSize,
                        labelColor = MaterialTheme.colorScheme.onBackground,
                        onClick = { onLaunch(entry) },
                        onLongClick = { menuFor = entry },
                    )
                }
            }
        }
    }

    menuFor?.let { entry ->
        AppActionsDialog(
            entry = entry,
            onHome = isOnHome(entry),
            onDismiss = { menuFor = null },
            onAppInfo = { menuFor = null; onAppInfo(entry) },
            onUninstall = { menuFor = null; onUninstall(entry) },
            onAddToHome = { menuFor = null; onAddToHome(entry) },
            onRemoveFromHome = { menuFor = null; onRemoveFromHome(entry) },
            onHide = { menuFor = null; onHide(entry) },
        )
    }
}

@Composable
private fun AppActionsDialog(
    entry: AppEntry,
    onHome: Boolean,
    onDismiss: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onAddToHome: () -> Unit,
    onRemoveFromHome: () -> Unit,
    onHide: () -> Unit,
) {
    // Every action is a row in the body and the only button is Cancel.
    //
    // An earlier version put Uninstall in AlertDialog's dismissButton slot --
    // bottom-left, exactly where Cancel normally lives. That invites someone
    // dismissing the dialog by habit to start uninstalling an app instead.
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.label) },
        text = {
            Column {
                Text(
                    text = entry.packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                if (onHome) {
                    DialogAction(stringResource(R.string.remove_from_home), onRemoveFromHome)
                } else {
                    DialogAction(stringResource(R.string.add_to_home), onAddToHome)
                }
                DialogAction(stringResource(R.string.hide_app), onHide)
                DialogAction(stringResource(R.string.app_info), onAppInfo)
                DialogAction(
                    label = stringResource(R.string.uninstall),
                    onClick = onUninstall,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun DialogAction(
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = tint,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
    )
}
