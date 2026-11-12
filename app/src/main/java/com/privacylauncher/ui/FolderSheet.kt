package com.privacylauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.privacylauncher.R
import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.IconLoader
import com.privacylauncher.designsystem.Radii
import com.privacylauncher.designsystem.Spacing
import com.privacylauncher.designsystem.cardSurface
import com.privacylauncher.ui.components.AppCell
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import com.privacylauncher.designsystem.fittedIconSize

/**
 * An open folder, as a rounded sheet over a blurred home screen.
 *
 * The blur is applied by the caller to the content underneath, using
 * `Modifier.blurBehind`. That works — unlike window blur, which cannot touch
 * a launcher's own wallpaper — because the pixels being blurred are ours.
 */
@Composable
fun FolderSheet(
    name: String,
    entries: List<AppEntry>,
    iconSize: Dp,
    iconLoader: IconLoader,
    editMode: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onRename: (String) -> Unit,
    onRemoveFromFolder: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val dismissLabel = stringResource(R.string.folder_close)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            // Tapping the scrim closes the folder. No ripple: the scrim is
            // not a button, it is the rest of the screen.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = dismissLabel,
                onClick = onDismiss,
            )
            // Said as its name too. With only the action labelled, a screen
            // reader lands on something it cannot name before it can say
            // what a double-tap would do.
            .semantics { contentDescription = dismissLabel }
            .safeDrawingPadding()
            .padding(Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.sheet))
                .cardSurface(level = 1)
                // Swallow taps so they do not reach the dismissing scrim.
                // A raw touch handler, not `clickable`: that also told
                // accessibility services the sheet was a button, one with
                // no name that did nothing when pressed.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FolderNameField(name = name, onRename = onRename)
            Spacer(Modifier.height(Spacing.sm))

            BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Four across, at the size asked for or the largest that fits
            // a quarter of the sheet.
            @Suppress("NAME_SHADOWING")
            val iconSize = fittedIconSize(iconSize, maxWidth / 4)
            Column {

            // Four across is enough: a folder is a small set by definition,
            // and this keeps the sheet from becoming a second app drawer.
            entries.chunked(4).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    row.forEach { entry ->
                        Box(Modifier.weight(1f)) {
                            AppCell(
                                entry = entry,
                                iconLoader = iconLoader,
                                iconSize = iconSize,
                                labelColor = MaterialTheme.colorScheme.onBackground,
                                showRemoveBadge = editMode,
                                onRemove = { onRemoveFromFolder(entry) },
                                removeLabel = stringResource(R.string.folder_remove_app),
                                onClick = { onLaunch(entry) },
                                onLongClick = { onRemoveFromFolder(entry) },
                            )
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            }
            }
        }
    }
}

@Composable
private fun FolderNameField(name: String, onRename: (String) -> Unit) {
    // Local copy so typing stays responsive; the store is updated as we go.
    var text by remember(name) { mutableStateOf(name) }
    val nameLabel = stringResource(R.string.folder_name_hint)

    BasicTextField(
        value = text,
        onValueChange = {
            text = it
            onRename(it)
        },
        singleLine = true,
        textStyle = LocalTextStyle.current.merge(
            MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)
            // A bare text field has no name. Its hint is one only while it
            // is empty, and a folder's name field almost never is.
            .semantics { contentDescription = nameLabel },
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.Center) {
                if (text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.folder_name_hint),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            }
        },
    )
}
