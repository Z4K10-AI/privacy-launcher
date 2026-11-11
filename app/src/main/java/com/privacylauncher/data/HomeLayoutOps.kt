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

    /**
     * Moves the item called [fromId] to where the item called [toId] is.
     *
     * This is what a drag on the home screen calls, and it takes ids rather
     * than positions for a reason that cost a bug: the screen does not
     * always draw everything that is stored. Focus mode hides apps, and an
     * app uninstalled a moment ago is skipped until it is cleaned up. So
     * "second on screen" and "second on disk" can be different items, and a
     * drag reported by position moved whichever one happened to be stored
     * there -- often one the user could not see.
     *
     * An id means the same thing in both lists. Looking the positions up
     * here, inside the same edit that writes the result, also means they
     * are read from the layout as it is at that instant rather than as it
     * was when the finger went down.
     *
     * Dragging forward lands just after the target and dragging backward
     * just before it, exactly as [move] does, so with nothing hidden the two
     * give the same answer. Hidden items between the two are stepped over
     * and keep their order.
     */
    fun moveById(items: List<HomeItem>, fromId: String, toId: String): List<HomeItem> {
        val from = items.indexOfFirst { it.id == fromId }
        val to = items.indexOfFirst { it.id == toId }
        if (from < 0 || to < 0) return items
        return move(items, from, to)
    }

    /** [combine], naming the two items by id. See [moveById] for why. */
    fun combineById(
        items: List<HomeItem>,
        fromId: String,
        ontoId: String,
        newFolderId: String,
        folderName: String,
    ): List<HomeItem> {
        val from = items.indexOfFirst { it.id == fromId }
        val onto = items.indexOfFirst { it.id == ontoId }
        if (from < 0 || onto < 0) return items
        return combine(items, from, onto, newFolderId, folderName)
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
     * Whether [installedKeys] can be believed enough to delete against.
     *
     * [prune] is a filter and does exactly what it is told: handed an empty
     * set it returns an empty layout, which is correct for a filter and
     * catastrophic for a user. The set comes from asking the system what is
     * installed, and that question can fail -- a binder transaction that
     * throws, or a query made while the package manager is still coming up
     * after a reboot -- and a failed query looks exactly like a phone with
     * no apps on it.
     *
     * So the rule is: an empty answer is never a true statement about a
     * running phone, because a phone with no launchable apps has no Settings
     * either. Treat it as a failure to ask rather than an answer, and leave
     * the layout alone. Nothing is lost by waiting for the next refresh; the
     * alternative is the home screen, the dock, the hidden list and the
     * focus list all being wiped at once, written to disk, with no undo.
     *
     * A short-but-not-empty answer is not caught here and cannot be: three
     * apps is a believable phone. In practice the failure modes throw rather
     * than silently truncate, which is why [AppRepository.loadApps] reports
     * a failure as null instead of as an empty list.
     */
    fun prunable(installedKeys: Set<String>): Boolean = installedKeys.isNotEmpty()

    /**
     * Drops everything referring to an app that is no longer installed.
     *
     * Runs when the app list loads, which is also what cleans up after a
     * restored backup from a phone that had apps this one does not. Callers
     * must check [prunable] first; this function trusts what it is given.
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
