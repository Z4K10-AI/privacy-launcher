package com.privacylauncher.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start, measured with and without the baseline profile.
 *
 * The two tests are the before and after. They differ in one thing only --
 * how the app was compiled before the measurement -- so the gap between them
 * is what the profile is worth, with everything else held still:
 *
 *  - [startupNoCompilation] compiles nothing ahead of time, so the startup
 *    path is interpreted and JIT-ed as it runs. This is the worst case, and
 *    close to what someone sees the very first time they open a freshly
 *    installed app.
 *  - [startupBaselineProfile] requires the profile to be present and used.
 *    `Require` rather than `UseIfAvailable` on purpose: if the profile is
 *    missing or unusable the test fails loudly instead of quietly measuring
 *    the same thing twice and reporting a flattering "no difference".
 *
 * Macrobenchmark restarts the process for each iteration and reports the
 * median with a confidence interval, which matters here: a single
 * `am start -W` on a software-rendered emulator varied by more than the
 * effect being measured.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    /**
     * Walk past onboarding before measuring, and leave the app stopped.
     *
     * A fresh install shows the welcome flow rather than the home screen, so
     * without this every iteration timed the first draw of onboarding and
     * called it the launcher's cold start.
     */
    @Before
    fun onboarded() = ensureOnboarded()

    @Test
    fun startupNoCompilation() = measure(CompilationMode.None())

    @Test
    fun startupBaselineProfile() =
        measure(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun measure(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        // StartupTimingMetric only.
        //
        // FrameTimingMetric was here and had to go: a cold start draws too
        // few frames for the platform's frame timeline to hold a single
        // expect/actual pair, so the query behind it does not report a small
        // number -- it throws "Observed no expect/actual slices in trace"
        // and takes the whole startup measurement down with it. Frames are
        // measured where there are frames; see FrameTimingBenchmark.
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
        setupBlock = { pressHome() },
    ) {
        // The same explicit HOME intent the profile generator uses, and for
        // the same reason: this app has no launcher intent to look up.
        startActivityAndWait(homeIntent())
    }

    private companion object {
        /**
         * Ten is the usual figure. This emulator is software-rendered and
         * noisy, so fewer would leave the confidence interval wider than the
         * difference being looked for.
         */
        const val ITERATIONS = 10
    }
}
