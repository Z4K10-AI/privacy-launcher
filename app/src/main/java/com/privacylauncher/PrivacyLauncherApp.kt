package com.privacylauncher

import android.app.Application
import com.privacylauncher.data.AppRepository
import com.privacylauncher.data.ContactRepository
import com.privacylauncher.data.IconLoader
import com.privacylauncher.data.PrivacyScanner
import com.privacylauncher.data.TrackerScanner
import com.privacylauncher.data.QuickControlsRepository
import com.privacylauncher.data.SettingsRepository

/**
 * Application singleton.
 *
 * Holds the objects that should exist exactly once for the life of the
 * process. This is deliberately hand-wired rather than using a dependency
 * injection library: it is six fields, and it keeps the APK smaller and the
 * dependency list shorter.
 */
class PrivacyLauncherApp : Application() {

    val appRepository: AppRepository by lazy { AppRepository(this) }

    // Held as a Lazy rather than only as a delegated property so that
    // onTrimMemory can ask whether it exists without creating it.
    private val iconLoaderLazy = lazy { IconLoader(this) }
    val iconLoader: IconLoader by iconLoaderLazy
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val contactRepository: ContactRepository by lazy { ContactRepository(this) }
    val privacyScanner: PrivacyScanner by lazy { PrivacyScanner(this) }
    val trackerScanner: TrackerScanner by lazy { TrackerScanner(this) }
    val quickControls: QuickControlsRepository by lazy { QuickControlsRepository(this) }

    /**
     * Hands the icon cache back when the system is short of memory.
     *
     * This is the one place the app has to answer that request, and for a
     * launcher the stake is specific: a launcher that gets killed makes the
     * home button slow, which is the one thing a home screen cannot be. The
     * icons are both the only large thing held and the cheapest to rebuild.
     * [IconCachePolicy] has the reasoning.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Asking for iconLoader directly would build a cache in order to
        // empty it, which is the opposite of the point.
        if (iconLoaderLazy.isInitialized()) iconLoader.trim(level)
        // The tracker list is read from the app's own files in a few
        // milliseconds, so it is never worth keeping when memory is short.
        // A scan in progress holds its own copy and is not disturbed.
        trackerScanner.trim()
    }

    /**
     * The older callback. Some vendor builds, Samsung's among them, still
     * raise this instead of (or as well as) a trim level, and this phone is
     * a Samsung.
     */
    override fun onLowMemory() {
        super.onLowMemory()
        if (iconLoaderLazy.isInitialized()) iconLoader.clear()
        trackerScanner.trim()
    }
}
