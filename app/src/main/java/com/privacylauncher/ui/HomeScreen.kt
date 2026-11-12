package com.privacylauncher.ui

import android.text.format.DateFormat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.privacylauncher.R
import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.GlanceInfo
import com.privacylauncher.data.IconLoader
import com.privacylauncher.designsystem.Radii
import com.privacylauncher.designsystem.Spacing
import com.privacylauncher.designsystem.isShortWindow
import com.privacylauncher.designsystem.rememberHaptics
import com.privacylauncher.ui.components.AppCell
import com.privacylauncher.ui.components.AppIconImage
import com.privacylauncher.ui.components.FolderIcon
import com.privacylauncher.ui.components.LauncherIcons
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.sin

/**
 * The home screen: clock, an optional glance line, a grid of apps and
 * folders, a dock, and the bottom bar.
 *
 * The window is transparent and shows the wallpaper through it, so text is
 * white with a soft shadow to stay readable on light and dark wallpapers.
 */
@Composable
internal fun HomeScreen(
    tiles: List<HomeTile>,
    dock: List<AppEntry>,
    glance: GlanceInfo,
    columns: Int,
    iconSize: Dp,
    iconLoader: IconLoader,
    editMode: Boolean,
    gesturesEnabled: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onOpenFolder: (String) -> Unit,
    onEnterEditMode: () -> Unit,
    onExitEditMode: () -> Unit,
    onRemoveTile: (HomeTile) -> Unit,
    onMove: (fromId: String, toId: String) -> Unit,
    onCombine: (fromId: String, ontoId: String) -> Unit,
    /** A grid icon was dropped on the dock, in front of [beforeKey] or at its end. */
    onMoveToDock: (fromId: String, beforeKey: String?) -> Unit,
    /** A dock icon was dropped: on the grid, onto an icon, or along the dock. */
    onDockDrop: (HomeDrop) -> Unit,
    onRemoveFromDock: (AppEntry) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenClock: () -> Unit,
    /** Accumulated vertical drag, negative upwards, as the drawer follows. */
    onDrawerDrag: (Float) -> Unit,
    /** Drag released: vertical velocity in px/s, negative upwards. */
    onDrawerRelease: (Float) -> Unit,
    /** A horizontal drag has begun, so the quick panel's state can be read. */
    onQuickDragStart: () -> Unit,
    /** Accumulated horizontal drag, negative leftwards, for the quick panel. */
    onQuickDrag: (Float) -> Unit,
    /** Horizontal drag released: velocity in px/s, negative leftwards. */
    onQuickRelease: (Float) -> Unit,
    onSwipeDown: () -> Unit,
    onDoubleTap: () -> Unit,
    onPinchIn: () -> Unit,
    modifier: Modifier = Modifier,
    /** The icon in the air, shared with the drawer; see [HomeDragState]. */
    drag: HomeDragState = remember { HomeDragState() },
) {
    DisposableEffect(drag) {
        onDispose { drag.geometry.home = null }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .homeGestures(
                enabled = gesturesEnabled,
                onDrawerDrag = onDrawerDrag,
                onDrawerRelease = onDrawerRelease,
                onQuickDragStart = onQuickDragStart,
                onQuickDrag = onQuickDrag,
                onQuickRelease = onQuickRelease,
                onSwipeDown = onSwipeDown,
                onDoubleTap = onDoubleTap,
                onPinchIn = onPinchIn,
            )
            .safeDrawingPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            // The clock and grid scroll; the dock and bottom bar never do.
            //
            // With enough apps at a large Display size the grid used to grow
            // past the available height and push the bottom bar off screen,
            // leaving the swipe-up gesture as the only way into the drawer.
            //
            // heightIn(min = viewport) is what lets SpaceBetween still work
            // inside a scrollable Column: without it the content is measured
            // against infinite height and the arrangement does nothing.
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { c ->
                        val p = c.positionInRoot()
                        drag.geometry.home =
                            Bounds(p.x, p.y, p.x + c.size.width, p.y + c.size.height)
                    },
            ) {
                val viewportHeight = maxHeight
                val scrollState = rememberScrollState()
                // Scrolling is enabled only when the content really overflows.
                // An always-on verticalScroll swallows swipe-up-to-open-drawer
                // even with nothing to scroll. maxValue is Int.MAX_VALUE until
                // the first measurement, so that case is excluded too.
                val canScroll = scrollState.maxValue > 0 &&
                    scrollState.maxValue != Int.MAX_VALUE

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState, enabled = canScroll)
                        .heightIn(min = viewportHeight),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    val shortScreen = isShortWindow()
                    Column(Modifier.fillMaxWidth()) {
                        HomeClock(
                            compact = shortScreen,
                            onClick = onOpenClock,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    top = if (shortScreen) Spacing.md else Spacing.xxl,
                                    start = Spacing.lg,
                                    end = Spacing.lg,
                                ),
                        )
                        if (!glance.isEmpty) {
                            GlanceLine(
                                glance = glance,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = Spacing.xs, start = Spacing.lg, end = Spacing.lg),
                            )
                        }
                    }

                    // An icon on its way in from the drawer needs a grid to
                    // land on, even if it will be the first one there.
                    if (tiles.isEmpty() && !drag.external) {
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
                            tiles = tiles,
                            columns = columns,
                            iconSize = iconSize,
                            iconLoader = iconLoader,
                            editMode = editMode,
                            onLaunch = onLaunch,
                            onOpenFolder = onOpenFolder,
                            onEnterEditMode = onEnterEditMode,
                            onRemoveTile = onRemoveTile,
                            onMove = onMove,
                            onCombine = onCombine,
                            onMoveToDock = onMoveToDock,
                            drag = drag,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
                        )
                    }
                }
            }

            if (dock.isNotEmpty()) {
                Dock(
                    entries = dock,
                    iconSize = iconSize,
                    iconLoader = iconLoader,
                    editMode = editMode,
                    drag = drag,
                    onLaunch = onLaunch,
                    onEnterEditMode = onEnterEditMode,
                    onRemove = onRemoveFromDock,
                    onDrop = onDockDrop,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
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
 * The home grid, with long-press-to-reorder and drop-onto-another-icon to
 * make a folder.
 *
 * Not a LazyVerticalGrid: the home screen holds a handful of items, all on
 * screen at once, and a plain Column of Rows gives every cell a known
 * position. Drag-and-drop needs exactly that.
 *
 * An icon picked up here can also be carried down to the dock, and one
 * carried in from the dock or the drawer can be put down here. Those cross
 * the edge of this grid, so what is in the air and where it would land is
 * kept in [drag], which the dock and the drawer share.
 */
@Composable
internal fun HomeGrid(
    tiles: List<HomeTile>,
    columns: Int,
    iconSize: Dp,
    iconLoader: IconLoader,
    editMode: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onOpenFolder: (String) -> Unit,
    onEnterEditMode: () -> Unit,
    onRemoveTile: (HomeTile) -> Unit,
    onMove: (fromId: String, toId: String) -> Unit,
    onCombine: (fromId: String, ontoId: String) -> Unit,
    modifier: Modifier = Modifier,
    /** A grid icon was dropped on the dock, in front of [beforeKey] or at its end. */
    onMoveToDock: (fromId: String, beforeKey: String?) -> Unit = { _, _ -> },
    drag: HomeDragState = remember { HomeDragState() },
) {
    val haptics = rememberHaptics()
    var dragIndex by remember { mutableIntStateOf(-1) }
    /** Which icon was picked up. A cell can change hands during a drag. */
    var dragId by remember { mutableStateOf<String?>(null) }
    var targetIndex by remember { mutableIntStateOf(-1) }
    /** Set when the drop would merge into a folder rather than reorder. */
    var combineIndex by remember { mutableIntStateOf(-1) }
    /** How far the finger has travelled since the icon was picked up. */
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    /** The middle of the icon when it was picked up, in window pixels. */
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    /** This grid's ticket for the icon in the air; see [HomeDragState.pickUp]. */
    var carry by remember { mutableIntStateOf(0) }

    // A cell's drag handler lives a long time: Compose restarts it only
    // when its keys change, and until then it carries on with whatever it
    // was holding when it started. Anything it reads that can change during
    // its life -- the icons, the callbacks, the cell size further down --
    // therefore goes through one of these, which always has the newest
    // value. Reading `tiles` directly meant that after a reorder the
    // handler was still working out what was under the finger from the
    // layout before it.
    val currentTiles by rememberUpdatedState(tiles)
    val currentOnEnterEditMode by rememberUpdatedState(onEnterEditMode)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnCombine by rememberUpdatedState(onCombine)
    val currentOnMoveToDock by rememberUpdatedState(onMoveToDock)
    val currentOnLaunch by rememberUpdatedState(onLaunch)
    val currentOnOpenFolder by rememberUpdatedState(onOpenFolder)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val currentRtl by rememberUpdatedState(rtl)

    val endDrag = {
        dragIndex = -1
        dragId = null
        targetIndex = -1
        combineIndex = -1
        dragOffset = Offset.Zero
        drag.clear(carry)
    }

    LaunchedEffect(editMode) {
        if (!editMode) endDrag()
    }

    // One animation drives every icon's wiggle. A separate infinite
    // transition per cell would mean one running animation per app.
    val wiggle = rememberInfiniteTransition(label = "wiggle")
    val wigglePhase by wiggle.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wigglePhase",
    )

    // An icon carried in from the dock or the drawer. The grid did not
    // start that gesture, so all it does is show where the icon would land.
    val external = drag.external
    val externalTarget = if (external) drag.target else DropTarget.None
    val externalCell = (externalTarget as? DropTarget.Cell)?.index ?: -1
    val externalOnto = (externalTarget as? DropTarget.Onto)?.index ?: -1
    // While one is in the air there is one more cell, after the last icon,
    // for a drop on empty space. It is there for the whole carry rather
    // than only while the finger is over empty space: a cell that came and
    // went with the finger would, when it starts a new row, move every
    // other row under that finger and change the answer it was showing.
    val cells: List<HomeTile?> = remember(tiles, external) {
        if (external) tiles + null else tiles
    }
    val tileIds = remember(tiles) { tiles.map { it.id } }

    DisposableEffect(drag) {
        onDispose {
            drag.geometry.grid = null
            drag.geometry.tileIds = emptyList()
        }
    }

    BoxWithConstraints(
        modifier.onGloballyPositioned { c ->
            val p = c.positionInRoot()
            drag.geometry.grid = Bounds(p.x, p.y, p.x + c.size.width, p.y + c.size.height)
        },
    ) {
        val cellWidth = maxWidth / columns
        // Label space follows the user's font size setting. At a fixed
        // height, a two-line name at 1.3x font scale is clipped.
        val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.6f)
        val cellHeight = iconSize + (Spacing.xl + Spacing.sm) * fontScale
        val density = LocalDensity.current
        // Through state for the same reason as the icons above: a larger
        // icon or font makes the rows taller without restarting any
        // handler, and a drop is placed by dividing by the row height.
        val cellWpx by rememberUpdatedState(with(density) { cellWidth.toPx() })
        val cellHpx by rememberUpdatedState(with(density) { cellHeight.toPx() })

        SideEffect {
            val g = drag.geometry
            g.cellW = cellWpx
            g.cellH = cellHpx
            g.columns = columns
            g.tileIds = tileIds
            g.rtl = rtl
        }

        Column {
            cells.chunked(columns).forEachIndexed { rowIndex, rowTiles ->
                Row {
                    rowTiles.forEachIndexed { colIndex, tile ->
                        val index = rowIndex * columns + colIndex
                        if (tile == null) {
                            // Where an icon dropped on empty space will go.
                            Box(
                                modifier = Modifier.width(cellWidth).height(cellHeight),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (externalTarget == DropTarget.End) DropOutline()
                            }
                        } else {
                        val isDragging = index == dragIndex
                        val isMoveTarget = externalCell == index ||
                            (editMode && dragIndex >= 0 && targetIndex == index &&
                                combineIndex < 0 && !isDragging)
                        val isCombineTarget = externalOnto == index ||
                            (editMode && combineIndex == index && !isDragging)

                        // Each icon leans a slightly different way, so the row
                        // does not look like one rigid object rocking.
                        val angle = if (editMode && !isDragging) {
                            sin(wigglePhase + index * 0.7f) * 1.1f
                        } else {
                            0f
                        }

                        Box(
                            modifier = Modifier
                                .width(cellWidth)
                                .height(cellHeight)
                                .zIndex(if (isDragging) 1f else 0f)
                                // What a screen reader is told. The gestures
                                // below are raw touch handlers, which say
                                // nothing to accessibility services by
                                // themselves: without this the icon had a
                                // name but could not be activated.
                                .semantics(mergeDescendants = true) {
                                    role = Role.Button
                                    if (!editMode) {
                                        onClick {
                                            when (tile) {
                                                is HomeTile.App -> currentOnLaunch(tile.entry)
                                                is HomeTile.Folder -> currentOnOpenFolder(tile.id)
                                            }
                                            true
                                        }
                                    }
                                }
                                .pointerInput(editMode, tile.id) {
                                    detectTapGestures(
                                        onTap = {
                                            if (editMode) return@detectTapGestures
                                            when (tile) {
                                                is HomeTile.App -> onLaunch(tile.entry)
                                                is HomeTile.Folder -> onOpenFolder(tile.id)
                                            }
                                        },
                                    )
                                }
                                // Long-press starts a drag in the same motion.
                                // Entering edit mode here means the first
                                // long-press can carry straight into a drag.
                                .pointerInput(index, columns) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            haptics.dragStart()
                                            currentOnEnterEditMode()
                                            dragIndex = index
                                            val picked = currentTiles.getOrNull(index)
                                            dragId = picked?.id
                                            targetIndex = index
                                            combineIndex = -1
                                            dragOffset = Offset.Zero
                                            // Where the icon is in the window
                                            // right now. From here on it is
                                            // the finger that is followed,
                                            // not the cell: turning on edit
                                            // mode changes the bottom bar,
                                            // which can move the whole grid
                                            // under a finger that has not
                                            // moved at all.
                                            val origin = drag.geometry.grid
                                            val drawnColumn =
                                                if (currentRtl) columns - 1 - colIndex else colIndex
                                            dragStart = Offset(
                                                (origin?.left ?: 0f) +
                                                    drawnColumn * cellWpx + cellWpx / 2,
                                                (origin?.top ?: 0f) +
                                                    rowIndex * cellHpx + cellHpx / 2,
                                            )
                                            if (picked != null) {
                                                carry = drag.pickUp(
                                                    picked, DragSource.GRID, dragStart,
                                                )
                                            }
                                        },
                                        onDrag = { change, delta ->
                                            change.consume()
                                            dragOffset += delta
                                            val at = dragStart + dragOffset
                                            drag.position = at
                                            val now = currentTiles
                                            // The same guard as the drop
                                            // below: only the icon that was
                                            // picked up may be offered the
                                            // dock.
                                            val held = now.getOrNull(index)
                                                ?.takeIf { it.id == dragId }
                                            val dock = drag.geometry.dockTarget(
                                                x = at.x,
                                                y = at.y,
                                                key = (held as? HomeTile.App)?.entry?.key,
                                                newOnly = true,
                                            )
                                            if (dock != null) {
                                                if (drag.target != dock) haptics.tick()
                                                drag.target = dock
                                                targetIndex = -1
                                                combineIndex = -1
                                            } else {
                                                drag.target = DropTarget.None
                                                // Measured from where the
                                                // grid is now, for the reason
                                                // given at the pick-up.
                                                val origin = drag.geometry.grid
                                                val hit = gridHit(
                                                    x = at.x - (origin?.left ?: 0f),
                                                    y = at.y - (origin?.top ?: 0f),
                                                    cellW = cellWpx,
                                                    cellH = cellHpx,
                                                    columns = columns,
                                                    count = now.size,
                                                    rtl = currentRtl,
                                                )
                                                val next = hit.index

                                                // Near the middle of another icon
                                                // means "make a folder"; nearer an
                                                // edge means "reorder to here".
                                                val canCombine = hit.overCentre &&
                                                    next != index &&
                                                    now.getOrNull(index) is HomeTile.App

                                                val nextCombine = if (canCombine) next else -1
                                                if (next != targetIndex ||
                                                    nextCombine != combineIndex
                                                ) {
                                                    haptics.tick()
                                                }
                                                targetIndex = next
                                                combineIndex = nextCombine
                                            }
                                        },
                                        onDragEnd = {
                                            haptics.dragEnd()
                                            // Reported by id, never by
                                            // position: this list can be
                                            // shorter than the stored one.
                                            val now = currentTiles
                                            // Only if this cell still holds
                                            // the icon that was picked up.
                                            // The icons can change during a
                                            // drag -- an app is removed, a
                                            // focus schedule starts -- and
                                            // then this cell has a different
                                            // one in it, which the user
                                            // never chose to move.
                                            val from = now.getOrNull(dragIndex)?.id
                                                ?.takeIf { it == dragId }
                                            val dock = drag.target as? DropTarget.Dock
                                            val onto = now.getOrNull(combineIndex)?.id
                                            val to = now.getOrNull(targetIndex)?.id
                                            when {
                                                from == null -> Unit
                                                dock != null ->
                                                    currentOnMoveToDock(from, dock.beforeKey)
                                                onto != null && onto != from ->
                                                    currentOnCombine(from, onto)
                                                to != null && to != from ->
                                                    currentOnMove(from, to)
                                            }
                                            endDrag()
                                        },
                                        onDragCancel = { endDrag() },
                                    )
                                }
                                // After the gesture modifiers, on purpose. A
                                // modifier sees pointer positions in the
                                // space of everything before it in the chain,
                                // so with this layer first the drag was being
                                // measured inside the lifted, 1.12x icon: the
                                // finger moved 112 pixels and the handler was
                                // told 100. The icon fell further behind the
                                // finger the further it was dragged -- a
                                // third of a cell across the screen -- and
                                // since the drop is judged from where the
                                // icon is, a drop aimed at the edge of a cell
                                // could land in its middle and make a folder.
                                // Here the layer changes only how the cell is
                                // drawn, and the gestures see plain pixels.
                                .graphicsLayer {
                                    rotationZ = angle
                                    if (isDragging) {
                                        if (drag.layerAttached) {
                                            // Drawn by HomeDragLayer instead,
                                            // above the dock and everything
                                            // else it may be carried over.
                                            alpha = 0f
                                        } else {
                                            // Nothing above this grid to draw
                                            // it, so the cell itself follows
                                            // the finger.
                                            val origin = drag.geometry.grid
                                            val drawnColumn =
                                                if (rtl) columns - 1 - colIndex else colIndex
                                            val at = dragStart + dragOffset
                                            translationX = at.x - (origin?.left ?: 0f) -
                                                (drawnColumn * cellWpx + cellWpx / 2)
                                            translationY = at.y - (origin?.top ?: 0f) -
                                                (rowIndex * cellHpx + cellHpx / 2)
                                            scaleX = 1.12f
                                            scaleY = 1.12f
                                            alpha = 0.9f
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isMoveTarget) DropOutline()
                            if (isCombineTarget) {
                                // A filled ring, clearly different from the
                                // reorder outline, so the two drops are not
                                // guessed at.
                                Box(
                                    Modifier
                                        .size(iconSize * 1.35f)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.3f)),
                                )
                            }
                            HomeTileContent(
                                tile = tile,
                                iconLoader = iconLoader,
                                iconSize = iconSize,
                                editMode = editMode,
                                onRemove = { onRemoveTile(tile) },
                            )
                        }
                        }
                    }
                    repeat(columns - rowTiles.size) {
                        Spacer(Modifier.width(cellWidth).height(cellHeight))
                    }
                }
            }
        }
    }
}

/** The outline that says "the icon will go here". */
@Composable
private fun DropOutline() {
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

/**
 * Draws the icon in the air, on top of everything.
 *
 * The carried icon used to be its own grid cell, moved by the amount the
 * finger had travelled. That cannot leave the grid: the grid scrolls, a
 * scrolling area clips what is inside it, and the dock is drawn after it.
 * An icon dragged toward the dock was cut off at the grid's bottom edge. So
 * the cell hides and this draws a copy instead, in a layer that covers the
 * whole launcher. It is also the only thing that can show an icon carried
 * out of the drawer, whose own cell is gone as soon as the drawer closes.
 *
 * It reads the icon's position while drawing, not while composing, so a
 * moving finger redraws one small layer and recomposes nothing.
 */
@Composable
internal fun HomeDragLayer(
    drag: HomeDragState,
    iconSize: Dp,
    iconLoader: IconLoader,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(drag) {
        drag.layerAttached = true
        onDispose { drag.layerAttached = false }
    }
    val tile = drag.carried ?: return
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    // As wide as a grid cell, so a long name wraps where it will wrap once
    // it is put down. With no grid on screen yet, a sensible guess.
    val width = drag.geometry.cellW.takeIf { it > 1f }
        ?.let { with(density) { it.toDp() } }
        ?: (iconSize + Spacing.xl)
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            // A picture of something that already exists elsewhere on
            // screen. A screen reader should not find it twice.
            .clearAndSetSemantics { },
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .graphicsLayer {
                    val at = drag.position
                    translationX = at.x - origin.x - size.width / 2f
                    translationY = at.y - origin.y - size.height / 2f
                    scaleX = 1.12f
                    scaleY = 1.12f
                    alpha = 0.9f
                },
            contentAlignment = Alignment.Center,
        ) {
            HomeTileContent(
                tile = tile,
                iconLoader = iconLoader,
                iconSize = iconSize,
                editMode = false,
                onRemove = {},
            )
        }
    }
}

@Composable
private fun HomeTileContent(
    tile: HomeTile,
    iconLoader: IconLoader,
    iconSize: Dp,
    editMode: Boolean,
    onRemove: () -> Unit,
) {
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        shadow = Shadow(color = Color.Black.copy(alpha = 0.7f), blurRadius = 6f),
    )
    when (tile) {
        is HomeTile.App -> AppCell(
            entry = tile.entry,
            iconLoader = iconLoader,
            iconSize = iconSize,
            labelColor = Color.White,
            labelStyle = labelStyle,
            showRemoveBadge = editMode,
            onRemove = onRemove,
            // Gestures are owned by the parent Box.
            interactive = false,
        )
        is HomeTile.Folder -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                FolderIcon(
                    entries = tile.entries,
                    iconLoader = iconLoader,
                    size = iconSize,
                )
                if (editMode) {
                    RemoveBadge(onRemove, Modifier.align(Alignment.TopStart))
                }
            }
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = tile.name,
                style = labelStyle,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RemoveBadge(
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.home_remove),
) {
    Box(
        modifier = modifier
            // 20dp is the drawn size, not the touch target.
            // minimumInteractiveComponentSize keeps the circle small
            // while padding what the finger has to hit out to the
            // 48dp minimum. Safe here because the badge only appears
            // in edit mode, where tapping an icon does nothing --
            // there is no other gesture for the bigger target to
            // steal, and dragging still works because a drag is not
            // consumed by clickable.
            .minimumInteractiveComponentSize()
            .size(20.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error)
            .clickable(onClick = onRemove),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = LauncherIcons.Close,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * Up to five pinned apps, on a soft rounded bar above the bottom controls.
 *
 * Long-press an icon to pick it up: along the dock to reorder it, up onto
 * the grid to move it to the home screen. An icon from the grid or the
 * drawer can be put down here as long as there is room. While the home
 * screen is being edited each icon carries the same ✕ the grid's do.
 */
@Composable
private fun Dock(
    entries: List<AppEntry>,
    iconSize: Dp,
    iconLoader: IconLoader,
    editMode: Boolean,
    drag: HomeDragState,
    onLaunch: (AppEntry) -> Unit,
    onEnterEditMode: () -> Unit,
    onRemove: (AppEntry) -> Unit,
    onDrop: (HomeDrop) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val keys = remember(entries) { entries.map { it.key } }
    // The middle of each icon in window pixels, filled in as they are laid
    // out. A plain map: it is read by a moving finger, not by composition.
    val centres = remember { HashMap<String, Offset>() }
    val publish = {
        val g = drag.geometry
        g.dockKeys = keys
        g.dockCentres = keys.map { centres[it]?.x ?: Float.NaN }
        g.rtl = rtl
    }
    SideEffect { publish() }
    DisposableEffect(drag) {
        onDispose {
            drag.geometry.dock = null
            drag.geometry.dockKeys = emptyList()
            drag.geometry.dockCentres = emptyList()
        }
    }

    // The same rule as the grid: a gesture handler outlives the values it
    // was started with, so anything that can change goes through these.
    val currentOnLaunch by rememberUpdatedState(onLaunch)
    val currentOnEnterEditMode by rememberUpdatedState(onEnterEditMode)
    val currentOnDrop by rememberUpdatedState(onDrop)

    val target = drag.target as? DropTarget.Dock
    val carriedFromHere = if (drag.source == DragSource.DOCK) drag.carriedKey else null
    val shape = RoundedCornerShape(Radii.sheet)
    val removeLabel = stringResource(R.string.remove_from_dock)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { c ->
                val p = c.positionInRoot()
                drag.geometry.dock = Bounds(p.x, p.y, p.x + c.size.width, p.y + c.size.height)
            }
            .clip(shape)
            // Darker and outlined while it is offering to take the icon in
            // the air, so "you can drop this here" does not rest on the
            // marker alone.
            .background(Color.Black.copy(alpha = if (target != null) 0.42f else 0.28f))
            .then(
                if (target != null) {
                    Modifier.border(2.dp, Color.White.copy(alpha = 0.8f), shape)
                } else {
                    Modifier
                }
            )
            .drawWithContent {
                drawContent()
                if (target != null) {
                    // A bar between the two icons the carried one would go
                    // between. The carried icon itself is not one of them.
                    val left = drag.geometry.dock?.left ?: 0f
                    val others = keys.filter { it != drag.carriedKey }
                        .mapNotNull { centres[it]?.x?.minus(left) }
                    if (others.isNotEmpty()) {
                        val outward = (iconSize / 2 + Spacing.sm).toPx() * if (rtl) -1 else 1
                        val x = when {
                            target.slot <= 0 -> others.first() - outward
                            target.slot >= others.size -> others.last() + outward
                            else -> (others[target.slot - 1] + others[target.slot]) / 2
                        }
                        val w = 3.dp.toPx()
                        val inset = Spacing.sm.toPx()
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.9f),
                            topLeft = Offset(x - w / 2, inset),
                            size = Size(w, size.height - inset * 2),
                            cornerRadius = CornerRadius(w / 2),
                        )
                    }
                }
            }
            .padding(vertical = Spacing.xs, horizontal = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entries.forEach { entry ->
            key(entry.key) {
                val carriedHere = carriedFromHere == entry.key
                Box(
                    Modifier
                        .onGloballyPositioned { c ->
                            val p = c.positionInRoot()
                            centres[entry.key] =
                                Offset(p.x + c.size.width / 2f, p.y + c.size.height / 2f)
                            publish()
                        }
                        .clip(RoundedCornerShape(Radii.card))
                        // No label is drawn in the dock: five across a phone
                        // width truncate to nothing useful, and these are
                        // the apps the user chose, so the icon is enough to
                        // look at. It is not enough to listen to, so the
                        // name is said here.
                        .semantics(mergeDescendants = true) {
                            contentDescription = entry.label
                            role = Role.Button
                            if (!editMode) {
                                onClick {
                                    currentOnLaunch(entry)
                                    true
                                }
                            }
                        }
                        .pointerInput(editMode, entry.key) {
                            detectTapGestures(
                                onTap = { if (!editMode) currentOnLaunch(entry) },
                            )
                        }
                        .pointerInput(entry.key) {
                            var start = Offset.Zero
                            var travelled = Offset.Zero
                            var ticket = 0
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptics.dragStart()
                                    currentOnEnterEditMode()
                                    start = centres[entry.key] ?: Offset.Zero
                                    travelled = Offset.Zero
                                    ticket = drag.pickUp(
                                        HomeTile.App(entry), DragSource.DOCK, start,
                                    )
                                },
                                onDrag = { change, delta ->
                                    change.consume()
                                    travelled += delta
                                    if (drag.owns(ticket)) {
                                        val before = drag.target
                                        drag.carryTo(start + travelled)
                                        if (drag.target != before) haptics.tick()
                                    }
                                },
                                onDragEnd = {
                                    haptics.dragEnd()
                                    val drop = if (drag.owns(ticket)) drag.dropRequest() else null
                                    drag.clear(ticket)
                                    if (drop != null) currentOnDrop(drop)
                                },
                                onDragCancel = { drag.clear(ticket) },
                            )
                        }
                        // Hidden while it is in the air: HomeDragLayer is
                        // drawing it under the finger.
                        .graphicsLayer {
                            alpha = if (carriedHere && drag.layerAttached) 0f else 1f
                        }
                        .padding(Spacing.xs),
                ) {
                    AppIconImage(entry = entry, loader = iconLoader, size = iconSize)
                    if (editMode) {
                        RemoveBadge(
                            onRemove = { onRemove(entry) },
                            modifier = Modifier.align(Alignment.TopStart),
                            label = removeLabel,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun GlanceLine(glance: GlanceInfo, modifier: Modifier = Modifier) {
    val parts = buildList {
        glance.nextAlarm?.let { add(stringResource(R.string.glance_alarm, it)) }
        glance.batteryPercent?.let {
            add(
                if (glance.charging) {
                    stringResource(R.string.glance_battery_charging, it)
                } else {
                    stringResource(R.string.glance_battery, it)
                }
            )
        }
    }
    if (parts.isEmpty()) return

    Text(
        text = parts.joinToString("  ·  "),
        style = MaterialTheme.typography.bodyMedium.copy(
            shadow = Shadow(color = Color.Black.copy(alpha = 0.55f), blurRadius = 10f),
        ),
        color = Color.White.copy(alpha = 0.85f),
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun BottomBar(
    editMode: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onExitEditMode: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs, bottom = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (editMode) {
            Text(
                text = stringResource(R.string.home_edit_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(
                    bottom = Spacing.xs,
                    start = Spacing.lg,
                    end = Spacing.lg,
                ),
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
internal fun HomeClock(compact: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
    // one screen whose main job is telling you the time.
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
    val openClockLabel = stringResource(R.string.home_open_clock)

    BoxWithConstraints(modifier) {
        // 64sp wrapped "3:38 PM" onto two lines at a large Display size, and
        // pushed the date onto three. Step the scale down when the window is
        // narrow or short rather than let it break.
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
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.card))
                .clickable(onClick = onClick)
                .semantics { contentDescription = openClockLabel }
                .padding(vertical = Spacing.xs),
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
