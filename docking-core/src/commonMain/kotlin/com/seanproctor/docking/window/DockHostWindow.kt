package com.seanproctor.docking.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.state.DockState

/**
 * One OS window showing a [com.seanproctor.docking.ui.DockArea], as the application describes
 * it to the docking system. Implement it over whatever window toolkit the application uses
 * (AWT, Nucleus, a platform's own) and register it with [registerDockingWindow]: it is what lets
 * a drag pass from one window to another, and lets [dockWindowDragArea] move a window and dock
 * it back.
 *
 * Positions here are screen coordinates in the window toolkit's logical units - the units of a
 * Compose `WindowState` position, which AWT and Nucleus both report - and every window shares
 * them, whatever the density of the monitor it is on. Don't scale them by the window's density:
 * the docking system turns positions in a window's Compose root into these with that dock
 * area's own density, which is what keeps windows on monitors of different densities in one
 * space.
 */
public interface DockHostWindow {
    /**
     * Where the window's Compose content is on screen, in logical units, or null while it is not on
     * screen or where windows cannot see their own position (native Wayland, where the
     * compositor alone places them). A window without bounds is never found under the
     * pointer; drags into it need the platform's drag-and-drop session (see
     * [DockingDragHandover]).
     */
    public fun contentBoundsOnScreen(): Rect?

    /** Whether the window has input focus: where windows overlap, the focused one is on top. */
    public val isFocused: Boolean get() = false

    /**
     * Moves the window so its content's top-left lands at [position] on screen, in logical units, as
     * [dockWindowDragArea] drags it. The move may land after this returns; the drag allows for
     * that. The default does nothing, for a window the docking system may not move.
     */
    public fun moveContentTo(position: Offset) {}
}

/**
 * Connects the OS window around [windowId]'s dock area to the docking system, for as long as
 * this is in the composition. Call it once inside every OS window that shows a
 * [com.seanproctor.docking.ui.DockArea], main and floating alike.
 *
 * Without it drags still work inside each window, but cannot pass between them: the docking
 * system has no other way to know where a window is.
 */
@Composable
public fun registerDockingWindow(
    state: DockState,
    windowId: WindowId,
    window: DockHostWindow,
) {
    DisposableEffect(state, windowId, window) {
        state.dragController.registerHostWindow(windowId, window)
        onDispose { state.dragController.unregisterHostWindow(windowId, window) }
    }
}

/**
 * The OS window registered for [windowId] with [registerDockingWindow], or null while none is.
 * What an application's window buttons act on: a floating window's minimize, for one.
 *
 * Read it when acting, not to drive composition: registration is not snapshot state.
 */
public fun DockState.hostWindow(windowId: WindowId = WindowId.MAIN): DockHostWindow? =
    dragController.hostWindow(windowId)
