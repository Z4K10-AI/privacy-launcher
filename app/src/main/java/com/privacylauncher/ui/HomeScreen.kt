package com.privacylauncher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.privacylauncher.R
import com.privacylauncher.ui.components.LauncherIcons
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The home screen.
 *
 * Deliberately almost empty: the window is transparent and shows the system
 * wallpaper through it, so everything drawn here sits on top of whatever
 * wallpaper the user has set. Text is therefore drawn in white with a soft
 * shadow so it stays readable on both light and dark wallpapers.
 *
 * Feature 2 will add the icon grid, favourites and rearranging.
 */
@Composable
fun HomeScreen(
    gesturesEnabled: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val swipeThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(swipeThresholdPx, gesturesEnabled) {
                if (!gesturesEnabled) return@pointerInput
                // Swiping up anywhere on the home screen opens the drawer,
                // which is the gesture people expect from a launcher.
                //
                // dragTotal is a plain local, NOT Compose state: using state
                // here would recompose the whole screen on every drag frame.
                var dragTotal = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onDragEnd = { if (dragTotal < -swipeThresholdPx) onOpenDrawer() },
                    onDragCancel = { dragTotal = 0f },
                ) { _, delta -> dragTotal += delta }
            }
            .safeDrawingPadding(),
    ) {
        Clock(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 72.dp, start = 24.dp, end = 24.dp),
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomePillButton(
                label = stringResource(R.string.home_apps_button),
                contentDescription = stringResource(R.string.drawer_open),
                onClick = onOpenDrawer,
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = LauncherIcons.Settings,
                    contentDescription = stringResource(R.string.settings_open),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun Clock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uses24Hour = remember { DateFormat.is24HourFormat(context) }

    val timeFormatter = remember(uses24Hour) {
        DateTimeFormatter.ofPattern(if (uses24Hour) "HH:mm" else "h:mm a")
    }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM") }

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        // Tick exactly on the minute boundary rather than every second, so the
        // process stays asleep almost all the time.
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            now = LocalDateTime.now()
        }
    }

    val shadow = Shadow(color = Color.Black.copy(alpha = 0.55f), blurRadius = 12f)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = now.format(timeFormatter),
            style = MaterialTheme.typography.displayLarge.copy(shadow = shadow),
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = now.format(dateFormatter),
            style = MaterialTheme.typography.titleLarge.copy(shadow = shadow),
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HomePillButton(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
        )
    }
}
