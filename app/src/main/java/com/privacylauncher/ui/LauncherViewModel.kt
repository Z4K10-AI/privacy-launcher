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
import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Holds all launcher state that must survive a configuration change
 * (rotation, dark-mode switch, font-size change).
 *
 * Extends AndroidViewModel so Compose's default `viewModel()` factory can
 * build it with no extra wiring.
 */
class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val launcherApp = application as PrivacyLauncherApp
    private val appRepository = launcherApp.appRepository
    private val settings = launcherApp.settingsRepository

    val iconLoader = launcherApp.iconLoader

    // --- app list ---------------------------------------------------------

    private val allApps = MutableStateFlow<List<AppEntry>>(emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    /** The app list after the search box has been applied. */
    val visibleApps: StateFlow<List<AppEntry>> =
        combine(allApps, _query) { apps, q ->
            if (q.isBlank()) apps else apps.filter { it.matches(q) }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    /** Total installed apps, regardless of the current search. */
    val totalAppCount: StateFlow<Int> = allApps
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // --- UI state ---------------------------------------------------------

    /** Whether the app drawer is showing. Compose state, so the UI recomposes. */
    var drawerOpen by mutableStateOf(false)
        private set

    /** Whether the settings sheet is showing. */
    var settingsOpen by mutableStateOf(false)
        private set

    /** Whether this app is currently the phone's home screen. */
    var isDefaultHome by mutableStateOf(false)
        private set

    // --- settings ---------------------------------------------------------

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    // --- package install/uninstall tracking -------------------------------

    /**
     * Refreshes the drawer when apps are installed, removed or updated.
     *
     * These are system broadcasts, so RECEIVER_NOT_EXPORTED is correct: no
     * other app should be able to poke this receiver.
     */
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
        viewModelScope.launch { allApps.value = appRepository.loadApps() }
    }

    fun refreshDefaultHome() {
        isDefaultHome = appRepository.isDefaultHome()
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun openDrawer() {
        _query.value = ""
        drawerOpen = true
    }

    fun closeDrawer() {
        drawerOpen = false
        _query.value = ""
    }

    fun openSettings() {
        settingsOpen = true
    }

    fun closeSettings() {
        settingsOpen = false
    }

    fun launch(entry: AppEntry) {
        if (appRepository.launch(entry)) {
            // Leave the drawer closed so returning home shows the home screen.
            closeDrawer()
        } else {
            // The app was uninstalled while the list was on screen.
            refreshApps()
        }
    }

    fun openAppInfo(entry: AppEntry) = appRepository.openAppInfo(entry.packageName)

    fun requestUninstall(entry: AppEntry) = appRepository.requestUninstall(entry.packageName)

    fun openHomeAppPicker() = appRepository.openHomeAppPicker()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }
}
