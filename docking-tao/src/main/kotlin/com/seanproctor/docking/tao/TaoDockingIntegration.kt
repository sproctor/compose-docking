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
    // Not a property: integrations are kept in a WeakHashMap keyed by the state, and a
    // value that held its key would keep every discarded state alive.
    state: DockState,
    /** Whether a window's screen position can be read and set; see [isPlaceable]. */
    private val placeable: (NucleusWindow) -> Boolean = { it.isPlaceable },
) {
    val windows = LinkedHashMap<WindowId, NucleusWindow>()
    val densities = HashMap<WindowId, Float>()

    /**
     * The window that took the drop of the platform-carried drag in flight (native Wayland),
     * recorded by its drop target for the drag's end to act on.
     */
    var transferDropWindow: WindowId? = null

    /**
     * Whether windows can be placed on screen, asked of any registered window (it is a
     * property of the display, not of one window); null while none is registered.
     */
    val windowsPlaceable: Boolean?
        get() = windows.values.firstOrNull()?.let(placeable)

    init {
        state.windowHost.windowResolver = { screen, exclude -> resolveWindowAt(screen, exclude) }
    }

    /**
     * The topmost docking window under a screen-px point: the focused one when it is hit,
     * else the most recently registered (floating windows register after main and usually
     * stack above it). [exclude] is the window being dragged, which is always under the
     * pointer and would otherwise mask what it is dragged over.
     *
     * Windows that cannot be placed (native Wayland) are never hit: there is no shared space
     * there, and [TaoDockArea] carries their drags in the platform session instead.
     */
    internal fun resolveWindowAt(
        screen: Offset,
        exclude: WindowId?,
    ): WindowId? {
        val hits =
            windows.entries.filter { (id, window) ->
                id != exclude && placeable(window) && screenRectOf(id, window)?.contains(screen) == true
            }
        if (hits.isEmpty()) return null
        return (hits.firstOrNull { it.value.isFocused } ?: hits.last()).key
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
@ExperimentalDockingTaoApi
public fun DockState.nucleusWindow(windowId: WindowId = WindowId.MAIN): NucleusWindow? = taoIntegration(this).windows[windowId]

/**
 * Connects the surrounding Nucleus window to the docking system: screen-coordinate
 * conversion for cross-window drags, and finding the window a drop lands in. Internal:
 * [TaoDockArea] calls it, and is the only way in, since a plain dock area on native Wayland
 * would silently lose its cross-window drags.
 */
@Composable
internal fun NucleusDecoratedWindowScope.registerTaoDockingWindow(
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
