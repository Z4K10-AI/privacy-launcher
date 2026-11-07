package com.privacylauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.IconLoader
import com.privacylauncher.designsystem.Radii
import com.privacylauncher.designsystem.Spacing

/**
 * A folder, drawn as the first four app icons in a 2x2 grid on a soft tile.
 *
 * Four, not more: at icon size a 3x3 of thumbnails is mush. Four is enough to
 * recognise a folder by its contents.
 */
@Composable
fun FolderIcon(
    entries: List<AppEntry>,
    iconLoader: IconLoader,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val preview = entries.take(4)
    val inner = (size - Spacing.sm) / 2

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(Radii.card))
            .background(Color.White.copy(alpha = 0.22f))
            .padding(Spacing.xxs),
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs / 2),
        ) {
            preview.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs / 2)) {
                    row.forEach { entry ->
                        AppIconImage(entry = entry, loader = iconLoader, size = inner)
                    }
                }
            }
        }
    }
}
