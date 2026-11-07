package com.privacylauncher.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.privacylauncher.R
import com.privacylauncher.data.AccentColor
import com.privacylauncher.data.LauncherSettings
import com.privacylauncher.data.ThemeMode
import com.privacylauncher.designsystem.Motion
import com.privacylauncher.designsystem.Spacing
import com.privacylauncher.designsystem.dynamicColorAvailable
import com.privacylauncher.designsystem.resolveIsDark
import com.privacylauncher.designsystem.swatch
import com.privacylauncher.ui.components.ChoiceRow
import com.privacylauncher.ui.components.SwatchRow
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 4

/**
 * The four screens shown once, the first time the launcher runs.
 *
 * Two rules shaped this:
 *
 *  - **Nothing here is a gate.** Skip is on every page, the home-app page can
 *    be walked past without setting anything, and no page asks for a
 *    permission. A launcher that holds your phone hostage on page two is a
 *    launcher you cannot get out of, because it *is* the home screen.
 *  - **The settings on page three are live.** Choosing a theme or accent
 *    writes it immediately and the onboarding itself recolours, so the choice
 *    is made by looking rather than by reading a label. That also means
 *    skipping from page three keeps what was chosen, which is what anyone
 *    would expect of a control that already did something.
 *
 * Drawn on an opaque background rather than the usual transparent launcher
 * window: the rest of the app sits over the wallpaper on purpose, but a page
 * of explanation has to be readable over whatever is behind it.
 */
@Composable
fun OnboardingScreen(
    settings: LauncherSettings,
    isDefaultHome: Boolean,
    onThemeMode: (ThemeMode) -> Unit,
    onAccent: (AccentColor) -> Unit,
    onChooseHomeApp: () -> Unit,
    onFinish: () -> Unit,
) {
    val pager = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val lastPage = pager.currentPage == PAGE_COUNT - 1

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {

            // Skip sits top-right on every page but the last, where the
            // primary button already says the same thing.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.sm),
                horizontalArrangement = Arrangement.End,
            ) {
                if (!lastPage) {
                    TextButton(onClick = onFinish) {
                        Text(stringResource(R.string.onboarding_skip))
                    }
                } else {
                    // Keeps the row's height so the pages below do not shift
                    // upward by a button's height on the last page.
                    Spacer(Modifier.height(Spacing.xxl))
                }
            }

            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                Column(
                    Modifier
                        .fillMaxSize()
                        // A small parallax and fade as a page slides away.
                        //
                        // The offset is computed inside this lambda, not
                        // outside it. graphicsLayer's block runs in the draw
                        // phase, so reading the pager's offset here redraws
                        // the layer each frame without recomposing the page.
                        // Read one line up instead -- as it was -- and every
                        // frame of every swipe recomposes the whole page,
                        // which is what Compose's @FrequentlyChangingValue
                        // warning is for.
                        .graphicsLayer {
                            val offset = (pager.currentPage - page) +
                                pager.currentPageOffsetFraction
                            alpha = 1f - (offset.absoluteValue * 0.6f).coerceIn(0f, 1f)
                            translationX = -offset * size.width * 0.15f
                        }
                        .verticalScroll(rememberScrollState()),
                ) {
                    when (page) {
                        0 -> WelcomePage()
                        1 -> DefaultHomePage(isDefaultHome, onChooseHomeApp)
                        2 -> ThemePage(settings, onThemeMode, onAccent)
                        else -> PrivacyPage()
                    }
                    Spacer(Modifier.height(Spacing.xl))
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dots(current = pager.currentPage)
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        if (lastPage) onFinish()
                        else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    },
                ) {
                    Text(
                        stringResource(
                            if (lastPage) R.string.onboarding_done else R.string.onboarding_next
                        )
                    )
                }
            }
        }
    }
}

/**
 * The page indicator.
 *
 * The dots are decorative shapes with no text, so a screen reader would
 * otherwise walk past four unlabelled nodes and announce nothing about
 * where you are. One contentDescription on the row, and the individual
 * dots cleared, turns them into a single spoken "Page 3 of 4".
 */
@Composable
private fun Dots(current: Int) {
    val label = stringResource(R.string.onboarding_page_of, current + 1, PAGE_COUNT)
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = label
        },
    ) {
        repeat(PAGE_COUNT) { index ->
            val active = index == current
            val alpha by animateFloatAsState(
                targetValue = if (active) 1f else 0.3f,
                animationSpec = Motion.short(),
                label = "dot",
            )
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = alpha)),
            )
        }
    }
}

/**
 * Every page's title and standfirst.
 *
 * The horizontal inset is applied here rather than on the page, so that
 * these line up exactly with the settings widgets on page three, which
 * carry the same inset of their own. Padding the page as well would double
 * it and push the last accent swatch off the right edge.
 */
@Composable
private fun PageHeading(title: String, body: String) {
    Spacer(Modifier.height(Spacing.xl))
    Text(
        text = title,
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = Spacing.lg),
    )
    Spacer(Modifier.height(Spacing.md))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.lg),
    )
    Spacer(Modifier.height(Spacing.lg))
}

@Composable
private fun WelcomePage() {
    PageHeading(
        title = stringResource(R.string.onboarding_welcome_title),
        body = stringResource(R.string.onboarding_welcome_body),
    )
}

@Composable
private fun DefaultHomePage(isDefaultHome: Boolean, onChooseHomeApp: () -> Unit) {
    PageHeading(
        title = stringResource(R.string.onboarding_home_title),
        body = stringResource(R.string.onboarding_home_body),
    )
    if (isDefaultHome) {
        Text(
            text = stringResource(R.string.onboarding_home_already),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
    } else {
        Button(
            onClick = onChooseHomeApp,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        ) {
            Text(stringResource(R.string.onboarding_home_action))
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.onboarding_home_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
    }
}

@Composable
private fun ThemePage(
    settings: LauncherSettings,
    onThemeMode: (ThemeMode) -> Unit,
    onAccent: (AccentColor) -> Unit,
) {
    val dark = settings.themeMode.resolveIsDark()
    PageHeading(
        title = stringResource(R.string.onboarding_theme_title),
        body = stringResource(R.string.onboarding_theme_body),
    )
    // The settings widgets, not copies of them: the same controls the
    // Appearance screen uses, so they cannot look or behave differently here.
    ChoiceRow(
        title = stringResource(R.string.settings_theme),
        options = ThemeMode.entries,
        selected = settings.themeMode,
        label = {
            stringResource(
                when (it) {
                    ThemeMode.SYSTEM -> R.string.settings_theme_system
                    ThemeMode.LIGHT -> R.string.settings_theme_light
                    ThemeMode.DARK -> R.string.settings_theme_dark
                }
            )
        },
        onSelect = onThemeMode,
    )
    SwatchRow(
        title = stringResource(R.string.settings_accent),
        swatches = AccentColor.entries.map { it.name to it.swatch(dark) },
        selectedLabel = settings.accent.name,
        onSelect = { name ->
            AccentColor.entries.firstOrNull { it.name == name }?.let(onAccent)
        },
    )
    if (dynamicColorAvailable) {
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = stringResource(R.string.onboarding_theme_more),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
    }
}

@Composable
private fun PrivacyPage() {
    PageHeading(
        title = stringResource(R.string.onboarding_privacy_title),
        body = stringResource(R.string.onboarding_privacy_body),
    )
    val points = remember {
        listOf(
            R.string.onboarding_privacy_point_network,
            R.string.onboarding_privacy_point_permissions,
            R.string.onboarding_privacy_point_storage,
            R.string.onboarding_privacy_point_source,
        )
    }
    points.forEach { res ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.sm),
        ) {
            Text(
                text = "•",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.size(width = 20.dp, height = 24.dp),
            )
            Text(
                text = stringResource(res),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
