package com.privacylauncher.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads and caches app icons.
 *
 * Why this exists instead of just calling the system on each frame:
 *
 *  - Decoding an icon is slow enough to stutter a scrolling list, so it is
 *    done on a background thread.
 *  - Every icon is scaled down to [ICON_PX] before caching. A raw Samsung app
 *    icon can be 192x192 or larger; at 128x128 each one costs about 64 KB, so
 *    a full cache is roughly 10 MB. That matters on a 4 GB phone.
 *  - [LruCache] drops the least recently seen icons automatically, so the
 *    drawer can hold 300 apps without the cache growing without bound.
 */
class IconLoader(private val context: Context) {

    private val cache = object : LruCache<String, ImageBitmap>(MAX_CACHED_ICONS) {}

    /** Returns a cached icon immediately, or null if it has not been loaded. */
    fun cached(entry: AppEntry): ImageBitmap? = cache.get(entry.key)

    /** Loads an icon, using the cache when possible. Safe to call repeatedly. */
    suspend fun load(entry: AppEntry): ImageBitmap? {
        cache.get(entry.key)?.let { return it }

        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val component = ComponentName(entry.packageName, entry.activityName)
                val drawable = context.packageManager.getActivityIcon(component)
                drawable.toBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
                    .asImageBitmap()
            }.getOrNull()
        } ?: return null

        cache.put(entry.key, bitmap)
        return bitmap
    }

    /**
     * The same, keyed by package rather than by launcher activity.
     *
     * The privacy dashboard lists packages, not drawer entries, and some of
     * them have no launcher activity to ask for an icon. The cache is shared
     * with the drawer's icons but the keys are prefixed, so a package icon
     * and an activity icon for the same app cannot collide.
     */
    fun cachedPackage(packageName: String): ImageBitmap? = cache.get(packageKey(packageName))

    suspend fun loadPackage(packageName: String): ImageBitmap? {
        val key = packageKey(packageName)
        cache.get(key)?.let { return it }

        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
                    .asImageBitmap()
            }.getOrNull()
        } ?: return null

        cache.put(key, bitmap)
        return bitmap
    }

    /** Called when apps are installed or removed, so stale icons are not shown. */
    fun clear() = cache.evictAll()

    private companion object {
        const val ICON_PX = 128
        const val MAX_CACHED_ICONS = 160

        fun packageKey(packageName: String) = "pkg:$packageName"
    }
}
