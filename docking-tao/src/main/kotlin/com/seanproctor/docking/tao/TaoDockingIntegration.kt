package com.seanproctor.docking.tao

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.spi.windowHost
import com.seanproctor.docking.state.DockState
import dev.nucleusframework.application.NucleusDecoratedWindowScope
import dev.nucleusframework.application.NucleusWindow
import java.util.WeakHashMap

/**
 * Per-[DockState] bookkeeping for the Nucleus windows hosting its dock areas: which window
 * shows which [WindowId], for screen-coordinate conversion and for finding the window under
 * a point during a cross-window drag.
 *
 * The shared drag space is physical screen pixels, as in the AWT layer: a window's logical
 * bounds ([NucleusWindow.boundsOnScreen]) times its own density.
 */
internal class TaoDockingIntegration(
    private val state: DockState,
    /** Whether a window's screen position can be read and set; see [isPlaceable]. */
    private val placeable: (NucleusWindow) -> Boolean = { it.isPlaceable },
) {
    val windows = LinkedHashMap<WindowId, NucleusWindow>()
    val densities = HashMap<WindowId, Float>()

    init {
        state.windowHost.windowResolver = { screen, exclude -> resolveWindowAt(screen, exclude) }
    }

    /**
     * The topmost docking window under a screen-px point: the focused one when it is hit,
     * else the most recently registered (floating windows register after main and usually
     * stack above it). [exclude] is the window being dragged, which is always under the
     * pointer and would otherwise mask what it is dragged over.
     *
     * Where windows cannot be placed (native Wayland) there is no shared space: every window
     * reports its origin as (0, 0), and positions are each window's own. Only the window the
     * drag is in - the focused one, which holds the pointer - can then be resolved.
     */
    internal fun resolveWindowAt(
        screen: Offset,
        exclude: WindowId?,
    ): WindowId? {
        val hits =
            windows.entries.filter { (id, window) ->
                id != exclude && placeable(window) && screenRectOf(id, window)?.contains(screen) == true
            }
        if (hits.isNotEmpty()) return (hits.firstOrNull { it.value.isFocused } ?: hits.last()).key
        return windows.entries
            .firstOrNull { (id, window) ->
                id != exclude && !placeable(window) && window.isFocused && localRectOf(id, window)?.contains(screen) == true
            }?.key
    }

    fun screenRectOf(
        id: WindowId,
        window: NucleusWindow,
    ): Rect? {
        val bounds = window.boundsOnScreen() ?: return null
        val density = densities[id] ?: 1f
        return Rect(
            bounds.x * density,
            bounds.y * density,
            (bounds.x + bounds.width) * density,
            (bounds.y + bounds.height) * density,
        )
    }

    private fun localRectOf(
        id: WindowId,
        window: NucleusWindow,
    ): Rect? {
        val bounds = window.boundsOnScreen() ?: return null
        val density = densities[id] ?: 1f
        return Rect(0f, 0f, bounds.width * density, bounds.height * density)
    }

    /** [id]'s content origin in the shared space, or null while it is not on screen. */
    fun originOf(id: WindowId): Offset? {
        val window = windows[id] ?: return null
        val bounds = window.boundsOnScreen() ?: return null
        val density = densities[id] ?: 1f
        return Offset(bounds.x * density, bounds.y * density)
    }
}

/**
 * Whether this window's screen position can be read and set. False on native Wayland, where
 * the compositor alone places windows and reports every one at (0, 0).
 */
internal val NucleusWindow.isPlaceable: Boolean
    get() = unsafe.taoWindow?.canPlaceOnScreen ?: true

private val integrations = WeakHashMap<DockState, TaoDockingIntegration>()

internal fun taoIntegration(state: DockState): TaoDockingIntegration = integrations.getOrPut(state) { TaoDockingIntegration(state) }

/**
 * The Nucleus window showing [windowId]'s dock area, or null when it is not on screen.
 *
 * A floating window is undecorated, so minimize, maximize and restore are the application's
 * to provide; this is what they act on. Pair it with
 * [com.seanproctor.docking.ui.LocalDockWindow] for the window the calling content is in.
 * Read it when acting, not to drive composition: windows are tracked as they register,
 * which is not snapshot state.
 */
public fun DockState.nucleusWindow(windowId: WindowId = WindowId.MAIN): NucleusWindow? = taoIntegration(this).windows[windowId]

/**
 * Connects the surrounding Nucleus window to the docking system: screen-coordinate
 * conversion for cross-window drags, and finding the window a drop lands in. Call once
 * inside every window that hosts a [com.seanproctor.docking.ui.DockArea]: the main window
 * in your own `DecoratedWindow` block; floating windows get it from [TaoFloatingDockWindows].
 *
 * On native Wayland windows cannot be placed, so there is no shared screen space: drags
 * work within a window, and a panel dragged out of one tears off into a new floating
 * window (placed by the compositor), but cannot be dropped into another window.
 */
@Composable
public fun NucleusDecoratedWindowScope.registerTaoDockingWindow(
    state: DockState,
    windowId: WindowId = WindowId.MAIN,
) {
    val window = nucleusWindow
    val density = LocalDensity.current.density
    DisposableEffect(state, windowId, window) {
        val integration = taoIntegration(state)
        integration.windows[windowId] = window
        integration.densities[windowId] = density
        if (window.isPlaceable) {
            state.windowHost.setScreenConverter(
                windowId,
                toScreen = { local -> integration.originOf(windowId)?.let { local + it } ?: local },
                fromScreen = { screen -> integration.originOf(windowId)?.let { screen - it } ?: screen },
            )
        }
        onDispose {
            state.windowHost.clearScreenConverter(windowId)
            integration.windows.remove(windowId)
            integration.densities.remove(windowId)
        }
    }
    DisposableEffect(density) {
        taoIntegration(state).densities[windowId] = density
        onDispose { }
    }
}
