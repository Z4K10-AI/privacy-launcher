package com.privacylauncher.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.privacylauncher.R
import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.IconLoader
import com.privacylauncher.designsystem.Radii
import com.privacylauncher.designsystem.Spacing

/**
 * One app: icon above its name.
 *
 * Shared by the drawer grid and the home screen grid so both honour the icon
 * size setting identically.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCell(
    entry: AppEntry,
    iconLoader: IconLoader,
    iconSize: Dp,
    labelColor: Color,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = MaterialTheme.typography.labelSmall,
    showRemoveBadge: Boolean = false,
    onRemove: (() -> Unit)? = null,
    /**
     * What the remove badge does, for screen readers. Differs by context:
     * on the home grid it removes from the home screen, inside a folder it
     * takes the app out of the folder.
     */
    removeLabel: String? = null,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    /**
     * When false the cell attaches no click handling of its own.
     *
     * The home grid needs this while reordering: a `combinedClickable` here
     * would claim the pointer stream and the parent's drag detector would
     * never see the gesture.
     */
    interactive: Boolean = true,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(Radii.card))
            .then(
                if (interactive) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier
                }
            )
            .padding(vertical = Spacing.xs, horizontal = Spacing.xxs),
    ) {
        Box {
            AppIconImage(entry = entry, loader = iconLoader, size = iconSize)
            if (showRemoveBadge && onRemove != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
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
                        .combinedClickable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = LauncherIcons.Close,
                        contentDescription = removeLabel
                            ?: stringResource(R.string.home_remove),
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            text = entry.label,
            style = labelStyle,
            color = labelColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
