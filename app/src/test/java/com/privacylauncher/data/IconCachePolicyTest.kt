package com.privacylauncher.data

import android.content.ComponentCallbacks2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The memory-pressure policy.
 *
 * [IconCachePolicy.targetSize] is written as a chain of `>=` comparisons, so
 * it is only correct while the trim constants really are ordered mildest to
 * most severe. That ordering is an assumption about the Android platform,
 * not about this code, and an assumption nothing checks is the kind that
 * quietly stops holding. [theLevelsAreOrderedMildestFirst] checks it, so if
 * a future SDK renumbers them this fails here rather than silently throwing
 * away every icon the first time the system is a little bit busy.
 */
class IconCachePolicyTest {

    private val max = 160

    private fun target(level: Int) = IconCachePolicy.targetSize(level, max)

    @Test
    fun `the levels are ordered mildest first, which the policy relies on`() {
        val ascending = listOf(
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
        )
        assertEquals(ascending.sorted(), ascending)
    }

    @Test
    fun `going off screen drops every icon`() {
        // Nobody is looking at them, and a launcher that is holding 10 MB
        // while invisible is the first thing the system kills.
        assertEquals(0, target(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN))
        assertEquals(0, target(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND))
        assertEquals(0, target(ComponentCallbacks2.TRIM_MEMORY_MODERATE))
        assertEquals(0, target(ComponentCallbacks2.TRIM_MEMORY_COMPLETE))
    }

    @Test
    fun `while visible it keeps enough to draw the screen`() {
        // The drawer shows roughly 20 icons at a time on a 720x1560 screen.
        // Dropping to zero while the user is looking at it would blank every
        // icon and then re-decode them, which is worse than the memory.
        val critical = target(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        assertTrue("kept $critical, too few to fill a screen", critical >= 20)
    }

    @Test
    fun `pressure that gets worse never keeps more`() {
        // The property that makes this a policy rather than a table.
        val levels = (0..90)
        val sizes = levels.map { target(it) }
        assertEquals(sizes.sortedDescending(), sizes)
    }

    @Test
    fun `a mild warning keeps most of the cache`() {
        val moderate = target(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)
        assertEquals(max / 2, moderate)
    }

    @Test
    fun `a level Android has not invented yet is still answered`() {
        // Trim levels have been added before. A new one must not fall
        // through to "keep everything", which is what `when` with no else
        // branch would have done.
        assertEquals(0, target(999))
        assertTrue(target(-5) > 0)
    }

    @Test
    fun `a tiny cache never asks for a negative size`() {
        // Integer division on a small max is how you get trimToSize(-1),
        // which LruCache treats as "evict the lot".
        for (small in 0..9) {
            for (level in listOf(5, 10, 15, 20, 80)) {
                assertTrue(
                    "max=$small level=$level",
                    IconCachePolicy.targetSize(level, small) >= 0,
                )
            }
        }
    }
}
