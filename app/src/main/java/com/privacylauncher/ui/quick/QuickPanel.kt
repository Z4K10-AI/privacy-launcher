package com.privacylauncher.ui.quick

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.privacylauncher.R
import com.privacylauncher.data.PrivateDnsMode
import com.privacylauncher.data.QuickControlsState
import com.privacylauncher.data.Toggle
import com.privacylauncher.designsystem.Motion
import com.privacylauncher.designsystem.Radii
import com.privacylauncher.designsystem.Spacing
import com.privacylauncher.designsystem.cardSurface
import com.privacylauncher.designsystem.panelSurface
import com.privacylauncher.designsystem.rememberHaptics
import com.privacylauncher.ui.components.LauncherIcons
import kotlin.math.abs
import kotlin.math.roundToInt
import com.privacylauncher.system.LockDownState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.unit.DpSize

/** How much of the brightness bar a step has to cover to earn a tick. */
private const val HAPTIC_STEP = 0.05f

/**
 * The quick controls panel: a side sheet over a blurred home screen.
 *
 * **The honest part.** Of the eight tiles, exactly one is a switch: the
 * flashlight, because `CameraManager.setTorchMode` needs no permission. Every
 * other one Android reserves for itself -- `setWifiEnabled` has been a no-op
 * for ordinary apps since Android 10, `BluetoothAdapter.enable` since 13, and
 * Do Not Disturb, auto-rotate and Private DNS each need a permission or a
 * special access this launcher does not take. Those tiles read their state,
 * show it, and open Android's own screen, with a corner arrow saying as much
 * and a line under the grid spelling it out. A tile that looked like a switch
 * and silently did nothing would be the worse answer.
 */
@Composable
fun QuickPanel(
    state: QuickControlsState,
    lockDownState: LockDownState,
    onObserve: () -> (() -> Unit),
    onRefresh: () -> Unit,
    onTile: (QuickTile) -> Unit,
    onBrightness: (Float) -> Unit,
    onBrightnessFinished: () -> Unit,
    onGrantWriteSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Registered while the panel is on screen and torn down when it leaves,
    // so a panel nobody is looking at observes nothing.
    DisposableEffect(Unit) {
        onRefresh()
        val cancel = onObserve()
        onDispose { cancel() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .panelSurface()
            .safeDrawingPadding(),
    ) {
        Text(
            text = stringResource(R.string.quick_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.md,
                bottom = Spacing.sm,
            ),
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(
                start = Spacing.md,
                end = Spacing.md,
                bottom = Spacing.xl,
            ),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrightnessCard(
                    state = state,
                    onBrightness = onBrightness,
                    onBrightnessFinished = onBrightnessFinished,
                    onGrant = onGrantWriteSettings,
                )
            }
            items(items = QuickTile.entries, key = { it.name }) { tile ->
                TileCard(
                    tile = tile,
                    state = state,
                    lockDownState = lockDownState,
                    onClick = { onTile(tile) },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = Spacing.xs,
                        vertical = Spacing.sm,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = stringResource(R.string.quick_honesty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Lock down deserves its own sentence: "lockdown" means
                    // something specific on Android, and this tile does the
                    // part of it that matters but not all of it.
                    Text(
                        text = stringResource(R.string.quick_lockdown_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// The slider's "thumb" slot is still marked experimental in Material 3,
// though it has been there unchanged since 1.3. It is the only way to say
// how tall the thumb is.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrightnessCard(
    state: QuickControlsState,
    onBrightness: (Float) -> Unit,
    onBrightnessFinished: () -> Unit,
    onGrant: () -> Unit,
) {
    val haptics = rememberHaptics()
    // Local while dragging: routing every frame through the Settings provider
    // and back would make the thumb lag behind the finger.
    var dragging by remember { mutableFloatStateOf(-1f) }
    var lastTick by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging >= 0f) dragging else state.brightness

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.card))
            .cardSurface(level = 1)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = LauncherIcons.Brightness,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = stringResource(R.string.quick_brightness),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(shown * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val brightnessLabel = stringResource(R.string.quick_brightness)
        val sliderInteraction = remember { MutableInteractionSource() }
        Slider(
            value = shown,
            enabled = state.canWriteSettings,
            // A slider says its position by itself ("60 percent") but not
            // what it is a slider of.
            modifier = Modifier.semantics { contentDescription = brightnessLabel },
            interactionSource = sliderInteraction,
            // Material's thumb is 44 dp tall and the slider is as tall as
            // its thumb, which left the whole control four short of the
            // 48 dp this project holds every touch target to. Asking for a
            // taller slider from outside changes the space around it, not
            // the part that takes the touch; the thumb is what sets that.
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = sliderInteraction,
                    enabled = state.canWriteSettings,
                    thumbSize = DpSize(4.dp, Spacing.touchTarget),
                )
            },
            onValueChange = { value ->
                dragging = value
                // A tick every twentieth of the bar. Per-pixel feedback on a
                // slider buzzes continuously and reads as a fault.
                if (abs(value - lastTick) >= HAPTIC_STEP) {
                    lastTick = value
                    haptics.tick()
                }
                onBrightness(value)
            },
            onValueChangeFinished = {
                onBrightnessFinished()
                dragging = -1f
            },
        )

        when {
            !state.canWriteSettings -> {
                Text(
                    text = stringResource(R.string.quick_brightness_blocked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onGrant,
                    contentPadding = PaddingValues(
                        horizontal = Spacing.xs,
                        vertical = Spacing.xxs,
                    ),
                ) { Text(stringResource(R.string.quick_brightness_allow)) }
            }
            state.autoBrightness -> Text(
                text = stringResource(R.string.quick_brightness_auto),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
        }
    }
}

@Composable
private fun TileCard(
    tile: QuickTile,
    state: QuickControlsState,
    lockDownState: LockDownState,
    onClick: () -> Unit,
) {
    val active = tile.isActive(state)
    val background by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = Motion.short(),
        label = "tileBackground",
    )
    val content = if (active) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.35f)
            .clip(RoundedCornerShape(Radii.sheet))
            .background(background)
            .clickable(onClick = onClick),
    ) {
        // Tiles that leave the app say so with a corner arrow. A rotated
        // chevron was tried here first and read as a stray bracket, so this
        // is a proper arrow pointing out of the tile.
        if (tile.opensSettings) {
            Icon(
                imageVector = LauncherIcons.ArrowOut,
                contentDescription = stringResource(R.string.quick_opens_settings),
                tint = content.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.xs)
                    .size(16.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                imageVector = tile.icon(),
                contentDescription = null,
                tint = content,
            )
            Column {
                Text(
                    text = stringResource(tile.labelRes),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    color = if (active) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                )
                Text(
                    text = tile.statusText(state, lockDownState),
                    style = MaterialTheme.typography.bodySmall,
                    color = content,
                )
            }
        }
    }
}

/** The status line under a tile's name. */
@Composable
private fun QuickTile.statusText(
    state: QuickControlsState,
    lockDownState: LockDownState,
): String = when (this) {
    QuickTile.FLASHLIGHT -> when {
        !state.torchAvailable -> stringResource(R.string.quick_no_flash)
        state.torch == Toggle.ON -> stringResource(R.string.quick_on)
        else -> stringResource(R.string.quick_off)
    }
    QuickTile.LOCKDOWN -> stringResource(
        when (lockDownState) {
            LockDownState.READY -> R.string.quick_locks_now
            LockDownState.NEEDS_PERMISSION -> R.string.quick_needs_ok
            LockDownState.NEEDS_SCREEN_LOCK -> R.string.quick_needs_screen_lock
        },
    )
    QuickTile.PRIVATE_DNS -> when (state.privateDns) {
        PrivateDnsMode.OFF -> stringResource(R.string.quick_off)
        PrivateDnsMode.AUTOMATIC -> stringResource(R.string.quick_dns_automatic)
        PrivateDnsMode.HOSTNAME ->
            state.privateDnsHost ?: stringResource(R.string.quick_on)
        PrivateDnsMode.UNKNOWN -> stringResource(R.string.quick_unknown)
    }
    else -> when (toggle(state)) {
        Toggle.ON -> stringResource(R.string.quick_on)
        Toggle.OFF -> stringResource(R.string.quick_off)
        Toggle.UNKNOWN -> stringResource(R.string.quick_unknown)
    }
}

@Composable
private fun QuickTile.icon() = when (this) {
    QuickTile.WIFI -> LauncherIcons.Wifi
    QuickTile.BLUETOOTH -> LauncherIcons.Bluetooth
    QuickTile.LOCATION -> LauncherIcons.Location
    QuickTile.FLASHLIGHT -> LauncherIcons.Flashlight
    QuickTile.DND -> LauncherIcons.DoNotDisturb
    QuickTile.ROTATION -> LauncherIcons.Rotation
    QuickTile.PRIVATE_DNS -> LauncherIcons.Dns
    QuickTile.LOCKDOWN -> LauncherIcons.Lock
}
