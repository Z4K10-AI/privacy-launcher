package com.privacylauncher.baselineprofile

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/**
 * The steps both the profile recording and the frame-timing benchmarks need,
 * in one place because getting any of them subtly wrong is invisible.
 *
 * Every lookup here either finds what it wants or throws saying what it
 * wanted. That is not pedantry. The first version of this module used
 * `findObject(...)?.let { }` throughout, so a screen that was not where it was
 * expected silently did nothing -- and silently doing nothing is how a
 * baseline profile of the wrong screens got recorded, shipped, and measured
 * without anyone noticing. See [dismissOnboarding].
 */
internal const val PACKAGE = "com.privacylauncher"

internal const val TIMEOUT_MS = 5_000L

/** `R.string.onboarding_skip`. */
internal const val SKIP = "Skip"

/** The content description on the home screen's drawer handle. */
internal const val DRAWER_DESC = "Open app drawer"

/** `R.string.drawer_search_hint`, which only exists once the drawer is open. */
internal const val SEARCH_HINT = "Search apps"

/** The content description on the home screen's settings button. */
internal const val SETTINGS_DESC = "Settings"

/** `R.string.settings_theme`, the first row of the first settings section. */
internal const val FIRST_SETTINGS_ROW = "Theme"

/**
 * Home screen, running, past onboarding.
 *
 * The explicit intent is not optional. `startActivityAndWait()`'s default
 * asks the package manager for the package's *launcher* intent, and a
 * launcher does not have one: this activity is declared `CATEGORY_HOME`, not
 * `CATEGORY_LAUNCHER`, because it is the home screen rather than an icon on
 * one. Asking for an intent that cannot exist fails with "Unable to acquire
 * intent for package", which reads like a missing app and is really a
 * category mismatch.
 */
internal fun MacrobenchmarkScope.goHome() {
    pressHome()
    startActivityAndWait(homeIntent())
    device.waitForIdle()
    dismissOnboarding()
}

/** The explicit CATEGORY_HOME intent, for the reason given on [goHome]. */
internal fun homeIntent(): Intent = Intent(Intent.ACTION_MAIN).apply {
    addCategory(Intent.CATEGORY_HOME)
    setClassName(PACKAGE, "$PACKAGE.MainActivity")
}

/**
 * Get the app past onboarding once, before a cold-start measurement, and
 * leave it stopped.
 *
 * [goHome] cannot do this job from a COLD benchmark's setup block.
 * Macrobenchmark kills the process *before* the setup block runs, so
 * launching the app there to tap Skip would leave the process warm and what
 * followed would no longer be a cold start. This runs once per test method
 * instead, from `@Before`, and force-stops the app again afterwards so the
 * first measured iteration is still genuinely cold.
 *
 * Without it, every "cold start" figure this module has ever produced was the
 * time to draw the onboarding welcome page, not the home screen -- which is
 * also why a baseline profile recorded from the same wrong state could not
 * show any difference.
 */
internal fun ensureOnboarded() {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    device.pressHome()
    device.executeShellCommand(
        "am start -W -a android.intent.action.MAIN -c android.intent.category.HOME " +
            "-n $PACKAGE/$PACKAGE.MainActivity"
    )
    device.waitForIdle()
    device.wait(Until.findObject(By.text(SKIP)), TIMEOUT_MS)?.let { skip ->
        skip.click()
        check(device.wait(Until.hasObject(By.desc(DRAWER_DESC)), TIMEOUT_MS)) {
            "Tapped Skip, but the home screen never appeared: no \"$DRAWER_DESC\"."
        }
    }
    device.executeShellCommand("am force-stop $PACKAGE")
    device.pressHome()
}

/**
 * Walk past first-run onboarding, if it is on screen.
 *
 * ## Why this function exists
 *
 * Both benchmarks install the app fresh, which means an empty DataStore,
 * which means `onboardingDone` is false, which means the first thing
 * `MainActivity` shows is the four-page welcome flow -- not the home screen.
 * Every later step then looks for something that is not there.
 *
 * That went unnoticed for a whole pass because nothing failed. The profile
 * generator looked for the drawer with `findObject(...)?.let { }`, found
 * nothing, skipped the body and reported success, so the recorded profile
 * held 45 onboarding classes and **zero** references to `AppDrawer`,
 * `HomeScreen` or `SettingsScreen`. The launcher shipped a baseline profile
 * for a screen each user sees exactly once, the startup comparison that was
 * run against it could not have shown a difference, and the README described
 * a journey through the drawer and settings that had never been recorded.
 *
 * So this is called from [goHome], not from the individual journeys: the only
 * safe default is that nothing gets to assume it is past onboarding.
 *
 * Tapping Skip rather than walking the pages is deliberate. Skip is on every
 * page but the last and finishes immediately, and what is wanted here is the
 * state after onboarding, not a recording of onboarding itself.
 */
internal fun MacrobenchmarkScope.dismissOnboarding() {
    val skip = device.wait(Until.findObject(By.text(SKIP)), TIMEOUT_MS) ?: return
    skip.click()
    // Wait for the home screen, not merely for Skip to vanish: finishing
    // onboarding writes a flag to DataStore and the home screen appears when
    // that write comes back through the settings flow.
    check(device.wait(Until.hasObject(By.desc(DRAWER_DESC)), TIMEOUT_MS)) {
        "Tapped Skip, but the home screen never appeared: no \"$DRAWER_DESC\"."
    }
}

/**
 * Swipe up from near the bottom of the screen to open the drawer, and check
 * that it opened.
 *
 * Deliberately unhurried. The drawer follows the finger, so each move event
 * is a frame; a ten-step swipe is over in about fifty milliseconds and leaves
 * almost nothing to measure but the spring settling afterwards.
 */
internal fun MacrobenchmarkScope.openDrawer(verify: Boolean = true) {
    device.swipe(
        device.displayWidth / 2, (device.displayHeight * 0.85).toInt(),
        device.displayWidth / 2, (device.displayHeight * 0.15).toInt(),
        SWIPE_STEPS,
    )
    device.waitForIdle()
    if (verify) {
        check(device.wait(Until.hasObject(By.text(SEARCH_HINT)), TIMEOUT_MS)) {
            "Swiping up did not open the drawer: no \"$SEARCH_HINT\" field."
        }
    }
}

/** Open Settings from the home screen and wait for its first row. */
internal fun MacrobenchmarkScope.openSettings() {
    // By.desc, because it is an icon. It has to be reached from the home
    // screen rather than the drawer, where Android's own Settings app is a
    // row carrying the same label.
    val gear = device.wait(Until.findObject(By.desc(SETTINGS_DESC)), TIMEOUT_MS)
        ?: error("No \"$SETTINGS_DESC\" button on the home screen.")
    gear.click()
    // By.text("Theme"), not the section heading "Appearance": SettingsGroup
    // draws its title through title.uppercase(), so the accessibility tree
    // holds "APPEARANCE" and a wait for "Appearance" simply times out and
    // carries on as though the screen were up.
    check(device.wait(Until.hasObject(By.text(FIRST_SETTINGS_ROW)), TIMEOUT_MS)) {
        "Settings did not open: no \"$FIRST_SETTINGS_ROW\" row appeared."
    }
}

/**
 * Close the drawer and wait until the home screen is back.
 *
 * Two back presses, not one, and the reason is worth keeping. The drawer
 * opens with the keyboard up -- `autoKeyboard` is on by default, because
 * typing two letters is the quickest way into an app -- so the first back
 * dismisses the IME and leaves the drawer exactly where it was. Measured on
 * the emulator:
 *
 * ```
 * after swipe: "Close app drawer"   mInputShown=true
 * after back1: "Close app drawer"   mInputShown=false
 * after back2: "Open app drawer"    mInputShown=false
 * ```
 *
 * That is correct behaviour for the app and a trap for anything driving it.
 * The profile generator pressed back once, carried on, and then looked for
 * the settings button on a screen that was still showing the drawer.
 */
internal fun MacrobenchmarkScope.closeDrawer() {
    repeat(MAX_BACKS) {
        if (device.hasObject(By.desc(DRAWER_DESC))) return
        device.pressBack()
        device.waitForIdle()
        device.wait(Until.hasObject(By.desc(DRAWER_DESC)), SHORT_TIMEOUT_MS)
    }
    check(device.hasObject(By.desc(DRAWER_DESC))) {
        "The drawer did not close after $MAX_BACKS back presses: still no " +
            "\"$DRAWER_DESC\" on screen."
    }
}

private const val SWIPE_STEPS = 40
private const val MAX_BACKS = 3
private const val SHORT_TIMEOUT_MS = 2_000L
