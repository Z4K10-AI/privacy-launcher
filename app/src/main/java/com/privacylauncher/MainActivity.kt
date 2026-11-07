package com.privacylauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.privacylauncher.data.ContactRepository
import com.privacylauncher.data.FocusState
import com.privacylauncher.data.LauncherSettings
import com.privacylauncher.designsystem.LauncherTheme
import com.privacylauncher.designsystem.Motion
import com.privacylauncher.designsystem.adaptiveColumns
import com.privacylauncher.designsystem.windowSizeDp
import com.privacylauncher.designsystem.blurBehind
import com.privacylauncher.designsystem.resolveIsDark
import com.privacylauncher.system.rememberDeviceUnlock
import com.privacylauncher.ui.AppDrawer
import com.privacylauncher.ui.FolderSheet
import com.privacylauncher.ui.rememberSheetSwipe
import com.privacylauncher.ui.HomeScreen
import com.privacylauncher.ui.HomeTile
import com.privacylauncher.ui.MinimalHome
import com.privacylauncher.ui.LauncherViewModel
import com.privacylauncher.ui.Panel
import com.privacylauncher.ui.components.rememberGlance
import com.privacylauncher.ui.onboarding.OnboardingScreen
import com.privacylauncher.ui.settings.DesignPreviewScreen
import com.privacylauncher.ui.settings.GestureExplainerDialog
import com.privacylauncher.ui.focus.FocusAppsScreen
import com.privacylauncher.ui.focus.FocusScreen
import com.privacylauncher.ui.privacy.PrivacyDashboardScreen
import com.privacylauncher.ui.quick.QuickPanel
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
            val focus by viewModel.focus.collectAsStateWithLifecycle()
            val onboarded by viewModel.onboarded.collectAsStateWithLifecycle()

            LauncherTheme(settings = settings, calm = focus.active) {
                when (onboarded) {
                    // Still reading the stored settings. Drawing nothing
                    // leaves the wallpaper showing for a frame or two, which
                    // is exactly what a launcher should look like while it
                    // starts -- and far better than flashing the welcome
                    // screen at someone who finished it months ago.
                    null -> Unit
                    false -> OnboardingScreen(
                        settings = settings,
                        isDefaultHome = viewModel.isDefaultHome,
                        onThemeMode = viewModel::setThemeMode,
                        onAccent = viewModel::setAccent,
                        onChooseHomeApp = { viewModel.openHomeAppPicker() },
                        onFinish = viewModel::finishOnboarding,
                    )
                    else -> LauncherRoot(viewModel, settings, focus)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.closeAllPanels()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSystemState()
    }
}

@Composable
private fun LauncherRoot(
    viewModel: LauncherViewModel,
    settings: LauncherSettings,
    focus: FocusState,
) {
    val drawerList by viewModel.drawerList.collectAsStateWithLifecycle()
    val extraRows by viewModel.extraRows.collectAsStateWithLifecycle()
    val privacyReport by viewModel.privacyReport.collectAsStateWithLifecycle()
    val quickState by viewModel.quickState.collectAsStateWithLifecycle()
    val homeTiles by viewModel.homeTiles.collectAsStateWithLifecycle()
    val dock by viewModel.dockApps.collectAsStateWithLifecycle()
    val allApps by viewModel.allInstalledApps.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    val iconSize = settings.iconSize.dp.dp
    val panel = viewModel.panel
    val panelOpen = panel != Panel.NONE
    val drawerOpen = panel == Panel.DRAWER

    // The drawer follows the finger, so its position is a fraction rather than
    // an on/off animation. The view model still owns "is the drawer open"; this
    // object just chases that, and the drag updates it directly in between.
    val drawerSwipe = rememberSheetSwipe()
    LaunchedEffect(drawerOpen) { drawerSwipe.settle(if (drawerOpen) 1f else 0f) }

    // The only permission request this app ever makes. It is reached from the
    // row in the drawer and the switch in Settings, and from nowhere else.
    val activity = LocalActivity.current
    val contactsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // After a refusal, shouldShowRequestPermissionRationale is still true
        // while Android is willing to ask again, and false once it has given
        // up. That distinction is what decides whether the drawer keeps
        // offering the switch or points at App info instead.
        val willAskAgain = activity?.shouldShowRequestPermissionRationale(
            ContactRepository.PERMISSION,
        ) ?: true
        viewModel.onContactsPermissionResult(granted, willAskAgain)
    }
    val askForContacts = {
        if (viewModel.contactsPermissionGranted()) {
            // Already granted from an earlier session; no need to ask again.
            viewModel.onContactsPermissionResult(true)
        } else {
            contactsPermission.launch(ContactRepository.PERMISSION)
        }
    }

    // Hidden apps can be put behind the phone's own lock. Resolved here
    // because the result launcher has to be registered in composition.
    val deviceUnlock = rememberDeviceUnlock()
    val unlockTitle = stringResource(R.string.unlock_title)
    val unlockDescription = stringResource(R.string.unlock_description)
    val openHiddenApps = {
        if (settings.lockHiddenApps) {
            deviceUnlock.confirm(unlockTitle, unlockDescription) {
                viewModel.openHiddenApps()
            }
        } else {
            viewModel.openHiddenApps()
        }
    }

    // derivedStateOf, not a plain read: this only invalidates when the boolean
    // flips, so a drag does not recompose the whole tree 60 times a second.
    val drawerVisible by remember(drawerSwipe) {
        derivedStateOf { drawerSwipe.progress.value > 0.001f }
    }

    // The quick controls panel, on the same machinery but horizontal.
    val quickOpen = panel == Panel.QUICK
    val quickSwipe = rememberSheetSwipe()
    LaunchedEffect(quickOpen) { quickSwipe.settle(if (quickOpen) 1f else 0f) }
    val quickVisible by remember(quickSwipe) {
        derivedStateOf { quickSwipe.progress.value > 0.001f }
    }

    // Set here, not inside the `if` that composes the panel. The drag starts
    // while the panel does not exist yet, and with the default extent of one
    // pixel the first pixel of movement would slam it fully open.
    val quickPanelWidth = minOf(QUICK_PANEL_MAX_WIDTH, windowSizeDp().width * 0.92f)
    val density = LocalDensity.current
    LaunchedEffect(quickPanelWidth, density) {
        quickSwipe.extentPx = with(density) { quickPanelWidth.toPx() }
    }
    val glance = rememberGlance(enabled = settings.showGlance)
    val openFolder = viewModel.openFolderId?.let { id ->
        homeTiles.filterIsInstance<HomeTile.Folder>().firstOrNull { it.id == id }
    }

    // Fade the home screen out underneath an open panel.
    //
    // There is no wallpaper blur here on purpose: see Surfaces.panelSurface
    // for why Window.setBackgroundBlurRadius cannot blur a launcher's own
    // wallpaper, and why reading the wallpaper bitmap instead would cost a
    // storage permission this app does not ask for.
    //
    // Not `by`: the value is read inside graphicsLayer blocks below, where a
    // change re-runs the layer without recomposing. The drawer is excluded
    // because its own drag fraction drives the fade instead of a fixed tween.
    val homeAlpha = animateFloatAsState(
        // The quick panel is excluded along with the drawer, but for the
        // opposite reason: it is a side sheet with the home screen blurred
        // behind it, so fading that to nothing would leave nothing to blur.
        targetValue = if (panelOpen && !drawerOpen && !quickOpen) 0f else 1f,
        animationSpec = Motion.medium(),
        label = "homeAlpha",
    )

    // Focus mode's calm tint over the wallpaper.
    //
    // The interface itself is drained of colour by the theme (see
    // LauncherTheme's `calm`), but the wallpaper is not ours to change: the
    // only way to desaturate it would be to read the wallpaper bitmap, which
    // costs a permission this app does not ask for. A veil over it is the
    // honest version, and it is one rectangle, so unlike the colour scheme it
    // is cheap enough to animate.
    val calmVeil = animateFloatAsState(
        targetValue = if (focus.active) CALM_VEIL_ALPHA else 0f,
        animationSpec = Motion.long(),
        label = "calmVeil",
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

    Box(
        Modifier
            .fillMaxSize()
            // The drag works in pixels and the sheet's position is a fraction,
            // so the swipe state needs to know how tall the window is.
            .onSizeChanged { drawerSwipe.extentPx = it.height.toFloat().coerceAtLeast(1f) },
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    alpha = homeAlpha.value * (1f - drawerSwipe.progress.value)
                }
                // Real frosted glass: these are our own composables, so
                // RenderEffect can blur them. Window blur cannot do this for
                // the wallpaper -- see Surfaces.panelSurface for why.
                .blurBehind(active = openFolder != null || quickVisible)
                // Behind the home content, in front of the wallpaper. Read
                // inside the draw lambda, so the fade redraws without
                // recomposing the home screen.
                .drawBehind {
                    if (calmVeil.value > 0.001f) {
                        drawRect(color = CALM_VEIL, alpha = calmVeil.value)
                    }
                },
        ) {
            if (settings.minimalMode) {
                MinimalHome(
                    tiles = homeTiles,
                    dock = dock,
                    glance = glance,
                    focusActive = focus.active,
                    // No edit mode here, so the only reasons to stop
                    // listening are a panel or an open folder.
                    gesturesEnabled = !panelOpen && openFolder == null,
                    onLaunch = viewModel::launch,
                    onOpenDrawer = viewModel::openDrawer,
                    onOpenSettings = viewModel::openSettings,
                    onOpenClock = { viewModel.openClock() },
                    onOpenFocus = viewModel::openFocusScreen,
                    onDrawerDrag = drawerSwipe::dragFromClosed,
                    onDrawerRelease = { velocity ->
                        if (drawerSwipe.shouldOpen(velocity, wasOpen = false)) {
                            viewModel.openDrawer()
                        } else {
                            drawerSwipe.settle(0f)
                        }
                    },
                    onQuickDragStart = viewModel::refreshQuickControls,
                    onQuickDrag = quickSwipe::dragFromClosed,
                    onQuickRelease = { velocity ->
                        if (quickSwipe.shouldOpen(velocity, wasOpen = false)) {
                            viewModel.openQuickPanel()
                        } else {
                            quickSwipe.settle(0f)
                        }
                    },
                    onSwipeDown = viewModel::onSwipeDown,
                    onDoubleTap = viewModel::onDoubleTap,
                    onPinchIn = viewModel::onPinchIn,
                )
            } else {
                HomeScreen(
                    tiles = homeTiles,
                    dock = dock,
                    glance = glance,
                    columns = adaptiveColumns(settings.homeColumns),
                    iconSize = iconSize,
                    iconLoader = viewModel.iconLoader,
                    editMode = viewModel.homeEditMode,
                    // With a panel up the home screen is covered, and while
                    // reordering a vertical drag belongs to the grid. Either way
                    // its swipe detector must be off or stray drags reach it.
                    gesturesEnabled = !panelOpen &&
                        !viewModel.homeEditMode &&
                        openFolder == null,
                    onLaunch = viewModel::launch,
                    onOpenFolder = viewModel::openFolder,
                    onEnterEditMode = { viewModel.setHomeEditing(true) },
                    onExitEditMode = { viewModel.setHomeEditing(false) },
                    onRemoveTile = viewModel::removeHomeTile,
                    onMove = viewModel::moveHomeItem,
                    onCombine = viewModel::combineHomeItems,
                    onOpenDrawer = viewModel::openDrawer,
                    onOpenSettings = viewModel::openSettings,
                    onOpenClock = { viewModel.openClock() },
                    onDrawerDrag = drawerSwipe::dragFromClosed,
                    onDrawerRelease = { velocity ->
                        if (drawerSwipe.shouldOpen(velocity, wasOpen = false)) {
                            // Setting the panel is what makes it open as far as
                            // back, search and the rest of the app are concerned;
                            // the LaunchedEffect above springs the sheet up.
                            viewModel.openDrawer()
                        } else {
                            drawerSwipe.settle(0f)
                        }
                    },
                    onQuickDragStart = viewModel::refreshQuickControls,
                    onQuickDrag = quickSwipe::dragFromClosed,
                    onQuickRelease = { velocity ->
                        if (quickSwipe.shouldOpen(velocity, wasOpen = false)) {
                            viewModel.openQuickPanel()
                        } else {
                            quickSwipe.settle(0f)
                        }
                    },
                    onSwipeDown = viewModel::onSwipeDown,
                    onDoubleTap = viewModel::onDoubleTap,
                    onPinchIn = viewModel::onPinchIn,
                )
            }
        }

        if (openFolder != null) {
            FolderSheet(
                name = openFolder.name,
                entries = openFolder.entries,
                iconSize = iconSize,
                iconLoader = viewModel.iconLoader,
                editMode = viewModel.homeEditMode,
                onLaunch = viewModel::launch,
                onRename = { viewModel.renameFolder(openFolder.id, it) },
                onRemoveFromFolder = { viewModel.removeFromFolder(openFolder.id, it) },
                onDismiss = viewModel::closeFolder,
            )
        }

        // Composed whenever any part of it is on screen, and positioned by a
        // graphicsLayer rather than an animation, so it tracks the finger.
        // No alpha here on purpose: a translucent full-screen layer forces an
        // offscreen buffer every frame, which is exactly the cost a 4 GB phone
        // cannot absorb mid-drag. The sheet is opaque and simply slides.
        if (drawerOpen || drawerVisible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = (1f - drawerSwipe.progress.value) * size.height
                    },
            ) {
                AppDrawer(
                    list = drawerList,
                    extraRows = extraRows,
                    query = query,
                    columns = adaptiveColumns(settings.drawerColumns),
                    iconSize = iconSize,
                    iconLoader = viewModel.iconLoader,
                    isOnHome = viewModel::isOnHome,
                    isInDock = viewModel::isInDock,
                    dockFull = viewModel.dockIsFull(),
                    isOpen = drawerOpen,
                    autoKeyboard = settings.autoKeyboard,
                    onHandleDrag = drawerSwipe::dragFromOpen,
                    onHandleRelease = { velocity ->
                        if (drawerSwipe.shouldOpen(velocity, wasOpen = true)) {
                            drawerSwipe.settle(1f)
                        } else {
                            viewModel.closePanel()
                        }
                    },
                    onQueryChange = viewModel::onQueryChange,
                    onLaunch = viewModel::launch,
                    onClose = viewModel::closePanel,
                    onAppInfo = { viewModel.openAppInfo(it) },
                    onUninstall = { viewModel.requestUninstall(it) },
                    onAddToHome = viewModel::addToHome,
                    onRemoveFromHome = viewModel::removeFromHome,
                    onAddToDock = viewModel::addToDock,
                    onRemoveFromDock = viewModel::removeFromDock,
                    onHide = { viewModel.setAppHidden(it, true) },
                    onOpenSettingsScreen = viewModel::openSettingsScreen,
                    onOpenContact = viewModel::openContact,
                    onEnableContacts = askForContacts,
                    onOwnAppInfo = { viewModel.openOwnAppInfo() },
                    onEndFocus = viewModel::endFocus,
                    onOpenFocus = viewModel::openFocusScreen,
                )
            }
        }

        PanelTransition(panel == Panel.SETTINGS) {
            SettingsScreen(
                settings = settings,
                isDefaultHome = viewModel.isDefaultHome,
                onBack = viewModel::closePanel,
                onThemeMode = viewModel::setThemeMode,
                onAccent = viewModel::setAccent,
                onDynamicColor = viewModel::setDynamicColor,
                onShowGlance = viewModel::setShowGlance,
                onAutoKeyboard = viewModel::setAutoKeyboard,
                onShowMostUsed = viewModel::setShowMostUsed,
                onClearUsage = viewModel::clearUsageHistory,
                onSearchContacts = { wanted ->
                    if (wanted) askForContacts() else viewModel.disableContactSearch()
                },
                onAppInfo = { viewModel.openOwnAppInfo() },
                onFont = viewModel::setFont,
                onIconSize = viewModel::setIconSize,
                onDrawerColumns = viewModel::setDrawerColumns,
                onHomeColumns = viewModel::setHomeColumns,
                onWallpaper = { viewModel.openWallpaperPicker() },
                onHiddenApps = openHiddenApps,
                deviceSecure = deviceUnlock.isSecure,
                onLockHiddenApps = viewModel::setLockHiddenApps,
                gestureServiceEnabled = viewModel.gestureServiceEnabled,
                onGestureService = { viewModel.showGestureExplainerDialog() },
                onDesignPreview = viewModel::openDesignPreview,
                onPrivacyDashboard = viewModel::openPrivacyDashboard,
                onQuickPanel = viewModel::openQuickPanel,
                focusActive = focus.active,
                onFocus = viewModel::openFocusScreen,
                onMinimalMode = viewModel::setMinimalMode,
                onSetDefaultHome = { viewModel.openHomeAppPicker() },
                // A handful of real apps for the live grid sample. Taken
                // from the list the drawer already loaded, and drawn with
                // the shared icon cache, so the preview costs no extra work.
                previewEntries = remember(allApps) { allApps.take(PREVIEW_APPS) },
                iconLoader = viewModel.iconLoader,
                appVersion = rememberAppVersion(),
                backupFileName = remember { viewModel.suggestedBackupFileName() },
                onBackupTo = viewModel::backupTo,
                onRestoreFrom = viewModel::restoreFrom,
                backupResult = viewModel.backupResult,
                onDismissBackupResult = viewModel::dismissBackupResult,
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

        // The quick controls side sheet. Positioned by a graphicsLayer so it
        // tracks the finger, like the drawer; see SheetSwipe.
        if (quickOpen || quickVisible) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = quickSwipe.progress.value * 0.4f }
                        .background(ComposeColor.Black)
                        // pointerInput rather than clickable: a scrim should
                        // swallow the tap, not ripple at it.
                        .pointerInput(Unit) {
                            detectTapGestures { viewModel.closePanel() }
                        },
                )
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .width(quickPanelWidth)
                        .fillMaxHeight()
                        .graphicsLayer {
                            translationX = (1f - quickSwipe.progress.value) * size.width
                        },
                ) {
                    QuickPanel(
                        state = quickState,
                        gestureServiceEnabled = viewModel.gestureServiceEnabled,
                        onObserve = viewModel::observeQuickControls,
                        onRefresh = viewModel::refreshQuickControls,
                        onTile = viewModel::onQuickTile,
                        onBrightness = viewModel::setBrightness,
                        onBrightnessFinished = viewModel::commitBrightness,
                        onGrantWriteSettings = viewModel::requestWriteSettings,
                    )
                }
            }
        }

        PanelTransition(panel == Panel.PRIVACY) {
            PrivacyDashboardScreen(
                report = privacyReport,
                iconLoader = viewModel.iconLoader,
                onBack = viewModel::closePanel,
                onOpenPermissions = viewModel::openAppPermissions,
                onOpenSystemPrivacy = viewModel::openSystemPrivacy,
            )
        }

        PanelTransition(panel == Panel.FOCUS) {
            FocusScreen(
                settings = settings,
                focus = focus,
                allowedCount = settings.focusAllowed.size,
                onBack = viewModel::closePanel,
                onStart = viewModel::startFocus,
                onEnd = viewModel::endFocus,
                onPickApps = viewModel::openFocusApps,
                onScheduleEnabled = viewModel::setFocusScheduleEnabled,
                onNudgeWindow = viewModel::nudgeFocusWindow,
            )
        }

        PanelTransition(panel == Panel.FOCUS_APPS) {
            FocusAppsScreen(
                apps = allApps,
                allowed = settings.focusAllowed,
                iconLoader = viewModel.iconLoader,
                onBack = viewModel::closePanel,
                onToggle = viewModel::setFocusAllowed,
            )
        }

        PanelTransition(panel == Panel.DESIGN_PREVIEW) {
            DesignPreviewScreen(onBack = viewModel::closePanel)
        }

        if (viewModel.showGestureExplainer) {
            GestureExplainerDialog(
                onDismiss = viewModel::dismissGestureExplainer,
                onOpenSettings = { viewModel.openAccessibilitySettings() },
            )
        }
    }

    // Back closes whatever is open, then leaves edit mode, then does nothing.
    // The handler stays enabled throughout: see the class comment for why
    // falling through to the default handler is wrong on a home screen.
    // Nothing to go back to on a bare home screen, so the press is swallowed
    // here rather than reaching the Activity's default handler. See the class
    // comment for why finishing a home Activity is wrong.
    BackHandler(enabled = true) { viewModel.onBack() }
}

/** Wider than this the panel stops reading as a sheet and starts as a screen. */
private val QUICK_PANEL_MAX_WIDTH = 380.dp

/**
 * The wallpaper veil in focus mode.
 *
 * Half-strength, not opaque: the point is to quieten the wallpaper, not to
 * replace it with a black rectangle. The home screen's own text is white with
 * a shadow, so a darker wallpaper makes it easier to read, not harder.
 */
private const val CALM_VEIL_ALPHA = 0.5f

/** The same near-black as the dark theme's background, rather than pure black. */
private val CALM_VEIL = ComposeColor(0xFF0E0E11)

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

/** How many apps the live grid sample in Settings draws from. */
private const val PREVIEW_APPS = 6

/**
 * The app's own version name, for the About section.
 *
 * Read from the package manager rather than from `BuildConfig`, because
 * BuildConfig generation is switched off in this project to keep the build
 * output minimal. Falls back to an empty string rather than throwing: a
 * missing version string is a cosmetic problem, and the launcher failing to
 * draw its settings screen is not.
 */
@Composable
private fun rememberAppVersion(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
}
