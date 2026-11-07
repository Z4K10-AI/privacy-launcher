package com.privacylauncher.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.privacylauncher.R
import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.IconLoader
import com.privacylauncher.data.IconSize
import com.privacylauncher.designsystem.Motion
import com.privacylauncher.designsystem.Spacing
import com.privacylauncher.designsystem.cardSurface
import com.privacylauncher.ui.components.AppIconImage

/**
 * A small live sample of the home grid, shown next to the controls that
 * change it.
 *
 * It uses real apps and real icons rather than coloured squares, because the
 * thing being judged is whether *your* icons are too big at this size, and a
 * placeholder cannot answer that. Icon size animates between choices so the
 * difference is visible as a change rather than a jump.
 *
 * It is deliberately cheap. The entries are a handful taken from the app list
 * the drawer has already loaded, and the icons come from the same
 * [IconLoader] cache the home screen and drawer fill, so showing this costs
 * no extra decoding and no extra memory on a 4 GB phone.
 *
 * The font needs no preview of its own: the whole app, this card included,
 * is already drawn in the chosen font, so the labels below show it directly.
 */
@Composable
fun GridPreview(
    entries: List<AppEntry>,
    iconLoader: IconLoader,
    iconSize: IconSize,
    columns: Int,
    modifier: Modifier = Modifier,
) {
    val size by animateDpAsState(
        targetValue = iconSize.dp.dp,
        animationSpec = Motion.medium(),
        label = "previewIconSize",
    )

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            .cardSurface()
            .padding(vertical = Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.settings_preview_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.sm),
        )

        if (entries.isEmpty()) {
            // The app list loads asynchronously, so this is what the card
            // looks like for the first moments after settings opens.
            Text(
                text = stringResource(R.string.settings_preview_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
            return@Column
        }

        // One row of exactly `columns` cells, which is what makes the column
        // count legible: the cells narrow as the count rises, the same way
        // they will on the real grid.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            val shown = entries.take(columns)
            shown.forEach { entry ->
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = Spacing.xxs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppIconImage(entry = entry, loader = iconLoader, size = size)
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = Spacing.xxs),
                    )
                }
            }
            // Empty cells so a grid set wider than the number of apps
            // available still shows its true column width.
            repeat(columns - shown.size) {
                Column(Modifier.weight(1f)) { Text("", Modifier.width(0.dp)) }
            }
        }
    }
}

/**
 * A sample of the chosen font, at the two sizes that actually matter.
 *
 * The whole app is already redrawn in the font the moment it is chosen, so
 * strictly this card shows nothing new. It is here because the difference
 * between Inter and the system sans at body size is a few hairlines, and
 * without a clock-sized sample on screen the choice is made blind — the
 * large time on the home screen is where a font choice is actually felt.
 */
@Composable
fun FontPreview(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            .cardSurface()
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.settings_preview_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        Text(
            text = stringResource(R.string.settings_font_sample_time),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.settings_font_sample_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xxs),
        )
    }
}
