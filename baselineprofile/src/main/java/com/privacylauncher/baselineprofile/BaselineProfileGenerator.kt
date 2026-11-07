package com.privacylauncher.baselineprofile

import android.content.Intent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records which code runs during startup and the first few interactions, so
 * Android can compile it ahead of time instead of interpreting it the first
 * time you ever open the launcher.
 *
 * What the journey below covers, and why each part is in it:
 *
 *  - **Starting up and drawing the home screen.** The only path that every
 *    user takes, every time, and the one where slowness is least forgivable:
 *    a launcher that stutters on the Home button feels like a broken phone.
 *  - **Opening the app drawer and scrolling it.** The next most common
 *    action, and the one with the most Compose work behind it.
 *  - **Opening settings.** Less common, but it pulls in a large slice of the
 *    Compose material layer that nothing else touches, so leaving it out
 *    would make the first visit to settings the slowest screen in the app.
 *
 * Deliberately not here: focus mode, the privacy dashboard and onboarding.
 * A profile is a budget, not a wish list -- every method added costs install
 * time and storage, and these are either once-ever (onboarding) or rare
 * enough that paying for them on every install is the wrong trade.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE) {
        pressHome()

        // An explicit intent, not startActivityAndWait()'s default.
        //
        // The default asks the package manager for the package's *launcher*
        // intent, and a launcher does not have one: this app's activity is
        // declared with CATEGORY_HOME, not CATEGORY_LAUNCHER, because it is
        // the home screen rather than an icon on one. Asking for an intent
        // that cannot exist fails with "Unable to acquire intent for
        // package", which reads like a missing app and is really a category
        // mismatch.
        startActivityAndWait(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                setClassName(PACKAGE, "$PACKAGE.MainActivity")
            }
        )

        // The app list loads off the main thread, so wait for it rather than
        // racing it: a profile recorded before the icons arrive would miss
        // the very code that makes the first draw slow.
        device.waitForIdle()

        // Swipe up for the drawer, then scroll it.
        device.swipe(
            device.displayWidth / 2, (device.displayHeight * 0.75).toInt(),
            device.displayWidth / 2, (device.displayHeight * 0.25).toInt(),
            10,
        )
        device.waitForIdle()
        device.findObject(By.scrollable(true))?.let { list ->
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(Direction.DOWN)
            device.waitForIdle()
            list.fling(Direction.UP)
            device.waitForIdle()
        }

        device.pressBack()
        device.waitForIdle()

        // Settings, via its button on the home screen.
        device.findObject(By.desc("Settings"))?.click()
        device.wait(Until.hasObject(By.textContains("Appearance")), TIMEOUT_MS)
        device.findObject(By.scrollable(true))?.let { list ->
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(Direction.DOWN)
            device.waitForIdle()
        }
        device.pressBack()
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE = "com.privacylauncher"
        const val TIMEOUT_MS = 5_000L
    }
}
