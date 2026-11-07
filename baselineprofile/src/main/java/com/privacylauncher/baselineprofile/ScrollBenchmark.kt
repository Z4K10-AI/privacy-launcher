package com.privacylauncher.baselineprofile

import android.content.Intent
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing while the app drawer is scrolled.
 *
 * Startup frame timing turned out to be useless here: a cold start draws one
 * frame, so [StartupBenchmark]'s FrameTimingMetric reported a frameCount of
 * 1 and nothing worth reading. Jank lives where frames are produced in a
 * run, and in a launcher that means the drawer's grid.
 *
 * `frameDurationCpuMs` percentiles are the number that matters. P50 says how
 * it feels normally; P99 says how bad the worst frames are, and a dropped
 * frame every hundred is what reads as "this phone is slow" even when the
 * median looks fine.
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scrollDrawerNoCompilation() = measure(CompilationMode.None())

    @Test
    fun scrollDrawerBaselineProfile() =
        measure(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun measure(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        // WARM, not COLD: the drawer is the thing being measured, and a cold
        // start before every iteration would fold startup cost into frames
        // that have nothing to do with scrolling.
        startupMode = StartupMode.WARM,
        iterations = ITERATIONS,
        setupBlock = {
            pressHome()
            startActivityAndWait(
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    setClassName(PACKAGE, "$PACKAGE.MainActivity")
                }
            )
            // Open the drawer in setup so the swipe that opens it is not
            // itself measured.
            device.swipe(
                device.displayWidth / 2, (device.displayHeight * 0.80).toInt(),
                device.displayWidth / 2, (device.displayHeight * 0.20).toInt(),
                10,
            )
            device.waitForIdle()
        },
    ) {
        val grid = device.findObject(By.scrollable(true)) ?: return@measureRepeated
        // Keep the gesture away from the screen edges, where the system's
        // own navigation gestures would take it instead.
        grid.setGestureMargin(device.displayWidth / 5)
        repeat(3) {
            grid.fling(Direction.DOWN)
            device.waitForIdle()
            grid.fling(Direction.UP)
            device.waitForIdle()
        }
    }

    private companion object {
        const val PACKAGE = "com.privacylauncher"
        const val ITERATIONS = 8
    }
}
