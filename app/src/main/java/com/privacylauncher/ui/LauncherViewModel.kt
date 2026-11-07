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
import com.privacylauncher.data.IconSize
import com.privacylauncher.data.LauncherSettings
import com.privacylauncher.data.ThemeMode
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
     * The home screen's apps, in the user's order.
     *
     * Keys that no longer resolve are skipped here and pruned from storage by
     * [refreshApps], so an uninstalled app leaves no gap and no dead entry.
     */
    val homeApps: StateFlow<List<AppEntry>> =
        combine(allApps, settings) { apps, s ->
            val byKey = apps.associateBy { it.key }
            s.homeApps.mapNotNull { byKey[it] }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // --- UI state ---------------------------------------------------------

    var panel by mutableStateOf(Panel.NONE)
        private set

    /** Long-press on the home grid turns on reordering. */
    var homeEditMode by mutableStateOf(false)
        private set

    var isDefaultHome by mutableStateOf(false)
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
        refreshDefaultHome()
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

    fun refreshDefaultHome() {
        isDefaultHome = appRepository.isDefaultHome()
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
            panel == Panel.DRAWER && _query.value.isNotBlank() -> _query.value = ""
            panel != Panel.NONE -> closePanel()
            homeEditMode -> homeEditMode = false
        }
    }

    fun closeAllPanels() {
        panel = Panel.NONE
        homeEditMode = false
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
        // A hidden app should not linger on the home screen.
        if (hidden) settingsRepository.removeFromHome(entry.key)
    }

    fun addToHome(entry: AppEntry) = edit { settingsRepository.addToHome(entry.key) }

    fun removeFromHome(entry: AppEntry) = edit {
        settingsRepository.removeFromHome(entry.key)
    }

    fun moveHomeApp(from: Int, to: Int) = edit {
        settingsRepository.moveHomeApp(from, to)
    }

    fun isOnHome(entry: AppEntry): Boolean = entry.key in settings.value.homeApps

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
