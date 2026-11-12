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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.privacylauncher.R
import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.ContactMatch
import com.privacylauncher.data.IconLoader
import com.privacylauncher.data.LauncherSettings
import com.privacylauncher.data.SettingsTarget
import com.privacylauncher.designsystem.Radii
import com.privacylauncher.designsystem.Spacing
import com.privacylauncher.designsystem.cardSurface
import com.privacylauncher.designsystem.panelSurface
import com.privacylauncher.designsystem.rememberHaptics
import com.privacylauncher.ui.components.AppCell
import com.privacylauncher.ui.components.LauncherIcons
import kotlinx.coroutines.launch
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import com.privacylauncher.designsystem.fittedIconSize
import androidx.compose.ui.platform.LocalFocusManager

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
    /** Settings screens, contacts and the contact-search offer. */
    extraRows: List<DrawerRow>,
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
    onOpenSettingsScreen: (SettingsTarget) -> Unit,
    onOpenContact: (ContactMatch) -> Unit,
    onEnableContacts: () -> Unit,
    onOwnAppInfo: () -> Unit,
    onEndFocus: () -> Unit,
    onOpenFocus: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * An app was long-pressed and then dragged: it is being carried out of
     * the drawer to the home screen. Null where that is not possible, and
     * then a long-press only opens the menu.
     */
    onDragOut: ((AppEntry) -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val gridState = rememberLazyGridState()

    // One flat list, so settings and contact results scroll with the apps
    // rather than being pinned somewhere the keyboard can cover.
    val rows = remember(list.rows, extraRows) { list.rows + extraRows }

    // Tied to isOpen rather than to this composable entering the tree: the
    // drawer is composed as soon as a drag starts lifting it, and popping the
    // keyboard up while the sheet is still halfway would be awful.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(isOpen, autoKeyboard) {
        if (isOpen && autoKeyboard) {
            searchFocus.requestFocus()
        } else if (!isOpen) {
            keyboard?.hide()
        }
    }

    // The drawer is kept built while it is closed, so closing it no longer
    // resets it for free. Each of these used to happen because the whole
    // thing was thrown away:
    //  - the search box gives up the cursor. Left with it, the box would
    //    go on blinking a cursor nobody can see, which is a redraw twice a
    //    second for as long as the home screen is up;
    //  - an app's menu left open does not stay floating over the home
    //    screen;
    //  - the list goes back to the top, where a drawer is expected to open.
    LaunchedEffect(isOpen) {
        if (!isOpen) {
            focusManager.clearFocus()
            menuFor = null
            gridState.scrollToItem(0)
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

        val searchLabel = stringResource(R.string.drawer_search_hint)
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                .focusRequester(searchFocus)
                // The hint inside the box is its only name, and it goes
                // away with the first letter typed. After that a screen
                // reader announced a box containing "c" and no word on
                // what it was for.
                .semantics { contentDescription = searchLabel },
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

        if (rows.isEmpty()) {
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
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                // Icons at the size asked for, or the largest that fits a
                // column this wide. Six columns of large icons are wider
                // than a small phone, and a grid does not shrink them by
                // itself: it lets them overlap.
                val cellWidth = (
                    maxWidth - Spacing.sm - (if (showLetterBar) Spacing.xl else Spacing.sm)
                    ) / columns
                @Suppress("NAME_SHADOWING")
                val iconSize = fittedIconSize(iconSize, cellWidth)
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
                        items = rows,
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
                            is DrawerRow.App -> DrawerAppCell(
                                entry = row.entry,
                                iconLoader = iconLoader,
                                iconSize = iconSize,
                                onLaunch = onLaunch,
                                onMenu = { menuFor = it },
                                onDragOut = onDragOut,
                            )
                            DrawerRow.SettingsSearchHeader ->
                                SectionHeader(stringResource(R.string.drawer_settings_results))
                            DrawerRow.ContactsHeader ->
                                SectionHeader(stringResource(R.string.drawer_contacts_results))
                            is DrawerRow.SettingsScreen -> ResultRow(
                                icon = LauncherIcons.Settings,
                                title = stringResource(row.target.labelRes),
                                onClick = { onOpenSettingsScreen(row.target) },
                            )
                            is DrawerRow.Contact -> ResultRow(
                                icon = LauncherIcons.Person,
                                title = row.match.name,
                                onClick = { onOpenContact(row.match) },
                            )
                            is DrawerRow.FocusNotice -> FocusNotice(
                                setAside = row.setAside,
                                onEndFocus = onEndFocus,
                                onOpenFocus = onOpenFocus,
                            )
                            is DrawerRow.ContactPrompt -> ResultRow(
                                icon = LauncherIcons.Person,
                                title = if (row.permanentlyDenied) {
                                    stringResource(R.string.settings_app_info)
                                } else {
                                    stringResource(R.string.drawer_contact_prompt)
                                },
                                subtitle = if (row.permanentlyDenied) {
                                    stringResource(R.string.drawer_contact_denied)
                                } else {
                                    stringResource(R.string.drawer_contact_prompt_body)
                                },
                                onClick = if (row.permanentlyDenied) {
                                    onOwnAppInfo
                                } else {
                                    onEnableContacts
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

/**
 * One app in the drawer: tap to open it, long-press for its menu, or
 * long-press and drag to carry it to the home screen.
 *
 * The menu opens when the finger lifts, not the moment the long-press is
 * recognised. That is what leaves room for the drag: a finger that holds
 * and then moves is carrying the icon away, and a menu that had already
 * opened under it would have to flash up and vanish again. The buzz at the
 * long-press and the icon lifting slightly say the hold has registered.
 *
 * "Moves" means further than the distance Android itself treats as a
 * deliberate movement. A resting finger is never perfectly still, and
 * without that margin most long-presses would turn into a drag of a few
 * pixels and the menu would rarely open.
 *
 * The gestures are raw touch handlers, which tell accessibility services
 * nothing, so the two things they do are declared again as actions a
 * screen reader can offer: open, and show the menu.
 */
@Composable
internal fun DrawerAppCell(
    entry: AppEntry,
    iconLoader: IconLoader,
    iconSize: Dp,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
    onDragOut: ((AppEntry) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val currentOnLaunch by rememberUpdatedState(onLaunch)
    val currentOnMenu by rememberUpdatedState(onMenu)
    val currentOnDragOut by rememberUpdatedState(onDragOut)
    var lifted by remember { mutableStateOf(false) }
    val menuLabel = stringResource(R.string.drawer_app_options)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radii.card))
            .indication(interaction, LocalIndication.current)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick {
                    currentOnLaunch(entry)
                    true
                }
                onLongClick(label = menuLabel) {
                    currentOnMenu(entry)
                    true
                }
            }
            .pointerInput(entry.key) {
                detectTapGestures(
                    // The pressed look that `clickable` would have given.
                    onPress = { at ->
                        val press = PressInteraction.Press(at)
                        interaction.emit(press)
                        interaction.emit(
                            if (tryAwaitRelease()) {
                                PressInteraction.Release(press)
                            } else {
                                PressInteraction.Cancel(press)
                            }
                        )
                    },
                    onTap = { currentOnLaunch(entry) },
                )
            }
            .pointerInput(entry.key) {
                var travelled = Offset.Zero
                var handedOver = false
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        haptics.longPress()
                        lifted = true
                        travelled = Offset.Zero
                        handedOver = false
                    },
                    onDrag = { change, delta ->
                        change.consume()
                        travelled += delta
                        val carry = currentOnDragOut
                        if (!handedOver && carry != null &&
                            travelled.getDistance() > viewConfiguration.touchSlop
                        ) {
                            // From here the launcher's outermost box follows
                            // the finger. This cell is about to leave the
                            // screen with the rest of the drawer.
                            handedOver = true
                            lifted = false
                            carry(entry)
                        }
                    },
                    onDragEnd = {
                        lifted = false
                        if (!handedOver) currentOnMenu(entry)
                    },
                    onDragCancel = { lifted = false },
                )
            }
            .graphicsLayer {
                val scale = if (lifted) 1.08f else 1f
                scaleX = scale
                scaleY = scale
            },
    ) {
        AppCell(
            entry = entry,
            iconLoader = iconLoader,
            iconSize = iconSize,
            labelColor = MaterialTheme.colorScheme.onBackground,
            interactive = false,
        )
    }
}

/**
 * The banner at the top of the drawer while focus mode is on.
 *
 * Says what is happening, how much is being held back, and carries the way
 * out. Tapping the body opens the focus screen; the button ends the session
 * outright, which is the thing most likely to be wanted in a hurry.
 */
@Composable
private fun FocusNotice(
    setAside: Int,
    onEndFocus: () -> Unit,
    onOpenFocus: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs, vertical = Spacing.xxs)
            .clip(RoundedCornerShape(Radii.card))
            .cardSurface(level = 2)
            .clickable(onClick = onOpenFocus)
            .padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.focus_banner_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.focus_banner_set_aside,
                    setAside,
                    setAside,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onEndFocus) {
            Text(stringResource(R.string.focus_end))
        }
    }
}

/**
 * A full-width result that is not an app: a settings screen, a contact, or
 * the offer to turn contact search on.
 *
 * Deliberately a row rather than a grid cell. These are not icons the user
 * recognises by shape, and a one-word label in a 72dp square would truncate.
 */
@Composable
private fun ResultRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.small))
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = Spacing.touchTarget)
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.md))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        // A heading, so the A-Z sections can be stepped through with a
        // screen reader the way the letter strip does it by touch.
        modifier = Modifier
            .semantics { heading() }
            .padding(
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
