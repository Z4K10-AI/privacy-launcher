package com.privacylauncher

import android.content.Intent
import com.privacylauncher.ui.theme.resolveIsDark
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import androidx.activity.SystemBarStyle
import android.os.Build
import android.graphics.Color
import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.privacylauncher.ui.AppDrawer
import com.privacylauncher.ui.HomeScreen
import com.privacylauncher.ui.LauncherViewModel
import com.privacylauncher.ui.SettingsDialog
import com.privacylauncher.ui.theme.PrivacyLauncherTheme

/**
 * The launcher's one and only Activity.
 *
 * Because the manifest declares the HOME category, Android can run this as the
 * home screen. A few launcher-specific behaviours live here rather than in
 * Compose:
 *
 *  - [onNewIntent]: pressing the hardware/gesture Home button while already on
 *    the home screen re-delivers the HOME intent to this same Activity. A
 *    launcher is expected to respond by closing the drawer.
 *  - Back press on the home screen does nothing. Exiting the home screen is
 *    not a thing, and letting back finish() the Activity would leave the user
 *    on a blank screen.
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
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

            PrivacyLauncherTheme(themeMode = themeMode) {
                LauncherRoot(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while already home: go back to a clean home screen.
        viewModel.closeDrawer()
    }

    override fun onResume() {
        super.onResume()
        // The user may have changed the default home app, or installed or
        // removed something, while we were in the background.
        viewModel.refreshDefaultHome()
    }
}

@Composable
private fun LauncherRoot(viewModel: LauncherViewModel) {
    val apps by viewModel.visibleApps.collectAsStateWithLifecycle()
    val totalAppCount by viewModel.totalAppCount.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    // Status/nav bar icons must contrast with whatever is drawn beneath them.
    //
    //  - Drawer or settings open: that surface follows the launcher theme, so
    //    light theme needs dark icons and dark theme needs light ones.
    //  - Bare home screen: we are over the wallpaper, where the clock is drawn
    //    white with a shadow, so light icons always match.
    val view = LocalView.current
    val isDark = viewModel.themeMode.collectAsStateWithLifecycle().value.resolveIsDark()
    val overThemedSurface = viewModel.drawerOpen || viewModel.settingsOpen
    val lightBackgroundBehindBars = overThemedSurface && !isDark
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightBackgroundBehindBars
            isAppearanceLightNavigationBars = lightBackgroundBehindBars
        }
    }

    Box(Modifier.fillMaxSize()) {
        HomeScreen(
            // With the drawer up, the home screen is covered. Its swipe
            // detector has to be switched off or stray drags in the drawer's
            // empty areas would fall through to it.
            gesturesEnabled = !viewModel.drawerOpen && !viewModel.settingsOpen,
            onOpenDrawer = viewModel::openDrawer,
            onOpenSettings = viewModel::openSettings,
        )

        AnimatedVisibility(
            visible = viewModel.drawerOpen,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            AppDrawer(
                apps = apps,
                totalAppCount = totalAppCount,
                query = query,
                iconLoader = viewModel.iconLoader,
                onQueryChange = viewModel::onQueryChange,
                onLaunch = viewModel::launch,
                onClose = viewModel::closeDrawer,
                onAppInfo = { viewModel.openAppInfo(it) },
                onUninstall = { viewModel.requestUninstall(it) },
            )
        }

        if (viewModel.settingsOpen) {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            SettingsDialog(
                themeMode = themeMode,
                isDefaultHome = viewModel.isDefaultHome,
                onThemeModeChange = viewModel::setThemeMode,
                onSetDefaultHome = {
                    viewModel.openHomeAppPicker()
                    viewModel.closeSettings()
                },
                onDismiss = viewModel::closeSettings,
            )
        }
    }

    // Back closes whatever is open. On a bare home screen it must do NOTHING,
    // and crucially the handler stays enabled so the press is swallowed here.
    // If it were disabled, back would fall through to the Activity's default
    // handler, which calls finish() -- and finishing the home Activity makes
    // the system immediately relaunch it, so the user sees the home screen
    // flicker for no reason.
    BackHandler(enabled = true) {
        when {
            viewModel.settingsOpen -> viewModel.closeSettings()
            viewModel.drawerOpen -> viewModel.closeDrawer()
            else -> Unit // already home; there is nowhere to go back to
        }
    }
}
