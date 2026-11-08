package com.privacylauncher.data

/**
 * Every rearrangement the home screen can do, as pure functions.
 *
 * These were inline in [SettingsRepository], inside `DataStore.edit` blocks.
 * That made them untestable without a device and an Android context, which
 * meant the trickiest code in the app -- index arithmetic that has to stay
 * right while a finger drags an icon across a grid -- was the only code with
 * no test at all.
 *
 * Taking a list and returning a list, rather than mutating one in place,
 * is what makes a test read as "this layout, this drag, that layout".
 */
internal object HomeLayoutOps {

    /**
     * Moves the item at [from] so that it sits at [to].
     *
     * Out-of-range indices are ignored rather than clamped for [from] and
     * clamped for [to], which matches what a drag can actually produce: the
     * dragged item always exists, but the finger can be past either end.
     */
    fun move(items: List<HomeItem>, from: Int, to: Int): List<HomeItem> {
        if (from !in items.indices) return items
        val out = items.toMutableList()
        val target = to.coerceIn(0, out.lastIndex)
        out.add(target, out.removeAt(from))
        return out
    }

    /**
     * Drops the item at [from] onto the one at [onto].
     *
     * App onto app makes a folder. App onto folder adds to it. A folder
     * dragged onto anything does nothing: merging two folders is rarely what
     * someone means and silently destroys one of the two names.
     *
     * [newFolderId] is passed in rather than generated here so a test can
     * say what the resulting folder's id should be. In the app it is a
     * random UUID.
     */
    fun combine(
        items: List<HomeItem>,
        from: Int,
        onto: Int,
        newFolderId: String,
        folderName: String,
    ): List<HomeItem> {
        if (from !in items.indices || onto !in items.indices) return items
        if (from == onto) return items

        val dragged = items[from]
        if (dragged !is HomeItem.App) return items

        val merged = when (val target = items[onto]) {
            is HomeItem.App -> HomeItem.Folder(
                id = newFolderId,
                name = folderName,
                appKeys = listOf(target.key, dragged.key),
            )
            is HomeItem.Folder -> target.copy(appKeys = target.appKeys + dragged.key)
        }

        val out = items.toMutableList()
        out[onto] = merged
        // Removing after writing is safe in both directions: when `from` is
        // before `onto` the merged item simply shifts down one place, which
        // is where it belongs once the dragged item is gone.
        out.removeAt(from)
        return out
    }

    /** Renames one folder, leaving everything else alone. */
    fun renameFolder(items: List<HomeItem>, folderId: String, name: String): List<HomeItem> =
        items.map { item ->
            if (item is HomeItem.Folder && item.id == folderId) {
                item.copy(name = name.trim())
            } else {
                item
            }
        }

    /** Takes an app out of a folder and lets the folder collapse if it is now thin. */
    fun removeFromFolder(
        items: List<HomeItem>,
        folderId: String,
        appKey: String,
    ): List<HomeItem> =
        dissolveThinFolders(
            items.map { item ->
                if (item is HomeItem.Folder && item.id == folderId) {
                    item.copy(appKeys = item.appKeys.filterNot { it == appKey })
                } else {
                    item
                }
            }
        )

    /**
     * A folder holding one app or none is clutter, so it collapses back to a
     * plain icon or disappears.
     */
    fun dissolveThinFolders(items: List<HomeItem>): List<HomeItem> =
        items.mapNotNull { item ->
            if (item !is HomeItem.Folder) {
                item
            } else {
                when (item.appKeys.size) {
                    0 -> null
                    1 -> HomeItem.App(item.appKeys.first())
                    else -> item
                }
            }
        }

    /**
     * Drops everything referring to an app that is no longer installed.
     *
     * Runs when the app list loads, which is also what cleans up after a
     * restored backup from a phone that had apps this one does not.
     */
    fun prune(items: List<HomeItem>, installedKeys: Set<String>): List<HomeItem> =
        dissolveThinFolders(
            items.mapNotNull { item ->
                when (item) {
                    is HomeItem.App -> item.takeIf { it.key in installedKeys }
                    is HomeItem.Folder ->
                        item.copy(appKeys = item.appKeys.filter { it in installedKeys })
                }
            }
        )

    /**
     * The dock after adding [key]: unchanged when it is already there or the
     * dock is full.
     */
    fun addToDock(dock: List<String>, key: String, max: Int): List<String> =
        if (key in dock || dock.size >= max) dock else dock + key
}
