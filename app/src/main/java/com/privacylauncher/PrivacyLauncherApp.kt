package com.privacylauncher

import android.app.Application
import com.privacylauncher.data.AppRepository
import com.privacylauncher.data.IconLoader
import com.privacylauncher.data.SettingsRepository

/**
 * Application singleton.
 *
 * Holds the three objects that should exist exactly once for the life of the
 * process. This is deliberately hand-wired rather than using a dependency
 * injection library: it is three fields, and it keeps the APK smaller and the
 * dependency list shorter.
 */
class PrivacyLauncherApp : Application() {

    val appRepository: AppRepository by lazy { AppRepository(this) }
    val iconLoader: IconLoader by lazy { IconLoader(this) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
}
