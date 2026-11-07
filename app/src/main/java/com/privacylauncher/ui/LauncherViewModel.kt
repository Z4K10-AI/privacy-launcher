package com.privacylauncher.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.privacylauncher.PrivacyLauncherApp
import com.privacylauncher.data.AccentColor
import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.FontChoice
import com.privacylauncher.data.HomeItem
import com.privacylauncher.data.IconSize
import com.privacylauncher.data.LauncherSettings
import com.privacylauncher.data.ThemeMode
import com.privacylauncher.system.LauncherAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which full-screen panel is showing, if any. */
enum class Panel { NONE, DRAWER, SETTINGS, HIDDEN_APPS, DESIGN_PREVIEW }

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val launcherApp = application as PrivacyLauncherApp
    private val appRepository = launcherApp.appRepository
    private val settingsRepository = launcherApp.settingsRepository

    val iconLoader = launcherApp.iconLoader

    // --- settings ---------------------------------------------------------

    val settings: StateFlow<LauncherSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, LauncherSettings())

    // --- app lists --------------------------------------------------------

    private val allApps = MutableStateFlow<List<AppEntry>>(emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    /** Everything installed, for the hide-apps screen (hidden ones included). */
    val allInstalledApps: StateFlow<List<AppEntry>> = allApps
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The drawer list: hidden apps removed, then the search applied. */
    val drawerApps: StateFlow<List<AppEntry>> =
        combine(allApps, settings, _query) { apps, s, q ->
            apps.asSequence()
                .filterNot { it.key in s.hiddenApps }
                .filter { it.matches(q) }
                .toList()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** How many apps the drawer would show with no search active. */
    val drawerTotal: StateFlow<Int> =
        combine(allApps, settings) { apps, s ->
            apps.count { it.key !in s.hiddenApps }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /**
     * The home screen layout, resolved to drawable tiles.
     *
     * Keys that no longer resolve are skipped here and pruned from storage by
     * [refreshApps], so an uninstalled app leaves no gap and no dead entry.
     */
    val homeTiles: StateFlow<List<HomeTile>> =
        combine(allApps, settings) { apps, s ->
            val byKey = apps.associateBy { it.key }
            s.homeItems.mapNotNull { item ->
                when (item) {
                    is HomeItem.App -> byKey[item.key]?.let { HomeTile.App(it) }
                    is HomeItem.Folder -> {
                        val entries = item.appKeys.mapNotNull { byKey[it] }
                        if (entries.isEmpty()) null
                        else HomeTile.Folder(item.id, item.name, entries)
                    }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The dock, resolved and capped. */
    val dockApps: StateFlow<List<AppEntry>> =
        combine(allApps, settings) { apps, s ->
            val byKey = apps.associateBy { it.key }
            s.dockApps.mapNotNull { byKey[it] }
                .take(LauncherSettings.MAX_DOCK_APPS)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The folder currently open as a sheet, if any. */
    var openFolderId by mutableStateOf<String?>(null)
        private set

    // --- UI state ---------------------------------------------------------

    var panel by mutableStateOf(Panel.NONE)
        private set

    /** Long-press on the home grid turns on reordering. */
    var homeEditMode by mutableStateOf(false)
        private set

    var isDefaultHome by mutableStateOf(false)
        private set

    /** Whether the optional gesture accessibility service is switched on. */
    var gestureServiceEnabled by mutableStateOf(false)
        private set

    /** Shown when a gesture is used that needs the service, but it is off. */
    var showGestureExplainer by mutableStateOf(false)
        private set

    val drawerOpen: Boolean get() = panel == Panel.DRAWER

    // --- package install/uninstall tracking -------------------------------

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            iconLoader.clear()
            refreshApps()
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            application,
            packageReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        refreshApps()
        refreshSystemState()
    }

    override fun onCleared() {
        // No super call: ViewModel.onCleared is defined to be empty.
        getApplication<Application>().unregisterReceiver(packageReceiver)
    }

    // --- actions ----------------------------------------------------------

    fun refreshApps() {
        viewModelScope.launch {
            val apps = appRepository.loadApps()
            allApps.value = apps
            // Drop home/hidden entries for apps that have been uninstalled.
            settingsRepository.pruneMissing(apps.mapTo(mutableSetOf()) { it.key })
        }
    }

    /**
     * Re-reads system state that can change while we are in the background:
     * the default home app, and whether the gesture service is enabled.
     */
    fun refreshSystemState() {
        isDefaultHome = appRepository.isDefaultHome()
        gestureServiceEnabled = LauncherAccessibilityService.isEnabled
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun openDrawer() {
        _query.value = ""
        homeEditMode = false
        panel = Panel.DRAWER
    }

    fun openSettings() {
        homeEditMode = false
        panel = Panel.SETTINGS
    }

    fun openHiddenApps() {
        panel = Panel.HIDDEN_APPS
    }

    fun openDesignPreview() {
        panel = Panel.DESIGN_PREVIEW
    }

    /** Back behaviour: the hide-apps screen returns to settings, not home. */
    fun closePanel() {
        panel = when (panel) {
            Panel.HIDDEN_APPS, Panel.DESIGN_PREVIEW -> Panel.SETTINGS
            else -> Panel.NONE
        }
        if (panel == Panel.NONE) _query.value = ""
    }

    /**
     * What the Back gesture should do, in priority order.
     *
     * Clearing a search before closing the drawer matters: typing three
     * letters, realising it is the wrong app and pressing back used to throw
     * away the whole drawer, so you had to open it and scroll from the top
     * again.
     */
    fun onBack() {
        when {
            openFolderId != null -> openFolderId = null
            panel == Panel.DRAWER && _query.value.isNotBlank() -> _query.value = ""
            panel != Panel.NONE -> closePanel()
            homeEditMode -> homeEditMode = false
        }
    }

    fun closeAllPanels() {
        panel = Panel.NONE
        homeEditMode = false
        openFolderId = null
        _query.value = ""
    }

    /**
     * Named `setHomeEditing`, not `setHomeEditMode`: the latter is the JVM
     * setter Kotlin already generates for the `homeEditMode` property, and
     * declaring both is a signature clash.
     */
    fun setHomeEditing(enabled: Boolean) {
        homeEditMode = enabled
    }

    fun launch(entry: AppEntry) {
        if (homeEditMode) return
        if (appRepository.launch(entry)) closeAllPanels() else refreshApps()
    }

    fun openAppInfo(entry: AppEntry) = appRepository.openAppInfo(entry.packageName)

    fun requestUninstall(entry: AppEntry) = appRepository.requestUninstall(entry.packageName)

    fun openHomeAppPicker() = appRepository.openHomeAppPicker()

    fun openWallpaperPicker() = appRepository.openWallpaperPicker()

    fun openClock() = appRepository.openClock()

    fun openAccessibilitySettings(): Boolean {
        showGestureExplainer = false
        return appRepository.openAccessibilitySettings()
    }

    // --- gestures that need the accessibility service ---------------------
    //
    // When the service is off these open the explainer instead of failing
    // silently. A gesture that does nothing with no feedback reads as a bug.

    fun onSwipeDown() {
        if (LauncherAccessibilityService.isEnabled) {
            LauncherAccessibilityService.openNotifications()
        } else {
            showGestureExplainer = true
        }
    }

    fun onDoubleTap() {
        if (!LauncherAccessibilityService.lockScreenSupported) return
        if (LauncherAccessibilityService.isEnabled) {
            LauncherAccessibilityService.lockScreen()
        } else {
            showGestureExplainer = true
        }
    }

    /** Pinching in on the home screen opens settings. */
    fun onPinchIn() = openSettings()

    fun dismissGestureExplainer() {
        showGestureExplainer = false
    }

    /** Opened from settings, rather than by a gesture that needs it. */
    fun showGestureExplainerDialog() {
        showGestureExplainer = true
    }

    // --- settings writes --------------------------------------------------

    fun setThemeMode(v: ThemeMode) = edit { settingsRepository.setThemeMode(v) }

    fun setAccent(v: AccentColor) = edit { settingsRepository.setAccent(v) }

    fun setFont(v: FontChoice) = edit { settingsRepository.setFont(v) }

    fun setDynamicColor(v: Boolean) = edit { settingsRepository.setDynamicColor(v) }

    fun setIconSize(v: IconSize) = edit { settingsRepository.setIconSize(v) }

    fun setDrawerColumns(v: Int) = edit { settingsRepository.setDrawerColumns(v) }

    fun setHomeColumns(v: Int) = edit { settingsRepository.setHomeColumns(v) }

    fun setAppHidden(entry: AppEntry, hidden: Boolean) = edit {
        settingsRepository.setAppHidden(entry.key, hidden)
        // A hidden app should not linger on the home screen or in the dock.
        if (hidden) {
            settingsRepository.removeAppFromHome(entry.key)
            settingsRepository.removeFromDock(entry.key)
        }
    }

    fun addToHome(entry: AppEntry) = edit { settingsRepository.addToHome(entry.key) }

    fun removeFromHome(entry: AppEntry) = edit {
        settingsRepository.removeAppFromHome(entry.key)
    }

    fun removeHomeTile(tile: HomeTile) = edit {
        settingsRepository.removeHomeItem(tile.id)
    }

    fun moveHomeItem(from: Int, to: Int) = edit {
        settingsRepository.moveHomeItem(from, to)
    }

    /** Dropping one icon onto another: makes a folder, or adds to one. */
    fun combineHomeItems(from: Int, onto: Int) = edit {
        settingsRepository.combineHomeItems(from, onto)
    }

    fun renameFolder(folderId: String, name: String) = edit {
        settingsRepository.renameFolder(folderId, name)
    }

    fun removeFromFolder(folderId: String, entry: AppEntry) = edit {
        settingsRepository.removeFromFolder(folderId, entry.key)
    }

    fun openFolder(id: String) {
        openFolderId = id
    }

    fun closeFolder() {
        openFolderId = null
    }

    fun addToDock(entry: AppEntry) = edit { settingsRepository.addToDock(entry.key) }

    fun removeFromDock(entry: AppEntry) = edit {
        settingsRepository.removeFromDock(entry.key)
    }

    fun setShowGlance(v: Boolean) = edit { settingsRepository.setShowGlance(v) }

    fun isOnHome(entry: AppEntry): Boolean =
        settings.value.homeItems.any {
            when (it) {
                is HomeItem.App -> it.key == entry.key
                is HomeItem.Folder -> entry.key in it.appKeys
            }
        }

    fun isInDock(entry: AppEntry): Boolean = entry.key in settings.value.dockApps

    fun dockIsFull(): Boolean =
        settings.value.dockApps.size >= LauncherSettings.MAX_DOCK_APPS

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
