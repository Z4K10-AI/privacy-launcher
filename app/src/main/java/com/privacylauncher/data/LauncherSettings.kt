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
enum class FontChoice { SYSTEM, SANS_SERIF, SERIF, MONOSPACE }

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
    val font: FontChoice = FontChoice.SYSTEM,
    val iconSize: IconSize = IconSize.MEDIUM,
    val drawerColumns: Int = DEFAULT_DRAWER_COLUMNS,
    val homeColumns: Int = DEFAULT_HOME_COLUMNS,
    /** Component keys ("package/activity") kept out of the drawer. */
    val hiddenApps: Set<String> = emptySet(),
    /** Component keys shown on the home screen, in display order. */
    val homeApps: List<String> = emptyList(),
) {
    companion object {
        const val DEFAULT_DRAWER_COLUMNS = 4
        const val DEFAULT_HOME_COLUMNS = 4
        val COLUMN_CHOICES = listOf(3, 4, 5, 6)
    }
}
