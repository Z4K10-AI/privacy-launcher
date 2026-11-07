package com.privacylauncher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.privacylauncher.R
import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.IconLoader
import com.privacylauncher.designsystem.Radii
import com.privacylauncher.designsystem.Spacing
import com.privacylauncher.designsystem.isShortWindow
import com.privacylauncher.designsystem.rememberHaptics
import com.privacylauncher.ui.components.AppCell
import com.privacylauncher.ui.components.LauncherIcons
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The home screen: clock, a grid of chosen apps, and the bottom bar.
 *
 * The window is transparent and shows the system wallpaper through it, so
 * everything here sits on top of whatever wallpaper is set. Text is therefore
 * white with a soft shadow, which stays readable on light and dark wallpapers
 * alike.
 */
@Composable
fun HomeScreen(
    homeApps: List<AppEntry>,
    columns: Int,
    iconSize: Dp,
    iconLoader: IconLoader,
    editMode: Boolean,
    gesturesEnabled: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onEnterEditMode: () -> Unit,
    onExitEditMode: () -> Unit,
    onRemove: (AppEntry) -> Unit,
    onMove: (Int, Int) -> Unit,
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
                // Swiping up anywhere opens the drawer, the gesture people
                // expect from a launcher.
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
        Column(Modifier.fillMaxSize()) {
            // The clock and grid scroll; the bottom bar never does.
            //
            // This was a real failure, not a precaution. With eleven apps on
            // the home screen at a large Display size, the grid grew past the
            // available height, the Apps button and settings gear were pushed
            // off the bottom of the screen, and the only remaining way into
            // the drawer was the swipe-up gesture -- which a new user has no
            // reason to know exists.
            //
            // heightIn(min = viewport) is what lets Arrangement.SpaceBetween
            // still work inside a scrollable Column: without it the content is
            // measured against infinite height and the arrangement does
            // nothing.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val viewportHeight = maxHeight
                val scrollState = rememberScrollState()
                // Scrolling is enabled only when the content genuinely
                // overflows. An always-on verticalScroll swallows the
                // swipe-up-to-open-drawer gesture even with nothing to
                // scroll, which silently broke the launcher's main gesture.
                // maxValue is Int.MAX_VALUE until the first measurement, so
                // that case has to be excluded too.
                val canScroll = scrollState.maxValue > 0 &&
                    scrollState.maxValue != Int.MAX_VALUE
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState, enabled = canScroll)
                        .heightIn(min = viewportHeight),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
            // On a short screen -- landscape, or a small phone -- a 64sp
            // clock plus 48dp of padding ate enough height to slice the
            // second row of apps in half. Step both down.
            val shortScreen = isShortWindow()
            Clock(
                compact = shortScreen,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = if (shortScreen) Spacing.md else Spacing.xxl,
                        start = Spacing.lg,
                        end = Spacing.lg,
                    ),
            )

            if (homeApps.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xl, vertical = Spacing.md),
                )
            } else {
                HomeGrid(
                    apps = homeApps,
                    columns = columns,
                    iconSize = iconSize,
                    iconLoader = iconLoader,
                    editMode = editMode,
                    onLaunch = onLaunch,
                    onEnterEditMode = onEnterEditMode,
                    onRemove = onRemove,
                    onMove = onMove,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
                )
            }
                }
            }

            BottomBar(
                editMode = editMode,
                onOpenDrawer = onOpenDrawer,
                onOpenSettings = onOpenSettings,
                onExitEditMode = onExitEditMode,
            )
        }
    }
}

/**
 * The home app grid, with long-press-to-reorder.
 *
 * Not a LazyVerticalGrid: the home screen holds a handful of apps, all of
 * them on screen at once, and a plain Column of Rows gives every cell a known
 * position. Drag-to-reorder needs exactly that, and computing it against a
 * lazy layout's scroll state would be far more code for no benefit here.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeGrid(
    apps: List<AppEntry>,
    columns: Int,
    iconSize: Dp,
    iconLoader: IconLoader,
    editMode: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onEnterEditMode: () -> Unit,
    onRemove: (AppEntry) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    var dragIndex by remember { mutableIntStateOf(-1) }
    var targetIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    // Leaving edit mode mid-drag would otherwise strand the offsets.
    LaunchedEffect(editMode) {
        if (!editMode) {
            dragIndex = -1
            targetIndex = -1
            dragOffset = Offset.Zero
        }
    }

    BoxWithConstraints(modifier) {
        val cellWidth = maxWidth / columns
        // Label space scales with the user's font size setting. At a fixed
        // height, a two-line app name at 1.3x font scale is clipped.
        val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.6f)
        val labelSpace = (Spacing.xl + Spacing.sm) * fontScale
        val cellHeight = iconSize + labelSpace
        val density = LocalDensity.current
        val cellWpx = with(density) { cellWidth.toPx() }
        val cellHpx = with(density) { cellHeight.toPx() }

        Column {
            apps.chunked(columns).forEachIndexed { rowIndex, rowApps ->
                Row {
                    rowApps.forEachIndexed { colIndex, entry ->
                        val index = rowIndex * columns + colIndex
                        val isDragging = index == dragIndex
                        val isTarget = editMode &&
                            targetIndex == index &&
                            dragIndex >= 0 &&
                            dragIndex != index

                        Box(
                            modifier = Modifier
                                .width(cellWidth)
                                .height(cellHeight)
                                // The dragged cell must paint above its
                                // neighbours or it slides underneath them.
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    if (isDragging) {
                                        translationX = dragOffset.x
                                        translationY = dragOffset.y
                                        scaleX = 1.12f
                                        scaleY = 1.12f
                                        alpha = 0.9f
                                    }
                                }
                                // Tap launches, but only when not editing.
                                .pointerInput(editMode, entry.key) {
                                    detectTapGestures(
                                        onTap = { if (!editMode) onLaunch(entry) },
                                    )
                                }
                                // Long-press starts a drag in the same motion.
                                // Previously edit mode and dragging were two
                                // separate gestures: long-press, let go, then
                                // long-press again to actually move anything.
                                // Entering edit mode from here means the first
                                // long-press can carry straight into a drag,
                                // which is what every other launcher does.
                                .pointerInput(index, columns, apps.size) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    haptics.dragStart()
                                                    onEnterEditMode()
                                                    dragIndex = index
                                                    targetIndex = index
                                                    dragOffset = Offset.Zero
                                                },
                                                onDrag = { change, delta ->
                                                    change.consume()
                                                    dragOffset += delta
                                                    // Where the cell's centre
                                                    // now sits, in grid terms.
                                                    val cx = colIndex * cellWpx +
                                                        cellWpx / 2 + dragOffset.x
                                                    val cy = rowIndex * cellHpx +
                                                        cellHpx / 2 + dragOffset.y
                                                    val tc = (cx / cellWpx).toInt()
                                                        .coerceIn(0, columns - 1)
                                                    val tr = (cy / cellHpx).toInt()
                                                        .coerceAtLeast(0)
                                                    val next = (tr * columns + tc)
                                                        .coerceIn(0, apps.lastIndex)
                                                    // Tick only when the drop
                                                    // target actually changes,
                                                    // not on every frame.
                                                    if (next != targetIndex) {
                                                        haptics.tick()
                                                    }
                                                    targetIndex = next
                                                },
                                                onDragEnd = {
                                                    haptics.dragEnd()
                                                    if (targetIndex >= 0 &&
                                                        targetIndex != dragIndex
                                                    ) {
                                                        onMove(dragIndex, targetIndex)
                                                    }
                                                    dragIndex = -1
                                                    targetIndex = -1
                                                    dragOffset = Offset.Zero
                                                },
                                                onDragCancel = {
                                                    dragIndex = -1
                                                    targetIndex = -1
                                                    dragOffset = Offset.Zero
                                                },
                                            )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isTarget) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .padding(Spacing.xxs)
                                        .border(
                                            width = 2.dp,
                                            color = Color.White.copy(alpha = 0.8f),
                                            shape = RoundedCornerShape(Radii.card),
                                        ),
                                )
                            }
                            AppCell(
                                entry = entry,
                                iconLoader = iconLoader,
                                iconSize = iconSize,
                                labelColor = Color.White,
                                labelStyle = MaterialTheme.typography.labelSmall.copy(
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.7f),
                                        blurRadius = 6f,
                                    ),
                                ),
                                showRemoveBadge = editMode,
                                onRemove = { onRemove(entry) },
                                // Gestures are owned by the parent Box above.
                                interactive = false,
                            )
                        }
                    }
                    // Pad the final row so its cells keep their column widths.
                    repeat(columns - rowApps.size) {
                        Spacer(Modifier.width(cellWidth).height(cellHeight))
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBar(
    editMode: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onExitEditMode: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm, bottom = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (editMode) {
            Text(
                text = stringResource(R.string.home_edit_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = Spacing.sm, start = Spacing.lg, end = Spacing.lg),
            )
            HomePill(
                label = stringResource(R.string.home_edit_done),
                contentDescription = stringResource(R.string.home_edit_done),
                onClick = onExitEditMode,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HomePill(
                    label = stringResource(R.string.home_apps_button),
                    contentDescription = stringResource(R.string.drawer_open),
                    onClick = onOpenDrawer,
                )
                Spacer(Modifier.width(Spacing.xs))
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
}

@Composable
private fun HomePill(label: String, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Radii.pill))
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
        )
    }
}

@Composable
private fun Clock(compact: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    // Re-read on every resume, not once: the user can change this in Settings
    // while we are in the background, and a launcher stuck on the wrong clock
    // format until its process restarts looks broken.
    var uses24Hour by remember { mutableStateOf(DateFormat.is24HourFormat(context)) }

    val timeFormatter = remember(uses24Hour) {
        DateTimeFormatter.ofPattern(if (uses24Hour) "HH:mm" else "h:mm a")
    }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM") }

    LaunchedEffect(Unit) {
        // Tick on the minute boundary rather than every second, so the process
        // stays asleep almost all the time.
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            now = LocalDateTime.now()
        }
    }

    // The minute ticker alone is not enough. While the phone is dozing or the
    // app is in standby the coroutine does not run on schedule, so unlocking
    // after a long sleep could show a stale time for up to a minute -- on the
    // one screen whose main job is telling you the time. Resuming forces a
    // re-read.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                now = LocalDateTime.now()
                uses24Hour = DateFormat.is24HourFormat(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val shadow = Shadow(color = Color.Black.copy(alpha = 0.55f), blurRadius = 12f)

    BoxWithConstraints(modifier) {
        // 64sp wrapped "3:38 PM" onto two lines at a large Display size, and
        // pushed the date onto three. Step the scale down when the screen is
        // narrow in dp rather than let it break.
        val typography = MaterialTheme.typography
        val timeStyle = when {
            compact || maxWidth < 280.dp -> typography.displaySmall
            maxWidth < 340.dp -> typography.displayMedium
            else -> typography.displayLarge
        }
        val dateStyle = if (compact || maxWidth < 300.dp) {
            typography.titleMedium
        } else {
            typography.titleLarge
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = now.format(timeFormatter),
                style = timeStyle.copy(shadow = shadow),
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = now.format(dateFormatter),
                style = dateStyle.copy(shadow = shadow),
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
