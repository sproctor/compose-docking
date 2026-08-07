package com.seanproctor.docking.demo

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.foundation.layout.fillMaxSize
import com.seanproctor.docking.desktop.FloatingDockWindows
import com.seanproctor.docking.desktop.registerDockingWindow
import com.seanproctor.docking.material3.Material3Docking
import com.seanproctor.docking.persistence.FileLayoutStorage
import com.seanproctor.docking.persistence.rememberAutoPersist
import com.seanproctor.docking.state.rememberDockState
import com.seanproctor.docking.ui.DockArea
import java.io.File

fun main() = application {
    val state = rememberDockState(initialLayout = ::demoLayout) { demoDockables() }
    rememberAutoPersist(
        state,
        FileLayoutStorage(File(System.getProperty("user.home"), ".compose-docking-demo/layout.json")),
    )

    Window(onCloseRequest = ::exitApplication, title = "Compose Docking Demo") {
        MaterialTheme {
            Material3Docking {
                registerDockingWindow(state)
                DockArea(state, modifier = Modifier.fillMaxSize())
            }
        }
    }
    MaterialTheme {
        Material3Docking {
            FloatingDockWindows(state)
        }
    }
}
