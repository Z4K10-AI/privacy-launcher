package com.privacylauncher.ui.privacy

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.privacylauncher.R
import com.privacylauncher.data.GroupReport
import com.privacylauncher.data.IconLoader
import com.privacylauncher.data.PermissionHolder
import com.privacylauncher.data.PrivacyReport
import com.privacylauncher.data.PrivacyScanner
import com.privacylauncher.data.ScoreBand
import com.privacylauncher.designsystem.LocalSignalColors
import com.privacylauncher.designsystem.Motion
import com.privacylauncher.designsystem.Radii
import com.privacylauncher.designsystem.Spacing
import com.privacylauncher.designsystem.cardSurface
import com.privacylauncher.ui.components.LauncherIcons
import com.privacylauncher.ui.components.PackageIconImage
import com.privacylauncher.ui.settings.ScreenScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import com.privacylauncher.data.ScanStatus
import com.privacylauncher.data.TrackerFinding
import com.privacylauncher.data.TrackerReport
import androidx.compose.foundation.layout.offset

/**
 * The privacy dashboard.
 *
 * Three claims on this screen have to hold, so they are stated on it rather
 * than only in the README: everything is computed here, the score is a rule
 * of thumb with its working shown, and the list is limited to apps this
 * launcher can see. See `PrivacyScanner` for why that limit exists and why
 * widening it would mean asking for QUERY_ALL_PACKAGES.
 */
@Composable
fun PrivacyDashboardScreen(
    report: PrivacyReport,
    iconLoader: IconLoader,
    onBack: () -> Unit,
    onOpenPermissions: (String) -> Unit,
    onOpenSystemPrivacy: () -> Unit,
    /** The tracker scan: what it found, or how far it has got. */
    trackers: TrackerReport = TrackerReport(),
    /** Start a scan. True means read every app again, not only the new ones. */
    onScanTrackers: (fresh: Boolean) -> Unit = {},
    onStopTrackerScan: () -> Unit = {},
) {
    ScreenScaffold(title = stringResource(R.string.privacy_title), onBack = onBack) {
        if (!report.ready) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.privacy_scanning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@ScreenScaffold
        }

        val signal = LocalSignalColors.current
        val scoreColor = when (report.band) {
            ScoreBand.GOOD -> signal.good
            ScoreBand.FAIR -> signal.fair
            ScoreBand.ATTENTION -> signal.attention
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                bottom = Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item("score") {
                ScoreCard(report, scoreColor)
            }
            item("on-device") {
                Text(
                    text = stringResource(R.string.privacy_on_device),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
            }
            item("working") {
                WorkingCard(report)
            }
            item("trackers") {
                TrackersCard(
                    report = trackers,
                    iconLoader = iconLoader,
                    onScan = onScanTrackers,
                    onStop = onStopTrackerScan,
                    onOpenApp = onOpenPermissions,
                )
            }
            item("usage") {
                RecentUseCard(onOpenSystemPrivacy)
            }
            // Above the list, not under it: it says what the list is and
            // what tapping a row does, which is no use after the scrolling.
            item("scope") {
                Text(
                    text = stringResource(R.string.privacy_scope),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                )
            }
            items(items = report.rankedGroups, key = { it.group.name }) { group ->
                GroupCard(
                    report = group,
                    iconLoader = iconLoader,
                    accent = scoreColor,
                    onOpenPermissions = onOpenPermissions,
                )
            }
        }
    }
}

@Composable
private fun ScoreCard(report: PrivacyReport, scoreColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.card))
            .cardSurface(level = 1)
            .padding(vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScoreRing(
            score = report.score,
            label = stringResource(
                when (report.band) {
                    ScoreBand.GOOD -> R.string.privacy_score_good
                    ScoreBand.FAIR -> R.string.privacy_score_fair
                    ScoreBand.ATTENTION -> R.string.privacy_score_attention
                },
            ),
            color = scoreColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = stringResource(R.string.privacy_score_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** What came off the score, and why. */
@Composable
private fun WorkingCard(report: PrivacyReport) {
    val scored = report.rankedGroups.filter { it.penalty > 0 }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.card))
            .cardSurface(level = 1)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.privacy_score_explainer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xxs))
        if (scored.isEmpty()) {
            Text(
                text = stringResource(R.string.privacy_score_perfect),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        } else {
            scored.forEach { group ->
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(group.group.labelRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.privacy_deduction_apps,
                            group.installedHolders.size,
                            group.installedHolders.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Text(
                        // U+2212 minus, not a hyphen: it lines up with digits
                        // and does not read as a bullet at small sizes.
                        text = "−${group.penalty}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.xxs))
        // Two sentences, not one string: with nothing preinstalled holding a
        // permission the second half read "0 more came with the phone; those
        // are listed but not scored", which is an explanation of nothing.
        val scanned = pluralStringResource(
            R.plurals.privacy_scanned,
            report.installedAppsScanned,
            report.installedAppsScanned,
        )
        val preinstalled = if (report.preinstalledAppsScanned > 0) {
            " " + pluralStringResource(
                R.plurals.privacy_scanned_preinstalled,
                report.preinstalledAppsScanned,
                report.preinstalledAppsScanned,
            )
        } else {
            ""
        }
        Text(
            text = scanned + preinstalled,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Recent permission use.
 *
 * This card exists to say honestly that the launcher cannot show it. The
 * timeline is real and Android 12 and later keep it, but reading it needs
 * GET_APP_OPS_STATS, which is signature|privileged. Linking to the system
 * screen is the whole of what an ordinary app can do here.
 */
@Composable
private fun RecentUseCard(onOpenSystemPrivacy: () -> Unit) {
    val available = PrivacyScanner.systemUsageTimelineAvailable
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.card))
            .cardSurface(level = 1)
            .padding(Spacing.lg),
    ) {
        Text(
            text = stringResource(R.string.privacy_usage_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = stringResource(
                if (available) {
                    R.string.privacy_usage_available
                } else {
                    R.string.privacy_usage_unavailable
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (available) {
            Spacer(Modifier.height(Spacing.xs))
            TextButton(
                onClick = onOpenSystemPrivacy,
                contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = Spacing.xs),
                modifier = Modifier.alignWithText(),
            ) {
                Text(stringResource(R.string.privacy_usage_open))
            }
        }
    }
}

/**
 * Lines a text button's words up with the text above it.
 *
 * A text button keeps a little space either side of its label so the
 * pressed shape has room. Under a paragraph that leaves the label starting
 * that much to the right of the paragraph, which reads as a mistake. This
 * moves the button left by the same amount.
 */
private fun Modifier.alignWithText(): Modifier = offset(x = -Spacing.xs)

/**
 * Trackers found inside apps.
 *
 * Three states, each designed rather than left to fall out: nothing scanned
 * yet, a scan in progress, and results. A scan is only ever started by the
 * button. It reads the code of every app on the phone, which is not
 * something to do behind the user's back because they opened a screen.
 *
 * The last paragraph on the card is the important one. A scanner like this
 * invites two wrong conclusions -- "found, so it is spying on me" and "not
 * found, so it is clean" -- and the card says plainly that it shows neither.
 */
@Composable
private fun TrackersCard(
    report: TrackerReport,
    iconLoader: IconLoader,
    onScan: (fresh: Boolean) -> Unit,
    onStop: () -> Unit,
    onOpenApp: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.card))
            .cardSurface(level = 1)
            .animateContentSize(animationSpec = Motion.medium())
            .padding(vertical = Spacing.lg),
    ) {
        Text(
            text = stringResource(R.string.tracker_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
        Spacer(Modifier.height(Spacing.xs))

        when {
            report.running -> TrackerProgress(report, onStop)
            !report.hasResults -> {
                Text(
                    text = stringResource(R.string.tracker_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
                Spacer(Modifier.height(Spacing.md))
                Button(
                    onClick = { onScan(false) },
                    enabled = report.total > 0,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                ) {
                    Text(
                        if (report.total > 0) {
                            pluralStringResource(
                                R.plurals.tracker_scan_apps, report.total, report.total,
                            )
                        } else {
                            stringResource(R.string.tracker_scan)
                        },
                    )
                }
            }
            else -> TrackerResults(report, iconLoader, onScan, onOpenApp)
        }

        Spacer(Modifier.height(Spacing.md))
        Text(
            text = stringResource(R.string.tracker_meaning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
        if (report.listSize > 0) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = pluralStringResource(
                    R.plurals.tracker_credit, report.listSize, report.snapshot, report.listSize,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }
    }
}

@Composable
private fun TrackerProgress(report: TrackerReport, onStop: () -> Unit) {
    val total = report.total.coerceAtLeast(1)
    val progressLabel = stringResource(
        R.string.tracker_scanning, (report.done + 1).coerceAtMost(total), total,
    )
    Column(Modifier.padding(horizontal = Spacing.lg)) {
        Text(
            text = progressLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        // Always takes its line, named or not, so the bar under it does not
        // jump up and down as apps go by.
        Text(
            text = report.current.ifEmpty { " " },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Spacing.sm))
        LinearProgressIndicator(
            progress = { report.done / total.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                // The text above already says how far it has got. Announcing
                // every step of the bar as well would bury it.
                .clearAndSetSemantics { },
        )
        Spacer(Modifier.height(Spacing.xs))
        TextButton(
            onClick = onStop,
            contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = Spacing.xs),
            modifier = Modifier.alignWithText(),
        ) {
            Text(stringResource(R.string.tracker_stop))
        }
    }
}

@Composable
private fun TrackerResults(
    report: TrackerReport,
    iconLoader: IconLoader,
    onScan: (fresh: Boolean) -> Unit,
    onOpenApp: (String) -> Unit,
) {
    val found = report.withTrackers
    Text(
        text = if (found.isEmpty()) {
            pluralStringResource(R.plurals.tracker_none_found, report.read, report.read)
        } else {
            pluralStringResource(
                R.plurals.tracker_found_in, found.size, found.size, report.read,
            )
        },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = Spacing.lg),
    )
    if (found.isNotEmpty()) {
        Spacer(Modifier.height(Spacing.xs))
        found.forEach { finding ->
            TrackerAppRow(finding, iconLoader, onOpenApp)
        }
    }
    if (report.unread > 0) {
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = pluralStringResource(R.plurals.tracker_unread, report.unread, report.unread),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
    }
    if (report.waiting > 0) {
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = pluralStringResource(R.plurals.tracker_waiting, report.waiting, report.waiting),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
    }
    Spacer(Modifier.height(Spacing.xs))
    Row(Modifier.padding(horizontal = Spacing.md)) {
        if (report.waiting > 0) {
            TextButton(
                onClick = { onScan(false) },
                contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = Spacing.xs),
            ) {
                Text(stringResource(R.string.tracker_scan_new))
            }
            Spacer(Modifier.width(Spacing.sm))
        }
        TextButton(
            onClick = { onScan(true) },
            contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = Spacing.xs),
        ) {
            Text(stringResource(R.string.tracker_rescan))
        }
    }
}

/** One app that contains trackers. Tap to see which, and to reach App info. */
@Composable
private fun TrackerAppRow(
    finding: TrackerFinding,
    iconLoader: IconLoader,
    onOpenApp: (String) -> Unit,
) {
    var expanded by rememberSaveable(finding.packageName) { mutableStateOf(false) }
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = Motion.short(),
        label = "trackerChevron",
    )
    val count = pluralStringResource(
        R.plurals.tracker_count, finding.trackers.size, finding.trackers.size,
    )
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = stringResource(
                        if (expanded) R.string.tracker_hide_list else R.string.tracker_show_list,
                    ),
                ) { expanded = !expanded }
                .defaultMinSize(minHeight = Spacing.touchTarget)
                .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PackageIconImage(
                packageName = finding.packageName,
                loader = iconLoader,
                size = 36.dp,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = finding.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = count,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = LauncherIcons.ChevronDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(chevron),
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(
                    // Lined up under the app's name, past its icon.
                    start = Spacing.lg + 36.dp + Spacing.md,
                    end = Spacing.lg,
                    bottom = Spacing.xs,
                ),
            ) {
                finding.trackers.forEach { tracker ->
                    Text(
                        text = tracker.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (tracker.categories.isNotEmpty()) {
                        Text(
                            text = tracker.categories.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(Spacing.xs))
                }
                if (finding.status == ScanStatus.PARTLY) {
                    Text(
                        text = stringResource(R.string.tracker_partly),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = { onOpenApp(finding.packageName) },
                    contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = Spacing.xs),
                    modifier = Modifier.alignWithText(),
                ) {
                    Text(stringResource(R.string.privacy_change))
                }
            }
        }
    }
}

@Composable
private fun GroupCard(
    report: GroupReport,
    iconLoader: IconLoader,
    accent: Color,
    onOpenPermissions: (String) -> Unit,
) {
    var expanded by rememberSaveable(report.group.name) { mutableStateOf(false) }
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = Motion.short(),
        label = "chevron",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.card))
            .cardSurface(level = 1)
            // animateContentSize rather than AnimatedVisibility: the rows are
            // part of this card, so the card should grow into them rather
            // than have a separate thing appear underneath it.
            .animateContentSize(animationSpec = Motion.medium()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(report.group.labelRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(report.group.bodyRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = report.installedHolders.size.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = if (report.installedHolders.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    accent
                },
            )
            Spacer(Modifier.width(Spacing.sm))
            Icon(
                imageVector = LauncherIcons.ChevronDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(chevron),
            )
        }

        if (expanded) {
            Column(Modifier.padding(bottom = Spacing.sm)) {
                if (report.installedHolders.isEmpty()) {
                    Text(
                        text = stringResource(R.string.privacy_none_hold),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = Spacing.lg,
                            vertical = Spacing.xs,
                        ),
                    )
                }
                report.installedHolders.forEach { holder ->
                    HolderRow(holder, iconLoader, onOpenPermissions)
                }
                if (report.preinstalledHolders.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.privacy_preinstalled_header),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = Spacing.lg,
                            top = Spacing.sm,
                            bottom = Spacing.xxs,
                        ),
                    )
                    report.preinstalledHolders.forEach { holder ->
                        HolderRow(holder, iconLoader, onOpenPermissions)
                    }
                }
            }
        }
    }
}

@Composable
private fun HolderRow(
    holder: PermissionHolder,
    iconLoader: IconLoader,
    onOpenPermissions: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenPermissions(holder.packageName) }
            .defaultMinSize(minHeight = Spacing.touchTarget)
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PackageIconImage(
            packageName = holder.packageName,
            loader = iconLoader,
            size = 36.dp,
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = holder.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (holder.isSelf) {
                Text(
                    text = stringResource(R.string.privacy_self),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = stringResource(R.string.privacy_change),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
