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
import com.privacylauncher.data.ContactMatch
import com.privacylauncher.data.ContactRepository
import com.privacylauncher.data.FontChoice
import com.privacylauncher.data.Fuzzy
import com.privacylauncher.data.SettingsTarget
import com.privacylauncher.data.SettingsTargets
import com.privacylauncher.data.HomeItem
import com.privacylauncher.data.IconSize
import com.privacylauncher.data.LauncherSettings
import com.privacylauncher.data.PrivacyReport
import com.privacylauncher.data.ThemeMode
import com.privacylauncher.system.LauncherAccessibilityService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which full-screen panel is showing, if any. */
enum class Panel { NONE, DRAWER, SETTINGS, HIDDEN_APPS, DESIGN_PREVIEW, PRIVACY }

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val launcherApp = application as PrivacyLauncherApp
    private val appRepository = launcherApp.appRepository
    private val settingsRepository = launcherApp.settingsRepository
    private val contactRepository = launcherApp.contactRepository
    private val privacyScanner = launcherApp.privacyScanner

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

    /**
     * On-device launch counts, behind the "most used" row.
     *
     * Read as its own flow rather than as part of [settings]: it changes on
     * every app launch, and folding it in would make the home grid and the
     * dock recompute each time too.
     */
    private val launchCounts = settingsRepository.launchCounts

    /** Everything the drawer draws: rows, section letters and the counts. */
    val drawerList: StateFlow<DrawerList> =
        combine(allApps, settings, _query, launchCounts) { apps, s, q, counts ->
            buildDrawerList(apps, s, q, counts)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, DrawerList())

    /**
     * Turns the installed apps into the flat row list the grid draws.
     *
     * Searching and browsing produce genuinely different lists, so they are
     * built separately rather than one filtering the other. A search is
     * ordered by how well each app matches and has no headings; browsing is
     * alphabetical with a heading per letter, optionally preceded by the
     * most-used row.
     */
    private fun buildDrawerList(
        apps: List<AppEntry>,
        s: LauncherSettings,
        rawQuery: String,
        counts: Map<String, Int>,
    ): DrawerList {
        val visible = apps.filterNot { it.key in s.hiddenApps }
        val total = visible.size
        val query = rawQuery.trim()

        if (query.isNotEmpty()) {
            val scored = visible
                .map { it to it.score(query) }
                .filter { it.second != Fuzzy.NO_MATCH }
                .sortedWith(
                    compareByDescending<Pair<AppEntry, Int>> { it.second }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.first.label },
                )
            return DrawerList(
                rows = scored.map { DrawerRow.App(it.first, SECTION_SEARCH) },
                letters = emptyList(),
                matchCount = scored.size,
                total = total,
                topMatch = scored.firstOrNull()?.first,
                searching = true,
            )
        }

        val rows = mutableListOf<DrawerRow>()
        val letters = mutableListOf<LetterJump>()

        if (s.showMostUsed) {
            val top = visible
                .filter { counts.containsKey(it.key) }
                .sortedByDescending { counts[it.key] ?: 0 }
                .take(LauncherSettings.MOST_USED_COUNT)
            // One icon under a heading is not a row, it is a mistake.
            if (top.size >= 2) {
                rows += DrawerRow.MostUsedHeader
                top.forEach { rows += DrawerRow.App(it, SECTION_MOST_USED) }
            }
        }

        if (visible.size < MIN_APPS_FOR_SECTIONS) {
            visible.forEach { rows += DrawerRow.App(it, SECTION_PLAIN) }
        } else {
            var section: String? = null
            for (entry in visible) {
                if (entry.sectionLetter != section) {
                    section = entry.sectionLetter
                    letters += LetterJump(section, rows.size)
                    rows += DrawerRow.Header(section)
                }
                rows += DrawerRow.App(entry, section)
            }
        }

        return DrawerList(
            rows = rows,
            letters = letters,
            matchCount = total,
            total = total,
            topMatch = visible.firstOrNull(),
            searching = false,
        )
    }

    /**
     * The privacy dashboard's picture of the phone.
     *
     * Not computed until the screen is opened. Walking every visible
     * package's permission list is real work, and it would be charged to
     * every cold start of the launcher for a screen most people open rarely.
     */
    private val _privacyReport = MutableStateFlow(PrivacyReport())
    val privacyReport: StateFlow<PrivacyReport> = _privacyReport

    private var privacyScanJob: Job? = null

    /** System settings screens this device actually has. Resolved once. */
    private val settingsTargets = MutableStateFlow<List<SettingsTarget>>(emptyList())

    private val contactMatches = MutableStateFlow<List<ContactMatch>>(emptyList())

    /**
     * Set when Android will no longer show the permission dialog.
     *
     * Once the system has decided to stop asking, it returns "denied"
     * immediately and silently, so a prompt row that still looked tappable
     * would do nothing at all. The row swaps to an explanation and a route
     * to App info instead. One refusal is not this: the caller works it out
     * from shouldShowRequestPermissionRationale after the dialog closes.
     */
    private val contactsBlocked = MutableStateFlow(false)

    /**
     * Non-app search results: settings screens, contacts, and the row that
     * offers to turn contact search on.
     *
     * Kept apart from [drawerList] rather than folded into it because they
     * come from somewhere else entirely -- a static table and a content
     * provider -- and because the contact query is asynchronous while the
     * app list is not. The drawer concatenates the two.
     */
    val extraRows: StateFlow<List<DrawerRow>> =
        combine(
            _query,
            settings,
            settingsTargets,
            contactMatches,
            contactsBlocked,
        ) { q, s, targets, contacts, blocked ->
            buildExtraRows(q.trim(), s, targets, contacts, blocked)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun buildExtraRows(
        query: String,
        s: LauncherSettings,
        targets: List<SettingsTarget>,
        contacts: List<ContactMatch>,
        blocked: Boolean,
    ): List<DrawerRow> {
        if (query.isEmpty()) return emptyList()
        val rows = mutableListOf<DrawerRow>()

        val context = getApplication<Application>()
        val matchedSettings = targets
            .map { it to Fuzzy.score(context.getString(it.labelRes), query) }
            .filter { it.second != Fuzzy.NO_MATCH }
            .sortedByDescending { it.second }
            .take(MAX_SETTINGS_RESULTS)
        if (matchedSettings.isNotEmpty()) {
            rows += DrawerRow.SettingsSearchHeader
            matchedSettings.forEach { rows += DrawerRow.SettingsScreen(it.first) }
        }

        when {
            contacts.isNotEmpty() -> {
                rows += DrawerRow.ContactsHeader
                contacts.forEach { rows += DrawerRow.Contact(it) }
            }
            // Offered, never assumed. Nothing is read, and no permission is
            // requested, until this row is tapped. It carries the Contacts
            // heading too, so it does not read as part of whatever section
            // happens to be above it.
            !s.searchContacts || !contactRepository.hasPermission() -> {
                rows += DrawerRow.ContactsHeader
                rows += DrawerRow.ContactPrompt(permanentlyDenied = blocked)
            }
        }
        return rows
    }

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
        viewModelScope.launch {
            settingsTargets.value = SettingsTargets.available(application)
        }
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
        if (panel == Panel.PRIVACY) rescanPrivacy()
    }

    fun onQueryChange(value: String) {
        _query.value = value
        refreshContacts(value)
    }

    // --- contacts ---------------------------------------------------------

    private var contactJob: Job? = null

    /**
     * Re-runs the contact search, debounced.
     *
     * Hand-rolled with a cancellable job rather than Flow.debounce so the
     * whole path stays on stable coroutine API, and so the guard conditions
     * are checked before anything is scheduled: with contact search off, or
     * the permission not granted, this never touches the provider at all.
     */
    private fun refreshContacts(rawQuery: String) {
        contactJob?.cancel()
        val query = rawQuery.trim()
        if (query.isEmpty() ||
            !settings.value.searchContacts ||
            !contactRepository.hasPermission()
        ) {
            contactMatches.value = emptyList()
            return
        }
        contactJob = viewModelScope.launch {
            delay(CONTACT_DEBOUNCE_MS)
            contactMatches.value = contactRepository.search(query)
        }
    }

    fun contactsPermissionGranted(): Boolean = contactRepository.hasPermission()

    /**
     * Called after the system permission dialog closes, either way.
     *
     * [willAskAgain] comes from shouldShowRequestPermissionRationale, which
     * the system flips to false once it has stopped asking.
     */
    fun onContactsPermissionResult(granted: Boolean, willAskAgain: Boolean = true) {
        contactsBlocked.value = !granted && !willAskAgain
        edit {
            settingsRepository.setSearchContacts(granted)
            if (granted) refreshContacts(_query.value)
        }
    }

    /** Turning the feature off. Android owns revoking the grant itself. */
    fun disableContactSearch() = edit {
        settingsRepository.setSearchContacts(false)
        contactMatches.value = emptyList()
    }

    fun openContact(match: ContactMatch) {
        if (contactRepository.open(match)) closeAllPanels()
    }

    fun openSettingsScreen(target: SettingsTarget) {
        if (appRepository.openAction(target.action)) closeAllPanels()
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

    fun openPrivacyDashboard() {
        panel = Panel.PRIVACY
        rescanPrivacy()
    }

    /**
     * Re-reads permissions.
     *
     * Called when the screen opens and again when the launcher comes back to
     * the foreground, because the usual reason to leave this screen is to go
     * and revoke something, and coming back to the old numbers would make it
     * look as though nothing had happened.
     */
    fun rescanPrivacy() {
        privacyScanJob?.cancel()
        privacyScanJob = viewModelScope.launch {
            _privacyReport.value = privacyScanner.scan()
        }
    }

    fun openAppPermissions(packageName: String) {
        privacyScanner.openPermissions(packageName)
    }

    fun openSystemPrivacy() {
        privacyScanner.openSystemPrivacy()
    }

    /** Back behaviour: the hide-apps screen returns to settings, not home. */
    fun closePanel() {
        panel = when (panel) {
            Panel.HIDDEN_APPS, Panel.DESIGN_PREVIEW, Panel.PRIVACY -> Panel.SETTINGS
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
        if (appRepository.launch(entry)) {
            closeAllPanels()
            // Only counted for the most-used row, and only while that row is
            // switched on -- recordLaunch checks the setting itself.
            edit { settingsRepository.recordLaunch(entry.key) }
        } else {
            refreshApps()
        }
    }

    fun openAppInfo(entry: AppEntry) = appRepository.openAppInfo(entry.packageName)

    /** Android's own page for this launcher: permissions, storage, uninstall. */
    fun openOwnAppInfo() = appRepository.openOwnAppInfo()

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

    fun setAutoKeyboard(v: Boolean) = edit { settingsRepository.setAutoKeyboard(v) }

    fun setShowMostUsed(v: Boolean) = edit { settingsRepository.setShowMostUsed(v) }

    fun setLockHiddenApps(v: Boolean) = edit { settingsRepository.setLockHiddenApps(v) }

    fun clearUsageHistory() = edit { settingsRepository.clearLaunchCounts() }

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

    private companion object {
        const val SECTION_SEARCH = "q"
        const val SECTION_MOST_USED = "mu"
        const val SECTION_PLAIN = "a"

        /**
         * Below this many apps the drawer is a plain alphabetical grid.
         *
         * A heading takes a whole line, so sectioning a short list spreads a
         * screenful of icons over several screens of mostly empty space. The
         * headings only start paying for themselves once each one has a few
         * icons under it, and at that point the list is long enough to be
         * worth skipping through.
         */
        const val MIN_APPS_FOR_SECTIONS = 24

        /** Settings results should help, not bury the apps you searched for. */
        const val MAX_SETTINGS_RESULTS = 4

        /**
         * Long enough that typing a word is one provider query rather than
         * six, short enough that the results feel live.
         */
        const val CONTACT_DEBOUNCE_MS = 160L
    }

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
