package com.privacylauncher

import android.content.Intent
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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

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

    // Back closes whatever is open; on a bare home screen it does nothing.
    BackHandler(enabled = viewModel.drawerOpen || viewModel.settingsOpen) {
        when {
            viewModel.settingsOpen -> viewModel.closeSettings()
            viewModel.drawerOpen -> viewModel.closeDrawer()
        }
    }
}
