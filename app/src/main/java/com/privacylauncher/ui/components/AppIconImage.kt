package com.privacylauncher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.IconLoader
import com.privacylauncher.designsystem.rememberCalmFilter

/**
 * One app icon.
 *
 * This is the lazy-loading piece. The composable starts with whatever is
 * already in the in-memory cache (usually nothing on first scroll), draws a
 * neutral placeholder, and asks [IconLoader] for the real icon in the
 * background. Because it only runs for items the list has actually composed,
 * a phone with 200 apps never decodes more than a screenful at a time.
 */
@Composable
fun AppIconImage(
    entry: AppEntry,
    loader: IconLoader,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    var icon by remember(entry.key) { mutableStateOf(loader.cached(entry)) }

    LaunchedEffect(entry.key) {
        if (icon == null) icon = loader.load(entry)
    }

    val current = icon
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = null, // the label next to it already names the app
            // Drained of colour while focus is on. App icons are supplied by
            // other apps and are the loudest thing on the screen, so a calm
            // theme that left them alone would not look calm at all.
            colorFilter = rememberCalmFilter(),
            modifier = modifier.size(size),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size / 4))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

/**
 * The same, for a package rather than a drawer entry.
 *
 * The privacy dashboard lists packages, some of which have no launcher
 * activity to take an icon from, so it asks the package manager for the
 * application icon instead.
 */
@Composable
fun PackageIconImage(
    packageName: String,
    loader: IconLoader,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    var icon by remember(packageName) { mutableStateOf(loader.cachedPackage(packageName)) }

    LaunchedEffect(packageName) {
        if (icon == null) icon = loader.loadPackage(packageName)
    }

    val current = icon
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = null, // the label next to it already names the app
            // Drained of colour while focus is on. App icons are supplied by
            // other apps and are the loudest thing on the screen, so a calm
            // theme that left them alone would not look calm at all.
            colorFilter = rememberCalmFilter(),
            modifier = modifier.size(size),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size / 4))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

/** Default icon size used by the drawer grid. */
val DefaultIconSize: Dp = 52.dp
