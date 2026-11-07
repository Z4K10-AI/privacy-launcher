package com.privacylauncher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "launcher_settings",
)

/**
 * Stores every launcher preference.
 *
 * Backed by one file in the app's private directory
 * (/data/data/com.privacylauncher/files/datastore). Nothing is uploaded, and
 * the manifest disables cloud backup and device transfer, so these settings
 * never leave the phone.
 */
class SettingsRepository(private val context: Context) {

    /** All settings as one object; emits again on every change. */
    val settings: Flow<LauncherSettings> = context.dataStore.data.map { p ->
        LauncherSettings(
            themeMode = p[KEY_THEME]?.toEnum<ThemeMode>() ?: ThemeMode.SYSTEM,
            accent = p[KEY_ACCENT]?.toEnum<AccentColor>() ?: AccentColor.TEAL,
            font = p[KEY_FONT]?.toEnum<FontChoice>() ?: FontChoice.SYSTEM,
            iconSize = p[KEY_ICON_SIZE]?.toEnum<IconSize>() ?: IconSize.MEDIUM,
            drawerColumns = p[KEY_DRAWER_COLUMNS]
                ?: LauncherSettings.DEFAULT_DRAWER_COLUMNS,
            homeColumns = p[KEY_HOME_COLUMNS]
                ?: LauncherSettings.DEFAULT_HOME_COLUMNS,
            hiddenApps = p[KEY_HIDDEN_APPS].orEmpty(),
            homeApps = p[KEY_HOME_APPS]?.decodeList().orEmpty(),
        )
    }

    suspend fun setThemeMode(value: ThemeMode) = put(KEY_THEME, value.name)

    suspend fun setAccent(value: AccentColor) = put(KEY_ACCENT, value.name)

    suspend fun setFont(value: FontChoice) = put(KEY_FONT, value.name)

    suspend fun setIconSize(value: IconSize) = put(KEY_ICON_SIZE, value.name)

    suspend fun setDrawerColumns(value: Int) {
        context.dataStore.edit { it[KEY_DRAWER_COLUMNS] = value }
    }

    suspend fun setHomeColumns(value: Int) {
        context.dataStore.edit { it[KEY_HOME_COLUMNS] = value }
    }

    suspend fun setAppHidden(key: String, hidden: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_HIDDEN_APPS].orEmpty()
            prefs[KEY_HIDDEN_APPS] =
                if (hidden) current + key else current - key
        }
    }

    /** Adds to the end of the home screen, ignoring duplicates. */
    suspend fun addToHome(key: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_HOME_APPS]?.decodeList().orEmpty()
            if (key !in current) {
                prefs[KEY_HOME_APPS] = (current + key).encodeList()
            }
        }
    }

    suspend fun removeFromHome(key: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_HOME_APPS]?.decodeList().orEmpty()
            prefs[KEY_HOME_APPS] = current.filterNot { it == key }.encodeList()
        }
    }

    /** Moves one entry to a new index, used by drag-to-reorder. */
    suspend fun moveHomeApp(fromIndex: Int, toIndex: Int) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_HOME_APPS]?.decodeList().orEmpty().toMutableList()
            if (fromIndex !in current.indices) return@edit
            val target = toIndex.coerceIn(0, current.lastIndex)
            current.add(target, current.removeAt(fromIndex))
            prefs[KEY_HOME_APPS] = current.encodeList()
        }
    }

    /** Drops entries whose app is no longer installed. */
    suspend fun pruneMissing(installedKeys: Set<String>) {
        context.dataStore.edit { prefs ->
            val home = prefs[KEY_HOME_APPS]?.decodeList().orEmpty()
            val keptHome = home.filter { it in installedKeys }
            if (keptHome.size != home.size) prefs[KEY_HOME_APPS] = keptHome.encodeList()

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
        val KEY_FONT = stringPreferencesKey("font_choice")
        val KEY_ICON_SIZE = stringPreferencesKey("icon_size")
        val KEY_DRAWER_COLUMNS = intPreferencesKey("drawer_columns")
        val KEY_HOME_COLUMNS = intPreferencesKey("home_columns")
        val KEY_HIDDEN_APPS = stringSetPreferencesKey("hidden_apps")

        /**
         * Home apps are an ordered list, and DataStore only offers an
         * unordered Set, so the order is preserved by joining with a newline.
         * A component key is "package/activity"; neither part can contain a
         * newline, so this round-trips safely.
         */
        val KEY_HOME_APPS = stringPreferencesKey("home_apps")
        const val SEPARATOR = "\n"

        fun String.decodeList(): List<String> =
            split(SEPARATOR).filter { it.isNotBlank() }

        fun List<String>.encodeList(): String = joinToString(SEPARATOR)

        /** Falls back to the default if a stored name is unknown. */
        inline fun <reified T : Enum<T>> String.toEnum(): T? =
            enumValues<T>().firstOrNull { it.name == this }
    }
}
