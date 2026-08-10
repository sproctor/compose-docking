package com.seanproctor.docking.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.FrameWindowScope
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.state.DockState
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.WeakHashMap

/**
 * Per-[DockState] desktop bookkeeping: the AWT window of each docking window, used for
 * screen-coordinate conversion and z-order-aware drop-window resolution during
 * cross-window drags.
 */
internal class DesktopDockingIntegration(private val state: DockState) {

    val awtWindows = LinkedHashMap<WindowId, ComposeWindow>()
    val densities = HashMap<WindowId, Float>()

    init {
        state.dragController.windowResolver = { screen -> resolveWindowAt(screen) }
    }

    /**
     * The topmost docking window under a screen-px point: prefer the focused window,
     * then the most recently registered (floating windows register after main and
     * usually stack above it).
     */
    private fun resolveWindowAt(screen: Offset): WindowId? {
        val hits = awtWindows.entries.filter { (id, w) ->
            w.isShowing && screenRectOf(id, w)?.contains(screen) == true
        }
        return when {
            hits.isEmpty() -> null
            else -> (hits.firstOrNull { it.value.isFocused } ?: hits.last()).key
        }
    }

    private fun screenRectOf(id: WindowId, window: ComposeWindow): Rect? {
        val density = densities[id] ?: 1f
        return runCatching {
            val content = window.contentPane
            val loc = content.locationOnScreen
            Rect(
                loc.x * density,
                loc.y * density,
                loc.x * density + content.width * density,
                loc.y * density + content.height * density,
            )
        }.getOrNull()
    }
}

private val integrations = WeakHashMap<DockState, DesktopDockingIntegration>()

internal fun desktopIntegration(state: DockState): DesktopDockingIntegration =
    integrations.getOrPut(state) { DesktopDockingIntegration(state) }

/**
 * Connects the surrounding OS window to the docking system: screen-coordinate
 * conversion for cross-window drags and z-order drop resolution on
 * focus loss. Call once inside every `Window` that hosts a [com.seanproctor.docking.ui.DockArea] -
 * the main window in your own `Window` block; floating windows get it automatically from
 * [FloatingDockWindows].
 */
@Composable
public fun FrameWindowScope.registerDockingWindow(
    state: DockState,
    windowId: WindowId = WindowId.MAIN,
) {
    val density by rememberUpdatedState(LocalDensity.current.density)
    DisposableEffect(state, windowId, window) {
        val integration = desktopIntegration(state)
        integration.awtWindows[windowId] = window
        integration.densities[windowId] = density
        state.dragController.setScreenConverter(
            windowId,
            toScreen = { local ->
                val d = integration.densities[windowId] ?: 1f
                runCatching {
                    val loc = window.contentPane.locationOnScreen
                    Offset(loc.x * d + local.x, loc.y * d + local.y)
                }.getOrDefault(local)
            },
            fromScreen = { screen ->
                val d = integration.densities[windowId] ?: 1f
                runCatching {
                    val loc = window.contentPane.locationOnScreen
                    Offset(screen.x - loc.x * d, screen.y - loc.y * d)
                }.getOrDefault(screen)
            },
        )
        onDispose {
            state.dragController.clearScreenConverter(windowId)
            integration.awtWindows.remove(windowId)
            integration.densities.remove(windowId)
        }
    }
    DisposableEffect(density) {
        desktopIntegration(state).densities[windowId] = density
        onDispose { }
    }
}
