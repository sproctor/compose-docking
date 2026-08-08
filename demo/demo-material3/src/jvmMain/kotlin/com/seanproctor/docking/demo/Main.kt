package com.seanproctor.docking.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
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
import com.seanproctor.docking.material3.Material3Docking
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.persistence.DockingPersistence
import com.seanproctor.docking.persistence.FileLayoutStorage
import com.seanproctor.docking.persistence.captureLayout
import com.seanproctor.docking.persistence.rememberAutoPersist
import com.seanproctor.docking.persistence.restoreLayout
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget
import com.seanproctor.docking.state.DockableSpec
import com.seanproctor.docking.state.rememberDockState
import com.seanproctor.docking.ui.DockArea
import java.awt.FileDialog
import java.io.File

fun main() = application {
    val state = rememberDockState(initialLayout = ::demoLayout) { demoDockables() }
    // Snapshot the pristine default before auto-persist restores (MainFrame's "default" layout).
    remember { state.layouts.save("default") }

    var autoPersist by remember { mutableStateOf(true) }
    rememberAutoPersist(
        state,
        FileLayoutStorage(File(System.getProperty("user.home"), ".compose-docking-demo/basic-demo-layout.json")),
        paused = !autoPersist,
    )

    Window(
        onCloseRequest = ::exitApplication,
        state = rememberWindowState(size = DpSize(800.dp, 600.dp)),
        title = "Compose Docking Basic Demo",
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

        DemoTheme {
            Material3Docking {
                registerDockingWindow(state)
                Surface(Modifier.fillMaxSize()) {
                    DockArea(state, modifier = Modifier.fillMaxSize())
                }
                DemoDialogs()
            }
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

    DemoTheme {
        Material3Docking {
            FloatingDockWindows(state)
        }
    }
}

/**
 * ModernDocking's `DockableMenuItem`: checked while docked, and clicking toggles —
 * undocking it when it is docked, displaying it when it is not. Undocking deliberately
 * skips the `canClose` veto, matching `Docking.undock`: the confirmation dialog belongs
 * to the header's close button, not to a view menu.
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
        DemoTheme {
            Surface(Modifier.fillMaxSize()) {
                var name by remember { mutableStateOf("") }
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(label) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = { onResult(null) }) { Text("Cancel") }
                        Button(onClick = { onResult(name) }, enabled = name.isNotBlank()) { Text("OK") }
                    }
                }
            }
        }
    }
}
