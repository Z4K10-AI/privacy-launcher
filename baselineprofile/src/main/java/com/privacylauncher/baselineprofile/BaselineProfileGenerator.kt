package com.privacylauncher.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
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
 *  - **Starting up and drawing the home screen.** The only path every user
 *    takes, every time, and the one where slowness is least forgivable: a
 *    launcher that stutters on the Home button feels like a broken phone.
 *  - **Opening the app drawer and scrolling it.** The next most common
 *    action, and the one with the most Compose work behind it.
 *  - **Opening Settings.** Less common, but it pulls in a large slice of the
 *    Compose material layer that nothing else touches, so leaving it out
 *    would make the first visit to Settings the slowest screen in the app.
 *  - **Searching, the quick controls panel and the privacy dashboard.** Each
 *    is a first use that would otherwise be interpreted: the fuzzy matcher,
 *    a second sliding panel, and the dashboard's own drawing.
 *
 * ## What this recording got wrong once, and how to tell
 *
 * A fresh install has an empty DataStore, so the first thing `MainActivity`
 * draws is first-run onboarding, not the home screen. The earlier version of
 * this journey went looking for the drawer with `findObject(...)?.let { }`,
 * found nothing behind the welcome page, skipped its body and reported
 * success. The profile it recorded held 45 onboarding classes and **nothing**
 * from `HomeScreen`, `AppDrawer` or `SettingsScreen` -- a profile for the one
 * screen each user sees exactly once. It shipped, and the startup comparison
 * run against it could not possibly have shown a difference.
 *
 * [goHome] now walks past onboarding, and every step either finds what it
 * wants or throws. The check that catches a repeat is cheap, so do it after
 * re-recording:
 *
 * ```bash
 * grep -c AppDrawer app/src/release/generated/baselineProfiles/baseline-prof.txt
 * ```
 *
 * Zero means this happened again.
 *
 * Onboarding's own classes do stay in the profile, because reaching the home
 * screen on a fresh install means passing through it. That is not the budget
 * this file would have chosen, but it is honest: it is genuinely the first
 * thing a new user sees, and the alternative is a profile recorded from a
 * state no new install is ever in.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE) {
        goHome()

        // The drawer, and its grid if there is enough to scroll.
        //
        // On the AOSP emulator this is recorded on there are twelve
        // launchable apps, which is three rows in a four-column grid: it does
        // not overflow, so it is not scrollable and there is no scrollable
        // node to fling. On a phone with a real app list there is. Recording
        // here therefore leaves the fling path out of the profile, and the
        // honest fix is to re-record on a full phone rather than to pretend
        // -- see FrameTimingBenchmark, which hit the same wall.
        openDrawer()
        device.findObject(By.scrollable(true))?.let { grid ->
            grid.setGestureMargin(device.displayWidth / 5)
            grid.fling(Direction.DOWN)
            device.waitForIdle()
            grid.fling(Direction.UP)
            device.waitForIdle()
        }
        closeDrawer()

        // Twice more. Opening and closing the drawer is the one piece of
        // motion nobody using the launcher can avoid, and once through is
        // not always enough for every branch of it to be recorded.
        repeat(2) {
            openDrawer()
            closeDrawer()
        }

        // Search: each letter typed re-ranks the whole list.
        openDrawer()
        searchFor("ca")
        closeDrawer()

        // The quick controls panel, which slides in from the side.
        openQuickPanel()
        device.pressBack()
        device.waitForIdle()

        // Settings, and a scroll through it.
        openSettings()
        device.findObject(By.scrollable(true))?.let { list ->
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(Direction.DOWN)
            device.waitForIdle()
        }
        // The privacy dashboard, reached from Settings: the score ring, the
        // permission groups and the tracker card.
        openPrivacyDashboard()
        device.pressBack()
        device.waitForIdle()

        device.pressBack()
        device.waitForIdle()
    }
}
