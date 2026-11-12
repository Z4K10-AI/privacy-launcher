package com.privacylauncher.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import com.privacylauncher.data.HomeItem
import com.privacylauncher.data.LauncherSettings
import kotlin.math.abs

/*
 * Carrying an icon across the home screen: where it is, and where it would
 * land.
 *
 * An icon can be picked up in three places -- the grid, the dock, and the
 * app drawer -- and put down in two, the grid and the dock. Each pair used
 * to need its own arithmetic. Here there is one description of what is on
 * screen ([HomeGeometry]), one answer to "what is under this point"
 * ([HomeGeometry.targetFor]) and one object that holds the icon in the air
 * ([HomeDragState]).
 *
 * Everything is measured in pixels from the top-left of the window, so a
 * point means the same thing to the grid, the dock and the drawer. The
 * functions that decide are plain arithmetic on plain numbers, so they are
 * tested on the build machine without a screen (HomeDropTargetTest).
 */

/** A rectangle on screen, in window pixels. */
internal data class Bounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun contains(x: Float, y: Float): Boolean =
        x >= left && x < right && y >= top && y < bottom
}

/** Where a carried icon would land if the finger lifted now. */
internal sealed interface DropTarget {
    /** Nowhere. The icon goes back where it came from. */
    data object None : DropTarget

    /** Take the place of the icon in this grid cell. */
    data class Cell(val index: Int) : DropTarget

    /** Join the icon in this grid cell, making a folder or adding to one. */
    data class Onto(val index: Int) : DropTarget

    /** After the last icon on the grid. */
    data object End : DropTarget

    /**
     * Into the dock, in front of the dock icon [beforeKey], or at the end
     * when that is null. [slot] is the same place as a number, counted
     * among the dock's other icons, for drawing the marker.
     */
    data class Dock(val slot: Int, val beforeKey: String?) : DropTarget
}

/** What a finished carry asks for. Names things by id and key, never by position. */
internal sealed interface HomeDrop {
    val key: String

    /** Put the app where [beforeId] is, or at the end of the home screen. */
    data class Place(override val key: String, val beforeId: String?) : HomeDrop

    /** Put the app together with the icon [ontoId]. */
    data class Onto(override val key: String, val ontoId: String) : HomeDrop

    /** Put the app in the dock, in front of [beforeKey] or at the end. */
    data class Dock(override val key: String, val beforeKey: String?) : HomeDrop
}

/** What a point on the grid is over. */
internal data class GridHit(
    /** The nearest cell that holds an icon. */
    val index: Int,
    /** Over the middle of that icon, which means "put it with this one". */
    val overCentre: Boolean,
    /** Over empty space after the last icon. */
    val pastEnd: Boolean,
)

/**
 * What the point ([x], [y]) is over, measured from the grid's top-left.
 *
 * A point outside the grid is pulled to the nearest row and column, so a
 * finger that strays above the first row still means the first row.
 *
 * Near the middle of an icon means "make a folder"; nearer an edge means
 * "reorder to here". Empty space after the last icon is never the middle of
 * anything: the question used to be asked of the empty cell and the answer
 * applied to the last icon, so a drop on empty space made a folder with an
 * icon the finger was nowhere near.
 *
 * [rtl] is for right-to-left languages, where the first column is drawn on
 * the right.
 */
internal fun gridHit(
    x: Float,
    y: Float,
    cellW: Float,
    cellH: Float,
    columns: Int,
    count: Int,
    rtl: Boolean = false,
): GridHit {
    val drawnColumn = (x / cellW).toInt().coerceIn(0, columns - 1)
    val column = if (rtl) columns - 1 - drawnColumn else drawnColumn
    val row = (y / cellH).toInt().coerceAtLeast(0)
    val raw = row * columns + column
    val pastEnd = raw > count - 1
    val dx = abs(x - (drawnColumn * cellW + cellW / 2))
    val dy = abs(y - (row * cellH + cellH / 2))
    return GridHit(
        index = raw.coerceAtMost(count - 1).coerceAtLeast(0),
        overCentre = !pastEnd && dx < cellW * CENTRE_FRACTION && dy < cellH * CENTRE_FRACTION,
        pastEnd = pastEnd,
    )
}

/** How much of a cell, either side of its middle, counts as "on the icon". */
private const val CENTRE_FRACTION = 0.28f

/**
 * What is on the home screen and where, in window pixels.
 *
 * Written by the grid and the dock as they are laid out, read while a
 * finger is dragging. Plain fields on purpose: nothing draws from these, so
 * there is nothing to recompose when they change.
 */
internal class HomeGeometry {
    /** Everything above the dock: the clock and the grid. Null when not on screen. */
    var home: Bounds? = null

    /** The grid itself. Null when the home screen has no icons. */
    var grid: Bounds? = null
    var cellW: Float = 1f
    var cellH: Float = 1f
    var columns: Int = 1

    /** The ids of the icons on the grid, in the order they are drawn. */
    var tileIds: List<String> = emptyList()

    /** The dock. Null when it is not on screen, which includes when it is empty. */
    var dock: Bounds? = null

    /** The dock's apps, in the order they are drawn. */
    var dockKeys: List<String> = emptyList()

    /** The middle of each dock icon, left to right in pixels, matching [dockKeys]. */
    var dockCentres: List<Float> = emptyList()
    var dockMax: Int = LauncherSettings.MAX_DOCK_APPS

    /** A right-to-left language is in use, so first means rightmost. */
    var rtl: Boolean = false
}

/**
 * The dock target under ([x], [y]) for the app [key], or null when the dock
 * is not there, the point is not on it, or it cannot take the app.
 *
 * A full dock takes nothing new but can still be reordered. With
 * [newOnly], an app that is already in the dock is refused as well: that is
 * for an icon coming from the home grid, which would otherwise leave the
 * home screen and add nothing to the dock.
 */
internal fun HomeGeometry.dockTarget(
    x: Float,
    y: Float,
    key: String?,
    newOnly: Boolean = false,
): DropTarget.Dock? {
    val bounds = dock ?: return null
    if (key == null || !bounds.contains(x, y)) return null
    val already = key in dockKeys
    if (already && newOnly) return null
    if (!already && dockKeys.size >= dockMax) return null

    // Count the other icons that come before the finger. Their middles are
    // measured after layout, so for an instant there can be fewer of them
    // than there are keys.
    var slot = 0
    val others = ArrayList<String>(dockKeys.size)
    for (i in dockKeys.indices) {
        val other = dockKeys[i]
        if (other == key) continue
        others += other
        val centre = dockCentres.getOrNull(i) ?: continue
        if (if (rtl) centre > x else centre < x) slot++
    }
    return DropTarget.Dock(slot = slot, beforeKey = others.getOrNull(slot))
}

/**
 * Where the app [key], carried in from the drawer or the dock, would land
 * at ([x], [y]).
 *
 * Unlike a drag that starts on the grid, this one has somewhere it does not
 * belong: over the bottom bar, or anywhere at all while the home screen is
 * covered. There the answer is [DropTarget.None] and letting go changes
 * nothing.
 */
internal fun HomeGeometry.targetFor(x: Float, y: Float, key: String): DropTarget {
    if (dock?.contains(x, y) == true) return dockTarget(x, y, key) ?: DropTarget.None
    if (home?.contains(x, y) != true) return DropTarget.None

    val bounds = grid
    if (bounds == null || tileIds.isEmpty() || !bounds.contains(x, y)) return DropTarget.End
    val hit = gridHit(x - bounds.left, y - bounds.top, cellW, cellH, columns, tileIds.size, rtl)
    return when {
        hit.pastEnd -> DropTarget.End
        // Over its own icon: it stays where it is.
        tileIds[hit.index] == HomeItem.App(key).id -> DropTarget.Cell(hit.index)
        hit.overCentre -> DropTarget.Onto(hit.index)
        else -> DropTarget.Cell(hit.index)
    }
}

/** What dropping the app [key] on [target] asks for, or null for nothing. */
internal fun HomeGeometry.dropFor(target: DropTarget, key: String): HomeDrop? = when (target) {
    DropTarget.None -> null
    DropTarget.End -> HomeDrop.Place(key, beforeId = null)
    is DropTarget.Cell -> HomeDrop.Place(key, beforeId = tileIds.getOrNull(target.index))
    is DropTarget.Onto ->
        tileIds.getOrNull(target.index)?.let { HomeDrop.Onto(key, ontoId = it) }
    is DropTarget.Dock -> HomeDrop.Dock(key, beforeKey = target.beforeKey)
}

/** Where a carried icon was picked up. */
internal enum class DragSource { NONE, GRID, DOCK, DRAWER }

/**
 * The icon in the air.
 *
 * One of these is shared by the grid, the dock, the drawer and the layer
 * that draws the carried icon on top of them all. Whoever picks an icon up
 * says so here; everyone else reads it to decide what to highlight.
 */
@Stable
internal class HomeDragState {
    val geometry = HomeGeometry()

    /** The icon being carried, or null. */
    var carried by mutableStateOf<HomeTile?>(null)
        private set

    var source by mutableStateOf(DragSource.NONE)
        private set

    /**
     * The middle of the carried icon, in window pixels.
     *
     * Changes on every movement of the finger, so it must only be read
     * where things are drawn, never where they are composed.
     */
    var position by mutableStateOf(Offset.Zero)

    /** Where the icon would land. Changes only when the answer does. */
    var target by mutableStateOf<DropTarget>(DropTarget.None)

    /**
     * Where the finger last was, in window pixels. Kept up to date by
     * [followCarry] so that an icon picked up in the drawer starts under
     * the finger without the drawer having to know where it is on screen.
     */
    var finger: Offset = Offset.Zero

    /** True while [HomeDragLayer] is on screen to draw the carried icon. */
    var layerAttached: Boolean = false

    private var carryId = 0

    /** True while an icon from the dock or the drawer is being carried. */
    val external: Boolean
        get() = source == DragSource.DOCK || source == DragSource.DRAWER

    val carriedKey: String?
        get() = (carried as? HomeTile.App)?.entry?.key

    /**
     * Picks [tile] up at [at]. Returns a ticket to hand back to [clear], so
     * that a gesture which has already lost the icon cannot clear a newer
     * carry that is not its own.
     */
    fun pickUp(tile: HomeTile, from: DragSource, at: Offset): Int {
        carried = tile
        source = from
        position = at
        target = DropTarget.None
        return ++carryId
    }

    /** Whether [ticket] is for the icon that is in the air right now. */
    fun owns(ticket: Int): Boolean = ticket == carryId && carried != null

    /** Moves an icon from the dock or drawer, and works out where it would land. */
    fun carryTo(at: Offset) {
        position = at
        val key = carriedKey
        target = if (key == null) DropTarget.None else geometry.targetFor(at.x, at.y, key)
    }

    /** What letting go now asks for, or null for nothing. */
    fun dropRequest(): HomeDrop? = carriedKey?.let { geometry.dropFor(target, it) }

    fun clear(ticket: Int = carryId) {
        if (ticket != carryId) return
        carried = null
        source = DragSource.NONE
        target = DropTarget.None
    }
}

/**
 * Follows the finger while an icon from the drawer is in the air.
 *
 * This belongs on the outermost box of the launcher, not on the icon that
 * was picked up. The gesture starts on a cell in the drawer, and the drawer
 * then slides away: its cells leave the screen, their gesture handlers are
 * thrown away with them, and the finger would be left holding nothing. The
 * outermost box is never thrown away, and it is offered every touch before
 * anything inside it, so once an icon is in the air it takes the rest of
 * the gesture for itself.
 *
 * It also notes where the finger is on every touch, carrying or not, which
 * is how the icon starts out under the finger. That is one assignment per
 * touch event.
 *
 * [origin] is where the box sits in the window, normally the top-left.
 */
internal suspend fun PointerInputScope.followCarry(
    drag: HomeDragState,
    origin: () -> Offset,
    onDrop: (HomeDrop) -> Unit,
) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val changes = event.changes
            val pointer = changes.firstOrNull { it.pressed } ?: changes.firstOrNull() ?: continue
            val at = pointer.position + origin()
            drag.finger = at
            if (drag.source != DragSource.DRAWER) continue

            when {
                // Nothing has had a chance to use this event yet, so one
                // that arrives already used is how a gesture being taken
                // away is reported: the phone rang, or the user went home.
                // Whatever was being carried is put back.
                changes.any { it.isConsumed } -> drag.clear()
                changes.none { it.pressed } -> {
                    drag.carryTo(at)
                    val drop = drag.dropRequest()
                    drag.clear()
                    if (drop != null) onDrop(drop)
                }
                else -> drag.carryTo(at)
            }
            changes.forEach { it.consume() }
        }
    }
}
