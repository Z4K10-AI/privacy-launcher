package com.privacylauncher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.HomeItem
import com.privacylauncher.data.HomeLayoutCodec
import com.privacylauncher.data.HomeLayoutOps
import com.privacylauncher.data.IconLoader
import com.privacylauncher.data.LauncherSettings
import com.privacylauncher.designsystem.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renaming a folder the way a person does: delete the old name, type a new one.
 *
 * Found by the screenshot script, not by a person: it deleted "Folder" with
 * the backspace key, typed "Web", and got "WebFolder". The folder name is
 * saved on every key press, and a name that is saved empty reads back as
 * the default, "Folder". The name box then picked the default up as the
 * new name, so the moment it was empty "Folder" was back in it, with the
 * cursor in front, and whatever was typed next went in before it.
 *
 * The storage here is the real one, the same edit and the same encoding the
 * launcher saves with, so the default comes back exactly as it does on a
 * phone.
 */
@RunWith(AndroidJUnit4::class)
class FolderRenameTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun entry(name: String) = AppEntry("p.${name.lowercase()}", "A", name)

    private var stored by mutableStateOf<List<HomeItem>>(
        listOf(HomeItem.Folder("f1", "Folder", listOf("p.a/A", "p.b/A"))),
    )

    private val storedName: String get() = (stored.single() as HomeItem.Folder).name

    /** What the launcher does with a new name: edit, save, read back. */
    private fun save(name: String) {
        val edited = HomeLayoutOps.renameFolder(stored, "f1", name)
        stored = HomeLayoutCodec.decode(HomeLayoutCodec.encode(edited))
    }

    private fun show() {
        compose.setContent {
            LauncherTheme(settings = LauncherSettings()) {
                FolderSheet(
                    name = storedName,
                    entries = listOf(entry("A"), entry("B")),
                    iconSize = 52.dp,
                    iconLoader = IconLoader(context),
                    editMode = false,
                    onLaunch = {},
                    onRename = ::save,
                    onRemoveFromFolder = {},
                    onDismiss = {},
                )
            }
        }
        compose.waitForIdle()
    }

    private val nameBox get() = compose.onNode(hasContentDescription("Folder name"))

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun deletingTheOldNameAndTypingANewOneGivesTheNewName() {
        show()
        nameBox.performClick()
        nameBox.performTextInputSelection(TextRange("Folder".length))

        // Backspace, one letter at a time, as a person would.
        repeat("Folder".length) { nameBox.performKeyInput { pressKey(Key.Backspace) } }
        compose.waitForIdle()
        nameBox.performTextInput("Web")
        compose.waitForIdle()

        assertEquals("Web", storedName)
    }

    @Test
    fun aNameWithSpacesIsKeptAsTyped() {
        show()
        nameBox.performClick()
        nameBox.performTextInputSelection(TextRange("Folder".length))
        repeat("Folder".length) { nameBox.performKeyInput { pressKey(Key.Backspace) } }

        // Word by word, so the space is typed on its own, as on a phone.
        nameBox.performTextInput("Work")
        nameBox.performTextInput(" ")
        nameBox.performTextInput("apps")
        compose.waitForIdle()

        assertEquals("Work apps", storedName)
    }

    @Test
    fun aFolderLeftWithNoNameIsCalledFolder() {
        show()
        nameBox.performClick()
        nameBox.performTextInputSelection(TextRange("Folder".length))
        repeat("Folder".length) { nameBox.performKeyInput { pressKey(Key.Backspace) } }
        compose.waitForIdle()

        // Nobody is left with a folder that has no name at all.
        assertEquals("Folder", storedName)
    }
}
