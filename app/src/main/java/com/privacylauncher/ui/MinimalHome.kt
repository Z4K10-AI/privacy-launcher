package com.privacylauncher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.privacylauncher.R
import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.GlanceInfo
import com.privacylauncher.designsystem.Radii
import com.privacylauncher.designsystem.Spacing

/**
 * The text-only home screen.
 *
 * No icons, no grid, no dock: a clock and a list of app names in large type,
 * for a phone that is meant to be dull to look at. The gestures are the same
 * ones the ordinary home screen has, so swiping up still opens the drawer and
 * everything else stays where it was.
 *
 * Three decisions worth knowing about, each stated in Settings too:
 *
 *  - Folders are flattened. A folder is a picture of a pile of icons, and
 *    "just app names" cannot show one, so the apps inside are listed on their
 *    own instead.
 *  - Dock apps join the end of the list. They are usually not on the home
 *    screen as well, so dropping the dock without them would quietly put
 *    four or five apps out of reach.
 *  - There is no reordering here. Dragging a name around a text list is
 *    guesswork for everyone involved; turn minimal mode off to rearrange.
 */
@Composable
fun MinimalHome(
    tiles: List<HomeTile>,
    dock: List<AppEntry>,
    glance: GlanceInfo,
    focusActive: Boolean,
    gesturesEnabled: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenClock: () -> Unit,
    onOpenFocus: () -> Unit,
    onDrawerDrag: (Float) -> Unit,
    onDrawerRelease: (Float) -> Unit,
    onQuickDragStart: () -> Unit,
    onQuickDrag: (Float) -> Unit,
    onQuickRelease: (Float) -> Unit,
    onSwipeDown: () -> Unit,
    onDoubleTap: () -> Unit,
    onPinchIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Folders flattened, the dock appended, duplicates dropped. Computed once
    // per list change rather than per recomposition: this runs on every
    // gesture frame otherwise.
    val entries = remember(tiles, dock) {
        val seen = LinkedHashMap<String, AppEntry>()
        tiles.forEach { tile ->
            when (tile) {
                is HomeTile.App -> seen.putIfAbsent(tile.entry.key, tile.entry)
                is HomeTile.Folder ->
                    tile.entries.forEach { seen.putIfAbsent(it.key, it) }
            }
        }
        dock.forEach { seen.putIfAbsent(it.key, it) }
        seen.values.toList()
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
            val scrollState = rememberScrollState()
            // Scrolling is enabled only when the names really overflow.
            //
            // An always-on verticalScroll claims the drag before the Box
            // behind it ever sees it, so it swallows both home gestures --
            // swipe up for the drawer and swipe down for notifications --
            // even with nothing to scroll. Worse, a swallowed swipe up from
            // the bottom of the screen falls through to Android's own
            // navigation gesture, so the launcher looks like it shoved
            // itself aside and showed Recents.
            //
            // The ordinary home screen has the same guard for the same
            // reason; see HomeScreen. maxValue is Int.MAX_VALUE until the
            // first measurement, so that case is excluded too.
            val canScroll = scrollState.maxValue > 0 &&
                scrollState.maxValue != Int.MAX_VALUE

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState, enabled = canScroll),
            ) {
                // Compact on purpose: in minimal mode the list of names is
                // the screen, and a display-size clock would push it below
                // the fold before the first app was visible.
                HomeClock(
                    compact = true,
                    onClick = onOpenClock,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.lg, start = Spacing.lg, end = Spacing.lg),
                )
                if (!glance.isEmpty) {
                    GlanceLine(
                        glance = glance,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.xxs, start = Spacing.lg, end = Spacing.lg),
                    )
                }
                if (focusActive) {
                    MinimalLine(
                        text = stringResource(R.string.focus_banner_title),
                        style = MaterialTheme.typography.bodyLarge,
                        alpha = 0.7f,
                        onClick = onOpenFocus,
                    )
                }

                Spacer(Modifier.height(Spacing.xl))

                if (entries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.home_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.xl),
                    )
                } else {
                    entries.forEach { entry ->
                        MinimalLine(
                            text = entry.label,
                            style = MaterialTheme.typography.headlineSmall,
                            alpha = 1f,
                            onClick = { onLaunch(entry) },
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.xl))
            }

            // Text, not the icon button the ordinary home screen uses: an
            // icon is a picture, and this screen has none.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.sm, end = Spacing.sm, bottom = Spacing.lg),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MinimalAction(
                    text = stringResource(R.string.home_apps_button),
                    onClick = onOpenDrawer,
                )
                MinimalAction(
                    text = stringResource(R.string.settings_title),
                    onClick = onOpenSettings,
                )
            }
        }
    }
}

/** One name in the list. Large, left-aligned, generous to the thumb. */
@Composable
private fun MinimalLine(
    text: String,
    style: TextStyle,
    alpha: Float,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        // The window is transparent over the wallpaper, so every piece of
        // text on this screen carries its own shadow rather than relying on
        // a wallpaper that happens to be dark. Heavier than the clock's: a
        // name in headline weight has far less ink than a 64sp time, and
        // over the pale part of a wallpaper the thinner strokes were the
        // first thing to disappear.
        style = style.copy(
            shadow = Shadow(color = Color.Black.copy(alpha = 0.78f), blurRadius = 16f),
        ),
        color = Color.White.copy(alpha = alpha),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.card))
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = Spacing.touchTarget)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    )
}

@Composable
private fun MinimalAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(
            shadow = Shadow(color = Color.Black.copy(alpha = 0.78f), blurRadius = 16f),
        ),
        color = Color.White.copy(alpha = 0.9f),
        modifier = Modifier
            .clip(RoundedCornerShape(Radii.pill))
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = Spacing.touchTarget)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    )
}
