package com.privacylauncher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
            Clock(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 64.dp, start = 24.dp, end = 24.dp),
            )

            Spacer(Modifier.weight(1f))

            if (homeApps.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp, vertical = 16.dp),
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
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
        val cellHeight = iconSize + 44.dp
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
                                .then(
                                    if (editMode) {
                                        Modifier.pointerInput(index, columns, apps.size) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
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
                                                    targetIndex = (tr * columns + tc)
                                                        .coerceIn(0, apps.lastIndex)
                                                },
                                                onDragEnd = {
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
                                        }
                                    } else {
                                        Modifier.combinedClickable(
                                            onClick = { onLaunch(entry) },
                                            onLongClick = onEnterEditMode,
                                        )
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isTarget) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .padding(4.dp)
                                        .border(
                                            width = 2.dp,
                                            color = Color.White.copy(alpha = 0.8f),
                                            shape = RoundedCornerShape(14.dp),
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
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (editMode) {
            Text(
                text = stringResource(R.string.home_edit_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 10.dp, start = 24.dp, end = 24.dp),
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
}

@Composable
private fun HomePill(label: String, contentDescription: String, onClick: () -> Unit) {
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
        // Tick on the minute boundary rather than every second, so the process
        // stays asleep almost all the time.
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
