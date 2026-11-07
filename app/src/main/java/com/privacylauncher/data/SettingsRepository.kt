package com.privacylauncher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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

    // --- home layout ------------------------------------------------------

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

    suspend fun moveHomeItem(fromIndex: Int, toIndex: Int) = editHome { items ->
        if (fromIndex !in items.indices) return@editHome
        val target = toIndex.coerceIn(0, items.lastIndex)
        items.add(target, items.removeAt(fromIndex))
    }

    /**
     * Drops the item at [fromIndex] onto the one at [ontoIndex].
     *
     * App onto app makes a new folder. App onto folder adds it. Folders are
     * not merged into each other: it is rarely what someone means, and it
     * silently destroys one of the two names.
     */
    suspend fun combineHomeItems(fromIndex: Int, ontoIndex: Int) = editHome { items ->
        if (fromIndex !in items.indices || ontoIndex !in items.indices) return@editHome
        if (fromIndex == ontoIndex) return@editHome

        val dragged = items[fromIndex]
        val target = items[ontoIndex]
        if (dragged !is HomeItem.App) return@editHome

        val merged = when (target) {
            is HomeItem.App -> HomeItem.Folder(
                id = "f:${UUID.randomUUID()}",
                name = HomeLayoutCodec.DEFAULT_FOLDER_NAME,
                appKeys = listOf(target.key, dragged.key),
            )
            is HomeItem.Folder -> target.copy(appKeys = target.appKeys + dragged.key)
        }
        items[ontoIndex] = merged
        items.removeAt(fromIndex)
    }

    suspend fun renameFolder(folderId: String, name: String) = editHome { items ->
        items.replaceFolders { folder ->
            if (folder.id == folderId) folder.copy(name = name.trim()) else folder
        }
    }

    /** Takes an app out of a folder and puts it back on the home screen. */
    suspend fun removeFromFolder(folderId: String, appKey: String) = editHome { items ->
        items.replaceFolders { folder ->
            if (folder.id == folderId) {
                folder.copy(appKeys = folder.appKeys.filterNot { it == appKey })
            } else {
                folder
            }
        }
        items.dissolveThinFolders()
    }

    // --- dock -------------------------------------------------------------

    suspend fun addToDock(key: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_DOCK_APPS]?.decodeList().orEmpty()
            if (key in current || current.size >= LauncherSettings.MAX_DOCK_APPS) {
                return@edit
            }
            prefs[KEY_DOCK_APPS] = (current + key).encodeList()
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
            val kept = items.mapNotNull { item ->
                when (item) {
                    is HomeItem.App -> item.takeIf { it.key in installedKeys }
                    is HomeItem.Folder ->
                        item.copy(appKeys = item.appKeys.filter { it in installedKeys })
                }
            }.toMutableList()
            kept.dissolveThinFolders()
            if (kept != items) prefs[KEY_HOME_ITEMS] = HomeLayoutCodec.encode(kept)

            val dock = prefs[KEY_DOCK_APPS]?.decodeList().orEmpty()
            val keptDock = dock.filter { it in installedKeys }
            if (keptDock.size != dock.size) prefs[KEY_DOCK_APPS] = keptDock.encodeList()

            val hidden = prefs[KEY_HIDDEN_APPS].orEmpty()
            val keptHidden = hidden.filter { it in installedKeys }.toSet()
            if (keptHidden.size != hidden.size) prefs[KEY_HIDDEN_APPS] = keptHidden
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
