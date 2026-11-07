package com.privacylauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.privacylauncher.R
import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.IconLoader
import com.privacylauncher.data.LauncherSettings
import com.privacylauncher.designsystem.Radii
import com.privacylauncher.designsystem.Spacing
import com.privacylauncher.designsystem.panelSurface
import com.privacylauncher.designsystem.rememberHaptics
import com.privacylauncher.ui.components.AppCell
import com.privacylauncher.ui.components.LauncherIcons
import kotlinx.coroutines.launch

/** Below this many sections the letter bar is clutter rather than a shortcut. */
private const val MIN_SECTIONS_FOR_LETTER_BAR = 4

/** The bubble echoing the letter under the finger. */
private val BUBBLE_SIZE = 56.dp

/**
 * The app drawer: a searchable, alphabetically sectioned grid of every
 * installed, non-hidden app.
 *
 * Uses [LazyVerticalGrid], so only cells on screen exist. That plus the
 * on-demand icon loading in AppIconImage is what keeps this usable on a 4 GB
 * phone with a couple of hundred apps.
 *
 * The rows arrive already flattened from the view model, headings included,
 * which is what lets the letter bar jump straight to a grid index.
 */
@Composable
fun AppDrawer(
    list: DrawerList,
    query: String,
    columns: Int,
    iconSize: Dp,
    iconLoader: IconLoader,
    isOnHome: (AppEntry) -> Boolean,
    isInDock: (AppEntry) -> Boolean,
    dockFull: Boolean,
    /** True once the drawer has settled fully open. Drives the keyboard. */
    isOpen: Boolean,
    autoKeyboard: Boolean,
    /** Accumulated downward drag on the handle, in px. */
    onHandleDrag: (Float) -> Unit,
    /** Handle drag released: vertical velocity in px/s. */
    onHandleRelease: (Float) -> Unit,
    onQueryChange: (String) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onClose: () -> Unit,
    onAppInfo: (AppEntry) -> Unit,
    onUninstall: (AppEntry) -> Unit,
    onAddToHome: (AppEntry) -> Unit,
    onRemoveFromHome: (AppEntry) -> Unit,
    onAddToDock: (AppEntry) -> Unit,
    onRemoveFromDock: (AppEntry) -> Unit,
    onHide: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val gridState = rememberLazyGridState()

    // Tied to isOpen rather than to this composable entering the tree: the
    // drawer is composed as soon as a drag starts lifting it, and popping the
    // keyboard up while the sheet is still halfway would be awful.
    LaunchedEffect(isOpen, autoKeyboard) {
        if (isOpen && autoKeyboard) {
            searchFocus.requestFocus()
        } else if (!isOpen) {
            keyboard?.hide()
        }
    }

    // Typing reorders the whole list by score, so an old scroll position is
    // meaningless. Without this, deleting back to an empty box leaves you
    // somewhere in the middle of the alphabet.
    LaunchedEffect(list.searching) {
        gridState.scrollToItem(0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Opaque. An earlier near-opaque version let the home clock ghost
            // through, and a translucent full-screen layer also forces an
            // offscreen buffer on every frame of the open drag.
            .panelSurface()
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
                // Drag-to-close lives on the handle, not the whole sheet: a
                // downward drag anywhere else belongs to the app grid's
                // scroll, and whichever won would be a coin toss.
                .pointerInput(Unit) {
                    var total = 0f
                    var velocity = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = {
                            total = 0f
                            velocity = VelocityTracker()
                            // Pulling the sheet away with the keyboard still
                            // sitting on top of it looks broken, and the IME
                            // takes long enough to animate out that waiting
                            // for the drag to finish is too late.
                            keyboard?.hide()
                        },
                        onDragEnd = {
                            onHandleRelease(velocity.calculateVelocity().y)
                        },
                        onDragCancel = { onHandleRelease(0f) },
                    ) { change, delta ->
                        total += delta
                        velocity.addPointerInputChange(change)
                        onHandleDrag(total)
                    }
                }
                // A 4dp pill is not a 48dp touch target. The padding is what
                // makes the handle grabbable, so it is generous.
                .padding(vertical = Spacing.md),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                .focusRequester(searchFocus),
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
                onSearch = { list.topMatch?.let(onLaunch) },
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )

        Text(
            text = if (list.searching) {
                stringResource(R.string.app_count_filtered, list.matchCount, list.total)
            } else {
                pluralStringResource(R.plurals.app_count, list.total, list.total)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.lg, bottom = Spacing.xxs),
        )

        if (list.rows.isEmpty()) {
            // weight(1f), not fillMaxSize(): inside a Column the latter asks for
            // the full parent height rather than what is left after the search
            // field, which pushes the bottom of the list off screen.
            Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    // With everything hidden there is no search to blame, and
                    // "No apps match that search" would send the user looking
                    // for a search box they never typed in.
                    text = if (list.searching) {
                        stringResource(R.string.drawer_empty)
                    } else {
                        stringResource(R.string.drawer_empty_all)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Spacing.xl),
                )
            }
        } else {
            val showLetterBar = list.letters.size >= MIN_SECTIONS_FOR_LETTER_BAR
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(
                        start = Spacing.sm,
                        // Room for the letter bar, so the right-hand column
                        // of icons is not sitting underneath it.
                        end = if (showLetterBar) Spacing.xl else Spacing.sm,
                        top = Spacing.xs,
                        bottom = Spacing.xl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        items = list.rows,
                        key = { it.id },
                        span = { row ->
                            if (row is DrawerRow.App) GridItemSpan(1)
                            else GridItemSpan(maxLineSpan)
                        },
                    ) { row ->
                        when (row) {
                            is DrawerRow.Header -> SectionHeader(row.letter)
                            DrawerRow.MostUsedHeader ->
                                SectionHeader(stringResource(R.string.drawer_most_used))
                            is DrawerRow.App -> AppCell(
                                entry = row.entry,
                                iconLoader = iconLoader,
                                iconSize = iconSize,
                                labelColor = MaterialTheme.colorScheme.onBackground,
                                onClick = { onLaunch(row.entry) },
                                onLongClick = {
                                    haptics.longPress()
                                    menuFor = row.entry
                                },
                            )
                        }
                    }
                }

                if (showLetterBar) {
                    LetterBar(
                        letters = list.letters,
                        onJump = { jump ->
                            haptics.tick()
                            scope.launch { gridState.scrollToItem(jump.rowIndex) }
                        },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        }
    }

    menuFor?.let { entry ->
        AppActionsDialog(
            entry = entry,
            onHome = isOnHome(entry),
            inDock = isInDock(entry),
            dockFull = dockFull,
            onDismiss = { menuFor = null },
            onAppInfo = { menuFor = null; onAppInfo(entry) },
            onUninstall = { menuFor = null; onUninstall(entry) },
            onAddToHome = { menuFor = null; onAddToHome(entry) },
            onRemoveFromHome = { menuFor = null; onRemoveFromHome(entry) },
            onAddToDock = { menuFor = null; onAddToDock(entry) },
            onRemoveFromDock = { menuFor = null; onRemoveFromDock(entry) },
            onHide = { menuFor = null; onHide(entry) },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = Spacing.sm,
            top = Spacing.sm,
            bottom = Spacing.xxs,
        ),
    )
}

/**
 * The A-Z strip down the right edge.
 *
 * Touching or dragging it jumps the grid to that section. It handles the
 * pointer itself with [awaitEachGesture] and consumes every event, because
 * anything less and the grid underneath would try to fling at the same time.
 *
 * The letters it shows are the sections that exist, not the whole alphabet:
 * offering to jump to Q on a phone with no app starting with Q is a dead
 * target that still takes up height the real letters could have used.
 */
@Composable
private fun LetterBar(
    letters: List<LetterJump>,
    onJump: (LetterJump) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeIndex by remember { mutableIntStateOf(-1) }
    val label = stringResource(R.string.drawer_letter_bar)

    BoxWithConstraints(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.TopEnd,
    ) {
        val barHeight = maxHeight
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxHeight()
                .width(Spacing.xl)
                .semantics { contentDescription = label }
                .pointerInput(letters) {
                    if (letters.isEmpty()) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        var last = -1

                        fun update(y: Float) {
                            val span = size.height.toFloat().coerceAtLeast(1f)
                            val index = ((y / span) * letters.size).toInt()
                                .coerceIn(0, letters.lastIndex)
                            if (index != last) {
                                last = index
                                activeIndex = index
                                onJump(letters[index])
                            }
                        }

                        update(down.position.y)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: break
                            if (!change.pressed) break
                            change.consume()
                            update(change.position.y)
                        }
                        activeIndex = -1
                    }
                },
        ) {
            // Each letter gets an equal slice of the full height, because
            // the hit test above is a straight y/height fraction. Laying them
            // out any other way -- centred as a clump, say -- means the
            // letter under your finger is not the one you jump to.
            letters.forEachIndexed { index, jump ->
                val active = index == activeIndex
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = jump.letter,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        // The finger covers the strip, so the letter it is on is echoed in a
        // bubble beside it, level with the finger rather than parked in the
        // middle of the screen where it would mean nothing.
        if (activeIndex in letters.indices) {
            val slotCentre = barHeight * (activeIndex + 0.5f) / letters.size
            Box(
                modifier = Modifier
                    .padding(top = (slotCentre - BUBBLE_SIZE / 2).coerceAtLeast(0.dp))
                    .padding(end = Spacing.xl)
                    .size(BUBBLE_SIZE)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letters[activeIndex].letter,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun AppActionsDialog(
    entry: AppEntry,
    onHome: Boolean,
    inDock: Boolean,
    dockFull: Boolean,
    onDismiss: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onAddToHome: () -> Unit,
    onRemoveFromHome: () -> Unit,
    onAddToDock: () -> Unit,
    onRemoveFromDock: () -> Unit,
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
                Spacer(Modifier.height(Spacing.md))
                if (onHome) {
                    DialogAction(stringResource(R.string.remove_from_home), onRemoveFromHome)
                } else {
                    DialogAction(stringResource(R.string.add_to_home), onAddToHome)
                }
                when {
                    inDock -> DialogAction(
                        stringResource(R.string.remove_from_dock),
                        onRemoveFromDock,
                    )
                    // Shown but inert when the dock is full, with the reason
                    // on the row. Hiding it would look like the feature was
                    // missing rather than occupied.
                    dockFull -> DialogAction(
                        label = pluralStringResource(
                            R.plurals.dock_full,
                            LauncherSettings.MAX_DOCK_APPS,
                            LauncherSettings.MAX_DOCK_APPS,
                        ),
                        onClick = {},
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> DialogAction(stringResource(R.string.add_to_dock), onAddToDock)
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
            .clip(RoundedCornerShape(Radii.small))
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = Spacing.touchTarget)
            .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
    )
}
