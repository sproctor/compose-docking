package com.seanproctor.docking.demo

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeViewport
import com.seanproctor.docking.material3.Material3Docking
import com.seanproctor.docking.state.rememberDockState
import com.seanproctor.docking.ui.DockArea

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(viewportContainerId = "compose") {
        val state = rememberDockState(initialLayout = ::demoLayout) { demoDockables() }
        DemoTheme {
            Material3Docking {
                Surface(Modifier.fillMaxSize()) {
                    DockArea(state, modifier = Modifier.fillMaxSize())
                }
                DemoDialogs()
            }
        }
    }
}
