package com.privacylauncher.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.privacylauncher.R
import com.privacylauncher.designsystem.Spacing
import com.privacylauncher.designsystem.panelSurface
import com.privacylauncher.ui.components.LauncherIcons

/**
 * Shared frame for full-screen panels: a floating surface, a back arrow and a
 * title. Written by hand rather than with Material3's Scaffold and
 * TopAppBar, both of which are still experimental API.
 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .panelSurface()
            .safeDrawingPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = Spacing.md, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = LauncherIcons.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        content()
    }
}
