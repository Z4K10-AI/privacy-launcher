package com.privacylauncher.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.privacylauncher.PrivacyLauncherApp
import com.privacylauncher.data.AccentColor
import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.BackupResult
import com.privacylauncher.data.SettingsBackup
import com.privacylauncher.data.ContactMatch
import com.privacylauncher.data.ContactRepository
import com.privacylauncher.data.FocusClock
import com.privacylauncher.data.FocusDuration
import com.privacylauncher.data.FocusState
import com.privacylauncher.data.FontChoice
import com.privacylauncher.data.Fuzzy
import com.privacylauncher.data.coversMinute
import com.privacylauncher.data.minutesUntilChange
import com.privacylauncher.data.nextFocusChangeMillis
import com.privacylauncher.data.resolveFocus
import com.privacylauncher.data.SettingsTarget
import com.privacylauncher.data.SettingsTargets
import com.privacylauncher.ui.quick.QuickTile
import com.privacylauncher.data.HomeItem
import com.privacylauncher.data.IconSize
import com.privacylauncher.data.LauncherSettings
import com.privacylauncher.data.PrivacyReport
import com.privacylauncher.data.QuickControlsState
import com.privacylauncher.data.ThemeMode
import com.privacylauncher.data.Toggle
import com.privacylauncher.system.LauncherAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import com.privacylauncher.data.rankSettingsTargets
import kotlinx.coroutines.flow.distinctUntilChanged

/** Which full-screen panel is showing, if any. */
enum class Panel {
    NONE,
    DRAWER,
    SETTINGS,
    HIDDEN_APPS,
    DESIGN_PREVIEW,
    PRIVACY,
    QUICK,
    FOCUS,
    FOCUS_APPS,
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val launcherApp = application as PrivacyLauncherApp
    private val appRepository = launcherApp.appRepository
    private val settingsRepository = launcherApp.settingsRepository
    private val contactRepository = launcherApp.contactRepository
    private val privacyScanner = launcherApp.privacyScanner
    private val quickControls = launcherApp.quickControls

    val iconLoader = launcherApp.iconLoader

    // --- settings ---------------------------------------------------------

    val settings: StateFlow<LauncherSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, LauncherSettings())

    /**
     * Whether onboarding is finished -- null until the stored file has been
     * read for the first time.
     *
     * It needs its own flow precisely because of that null. Every other
     * preference has a default that is fine to show for the few milliseconds
     * before DataStore answers; this one does not, because the default is
     * "not onboarded" and showing that briefly would flash the welcome
     * screen at every cold start, on the one app that cannot afford to look
     * broken while it loads. Null means "do not know yet", and the UI draws
     * nothing rather than guessing.
     */
    val onboarded: StateFlow<Boolean?> = settingsRepository.settings
        .map { it.onboardingDone }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // --- focus mode -------------------------------------------------------

    /**
     * Bumped whenever focus might have changed by itself.
     *
     * Focus is a question about the clock, not an event, so there is no alarm
     * and no background work behind it. This exists only so that a screen
     * someone is looking at redraws at the moment the answer changes.
     */
    private val focusTick = MutableStateFlow(0L)

    val focus: StateFlow<FocusState> =
        combine(settings, focusTick) { s, _ ->
            val now = LocalTime.now()
            resolveFocus(s, System.currentTimeMillis(), now.minuteOfDay())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, FocusState())

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
        combine(allApps, settings, _query, launchCounts, focus) { apps, s, q, counts, f ->
            DrawerListBuilder.build(apps, s, q, counts, f)
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

    /** Live state behind the quick controls panel. */
    private val _quickState = MutableStateFlow(QuickControlsState())
    val quickState: StateFlow<QuickControlsState> = _quickState

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
        val matchedSettings = rankSettingsTargets(
            enabled = s.searchSettings,
            query = query,
            targets = targets,
            max = MAX_SETTINGS_RESULTS,
        ) { context.getString(it.labelRes) }
        if (matchedSettings.isNotEmpty()) {
            rows += DrawerRow.SettingsSearchHeader
            matchedSettings.forEach { rows += DrawerRow.SettingsScreen(it) }
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
        combine(allApps, settings, focus) { apps, s, f ->
            resolveHomeTiles(s.homeItems, apps, f)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The dock, resolved and capped. */
    val dockApps: StateFlow<List<AppEntry>> =
        combine(allApps, settings, focus) { apps, s, f ->
            val byKey = apps.associateBy { it.key }
            s.dockApps.mapNotNull { byKey[it] }
                .filter { f.allows(it.key) }
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
            // Only once the user has asked for settings in search. Finding
            // out which of these screens this phone has is one question to
            // the package manager per screen, and there is no reason to ask
            // nineteen of them at every start for a feature that is off.
            settings.map { it.searchSettings }.distinctUntilChanged().collect { on ->
                if (on && settingsTargets.value.isEmpty()) {
                    settingsTargets.value = SettingsTargets.available(application)
                }
            }
        }
        startFocusClock()
    }

    /**
     * Sleeps until the next moment focus could change, then nudges the state.
     *
     * Restarted whenever the three things that decide the timing change, and
     * only those three: an accent colour change must not reset the clock.
     * collectLatest is what cancels the sleep in flight when they do.
     */
    private fun startFocusClock() {
        viewModelScope.launch {
            settings
                .distinctUntilChangedBy {
                    Triple(it.focusUntil, it.focusSnoozeUntil, it.focusSchedule)
                }
                .collectLatest { s ->
                    while (true) {
                        val now = LocalTime.now()
                        delay(
                            nextFocusChangeMillis(
                                settings = s,
                                nowMillis = System.currentTimeMillis(),
                                minuteOfDay = now.minuteOfDay(),
                                secondOfMinuteMillis = now.millisIntoMinute(),
                            )
                        )
                        focusTick.value = System.currentTimeMillis()
                    }
                }
        }
    }

    override fun onCleared() {
        // No super call: ViewModel.onCleared is defined to be empty.
        getApplication<Application>().unregisterReceiver(packageReceiver)
    }

    // --- actions ----------------------------------------------------------

    fun refreshApps() {
        viewModelScope.launch {
            // null means the system would not answer, which is not the same
            // as a phone with no apps. Keep showing the list we already have
            // and try again on the next broadcast: replacing it would blank
            // the drawer, and pruning against it would delete the home
            // screen. See AppRepository.loadApps.
            val apps = appRepository.loadApps() ?: return@launch
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
        // The phone may have been asleep for hours, in which case the sleep
        // in startFocusClock has not finished yet and the focus state on
        // screen is whatever it was last night.
        focusTick.value = System.currentTimeMillis()
        gestureServiceEnabled = LauncherAccessibilityService.isEnabled
        if (panel == Panel.PRIVACY) rescanPrivacy()
        // Coming back from Android's own settings is the usual way one of
        // these changes, so the panel must not still be showing the old
        // state when the launcher returns to the foreground.
        if (panel == Panel.QUICK) refreshQuickControls()
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

    fun openQuickPanel() {
        homeEditMode = false
        panel = Panel.QUICK
        refreshQuickControls()
    }

    fun refreshQuickControls() {
        _quickState.value = quickControls.read()
    }

    // --- focus and minimal mode -------------------------------------------

    fun openFocusScreen() {
        homeEditMode = false
        panel = Panel.FOCUS
    }

    fun openFocusApps() {
        panel = Panel.FOCUS_APPS
    }

    fun setFocusAllowed(entry: AppEntry, allowed: Boolean) = edit {
        settingsRepository.setFocusAllowed(entry.key, allowed)
    }

    fun startFocus(duration: FocusDuration) = edit {
        val until = if (duration == FocusDuration.OPEN_ENDED) {
            FocusClock.NO_END
        } else {
            System.currentTimeMillis() + duration.minutes * 60_000L
        }
        settingsRepository.startFocus(until)
    }

    /**
     * Ends focus now.
     *
     * Inside a scheduled window this also snoozes the schedule until that
     * window's own end, so "end focus" means what it says instead of being
     * undone a second later by the schedule that started it.
     */
    fun endFocus() = edit {
        val schedule = settings.value.focusSchedule
        val now = LocalTime.now()
        val minute = now.minuteOfDay()
        val snoozeUntil = if (schedule.coversMinute(minute)) {
            val remaining = schedule.minutesUntilChange(minute) ?: 0
            System.currentTimeMillis() + remaining * 60_000L - now.millisIntoMinute()
        } else {
            0L
        }
        settingsRepository.endFocus(snoozeUntil)
    }

    fun setFocusScheduleEnabled(value: Boolean) = edit {
        settingsRepository.setFocusScheduleEnabled(value)
    }

    /** Steps the window's start or end, wrapping round midnight. */
    fun nudgeFocusWindow(startSteps: Int, endSteps: Int) = edit {
        val schedule = settings.value.focusSchedule
        val step = LauncherSettings.FOCUS_TIME_STEP
        settingsRepository.setFocusWindow(
            startMinute = schedule.startMinute + startSteps * step,
            endMinute = schedule.endMinute + endSteps * step,
        )
    }

    // --- onboarding -------------------------------------------------------

    /** Called by Skip and by Get started alike: both mean "do not show this again". */
    fun finishOnboarding() = edit { settingsRepository.setOnboardingDone(true) }

    // --- backup and restore -----------------------------------------------

    /** The outcome of the last backup or restore, for the dialog. */
    var backupResult by mutableStateOf<BackupResult?>(null)
        private set

    fun dismissBackupResult() {
        backupResult = null
    }

    /**
     * Writes the settings to a file the user picked.
     *
     * The Uri comes from Android's file picker, so this needs no storage
     * permission and can only touch the one file that was chosen. Every
     * failure mode here is the system's -- a removed SD card, a provider
     * that has gone away -- so they collapse into one message rather than
     * being guessed at.
     */
    fun backupTo(uri: Uri) {
        viewModelScope.launch {
            val json = settingsRepository.exportBackup()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver
                        .openOutputStream(uri, "wt")
                        ?.use { it.write(json.toByteArray()) }
                        ?: error("no stream")
                }.isSuccess
            }
            backupResult = if (ok) BackupResult.SAVED else BackupResult.SAVE_FAILED
        }
    }

    /**
     * Reads settings back from a file the user picked.
     *
     * Reading and parsing are reported apart on purpose: a file that cannot
     * be opened is the system's problem, a file that is not a backup is the
     * wrong file, and telling someone "could not restore" for both would
     * leave them unable to tell which.
     */
    fun restoreFrom(uri: Uri) {
        viewModelScope.launch {
            val raw = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver
                        .openInputStream(uri)
                        ?.use { it.readBytes().decodeToString() }
                }.getOrNull()
            }
            if (raw == null) {
                backupResult = BackupResult.RESTORE_UNREADABLE
                return@launch
            }
            backupResult = if (settingsRepository.importBackup(raw)) {
                BackupResult.RESTORED
            } else {
                BackupResult.RESTORE_NOT_A_BACKUP
            }
        }
    }

    /** The filename offered in the save dialog. */
    fun suggestedBackupFileName(): String = SettingsBackup.suggestedFileName()

    fun setMinimalMode(value: Boolean) = edit {
        settingsRepository.setMinimalMode(value)
        // There is no reordering in minimal mode, so edit mode must not be
        // left switched on behind it with no way to leave.
        if (value) homeEditMode = false
    }

    /**
     * Starts watching the settings the panel shows. Returns the undo.
     *
     * Driven from the panel's own DisposableEffect rather than from here, so
     * nothing is observed while the panel is closed.
     */
    fun observeQuickControls(): () -> Unit =
        quickControls.observe { refreshQuickControls() }

    /**
     * What a tile does.
     *
     * Two of the eight act; the rest hand off to Android, which is the whole
     * point of the panel being honest about what an app may change.
     */
    fun onQuickTile(tile: QuickTile) {
        when (tile) {
            QuickTile.FLASHLIGHT -> {
                quickControls.setTorch(_quickState.value.torch != Toggle.ON)
                // The torch callback normally reports the change, but it does
                // not fire when the camera refuses, so read back either way.
                refreshQuickControls()
            }
            QuickTile.LOCKDOWN -> {
                closeAllPanels()
                onLockDown()
            }
            QuickTile.WIFI -> quickControls.openWifi()
            QuickTile.BLUETOOTH -> quickControls.openBluetooth()
            QuickTile.LOCATION -> quickControls.openLocation()
            QuickTile.DND -> quickControls.openDnd()
            QuickTile.ROTATION -> quickControls.openRotation()
            QuickTile.PRIVATE_DNS -> quickControls.openPrivateDns()
        }
    }

    /**
     * Locks the screen, or explains why it cannot.
     *
     * Same route as double-tap-to-lock: there is no lock API for an ordinary
     * app, so it goes through the optional accessibility service.
     */
    private fun onLockDown() {
        if (!LauncherAccessibilityService.lockScreenSupported) return
        if (LauncherAccessibilityService.isEnabled) {
            LauncherAccessibilityService.lockScreen()
        } else {
            showGestureExplainer = true
        }
    }

    private var pendingBrightness: Float? = null
    private var lastBrightnessWrite = 0L

    /**
     * Brightness, as the slider is dragged.
     *
     * Throttled. Each write goes through the Settings provider, and doing
     * that on every frame of a drag is both slow and pointless -- the screen
     * cannot change faster than the eye can see it. The last value is kept so
     * [commitBrightness] can write it when the finger lifts, which is what
     * stops the throttle from swallowing where the drag actually ended.
     */
    fun setBrightness(fraction: Float) {
        pendingBrightness = fraction
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastBrightnessWrite < BRIGHTNESS_WRITE_INTERVAL_MS) return
        lastBrightnessWrite = now
        quickControls.setBrightness(fraction)
    }

    fun commitBrightness() {
        pendingBrightness?.let { quickControls.setBrightness(it) }
        pendingBrightness = null
        refreshQuickControls()
    }

    fun requestWriteSettings() {
        quickControls.requestWriteSettings()
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
            Panel.HIDDEN_APPS,
            Panel.DESIGN_PREVIEW,
            Panel.PRIVACY,
            Panel.FOCUS,
            -> Panel.SETTINGS
            Panel.FOCUS_APPS -> Panel.FOCUS
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

    /**
     * A drag on the home grid. Tiles are named by id because what is drawn
     * can be shorter than what is stored; see [resolveHomeTiles].
     */
    fun moveHomeItem(fromId: String, toId: String) = edit {
        settingsRepository.moveHomeItem(fromId, toId)
    }

    /** Dropping one icon onto another: makes a folder, or adds to one. */
    fun combineHomeItems(fromId: String, ontoId: String) = edit {
        settingsRepository.combineHomeItems(fromId, ontoId)
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

    /** A home screen icon was dropped on the dock. */
    fun moveHomeItemToDock(itemId: String, beforeKey: String?) = edit {
        settingsRepository.moveHomeItemToDock(itemId, beforeKey)
    }

    /**
     * An icon carried in from the drawer or the dock was let go.
     *
     * From the dock the icon moves: it leaves the dock for wherever it was
     * put. From the drawer nothing is left behind, because the drawer always
     * lists every app.
     */
    internal fun onHomeDrop(drop: HomeDrop, fromDock: Boolean) = edit {
        when (drop) {
            is HomeDrop.Place ->
                if (fromDock) {
                    settingsRepository.moveDockAppToHome(drop.key, drop.beforeId)
                } else {
                    settingsRepository.placeOnHome(drop.key, drop.beforeId)
                }
            is HomeDrop.Onto ->
                if (fromDock) {
                    settingsRepository.dropDockAppOntoHomeItem(drop.key, drop.ontoId)
                } else {
                    settingsRepository.dropOntoHomeItem(drop.key, drop.ontoId)
                }
            is HomeDrop.Dock -> settingsRepository.placeInDock(drop.key, drop.beforeKey)
        }
    }

    fun setShowGlance(v: Boolean) = edit { settingsRepository.setShowGlance(v) }

    fun setAutoKeyboard(v: Boolean) = edit { settingsRepository.setAutoKeyboard(v) }

    fun setShowMostUsed(v: Boolean) = edit { settingsRepository.setShowMostUsed(v) }

    fun setLockHiddenApps(v: Boolean) = edit { settingsRepository.setLockHiddenApps(v) }

    fun setSearchSettings(v: Boolean) = edit { settingsRepository.setSearchSettings(v) }

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
        /** Settings results should help, not bury the apps you searched for. */
        const val MAX_SETTINGS_RESULTS = 4

        /**
         * Long enough that typing a word is one provider query rather than
         * six, short enough that the results feel live.
         */
        const val CONTACT_DEBOUNCE_MS = 160L

        /** Roughly four writes a second while a brightness drag is running. */
        const val BRIGHTNESS_WRITE_INTERVAL_MS = 60L

        fun LocalTime.minuteOfDay(): Int = hour * 60 + minute

        fun LocalTime.millisIntoMinute(): Long = second * 1_000L + nano / 1_000_000L
    }

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
