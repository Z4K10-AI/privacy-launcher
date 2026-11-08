package com.privacylauncher.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing for the two things in this launcher that reliably produce
 * frames: the drawer's open and close animation, and scrolling Settings.
 *
 * ## Why the drawer's grid is not what gets scrolled here
 *
 * The obvious thing to measure is the app grid, and the first version of this
 * file did exactly that -- `findObject(By.scrollable(true))` with the drawer
 * open. It failed on every run, in both compilation modes, with
 *
 *     IllegalStateException: Observed no expect/actual slices in trace,
 *     please report bug and attach perfetto trace
 *
 * which reads like a broken trace and is nothing of the kind. The AOSP
 * emulator image has twelve launchable apps. Twelve apps in a four-column
 * grid is three rows, which does not overflow the screen, so
 * `LazyVerticalGrid` never becomes scrollable and publishes no scrollable
 * node at all: a `uiautomator dump` with the drawer open contains zero
 * `scrollable="true"` nodes, where the Settings screen contains exactly one.
 * `findObject` returned null, the measure block returned immediately, nothing
 * was drawn, and the frame query failed a hundred seconds later complaining
 * about the trace rather than about the empty measurement.
 *
 * So nothing here depends on how many apps are installed. The drawer
 * animation draws frames on any device, and Settings is one tall scrolling
 * column whatever is installed.
 *
 * ## What to read in the output
 *
 * `frameDurationCpuMs` percentiles. P50 is how it feels normally; P99 is how
 * bad the worst frames are, and one bad frame in a hundred is what reads as
 * "this phone is slow" even when the median looks fine. `frameOverrunMs`,
 * where the platform reports it, is the honest one: negative means the frame
 * finished with time to spare, positive means it missed its deadline.
 *
 * On this emulator the absolute numbers are worth little -- it renders in
 * software through SwiftShader on the same machine that is running the build
 * -- but two builds measured the same way are still comparable, which is what
 * catches a regression.
 */
@RunWith(AndroidJUnit4::class)
class FrameTimingBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun drawerAnimationNoCompilation() = drawerAnimation(CompilationMode.None())

    @Test
    fun drawerAnimationBaselineProfile() =
        drawerAnimation(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun settingsScrollNoCompilation() = settingsScroll(CompilationMode.None())

    @Test
    fun settingsScrollBaselineProfile() =
        settingsScroll(CompilationMode.Partial(BaselineProfileMode.Require))

    /**
     * Opening and closing the app drawer.
     *
     * The most-seen animation in the app: the panel follows the finger up and
     * a spring settles it. Worth measuring on its own, because it is the one
     * piece of motion a user cannot avoid.
     */
    private fun drawerAnimation(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        iterations = ITERATIONS,
        setupBlock = {
            // goHome() walks past first-run onboarding, which a freshly
            // installed app shows instead of the home screen.
            goHome()
            // Prove the gesture works before measuring with it, outside the
            // measured block: a swipe that fails to open the drawer draws
            // nothing, and an empty measurement does not fail here -- it
            // fails much later, in the trace query, pointing at the wrong
            // thing entirely.
            openDrawer()
            closeDrawer()
        },
    ) {
        repeat(OPENS_PER_ITERATION) {
            // Unverified inside the measured block: the check costs an
            // accessibility-tree poll, and setupBlock has already proved the
            // gesture lands on this screen.
            openDrawer(verify = false)
            closeDrawer()
        }
    }

    /**
     * Scrolling the Settings screen.
     *
     * The one genuinely long scrolling surface that exists regardless of what
     * is installed: six sections of rows in a single `verticalScroll` column,
     * which the accessibility tree reports as one `ScrollView`.
     */
    private fun settingsScroll(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        iterations = ITERATIONS,
        setupBlock = {
            goHome()
            openSettings()
        },
    ) {
        val list = device.findObject(By.scrollable(true))
            ?: error(
                "Settings exposes no scrollable node. It is one tall " +
                    "verticalScroll column, so this means the screen is not up " +
                    "rather than that it fitted on the display."
            )
        // Keep the gesture clear of the screen edges, where Android's own
        // navigation gestures would take it instead.
        list.setGestureMargin(device.displayWidth / 5)
        repeat(FLINGS_PER_ITERATION) {
            list.fling(Direction.DOWN)
            device.waitForIdle()
            list.fling(Direction.UP)
            device.waitForIdle()
        }
    }

    private companion object {
        const val ITERATIONS = 8
        const val OPENS_PER_ITERATION = 3
        const val FLINGS_PER_ITERATION = 2
    }
}
