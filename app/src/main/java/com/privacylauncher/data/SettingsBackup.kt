package com.privacylauncher.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

/**
 * Reads and writes a settings backup as a plain JSON file.
 *
 * **Where the file goes.** Nowhere, until the user says so. The launcher has
 * no network permission and no storage permission; backing up opens Android's
 * own file picker, and the file is written wherever the user points it. There
 * is no cloud copy, no "sync", and nothing is written on a schedule.
 *
 * **Why JSON by hand.** `org.json` is part of Android, so this adds no
 * dependency and no bytes to the APK. It is also readable: a backup you
 * cannot open in a text editor is a backup you have to trust, and the whole
 * point of this app is not having to.
 *
 * **What is deliberately left out**, and the restore screen says so:
 *
 *  - **Launch counts.** They are a record of what you use and when you
 *    started using it, which is the most revealing thing this app stores.
 *    Settings belong in a backup; a usage history does not, and copying it
 *    into a file the user may mail to themselves would undo the care taken
 *    to keep it in private storage.
 *  - **A running focus session.** `focusUntil` and the snooze are instants,
 *    not preferences. Restoring last week's end time would either switch
 *    focus on for no reason or switch it off while you were using it. The
 *    *schedule* is a preference and is kept.
 *  - **Whether onboarding is done.** Restoring happens from inside the app,
 *    so by then it is.
 *
 * Decoding is deliberately forgiving: anything missing or malformed falls
 * back to the default rather than throwing. A hand-edited or truncated file
 * should cost you the settings it mangled, not refuse to restore at all.
 */
/**
 * What happened on the last backup or restore, so the screen can say so.
 *
 * Separate cases for "this is not a backup file" and "the file could not be
 * read": one is the user's mistake and fixable by picking another file, the
 * other is the system's and is not, and a single "failed" would leave them
 * guessing which.
 */
enum class BackupResult {
    SAVED,
    SAVE_FAILED,
    RESTORED,
    RESTORE_NOT_A_BACKUP,
    RESTORE_UNREADABLE,
}

internal object SettingsBackup {

    const val FORMAT = "privacy-launcher-settings"
    const val VERSION = 1

    /**
     * Suggested filename for the file picker.
     *
     * Dated from the phone's own clock, not UTC. `Instant` would be simpler
     * but puts yesterday's date on a file saved after midnight anywhere east
     * of Greenwich, which looks like a bug to the person reading their own
     * Downloads folder. `savedAt` inside the file stays UTC, where an
     * unambiguous instant is what is wanted.
     */
    fun suggestedFileName(): String =
        "privacy-launcher-settings-${LocalDate.now()}.json"

    fun encode(s: LauncherSettings): String {
        val body = JSONObject().apply {
            put("themeMode", s.themeMode.name)
            put("accent", s.accent.name)
            put("dynamicColor", s.dynamicColor)
            put("font", s.font.name)
            put("iconSize", s.iconSize.name)
            put("drawerColumns", s.drawerColumns)
            put("homeColumns", s.homeColumns)
            put("hiddenApps", JSONArray(s.hiddenApps.toList()))
            // Reuses the same codec the live layout is stored with, so the
            // two can never drift into different shapes.
            put("homeItems", JSONArray(HomeLayoutCodec.encode(s.homeItems)))
            put("dockApps", JSONArray(s.dockApps))
            put("showGlance", s.showGlance)
            put("autoKeyboard", s.autoKeyboard)
            put("showMostUsed", s.showMostUsed)
            put("searchContacts", s.searchContacts)
            put("lockHiddenApps", s.lockHiddenApps)
            put("focusAllowed", JSONArray(s.focusAllowed.toList()))
            put(
                "focusSchedule",
                JSONObject().apply {
                    put("enabled", s.focusSchedule.enabled)
                    put("startMinute", s.focusSchedule.startMinute)
                    put("endMinute", s.focusSchedule.endMinute)
                },
            )
            put("minimalMode", s.minimalMode)
        }

        return JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("savedAt", Instant.now().toString())
            put("settings", body)
        }.toString(2)
    }

    /**
     * Parses a backup file.
     *
     * Returns null only when the file is not one of ours at all, or is from a
     * newer version this build cannot be sure it understands. Everything else
     * degrades to defaults field by field.
     */
    fun decode(raw: String): LauncherSettings? {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (root.optString("format") != FORMAT) return null
        if (root.optInt("version", 0) > VERSION) return null
        val o = root.optJSONObject("settings") ?: return null

        val defaults = LauncherSettings()
        return LauncherSettings(
            themeMode = o.enum("themeMode") ?: defaults.themeMode,
            accent = o.enum("accent") ?: defaults.accent,
            dynamicColor = o.optBoolean("dynamicColor", defaults.dynamicColor),
            font = o.enum("font") ?: defaults.font,
            iconSize = o.enum("iconSize") ?: defaults.iconSize,
            drawerColumns = o.clampedColumns("drawerColumns", defaults.drawerColumns),
            homeColumns = o.clampedColumns("homeColumns", defaults.homeColumns),
            hiddenApps = o.stringSet("hiddenApps"),
            homeItems = HomeLayoutCodec.decode(o.optJSONArray("homeItems")?.toString()),
            dockApps = o.stringList("dockApps").take(LauncherSettings.MAX_DOCK_APPS),
            showGlance = o.optBoolean("showGlance", defaults.showGlance),
            autoKeyboard = o.optBoolean("autoKeyboard", defaults.autoKeyboard),
            showMostUsed = o.optBoolean("showMostUsed", defaults.showMostUsed),
            searchContacts = o.optBoolean("searchContacts", defaults.searchContacts),
            lockHiddenApps = o.optBoolean("lockHiddenApps", defaults.lockHiddenApps),
            focusAllowed = o.stringSet("focusAllowed"),
            focusSchedule = o.optJSONObject("focusSchedule").let { f ->
                if (f == null) defaults.focusSchedule
                else FocusSchedule(
                    enabled = f.optBoolean("enabled", false),
                    startMinute = f.optInt("startMinute", defaults.focusSchedule.startMinute)
                        .mod(FocusClock.MINUTES_PER_DAY),
                    endMinute = f.optInt("endMinute", defaults.focusSchedule.endMinute)
                        .mod(FocusClock.MINUTES_PER_DAY),
                )
            },
            minimalMode = o.optBoolean("minimalMode", defaults.minimalMode),
        )
    }

    // --- small readers ----------------------------------------------------
    //
    // A backup can be hand-edited, so every one of these treats the file as
    // untrusted input rather than as something this app wrote.

    private inline fun <reified T : Enum<T>> JSONObject.enum(name: String): T? {
        val raw = optString(name).takeIf { it.isNotBlank() } ?: return null
        return enumValues<T>().firstOrNull { it.name == raw }
    }

    /**
     * A column count outside the offered range would produce a grid the
     * settings screen cannot show or undo, so it falls back instead.
     */
    private fun JSONObject.clampedColumns(name: String, fallback: Int): Int {
        val value = optInt(name, fallback)
        return if (value in LauncherSettings.COLUMN_CHOICES) value else fallback
    }

    private fun JSONObject.stringList(name: String): List<String> {
        val array = optJSONArray(name) ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                array.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }

    private fun JSONObject.stringSet(name: String): Set<String> =
        stringList(name).toSet()
}
