package com.privacylauncher.data

import android.content.ComponentCallbacks2
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The icon cache giving memory back, against a real LruCache and real icons.
 *
 * [IconCachePolicyTest] checks the numbers the policy produces. This checks
 * that handing those numbers to [android.util.LruCache] does what the policy
 * meant -- in particular that a target of zero empties the cache, rather than
 * `trimToSize(0)` quietly doing nothing, which is the sort of thing only the
 * real class can tell us.
 */
@RunWith(AndroidJUnit4::class)
class IconCacheTrimTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * A full cache, whatever the device has on it.
     *
     * The cache is deliberately given a small ceiling. An emulator has about
     * a dozen launchable apps and a real phone has a hundred and fifty, and
     * a test that loads "as many as there are" is really testing the
     * machine it runs on: at a ceiling of 160 the emulator never fills the
     * cache, so a trim has nothing to remove and the test passes while
     * proving nothing. Filling a ceiling of 8 means full is full everywhere.
     */
    private fun fullLoader(ceiling: Int = 8): IconLoader = runBlocking {
        val loader = IconLoader(context, maxIcons = ceiling)
        val apps = AppRepository(context).loadApps().orEmpty()
        assertTrue("need some installed apps to fill a cache", apps.size > ceiling)
        apps.forEach { loader.load(it) }
        assertEquals("the cache is not full, so a trim proves nothing",
            ceiling, loader.cachedCount())
        loader
    }

    @Test
    fun goingOffScreenReleasesEveryIcon() {
        val loader = fullLoader()
        loader.trim(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
        assertEquals(0, loader.cachedCount())
    }

    @Test
    fun uiHiddenAloneIsEnoughToRelease() {
        // The level the launcher actually gets, every time the user opens
        // another app -- which is most of the launcher's life.
        val loader = fullLoader()
        loader.trim(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        assertEquals(0, loader.cachedCount())
    }

    @Test
    fun pressureWhileVisibleShrinksTheCacheWithoutEmptyingIt() {
        val loader = fullLoader()
        val before = loader.cachedCount()
        loader.trim(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        val after = loader.cachedCount()
        assertTrue("expected a shrink from $before, got $after", after < before)
        // Trimming to nothing while the drawer is on screen would blank
        // every icon the user is looking at and then decode them again.
        assertTrue("blanked the screen the user is looking at", after > 0)
    }

    @Test
    fun iconsComeBackAfterATrim() {
        // The trade only makes sense if the cache refills. A loader that
        // returned null forever after being trimmed would be worse than one
        // that never trimmed.
        runBlocking {
            val apps = AppRepository(context).loadApps().orEmpty()
            assertTrue("need an app to load an icon for", apps.isNotEmpty())
            val loader = IconLoader(context)
            val first = apps.first()
            assertTrue(loader.load(first) != null)
            loader.trim(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
            assertEquals(null, loader.cached(first))
            assertTrue("icon did not reload after a trim", loader.load(first) != null)
        }
    }
}
