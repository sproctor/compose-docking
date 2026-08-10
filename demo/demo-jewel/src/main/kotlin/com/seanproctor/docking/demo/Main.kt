package com.seanproctor.docking.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.MenuScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.ui.window.rememberWindowState
import com.seanproctor.docking.desktop.FloatingDockWindows
import com.seanproctor.docking.desktop.registerDockingWindow
import com.seanproctor.docking.jewel.JewelDocking
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.TabPreference
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.persistence.DockingPersistence
import com.seanproctor.docking.persistence.FileLayoutStorage
import com.seanproctor.docking.persistence.captureLayout
import com.seanproctor.docking.persistence.rememberAutoPersist
import com.seanproctor.docking.persistence.restoreLayout
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockingSettings
import com.seanproctor.docking.state.DockTarget
import com.seanproctor.docking.state.DockableSpec
import com.seanproctor.docking.state.rememberDockState
import com.seanproctor.docking.ui.DockArea
import java.awt.FileDialog
import java.io.File
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

/**
 * `--always-use-tabs` mirrors ModernDocking's flag of the same name: every dockable
 * renders as a tab group even when alone, which is also the only mode in which its demo
 * shows tab close buttons and the strip's overflow menu. Off unless passed.
 *
 *     ./gradlew :demo:demo-jewel:run --args="--always-use-tabs"
 */
fun main(args: Array<String>) {
    val alwaysUseTabs = "--always-use-tabs" in args
    application {
        val state = rememberDockState(
            settings = DockingSettings(
                if (alwaysUseTabs) TabPreference.BottomAlways else TabPreference.Bottom,
            ),
            initialLayout = { demoLayout(alwaysUseTabs) },
        ) { }
        // Registered against the state so each panel's header actions can act on it.
        remember(state) { state.demoDockables() }
        // Snapshot the pristine default before auto-persist restores (MainFrame's "default" layout).
        remember { state.layouts.save("default") }

        var autoPersist by remember { mutableStateOf(true) }
        rememberAutoPersist(
            state,
            FileLayoutStorage(File(System.getProperty("user.home"), ".compose-docking-demo/jewel-demo-layout.json")),
            paused = !autoPersist,
        )

        DemoTheme {
            Window(
                onCloseRequest = ::exitApplication,
                state = rememberWindowState(size = DpSize(800.dp, 600.dp)),
                title = "Compose Docking Jewel Demo",
            ) {
                var showCreatePanel by remember { mutableStateOf(false) }
                var showStoreLayout by remember { mutableStateOf(false) }

                MenuBar {
                    Menu("File", mnemonic = 'F') {
                        CheckboxItem("Auto Persist Layout", checked = autoPersist) { autoPersist = it }
                        Item("Save Layout to File...") { saveLayoutToFile(window, state) }
                        Item("Load Layout from File...") { loadLayoutFromFile(window, state) }
                        Item("Create Panel...") { showCreatePanel = true }
                    }
                    Menu("View", mnemonic = 'V') {
                        Item("Bring One to Front") { state.show(DockableId("one")) }
                        Item("Generate Random Dockable") { generateRandomDockable(state) }
                        DockableItem(state, "one", panelOneTitle)
                        Item("one (to center of window)") {
                            state.dock(DockableId("one"), DockTarget.Root(WindowId.MAIN))
                        }
                        for (id in listOf("two", "three", "four", "five", "six", "seven", "eight")) {
                            DockableItem(state, id, id)
                        }
                        DockableItem(state, "explorer", "Explorer")
                        DockableItem(state, "output", "Output")
                        DockableItem(state, "fixed-size", "Fixed Size")
                        DockableItem(state, "props-demo", "props")
                        DockableItem(state, "always-displayed", "always displayed")
                        Item("Change tab text") { panelOneTitle = randomString("abcdefg", 4) }
                        DockableItem(state, "themes", "Themes")
                        DockableItem(state, "scroll-with-toolbar", "scrolling")
                    }
                    Menu("Window", mnemonic = 'W') {
                        Menu("Layouts") {
                            for (name in state.layouts.names) {
                                Item(name) { state.layouts.restore(name) }
                            }
                        }
                        Item("Store Current Layout...") { showStoreLayout = true }
                        Item("Restore Default Layout") { state.layouts.restore("default") }
                    }
                }

                JewelDocking {
                    registerDockingWindow(state)
                    Box(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) {
                        DockArea(state, modifier = Modifier.fillMaxSize())
                    }
                    DemoDialogs()
                }

                if (showCreatePanel) {
                    NameDialog(title = "Create Panel", label = "Panel name") { name ->
                        showCreatePanel = false
                        if (name != null) createPanel(state, name)
                    }
                }
                if (showStoreLayout) {
                    NameDialog(title = "Store Current Layout", label = "Name of Layout") { name ->
                        showStoreLayout = false
                        if (name != null) state.layouts.save(name)
                    }
                }
            }

            JewelDocking {
                FloatingDockWindows(state)
            }
        }
    }

}

/** The close-confirmation dialog behind ToolPanel.requestClose. */
@Composable
private fun DemoDialogs() {
    val message = closeConfirmation.message ?: return
    DialogWindow(
        onCloseRequest = { closeConfirmation.answer(false) },
        title = "Close Panel",
        state = rememberDialogState(size = DpSize(360.dp, 160.dp)),
    ) {
        DemoSurface {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text(message)
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { closeConfirmation.answer(false) }) { Text("No") }
                    DefaultButton(onClick = { closeConfirmation.answer(true) }) { Text("Yes") }
                }
            }
        }
    }
}

/**
 * A view-menu entry per dockable: checked while docked, and clicking toggles - undocking
 * it when it is docked, displaying it when it is not. Undocking deliberately skips the
 * `canClose` veto: the confirmation dialog belongs to the header's close button, not to a
 * view menu.
 *
 * Note this is NOT upstream ModernDocking behaviour. Its `DockableMenuItem` only ever
 * calls `Docking.display(dockable)`, so clicking a checked entry just brings the panel to
 * the front; the toggle is a deliberate improvement.
 */
@Composable
private fun MenuScope.DockableItem(state: DockState, id: String, label: String) {
    val dockableId = DockableId(id)
    CheckboxItem(label, checked = state.isDocked(dockableId)) { checked ->
        if (checked) state.show(dockableId) else state.undock(dockableId)
    }
}

/** File > Create Panel...: registers a new SimplePanel and docks it east of the window root. */
private fun createPanel(state: DockState, name: String) {
    val id = DockableId(name)
    if (!state.registry.isRegistered(id)) {
        state.registry.register(
            DockableSpec(id = id, title = { name }, content = { SimplePanelContent(name) }),
        )
    }
    state.dock(id, DockTarget.Root(WindowId.MAIN), DockRegion.East)
}

/** View > Generate Random Dockable: a random panel docked west of Panel One. */
private fun generateRandomDockable(state: DockState) {
    val id = DockableId(randomString("abcdefg", 10))
    val title = randomString("alpha", 6)
    state.registry.register(
        DockableSpec(id = id, title = { title }, content = { SimplePanelContent(id.value) }),
    )
    state.dock(id, DockTarget.OnDockable(DockableId("one")), DockRegion.West)
}

private fun saveLayoutToFile(parent: ComposeWindow, state: DockState) {
    val dialog = FileDialog(parent, "Save Layout to File", FileDialog.SAVE)
    dialog.isVisible = true
    val file = dialog.files.firstOrNull() ?: return
    runCatching { file.writeText(DockingPersistence.encode(state.captureLayout())) }
        .onFailure { it.printStackTrace() }
}

private fun loadLayoutFromFile(parent: ComposeWindow, state: DockState) {
    val dialog = FileDialog(parent, "Load Layout from File", FileDialog.LOAD)
    dialog.isVisible = true
    val file = dialog.files.firstOrNull() ?: return
    runCatching { state.restoreLayout(DockingPersistence.decode(file.readText())) }
        .onFailure { it.printStackTrace() }
}

/** A small OK/Cancel text-input dialog (the demo's JOptionPane.showInputDialog). */
@Composable
private fun NameDialog(title: String, label: String, onResult: (String?) -> Unit) {
    DialogWindow(
        onCloseRequest = { onResult(null) },
        title = title,
        state = rememberDialogState(size = DpSize(320.dp, 180.dp)),
    ) {
        DemoSurface {
            var name by remember { mutableStateOf(TextFieldValue("")) }
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text(label)
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { onResult(null) }) { Text("Cancel") }
                    DefaultButton(
                        onClick = { onResult(name.text) },
                        enabled = name.text.isNotBlank(),
                    ) { Text("OK") }
                }
            }
        }
    }
}

/** Dialog windows compose outside the main window's theme scope; re-establish it. */
@Composable
private fun DemoSurface(content: @Composable () -> Unit) {
    DemoTheme {
        Box(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) { content() }
    }
}
