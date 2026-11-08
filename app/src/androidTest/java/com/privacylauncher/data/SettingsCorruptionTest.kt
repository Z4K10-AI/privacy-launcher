package com.privacylauncher.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A damaged preferences file must not brick the home screen.
 *
 * The launcher reads its settings before it can draw, so an unreadable
 * settings file used to mean a crash on every single start -- with no way
 * out, because the thing crashing is the home screen. The user would be left
 * pressing home and getting nothing.
 *
 * [settingsCorruptionHandler] trades the settings for a launcher that opens.
 * This checks the trade actually happens, on a real file, with a real
 * DataStore, using the very handler the app installs rather than another one
 * configured to look like it.
 */
@RunWith(AndroidJUnit4::class)
class SettingsCorruptionTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var file: File

    /**
     * Opens a DataStore over [target], runs [block], then shuts it down.
     *
     * The shutdown is the point. DataStore allows one live instance per file
     * and throws if a second one appears, so a test that wants to model "and
     * then the launcher started again" has to actually close the first one.
     * Cancelling the scope and waiting for it is what releases the file.
     */
    private fun <T> overFile(target: File, block: suspend (DataStore<Preferences>) -> T): T =
        runBlocking {
            val job = Job()
            val scope = CoroutineScope(Dispatchers.IO + job)
            try {
                block(
                    PreferenceDataStoreFactory.create(
                        corruptionHandler = settingsCorruptionHandler,
                        scope = scope,
                        produceFile = { target },
                    )
                )
            } finally {
                job.cancelAndJoin()
            }
        }

    @Before
    fun setUp() {
        // A scratch file, not the launcher's own: this test deliberately
        // damages it, and doing that to the real one would break whatever
        // test ran next.
        file = File(context.cacheDir, "corruption-${System.nanoTime()}.preferences_pb")
        file.delete()
    }

    @Test
    fun aFileFullOfRubbishReadsAsDefaultsInsteadOfThrowing() {
        file.writeText("this is not a protocol buffer, it is a sentence")

        // Before the handler existed, this line threw CorruptionException and
        // the launcher died with it.
        val prefs = overFile(file) { it.data.first() }
        assertTrue("expected an empty set of preferences", prefs.asMap().isEmpty())
    }

    @Test
    fun aTruncatedFileAlsoRecovers() {
        // The shape real damage takes: a write that stopped halfway.
        overFile(file) { store ->
            store.edit { it[stringPreferencesKey("accent")] = "TEAL" }
        }
        val bytes = file.readBytes()
        assertTrue("nothing was written, so there is nothing to truncate", bytes.size > 2)
        file.writeBytes(bytes.copyOf(bytes.size / 2))

        assertTrue(overFile(file) { it.data.first() }.asMap().isEmpty())
    }

    @Test
    fun theLauncherCanStillBeConfiguredAfterRecovering() {
        // Recovering is only useful if the file is usable afterwards. A
        // handler that returned defaults but left the bad file in place
        // would hand back defaults forever and lose every later change.
        file.writeText("rubbish")
        val key = stringPreferencesKey("accent")

        overFile(file) { store ->
            assertTrue(store.data.first().asMap().isEmpty())
            store.edit { it[key] = "ROSE" }
            assertEquals("ROSE", store.data.first()[key])
        }

        // And it survives being read back by a fresh instance over the same
        // file, which is what the next launch does.
        assertEquals("ROSE", overFile(file) { it.data.first()[key] })
    }

    @Test
    fun theRealSettingsFileIsStillReadable() = runBlocking {
        // A sanity check on the production wiring: the repository the app
        // actually builds can read its own file. If the name or the handler
        // were wrong, this is where it would show.
        val repo = SettingsRepository(context)
        repo.setMinimalMode(false)
        assertEquals(false, repo.settings.first().minimalMode)
        assertTrue(
            "the preferences file is not where the app thinks it is",
            File(context.filesDir, "datastore/$SETTINGS_FILE.preferences_pb").exists(),
        )
    }
}
