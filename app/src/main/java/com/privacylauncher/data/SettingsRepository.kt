package com.privacylauncher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "launcher_settings",
)

/**
 * Stores every launcher preference.
 *
 * Backed by one file in the app's private directory. Nothing is uploaded, and
 * the manifest disables cloud backup and device transfer, so these settings
 * never leave the phone.
 */
class SettingsRepository(private val context: Context) {

    val settings: Flow<LauncherSettings> = context.dataStore.data.map { p ->
        LauncherSettings(
            themeMode = p[KEY_THEME]?.toEnum<ThemeMode>() ?: ThemeMode.SYSTEM,
            accent = p[KEY_ACCENT]?.toEnum<AccentColor>() ?: AccentColor.TEAL,
            dynamicColor = p[KEY_DYNAMIC_COLOR] ?: false,
            font = p[KEY_FONT]?.toEnum<FontChoice>() ?: FontChoice.INTER,
            iconSize = p[KEY_ICON_SIZE]?.toEnum<IconSize>() ?: IconSize.MEDIUM,
            drawerColumns = p[KEY_DRAWER_COLUMNS] ?: LauncherSettings.DEFAULT_DRAWER_COLUMNS,
            homeColumns = p[KEY_HOME_COLUMNS] ?: LauncherSettings.DEFAULT_HOME_COLUMNS,
            hiddenApps = p[KEY_HIDDEN_APPS].orEmpty(),
            homeItems = readHomeItems(p),
            dockApps = p[KEY_DOCK_APPS]?.decodeList().orEmpty(),
            showGlance = p[KEY_SHOW_GLANCE] ?: true,
            autoKeyboard = p[KEY_AUTO_KEYBOARD] ?: true,
            showMostUsed = p[KEY_SHOW_MOST_USED] ?: true,
            searchContacts = p[KEY_SEARCH_CONTACTS] ?: false,
            lockHiddenApps = p[KEY_LOCK_HIDDEN] ?: true,
            focusAllowed = p[KEY_FOCUS_ALLOWED].orEmpty(),
            focusUntil = p[KEY_FOCUS_UNTIL] ?: 0L,
            focusSnoozeUntil = p[KEY_FOCUS_SNOOZE] ?: 0L,
            focusSchedule = FocusSchedule(
                enabled = p[KEY_FOCUS_SCHEDULE_ON] ?: false,
                startMinute = p[KEY_FOCUS_START] ?: FocusSchedule().startMinute,
                endMinute = p[KEY_FOCUS_END] ?: FocusSchedule().endMinute,
            ),
            minimalMode = p[KEY_MINIMAL_MODE] ?: false,
            onboardingDone = p[KEY_ONBOARDING_DONE] ?: false,
        )
    }

    /**
     * How many times each app has been launched from this launcher.
     *
     * Kept out of [settings] on purpose. It changes on every launch, and
     * folding it into that flow would make every derived list -- the drawer,
     * the home grid, the dock -- recompute each time someone opens an app.
     */
    val launchCounts: Flow<Map<String, Int>> = context.dataStore.data.map { p ->
        decodeCounts(p[KEY_LAUNCH_COUNTS])
    }

    /**
     * Reads the home layout, falling back to the pre-folders format.
     *
     * Before folders existed the layout was a newline-joined list of app
     * keys. Reading that when the JSON key is absent means an existing home
     * screen survives the upgrade instead of silently emptying.
     */
    private fun readHomeItems(p: Preferences): List<HomeItem> {
        p[KEY_HOME_ITEMS]?.let { return HomeLayoutCodec.decode(it) }
        return p[KEY_HOME_APPS_LEGACY]?.decodeList().orEmpty().map { HomeItem.App(it) }
    }

    // --- simple values ----------------------------------------------------

    suspend fun setThemeMode(value: ThemeMode) = put(KEY_THEME, value.name)

    suspend fun setAccent(value: AccentColor) = put(KEY_ACCENT, value.name)

    suspend fun setFont(value: FontChoice) = put(KEY_FONT, value.name)

    suspend fun setIconSize(value: IconSize) = put(KEY_ICON_SIZE, value.name)

    suspend fun setDynamicColor(value: Boolean) {
        context.dataStore.edit { it[KEY_DYNAMIC_COLOR] = value }
    }

    suspend fun setShowGlance(value: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_GLANCE] = value }
    }

    suspend fun setAutoKeyboard(value: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_KEYBOARD] = value }
    }

    suspend fun setLockHiddenApps(value: Boolean) {
        context.dataStore.edit { it[KEY_LOCK_HIDDEN] = value }
    }

    suspend fun setSearchContacts(value: Boolean) {
        context.dataStore.edit { it[KEY_SEARCH_CONTACTS] = value }
    }

    /** Turning the most-used row off also throws away what it was built from. */
    suspend fun setShowMostUsed(value: Boolean) {
        context.dataStore.edit {
            it[KEY_SHOW_MOST_USED] = value
            if (!value) it.remove(KEY_LAUNCH_COUNTS)
        }
    }

    // --- on-device usage counts -------------------------------------------

    /**
     * Adds one to [key]'s launch count, if counting is switched on.
     *
     * The table is capped: past [MAX_COUNTED_APPS] the least-used half is
     * dropped. Someone with 300 apps should not accumulate a 300-row usage
     * record for the sake of a four-icon row.
     */
    suspend fun recordLaunch(key: String) {
        context.dataStore.edit { prefs ->
            if (prefs[KEY_SHOW_MOST_USED] == false) return@edit
            val counts = decodeCounts(prefs[KEY_LAUNCH_COUNTS]).toMutableMap()
            counts[key] = (counts[key] ?: 0) + 1
            val trimmed = if (counts.size > MAX_COUNTED_APPS) {
                counts.entries
                    .sortedByDescending { it.value }
                    .take(MAX_COUNTED_APPS / 2)
                    .associate { it.key to it.value }
            } else {
                counts
            }
            prefs[KEY_LAUNCH_COUNTS] = encodeCounts(trimmed)
        }
    }

    /** Erases the usage counts without changing any other preference. */
    suspend fun clearLaunchCounts() {
        context.dataStore.edit { it.remove(KEY_LAUNCH_COUNTS) }
    }

    suspend fun setDrawerColumns(value: Int) {
        context.dataStore.edit { it[KEY_DRAWER_COLUMNS] = value }
    }

    suspend fun setHomeColumns(value: Int) {
        context.dataStore.edit { it[KEY_HOME_COLUMNS] = value }
    }

    suspend fun setAppHidden(key: String, hidden: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_HIDDEN_APPS].orEmpty()
            prefs[KEY_HIDDEN_APPS] = if (hidden) current + key else current - key
        }
    }

    // --- focus mode -------------------------------------------------------

    suspend fun setFocusAllowed(key: String, allowed: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_FOCUS_ALLOWED].orEmpty()
            prefs[KEY_FOCUS_ALLOWED] = if (allowed) current + key else current - key
        }
    }

    /**
     * Starts a hand-run focus session.
     *
     * Clearing the snooze is the point of doing both in one edit: starting
     * focus by hand inside a window that was snoozed earlier should hand
     * control back to the schedule afterwards, rather than leave the rest of
     * the window suppressed.
     */
    suspend fun startFocus(until: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FOCUS_UNTIL] = until
            prefs[KEY_FOCUS_SNOOZE] = 0L
        }
    }

    /**
     * Ends focus now.
     *
     * [snoozeUntil] is when the schedule may take over again -- the end of
     * the window that is currently running, worked out by the caller, which
     * is the only place that knows what the clock says.
     */
    suspend fun endFocus(snoozeUntil: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FOCUS_UNTIL] = 0L
            prefs[KEY_FOCUS_SNOOZE] = snoozeUntil
        }
    }

    suspend fun setFocusScheduleEnabled(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FOCUS_SCHEDULE_ON] = value
            // A schedule switched on should start clean. Leaving an old
            // snooze in place would silently skip its first window.
            if (value) prefs[KEY_FOCUS_SNOOZE] = 0L
        }
    }

    suspend fun setFocusWindow(startMinute: Int, endMinute: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FOCUS_START] = startMinute.mod(FocusClock.MINUTES_PER_DAY)
            prefs[KEY_FOCUS_END] = endMinute.mod(FocusClock.MINUTES_PER_DAY)
        }
    }

    suspend fun setMinimalMode(value: Boolean) {
        context.dataStore.edit { it[KEY_MINIMAL_MODE] = value }
    }

    suspend fun setOnboardingDone(value: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_DONE] = value }
    }

    // --- backup and restore -----------------------------------------------

    /** The current settings as a JSON document, ready to be written out. */
    suspend fun exportBackup(): String = SettingsBackup.encode(settings.first())

    /**
     * Replaces every backed-up preference with the ones in [raw].
     *
     * All or nothing, in a single `edit`, so a half-applied restore cannot
     * leave the launcher in a state that is neither the old settings nor the
     * new ones. Returns false if the file is not a backup this build
     * understands, in which case nothing is written at all.
     *
     * Keys the backup deliberately omits -- launch counts, a running focus
     * session, the onboarding flag -- are left exactly as they are rather
     * than reset. See [SettingsBackup] for why they are omitted.
     *
     * Apps named in the restored layout that are not installed here are not
     * filtered out at this point. `pruneMissing` already runs when the app
     * list loads and will drop them, and doing it there rather than here
     * means a restore made while an app is still installing is not quietly
     * thrown away.
     */
    suspend fun importBackup(raw: String): Boolean {
        val s = SettingsBackup.decode(raw) ?: return false
        context.dataStore.edit { p ->
            p[KEY_THEME] = s.themeMode.name
            p[KEY_ACCENT] = s.accent.name
            p[KEY_DYNAMIC_COLOR] = s.dynamicColor
            p[KEY_FONT] = s.font.name
            p[KEY_ICON_SIZE] = s.iconSize.name
            p[KEY_DRAWER_COLUMNS] = s.drawerColumns
            p[KEY_HOME_COLUMNS] = s.homeColumns
            p[KEY_HIDDEN_APPS] = s.hiddenApps
            p[KEY_HOME_ITEMS] = HomeLayoutCodec.encode(s.homeItems)
            // The pre-folders key would otherwise win on the next read.
            p.remove(KEY_HOME_APPS_LEGACY)
            p[KEY_DOCK_APPS] = s.dockApps.encodeList()
            p[KEY_SHOW_GLANCE] = s.showGlance
            p[KEY_AUTO_KEYBOARD] = s.autoKeyboard
            p[KEY_SHOW_MOST_USED] = s.showMostUsed
            p[KEY_SEARCH_CONTACTS] = s.searchContacts
            p[KEY_LOCK_HIDDEN] = s.lockHiddenApps
            p[KEY_FOCUS_ALLOWED] = s.focusAllowed
            p[KEY_FOCUS_SCHEDULE_ON] = s.focusSchedule.enabled
            p[KEY_FOCUS_START] = s.focusSchedule.startMinute
            p[KEY_FOCUS_END] = s.focusSchedule.endMinute
            p[KEY_MINIMAL_MODE] = s.minimalMode
        }
        return true
    }

    // --- home layout ------------------------------------------------------

    /**
     * Rewrites the home layout with a pure transform.
     *
     * The sibling of [editHome] for everything in [HomeLayoutOps]: those
     * take a list and return one, which is what lets them be tested without
     * a device.
     */
    private suspend fun mapHome(transform: (List<HomeItem>) -> List<HomeItem>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HOME_ITEMS] = HomeLayoutCodec.encode(transform(readHomeItems(prefs)))
            prefs.remove(KEY_HOME_APPS_LEGACY)
        }
    }

    private suspend fun editHome(block: (MutableList<HomeItem>) -> Unit) {
        context.dataStore.edit { prefs ->
            val items = readHomeItems(prefs).toMutableList()
            block(items)
            prefs[KEY_HOME_ITEMS] = HomeLayoutCodec.encode(items)
            // The legacy key is now stale; drop it so it cannot resurface.
            prefs.remove(KEY_HOME_APPS_LEGACY)
        }
    }

    /** Adds an app to the end of the home screen, ignoring duplicates. */
    suspend fun addToHome(key: String) = editHome { items ->
        if (items.none { it.containsApp(key) }) items.add(HomeItem.App(key))
    }

    /** Removes an app or a whole folder by item id. */
    suspend fun removeHomeItem(itemId: String) = editHome { items ->
        items.removeAll { it.id == itemId }
    }

    /** Removes an app wherever it sits, including inside a folder. */
    suspend fun removeAppFromHome(key: String) = editHome { items ->
        items.removeAll { it is HomeItem.App && it.key == key }
        items.replaceFolders { folder ->
            folder.copy(appKeys = folder.appKeys.filterNot { it == key })
        }
        items.dissolveThinFolders()
    }

    suspend fun moveHomeItem(fromIndex: Int, toIndex: Int) = mapHome {
        HomeLayoutOps.move(it, fromIndex, toIndex)
    }

    /**
     * Drops the item at [fromIndex] onto the one at [ontoIndex].
     *
     * App onto app makes a new folder. App onto folder adds it. Folders are
     * not merged into each other: it is rarely what someone means, and it
     * silently destroys one of the two names.
     */
    /**
     * Drops the item at [fromIndex] onto the one at [ontoIndex].
     *
     * App onto app makes a new folder. App onto folder adds it. Folders are
     * not merged into each other: it is rarely what someone means, and it
     * silently destroys one of the two names. See [HomeLayoutOps.combine].
     */
    suspend fun combineHomeItems(fromIndex: Int, ontoIndex: Int) = mapHome {
        HomeLayoutOps.combine(
            items = it,
            from = fromIndex,
            onto = ontoIndex,
            newFolderId = "f:${UUID.randomUUID()}",
            folderName = HomeLayoutCodec.DEFAULT_FOLDER_NAME,
        )
    }

    suspend fun renameFolder(folderId: String, name: String) = mapHome {
        HomeLayoutOps.renameFolder(it, folderId, name)
    }

    /** Takes an app out of a folder and puts it back on the home screen. */
    suspend fun removeFromFolder(folderId: String, appKey: String) = mapHome {
        HomeLayoutOps.removeFromFolder(it, folderId, appKey)
    }

    // --- dock -------------------------------------------------------------

    suspend fun addToDock(key: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_DOCK_APPS]?.decodeList().orEmpty()
            val next = HomeLayoutOps.addToDock(current, key, LauncherSettings.MAX_DOCK_APPS)
            if (next === current) return@edit
            prefs[KEY_DOCK_APPS] = next.encodeList()
        }
    }

    suspend fun removeFromDock(key: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_DOCK_APPS]?.decodeList().orEmpty()
            prefs[KEY_DOCK_APPS] = current.filterNot { it == key }.encodeList()
        }
    }

    // --- housekeeping -----------------------------------------------------

    /** Drops anything referring to an app that is no longer installed. */
    suspend fun pruneMissing(installedKeys: Set<String>) {
        context.dataStore.edit { prefs ->
            val items = readHomeItems(prefs)
            val kept = HomeLayoutOps.prune(items, installedKeys)
            if (kept != items) prefs[KEY_HOME_ITEMS] = HomeLayoutCodec.encode(kept)

            val dock = prefs[KEY_DOCK_APPS]?.decodeList().orEmpty()
            val keptDock = dock.filter { it in installedKeys }
            if (keptDock.size != dock.size) prefs[KEY_DOCK_APPS] = keptDock.encodeList()

            val hidden = prefs[KEY_HIDDEN_APPS].orEmpty()
            val keptHidden = hidden.filter { it in installedKeys }.toSet()
            if (keptHidden.size != hidden.size) prefs[KEY_HIDDEN_APPS] = keptHidden

            val allowed = prefs[KEY_FOCUS_ALLOWED].orEmpty()
            val keptAllowed = allowed.filter { it in installedKeys }.toSet()
            if (keptAllowed.size != allowed.size) prefs[KEY_FOCUS_ALLOWED] = keptAllowed
        }
    }

    private suspend fun put(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_ACCENT = stringPreferencesKey("accent_color")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_FONT = stringPreferencesKey("font_choice")
        val KEY_ICON_SIZE = stringPreferencesKey("icon_size")
        val KEY_DRAWER_COLUMNS = intPreferencesKey("drawer_columns")
        val KEY_HOME_COLUMNS = intPreferencesKey("home_columns")
        val KEY_HIDDEN_APPS = stringSetPreferencesKey("hidden_apps")
        val KEY_HOME_ITEMS = stringPreferencesKey("home_items")
        val KEY_SHOW_GLANCE = booleanPreferencesKey("show_glance")
        val KEY_AUTO_KEYBOARD = booleanPreferencesKey("auto_keyboard")
        val KEY_SHOW_MOST_USED = booleanPreferencesKey("show_most_used")
        val KEY_SEARCH_CONTACTS = booleanPreferencesKey("search_contacts")
        val KEY_LOCK_HIDDEN = booleanPreferencesKey("lock_hidden_apps")
        val KEY_LAUNCH_COUNTS = stringPreferencesKey("launch_counts")
        val KEY_FOCUS_ALLOWED = stringSetPreferencesKey("focus_allowed")
        val KEY_FOCUS_UNTIL = longPreferencesKey("focus_until")
        val KEY_FOCUS_SNOOZE = longPreferencesKey("focus_snooze_until")
        val KEY_FOCUS_SCHEDULE_ON = booleanPreferencesKey("focus_schedule_on")
        val KEY_FOCUS_START = intPreferencesKey("focus_start_minute")
        val KEY_FOCUS_END = intPreferencesKey("focus_end_minute")
        val KEY_MINIMAL_MODE = booleanPreferencesKey("minimal_mode")
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

        const val MAX_COUNTED_APPS = 60

        /** Pre-folders home layout. Read once, then replaced. */
        val KEY_HOME_APPS_LEGACY = stringPreferencesKey("home_apps")

        /**
         * Dock entries are an ordered list and DataStore only offers an
         * unordered Set, so order is kept by joining with a newline. A
         * component key is "package/activity"; neither part can contain one.
         */
        val KEY_DOCK_APPS = stringPreferencesKey("dock_apps")
        const val SEPARATOR = "\n"

        fun String.decodeList(): List<String> =
            split(SEPARATOR).filter { it.isNotBlank() }

        fun List<String>.encodeList(): String = joinToString(SEPARATOR)

        /**
         * Launch counts as "count<tab>key" lines.
         *
         * A component key is "package/activity"; neither part can contain a
         * tab or a newline, so no escaping is needed. Unparseable lines are
         * skipped rather than throwing: a corrupt count costs you one row in
         * the drawer, and should not stop the launcher starting.
         */
        fun encodeCounts(counts: Map<String, Int>): String =
            counts.entries.joinToString(SEPARATOR) { "${it.value}\t${it.key}" }

        fun decodeCounts(raw: String?): Map<String, Int> {
            if (raw.isNullOrEmpty()) return emptyMap()
            return raw.split(SEPARATOR).mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) return@mapNotNull null
                val count = line.substring(0, tab).toIntOrNull() ?: return@mapNotNull null
                val key = line.substring(tab + 1)
                if (key.isEmpty()) null else key to count
            }.toMap()
        }

        inline fun <reified T : Enum<T>> String.toEnum(): T? =
            enumValues<T>().firstOrNull { it.name == this }

        fun HomeItem.containsApp(key: String): Boolean = when (this) {
            is HomeItem.App -> this.key == key
            is HomeItem.Folder -> key in appKeys
        }

        fun MutableList<HomeItem>.replaceFolders(
            transform: (HomeItem.Folder) -> HomeItem.Folder,
        ) {
            for (i in indices) {
                val item = this[i]
                if (item is HomeItem.Folder) this[i] = transform(item)
            }
        }

        /**
         * A folder holding one app or none is just clutter, so it collapses
         * back to a plain icon or disappears.
         */
        fun MutableList<HomeItem>.dissolveThinFolders() {
            for (i in indices.reversed()) {
                val item = this[i]
                if (item !is HomeItem.Folder) continue
                when (item.appKeys.size) {
                    0 -> removeAt(i)
                    1 -> this[i] = HomeItem.App(item.appKeys.first())
                }
            }
        }
    }
}
