package com.privacylauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.privacylauncher.data.LauncherSettings
import com.privacylauncher.designsystem.LauncherTheme
import com.privacylauncher.designsystem.Motion
import com.privacylauncher.designsystem.resolveIsDark
import com.privacylauncher.ui.AppDrawer
import com.privacylauncher.ui.HomeScreen
import com.privacylauncher.ui.LauncherViewModel
import com.privacylauncher.ui.Panel
import com.privacylauncher.ui.settings.DesignPreviewScreen
import com.privacylauncher.ui.settings.HiddenAppsScreen
import com.privacylauncher.ui.settings.SettingsScreen

/**
 * The launcher's one and only Activity.
 *
 * A few launcher-specific behaviours live here rather than in Compose:
 *
 *  - [onNewIntent]: pressing Home while already on the home screen re-delivers
 *    the HOME intent to this same Activity. A launcher is expected to respond
 *    by returning to a clean home screen.
 *  - Back on the bare home screen does nothing. Letting it reach the default
 *    handler would finish() the Activity, and the system would immediately
 *    relaunch it, which the user sees as a flicker.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Both bars fully transparent. The default `enableEdgeToEdge()` picks a
        // scrim from the *system's* dark-mode setting, which is wrong here: the
        // launcher has its own theme setting, and the home screen draws over
        // the wallpaper regardless of either. Icon colour is set in
        // LauncherRoot instead, from what is actually on screen.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Stops Android painting its own translucent scrim behind the
            // gesture/nav bar, which showed as a grey band under the drawer.
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            LauncherTheme(settings = settings) {
                LauncherRoot(viewModel, settings)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.closeAllPanels()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDefaultHome()
    }
}

@Composable
private fun LauncherRoot(viewModel: LauncherViewModel, settings: LauncherSettings) {
    val drawerApps by viewModel.drawerApps.collectAsStateWithLifecycle()
    val drawerTotal by viewModel.drawerTotal.collectAsStateWithLifecycle()
    val homeApps by viewModel.homeApps.collectAsStateWithLifecycle()
    val allApps by viewModel.allInstalledApps.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    val iconSize = settings.iconSize.dp.dp
    val panel = viewModel.panel
    val panelOpen = panel != Panel.NONE

    // Fade the home screen out underneath an open panel.
    //
    // There is no wallpaper blur here on purpose: see Surfaces.panelSurface
    // for why Window.setBackgroundBlurRadius cannot blur a launcher's own
    // wallpaper, and why reading the wallpaper bitmap instead would cost a
    // storage permission this app does not ask for.
    val homeAlpha by animateFloatAsState(
        targetValue = if (panelOpen) 0f else 1f,
        animationSpec = Motion.medium(),
        label = "homeAlpha",
    )

    // Status/nav bar icons must contrast with whatever is drawn beneath them.
    //
    //  - A panel is open: that surface follows the launcher theme, so light
    //    theme needs dark icons and dark theme needs light ones.
    //  - Bare home screen: we are over the wallpaper, where the clock is drawn
    //    white with a shadow, so light icons always match.
    val view = LocalView.current
    val isDark = settings.themeMode.resolveIsDark()
    val lightBackgroundBehindBars = panelOpen && !isDark
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightBackgroundBehindBars
            isAppearanceLightNavigationBars = lightBackgroundBehindBars
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.graphicsLayer { alpha = homeAlpha }) {
            HomeScreen(
                homeApps = homeApps,
                columns = settings.homeColumns,
                iconSize = iconSize,
                iconLoader = viewModel.iconLoader,
                editMode = viewModel.homeEditMode,
                // With a panel up the home screen is covered, and while
                // reordering a vertical drag belongs to the grid. Either way
                // its swipe detector must be off or stray drags reach it.
                gesturesEnabled = !panelOpen && !viewModel.homeEditMode,
                onLaunch = viewModel::launch,
                onEnterEditMode = { viewModel.setHomeEditing(true) },
                onExitEditMode = { viewModel.setHomeEditing(false) },
                onRemove = viewModel::removeFromHome,
                onMove = viewModel::moveHomeApp,
                onOpenDrawer = viewModel::openDrawer,
                onOpenSettings = viewModel::openSettings,
            )
        }

        AnimatedVisibility(
            visible = panel == Panel.DRAWER,
            enter = slideInVertically(Motion.panelSlide()) { it } + fadeIn(Motion.medium()),
            exit = slideOutVertically(Motion.panelSlide()) { it } + fadeOut(Motion.exit()),
        ) {
            AppDrawer(
                apps = drawerApps,
                totalAppCount = drawerTotal,
                query = query,
                columns = settings.drawerColumns,
                iconSize = iconSize,
                iconLoader = viewModel.iconLoader,
                isOnHome = viewModel::isOnHome,
                onQueryChange = viewModel::onQueryChange,
                onLaunch = viewModel::launch,
                onClose = viewModel::closePanel,
                onAppInfo = { viewModel.openAppInfo(it) },
                onUninstall = { viewModel.requestUninstall(it) },
                onAddToHome = viewModel::addToHome,
                onRemoveFromHome = viewModel::removeFromHome,
                onHide = { viewModel.setAppHidden(it, true) },
            )
        }

        PanelTransition(panel == Panel.SETTINGS) {
            SettingsScreen(
                settings = settings,
                isDefaultHome = viewModel.isDefaultHome,
                onBack = viewModel::closePanel,
                onThemeMode = viewModel::setThemeMode,
                onAccent = viewModel::setAccent,
                onDynamicColor = viewModel::setDynamicColor,
                onFont = viewModel::setFont,
                onIconSize = viewModel::setIconSize,
                onDrawerColumns = viewModel::setDrawerColumns,
                onHomeColumns = viewModel::setHomeColumns,
                onWallpaper = { viewModel.openWallpaperPicker() },
                onHiddenApps = viewModel::openHiddenApps,
                onDesignPreview = viewModel::openDesignPreview,
                onSetDefaultHome = { viewModel.openHomeAppPicker() },
            )
        }

        PanelTransition(panel == Panel.HIDDEN_APPS) {
            HiddenAppsScreen(
                apps = allApps,
                hidden = settings.hiddenApps,
                iconLoader = viewModel.iconLoader,
                onBack = viewModel::closePanel,
                onToggle = viewModel::setAppHidden,
            )
        }

        PanelTransition(panel == Panel.DESIGN_PREVIEW) {
            DesignPreviewScreen(onBack = viewModel::closePanel)
        }
    }

    // Back closes whatever is open, then leaves edit mode, then does nothing.
    // The handler stays enabled throughout: see the class comment for why
    // falling through to the default handler is wrong on a home screen.
    BackHandler(enabled = true) {
        when {
            panelOpen -> viewModel.closePanel()
            viewModel.homeEditMode -> viewModel.setHomeEditing(false)
            else -> Unit // already home; there is nowhere to go back to
        }
    }
}

/**
 * Standard entrance for the full-screen panels: a short rise plus a fade, so
 * nothing ever appears or vanishes instantly.
 */
@Composable
private fun PanelTransition(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(Motion.panelSlide()) { it / 8 } + fadeIn(Motion.medium()),
        exit = fadeOut(Motion.exit()),
    ) {
        content()
    }
}
