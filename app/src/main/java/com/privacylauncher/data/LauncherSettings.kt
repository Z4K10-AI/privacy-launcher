package com.privacylauncher.data

/** Which colour scheme to use. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Accent colour choices.
 *
 * Plain enum values with no Compose types, so this file stays usable from
 * non-UI code. The actual colours live in `ui/theme/Accents.kt`.
 */
enum class AccentColor { TEAL, BLUE, VIOLET, AMBER, ROSE, SLATE }

/**
 * Font choices.
 *
 * All four are families Android already ships, so no font files are bundled
 * and the APK does not grow.
 */
enum class FontChoice {
    /** Inter, bundled with the app. The default. */
    INTER,
    SYSTEM,
    SANS_SERIF,
    SERIF,
    MONOSPACE,
}

/** Icon sizes offered in settings, in dp. */
enum class IconSize(val dp: Int) {
    SMALL(40),
    MEDIUM(52),
    LARGE(64),
}

/**
 * Everything the user can configure, in one object.
 *
 * Read as a single Flow so the UI recomposes once per change rather than
 * subscribing to eight separate preference flows.
 */
data class LauncherSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentColor = AccentColor.TEAL,
    /**
     * Material You: let the system derive colours from the wallpaper.
     *
     * Off by default. It is computed on-device and involves no permission,
     * but contrast over an arbitrary wallpaper is unpredictable, so the
     * hand-checked accents are the safer default.
     */
    val dynamicColor: Boolean = false,
    val font: FontChoice = FontChoice.INTER,
    val iconSize: IconSize = IconSize.MEDIUM,
    val drawerColumns: Int = DEFAULT_DRAWER_COLUMNS,
    val homeColumns: Int = DEFAULT_HOME_COLUMNS,
    /** Component keys ("package/activity") kept out of the drawer. */
    val hiddenApps: Set<String> = emptySet(),
    /** The home screen layout: apps and folders, in display order. */
    val homeItems: List<HomeItem> = emptyList(),
    /** Component keys pinned to the dock, in display order. */
    val dockApps: List<String> = emptyList(),
    /** The offline "at a glance" line under the clock. */
    val showGlance: Boolean = true,
) {
    companion object {
        const val DEFAULT_DRAWER_COLUMNS = 4
        const val DEFAULT_HOME_COLUMNS = 4
        val COLUMN_CHOICES = listOf(3, 4, 5, 6)

        /** A dock wider than this stops being a dock and starts being a row. */
        const val MAX_DOCK_APPS = 5
    }
}
