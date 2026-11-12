package com.privacylauncher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.privacylauncher.R
import com.privacylauncher.data.AppEntry
import com.privacylauncher.data.FocusSource
import com.privacylauncher.data.FocusState
import com.privacylauncher.data.GlanceInfo
import com.privacylauncher.data.GroupReport
import com.privacylauncher.data.IconLoader
import com.privacylauncher.data.LauncherSettings
import com.privacylauncher.data.PermissionGroup
import com.privacylauncher.data.PermissionHolder
import com.privacylauncher.data.PrivacyReport
import com.privacylauncher.data.QuickControlsState
import com.privacylauncher.data.ScanStatus
import com.privacylauncher.data.SettingsTarget
import com.privacylauncher.data.Toggle
import com.privacylauncher.data.Tracker
import com.privacylauncher.data.TrackerFinding
import com.privacylauncher.data.TrackerReport
import com.privacylauncher.designsystem.LauncherTheme
import com.privacylauncher.system.LockDownState
import com.privacylauncher.ui.focus.FocusAppsScreen
import com.privacylauncher.ui.focus.FocusScreen
import com.privacylauncher.ui.onboarding.OnboardingScreen
import com.privacylauncher.ui.privacy.PrivacyDashboardScreen
import com.privacylauncher.ui.quick.QuickPanel
import com.privacylauncher.ui.settings.DesignPreviewScreen
import com.privacylauncher.ui.settings.GestureExplainerDialog
import com.privacylauncher.ui.settings.HiddenAppsScreen
import com.privacylauncher.ui.settings.LockDownDialog
import com.privacylauncher.ui.settings.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every screen, checked for two things a person cannot work round.
 *
 * **Everything that can be pressed has a name.** A screen reader says the
 * name; with none it says "button" and the user has to guess. Icons are the
 * usual culprits, because an icon needs no words to be understood by
 * someone who can see it.
 *
 * **Everything that can be pressed is big enough to press.** Android's
 * guidance, and this project's own rule, is 48 by 48 dp. Smaller targets
 * are missed by anyone with a tremor, large fingers, or a moving bus.
 *
 * This reads what each screen tells Android's accessibility services about
 * itself, which is exactly what TalkBack and Switch Access act on. It is
 * not the same as listening to TalkBack: it cannot tell a good name from a
 * useless one, or notice that things are read in a confusing order. Those
 * still need a person. TalkBack itself is not installed on the emulators
 * this project uses, so nobody has listened to it yet, and the README says
 * so.
 *
 * Long screens are checked a screenful at a time, scrolling between, since
 * only what is on screen can be measured.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityWalkTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val density = context.resources.displayMetrics.density
    private val loader by lazy { IconLoader(context) }

    private fun entry(name: String) =
        AppEntry(packageName = "p.${name.lowercase()}", activityName = "A", label = name)
    private val apps = listOf("Calendar", "Camera", "Clock", "Contacts", "Files", "Gallery", "Maps", "Music", "Notes", "Phone")
        .map(::entry)

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            LauncherTheme(settings = LauncherSettings()) {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
        compose.waitForIdle()
    }

    // --- the walk ----------------------------------------------------------------

    private fun describe(node: SemanticsNode): String {
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        val spoken = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
        return listOf(spoken, text).filter { it.isNotBlank() }.joinToString(" / ")
    }

    private fun pressable(node: SemanticsNode): Boolean =
        node.config.contains(SemanticsActions.OnClick) ||
            node.config.contains(SemanticsProperties.ToggleableState) ||
            node.config.contains(SemanticsActions.SetProgress)

    /** What is wrong with what is on screen right now, and how many pressable things were looked at. */
    private fun look(problems: MutableSet<String>, seen: MutableSet<String>) {
        val smallest = 48 * density - 1f // a pixel for rounding
        fun walk(node: SemanticsNode) {
            if (pressable(node)) {
                val name = describe(node)
                val drawn = node.boundsInRoot
                // Only what is wholly on screen can be measured: something
                // half scrolled away is cut off, not small.
                val whole = drawn.width >= node.size.width - 1 && drawn.height >= node.size.height - 1
                if (whole) {
                    val touch = node.touchBoundsInRoot
                    val w = (touch.width / density).toInt()
                    val h = (touch.height / density).toInt()
                    seen += "$name@${drawn.left.toInt()},${drawn.top.toInt()}"
                    if (name.isBlank()) {
                        problems += "something pressable has no name ($w by $h dp)"
                    } else if (touch.width < smallest || touch.height < smallest) {
                        problems += "\"${name.take(40)}\" is $w by $h dp to touch"
                    }
                }
            }
            node.children.forEach(::walk)
        }
        compose.onAllNodes(isRoot()).fetchSemanticsNodes().forEach(::walk)
    }

    /**
     * Checks the screen, scrolling down a screenful at a time until nothing
     * new comes into view. Returns the problems found, in a stable order.
     */
    private fun problems(scroll: Boolean = true): List<String> {
        val problems = sortedSetOf<String>()
        val seen = HashSet<String>()
        look(problems, seen)
        if (scroll) {
            repeat(30) {
                val before = seen.size
                compose.onAllNodes(isRoot()).onFirst().performTouchInput {
                    val x = centerX
                    down(Offset(x, height * 0.75f))
                    moveTo(Offset(x, height * 0.55f))
                    moveTo(Offset(x, height * 0.35f))
                    up()
                }
                compose.waitForIdle()
                look(problems, seen)
                if (seen.size == before) return problems.toList()
            }
        }
        return problems.toList()
    }

    private fun assertAccessible(screen: String, scroll: Boolean = true) {
        assertEquals("$screen:", emptyList<String>(), problems(scroll))
    }

    // --- home ----------------------------------------------------------------------

    @Composable
    private fun Home(editMode: Boolean, dock: List<AppEntry> = apps.take(3)) {
        HomeScreen(
            tiles = apps.drop(3).take(5).map { HomeTile.App(it) } +
                HomeTile.Folder("f1", "Travel", apps.take(3)),
            dock = dock,
            glance = GlanceInfo(nextAlarm = "Mon 07:30", batteryPercent = 80),
            columns = 4,
            iconSize = 52.dp,
            iconLoader = loader,
            editMode = editMode,
            gesturesEnabled = !editMode,
            onLaunch = {}, onOpenFolder = {}, onEnterEditMode = {}, onExitEditMode = {},
            onRemoveTile = {}, onMove = { _, _ -> }, onCombine = { _, _ -> },
            onMoveToDock = { _, _ -> }, onDockDrop = {}, onRemoveFromDock = {},
            onOpenDrawer = {}, onOpenSettings = {}, onOpenClock = {},
            onDrawerDrag = {}, onDrawerRelease = {}, onQuickDragStart = {},
            onQuickDrag = {}, onQuickRelease = {}, onSwipeDown = {}, onDoubleTap = {},
            onPinchIn = {},
        )
    }

    @Test
    fun theHomeScreen() {
        show { Home(editMode = false) }
        assertAccessible("home screen", scroll = false)
    }

    @Test
    fun theHomeScreenWhileEditing() {
        show { Home(editMode = true) }
        assertAccessible("home screen, editing", scroll = false)
    }

    @Test
    fun theTextOnlyHomeScreen() {
        show {
            MinimalHome(
                tiles = apps.take(6).map { HomeTile.App(it) },
                dock = apps.takeLast(2),
                glance = GlanceInfo(batteryPercent = 80),
                focusActive = false,
                gesturesEnabled = true,
                onLaunch = {}, onOpenDrawer = {}, onOpenSettings = {}, onOpenClock = {},
                onOpenFocus = {}, onDrawerDrag = {}, onDrawerRelease = {},
                onQuickDragStart = {}, onQuickDrag = {}, onQuickRelease = {},
                onSwipeDown = {}, onDoubleTap = {}, onPinchIn = {},
            )
        }
        assertAccessible("text-only home screen")
    }

    @Test
    fun anOpenFolder() {
        show {
            FolderSheet(
                name = "Travel", entries = apps.take(4), iconSize = 52.dp, iconLoader = loader,
                editMode = true, onLaunch = {}, onRename = {}, onRemoveFromFolder = {},
                onDismiss = {},
            )
        }
        assertAccessible("folder", scroll = false)
    }

    // --- the drawer ----------------------------------------------------------------

    @Composable
    private fun Drawer(query: String, extra: List<DrawerRow> = emptyList()) {
        AppDrawer(
            list = DrawerListBuilder.build(
                apps = apps,
                s = LauncherSettings(showMostUsed = false),
                rawQuery = query,
                counts = emptyMap(),
                focus = FocusState(),
            ),
            extraRows = extra,
            query = query,
            columns = 4,
            iconSize = 52.dp,
            iconLoader = loader,
            isOnHome = { false }, isInDock = { false }, dockFull = false,
            isOpen = false, autoKeyboard = false,
            onHandleDrag = {}, onHandleRelease = {}, onQueryChange = {}, onLaunch = {},
            onClose = {}, onAppInfo = {}, onUninstall = {}, onAddToHome = {},
            onRemoveFromHome = {}, onAddToDock = {}, onRemoveFromDock = {}, onHide = {},
            onOpenSettingsScreen = {}, onOpenContact = {}, onEnableContacts = {},
            onOwnAppInfo = {}, onEndFocus = {}, onOpenFocus = {},
        )
    }

    @Test
    fun theDrawer() {
        show { Drawer(query = "") }
        assertAccessible("drawer", scroll = false)
    }

    @Test
    fun theDrawerWhileSearching() {
        show {
            Drawer(
                query = "c",
                extra = listOf(
                    DrawerRow.SettingsSearchHeader,
                    DrawerRow.SettingsScreen(SettingsTarget(R.string.target_wifi, "x")),
                    DrawerRow.ContactsHeader,
                    DrawerRow.ContactPrompt(permanentlyDenied = false),
                ),
            )
        }
        assertAccessible("drawer, searching", scroll = false)
    }

    @Test
    fun anAppsMenuInTheDrawer() {
        show { Drawer(query = "") }
        compose.onNodeWithText("Camera").performTouchInput { longClick() }
        compose.waitForIdle()
        assertAccessible("app menu", scroll = false)
    }

    // --- settings and what hangs off it ----------------------------------------------

    @Test
    fun settings() {
        show {
            SettingsScreen(
                settings = LauncherSettings(hiddenApps = setOf("p.notes/A")),
                isDefaultHome = false,
                onBack = {}, onThemeMode = {}, onAccent = {}, onDynamicColor = {},
                onShowGlance = {}, onAutoKeyboard = {}, onShowMostUsed = {}, onClearUsage = {},
                onSearchContacts = {}, onSearchSettings = {}, onAppInfo = {}, onFont = {},
                onIconSize = {}, onDrawerColumns = {}, onHomeColumns = {}, onWallpaper = {},
                onHiddenApps = {}, deviceSecure = false, onLockHiddenApps = {},
                gestureServiceEnabled = false, onGestureService = {},
                lockDownState = LockDownState.NEEDS_PERMISSION, onLockDown = {},
                onDesignPreview = {}, onPrivacyDashboard = {}, onQuickPanel = {},
                focusActive = false, onFocus = {}, onMinimalMode = {}, onSetDefaultHome = {},
                previewEntries = apps.take(6), iconLoader = loader, appVersion = "0.1.0",
                backupFileName = "backup.json", onBackupTo = {}, onRestoreFrom = {},
                backupResult = null, onDismissBackupResult = {},
            )
        }
        assertAccessible("settings")
    }

    @Test
    fun hiddenApps() {
        show {
            HiddenAppsScreen(
                apps = apps, hidden = setOf(apps[1].key), iconLoader = loader,
                onBack = {}, onToggle = { _, _ -> },
            )
        }
        assertAccessible("hidden apps")
    }

    @Test
    fun focusAndItsApps() {
        show {
            FocusScreen(
                settings = LauncherSettings(focusAllowed = setOf(apps[0].key)),
                focus = FocusState(),
                allowedCount = 1,
                onBack = {}, onStart = {}, onEnd = {}, onPickApps = {},
                onScheduleEnabled = {}, onNudgeWindow = { _, _ -> },
            )
        }
        assertAccessible("focus")
    }

    @Test
    fun focusWhileItIsRunning() {
        show {
            FocusScreen(
                settings = LauncherSettings(focusAllowed = setOf(apps[0].key)),
                focus = FocusState(source = FocusSource.MANUAL, allowed = setOf(apps[0].key)),
                allowedCount = 1,
                onBack = {}, onStart = {}, onEnd = {}, onPickApps = {},
                onScheduleEnabled = {}, onNudgeWindow = { _, _ -> },
            )
        }
        assertAccessible("focus, running")
    }

    @Test
    fun focusApps() {
        show {
            FocusAppsScreen(
                apps = apps, allowed = setOf(apps[2].key), iconLoader = loader,
                onBack = {}, onToggle = { _, _ -> },
            )
        }
        assertAccessible("focus apps")
    }

    @Test
    fun theDesignPreview() {
        show { DesignPreviewScreen(onBack = {}) }
        assertAccessible("design preview")
    }

    // --- quick controls -----------------------------------------------------------------

    @Composable
    private fun Quick(canWrite: Boolean) {
        QuickPanel(
            state = QuickControlsState(
                wifi = Toggle.ON, bluetooth = Toggle.OFF, torchAvailable = true,
                canWriteSettings = canWrite,
            ),
            lockDownState = LockDownState.READY,
            onObserve = { {} }, onRefresh = {}, onTile = {}, onBrightness = {},
            onBrightnessFinished = {}, onGrantWriteSettings = {},
        )
    }

    @Test
    fun quickControls() {
        show { Quick(canWrite = true) }
        assertAccessible("quick controls")
    }

    @Test
    fun quickControlsBeforeBrightnessIsAllowed() {
        show { Quick(canWrite = false) }
        assertAccessible("quick controls, brightness not allowed")
    }

    // --- the privacy dashboard ------------------------------------------------------------

    @Test
    fun thePrivacyDashboard() {
        val tracker = Tracker(1, "Acme Analytics", listOf("Analytics"))
        show {
            PrivacyDashboardScreen(
                report = PrivacyReport(
                    groups = PermissionGroup.entries.map { group ->
                        GroupReport(
                            group,
                            listOf(PermissionHolder("p.maps", "Maps", preinstalled = false, isSelf = false)),
                        )
                    },
                    score = 60,
                    installedAppsScanned = 1,
                    ready = true,
                ),
                iconLoader = loader,
                onBack = {}, onOpenPermissions = {}, onOpenSystemPrivacy = {},
                trackers = TrackerReport(
                    findings = listOf(
                        TrackerFinding("p.maps", "Maps", ScanStatus.SCANNED, listOf(tracker)),
                        TrackerFinding("p.notes", "Notes", ScanStatus.SCANNED, emptyList()),
                    ),
                    total = 3, waiting = 1, snapshot = "2026-01-02", listSize = 428,
                ),
            )
        }
        assertAccessible("privacy dashboard")
    }

    @Test
    fun thePrivacyDashboardBeforeAndDuringAScan() {
        show {
            PrivacyDashboardScreen(
                report = PrivacyReport(ready = true),
                iconLoader = loader,
                onBack = {}, onOpenPermissions = {}, onOpenSystemPrivacy = {},
                trackers = TrackerReport(running = true, done = 2, total = 5, current = "Maps", listSize = 428, snapshot = "2026-01-02"),
            )
        }
        assertAccessible("privacy dashboard, scanning")
    }

    // --- first run, and the dialogs ----------------------------------------------------------

    @Test
    fun everyWelcomePage() {
        show {
            OnboardingScreen(
                settings = LauncherSettings(), isDefaultHome = false,
                onThemeMode = {}, onAccent = {}, onChooseHomeApp = {}, onFinish = {},
            )
        }
        assertAccessible("welcome, page 1")
        for (page in 2..4) {
            compose.onNodeWithText("Next").performClick()
            compose.waitForIdle()
            assertAccessible("welcome, page $page")
        }
    }

    @Test
    fun theDialogs() {
        show {
            LockDownDialog(
                state = LockDownState.NEEDS_PERMISSION,
                onDismiss = {}, onAllow = {}, onOpenSecurity = {}, onTurnOff = {},
            )
        }
        assertAccessible("lock down dialog", scroll = false)
    }

    @Test
    fun theGestureServiceDialog() {
        show { GestureExplainerDialog(onDismiss = {}, onOpenSettings = {}) }
        assertAccessible("gesture service dialog", scroll = false)
    }
}
