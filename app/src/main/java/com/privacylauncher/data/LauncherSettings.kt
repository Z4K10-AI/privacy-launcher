package com.privacylauncher.data

import androidx.compose.runtime.Immutable

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
@Immutable
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
    /**
     * Open the keyboard as soon as the drawer settles open.
     *
     * On by default, because typing two letters is the fastest way into an
     * app. Off is a real preference though: the keyboard covers half the grid,
     * so anyone who browses rather than types will want it off.
     */
    val autoKeyboard: Boolean = true,
    /**
     * Show a "most used" row at the top of the drawer.
     *
     * The counts behind it are a usage record, so they are kept in this app's
     * private storage, never leave the device, and can be erased from
     * Settings. Turning this off stops the counting as well as the row.
     */
    val showMostUsed: Boolean = true,
    /**
     * Include contacts in drawer search.
     *
     * Off until the user turns it on, which is also the only thing that ever
     * triggers the READ_CONTACTS request. See ContactSearch for what is read
     * and what is deliberately not.
     */
    val searchContacts: Boolean = false,
    /**
     * Include Android's own settings screens in drawer search.
     *
     * Off until the user turns it on, like contacts. Nothing private is
     * read to offer them -- the list is a fixed table of shortcuts -- but
     * search results the user never asked for are still noise, and the
     * rule for this launcher is that search finds apps and anything more is
     * a choice.
     */
    val searchSettings: Boolean = false,
    /** Require the device PIN, pattern or biometric to open hidden apps. */
    val lockHiddenApps: Boolean = true,
    /**
     * Component keys that stay visible while focus mode is on.
     *
     * Empty by default, which is why the focus screen refuses to start a
     * session until at least one app is chosen: focus with nothing allowed
     * would empty the home screen and the drawer at once.
     */
    val focusAllowed: Set<String> = emptySet(),
    /**
     * When a hand-started focus session ends, as epoch milliseconds.
     *
     * 0 means no session. [FocusClock.NO_END] means one with no end time.
     * Stored rather than held in memory so a session survives the launcher
     * being killed, which on a 4 GB phone happens often.
     */
    val focusUntil: Long = 0L,
    /**
     * Ignore the schedule until this instant, as epoch milliseconds.
     *
     * What "end focus" does during a scheduled window. Without it, ending a
     * session the schedule had started would simply switch it straight back
     * on, and the only way out would be to turn the whole schedule off.
     */
    val focusSnoozeUntil: Long = 0L,
    val focusSchedule: FocusSchedule = FocusSchedule(),
    /**
     * Minimal mode: a text-only home screen.
     *
     * A separate setting from focus mode, and they compose: minimal mode
     * changes how the home screen looks, focus changes what is on it.
     */
    val minimalMode: Boolean = false,
    /**
     * Whether first-run onboarding has been finished or skipped.
     *
     * Read through its own flow rather than this one, because everything
     * else here has a sensible default that can be shown while the stored
     * file is still being read, and this does not: defaulting to `false`
     * for those few milliseconds would flash the welcome screen at someone
     * who set the launcher up months ago. See `LauncherViewModel.onboarded`.
     */
    val onboardingDone: Boolean = false,
) {
    companion object {
        const val DEFAULT_DRAWER_COLUMNS = 4
        const val DEFAULT_HOME_COLUMNS = 4
        val COLUMN_CHOICES = listOf(3, 4, 5, 6)

        /** A dock wider than this stops being a dock and starts being a row. */
        const val MAX_DOCK_APPS = 5

        /** How many apps the "most used" row shows. */
        const val MOST_USED_COUNT = 4

        /** The step the focus schedule's time controls move in, in minutes. */
        const val FOCUS_TIME_STEP = 15
    }
}
