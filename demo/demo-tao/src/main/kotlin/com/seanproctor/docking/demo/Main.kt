package com.seanproctor.docking.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import com.seanproctor.docking.jewel.JewelDocking
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.TabPreference
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.persistence.FileLayoutStorage
import com.seanproctor.docking.persistence.rememberAutoPersist
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget
import com.seanproctor.docking.state.DockableSpec
import com.seanproctor.docking.state.DockingSettings
import com.seanproctor.docking.state.EmptyAnchorVisibility
import com.seanproctor.docking.state.rememberDockState
import com.seanproctor.docking.window.windowHost
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.window.TitleBar
import dev.nucleusframework.window.styling.LocalTitleBarStyle
import java.io.File
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

/**
 * The docking demo on Nucleus's Tao windows: no AWT in the process. The same panels as the
 * Jewel demo; the windows are the application's, connected to the docking system through its
 * window API in NucleusDocking.kt - the library itself knows nothing of Nucleus.
 *
 *     ./gradlew :demo:demo-tao:run
 *
 * On native Wayland, drags between windows ride the platform's drag-and-drop session; run
 * with `GDK_BACKEND=x11` for the screen-coordinate path X11, Windows and macOS take.
 */
fun main(args: Array<String>) {
    val alwaysUseTabs = "--always-use-tabs" in args
    // A second launch of the demo is a second demo, not a request to raise the first.
    nucleusApplication(args, enableSingleInstance = false) {
        val state =
            rememberDockState(
                settings =
                    DockingSettings(
                        defaultTabPreference = if (alwaysUseTabs) TabPreference.BottomAlways else TabPreference.Bottom,
                        collapsedAnchorThickness = 24.dp,
                        emptyAnchorVisibility = EmptyAnchorVisibility.WhileDragging,
                    ),
                initialLayout = { demoLayout(alwaysUseTabs) },
            ) { }
        remember(state) { state.demoDockables() }
        remember { state.layouts.save("default") }
        rememberAutoPersist(
            state,
            FileLayoutStorage(File(System.getProperty("user.home"), ".compose-docking-demo/tao-demo-layout.json")),
        )

        DemoTheme {
            DecoratedWindow(
                onCloseRequest = ::exitApplication,
                state = rememberWindowState(size = DpSize(900.dp, 640.dp)),
                title = "Compose Docking Tao Demo",
                // Escape cancels a drag whatever has focus in the window.
                onPreviewKeyEvent = state.windowHost::cancelOnEscape,
            ) {
                JewelDocking {
                    Column(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) {
                        TitleBar { _ ->
                            CompositionLocalProvider(LocalContentColor provides LocalTitleBarStyle.current.colors.content) {
                                Row(
                                    Modifier.align(Alignment.Start).padding(start = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedButton(onClick = { state.layouts.restore("default") }) { Text("Reset layout") }
                                    OutlinedButton(onClick = { createPanel(state) }) { Text("New panel") }
                                }
                                Text("Compose Docking Tao Demo", Modifier.align(Alignment.CenterHorizontally))
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            NucleusDockArea(state, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
            JewelDocking {
                NucleusFloatingDockWindows(state)
            }
        }
    }
}

private var panelCount by mutableStateOf(0)

/** "New panel": registers a simple panel and docks it east of the window root. */
private fun createPanel(state: DockState) {
    panelCount += 1
    val name = "panel-$panelCount"
    val id = DockableId(name)
    state.registry.register(DockableSpec(id = id, title = { name }, content = { SimplePanelContent(name) }))
    state.dock(id, DockTarget.Root(WindowId.MAIN), DockRegion.East)
}
