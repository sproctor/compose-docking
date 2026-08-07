package com.seanproctor.docking.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.seanproctor.docking.desktop.FloatingDockWindows
import com.seanproctor.docking.desktop.registerDockingWindow
import com.seanproctor.docking.layout.dockLayout
import com.seanproctor.docking.model.AutoHideSide
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableOptions
import com.seanproctor.docking.persistence.FileLayoutStorage
import com.seanproctor.docking.persistence.rememberAutoPersist
import com.seanproctor.docking.state.rememberDockState
import com.seanproctor.docking.ui.DockArea
import java.io.File

fun main() = application {
    val state = rememberDockState(
        initialLayout = {
            dockLayout {
                mainWindow {
                    dock("project")
                    dock("editor", target = "project", region = DockRegion.East, proportion = 0.75f)
                    dock("terminal", target = "editor", region = DockRegion.South, proportion = 0.3f)
                    dock("problems", target = "terminal", region = DockRegion.Center)
                    display("terminal")
                    autoHide("todo", AutoHideSide.South)
                }
            }
        },
    ) {
        dockable("project", title = { "Project" }, options = DockableOptions(closable = false)) {
            LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
                items(50) { BasicText("src/file$it.kt") }
            }
        }
        dockable("editor", title = { "Editor" }) {
            var text by rememberSaveable { mutableStateOf("fun main() {\n    println(\"hello\")\n}") }
            TextField(text, onValueChange = { text = it }, modifier = Modifier.fillMaxSize())
        }
        dockable("terminal", title = { "Terminal" }) {
            Column(Modifier.fillMaxSize().padding(8.dp)) { Text("$ ready") }
        }
        dockable("problems", title = { "Problems" }) {
            Column(Modifier.fillMaxSize().padding(8.dp)) { Text("No problems detected") }
        }
        dockable("todo", title = { "TODO" }) {
            Column(Modifier.fillMaxSize().padding(8.dp)) { Text("- publish library") }
        }
    }
    rememberAutoPersist(
        state,
        FileLayoutStorage(File(System.getProperty("user.home"), ".compose-docking-demo/layout.json")),
    )

    Window(onCloseRequest = ::exitApplication, title = "Compose Docking Demo") {
        MaterialTheme {
            registerDockingWindow(state)
            DockArea(state, modifier = Modifier.fillMaxSize())
        }
    }
    MaterialTheme {
        FloatingDockWindows(state)
    }
}
