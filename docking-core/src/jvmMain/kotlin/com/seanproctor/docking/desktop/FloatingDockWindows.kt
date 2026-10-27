package com.seanproctor.docking.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockWindow
import com.seanproctor.docking.spi.LocalDockingRenderer
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.tree.dockableIds
import com.seanproctor.docking.ui.DockArea
import com.seanproctor.docking.window.windowHost

/**
 * Emits one OS window per floating entry in the layout, plus the drag preview window
 * that follows the pointer during drags. Call from your `application` scope, inside the
 * same theme/renderer providers as the main window:
 *
 * ```
 * application {
 *     val state = rememberDockState(...) { ... }
 *     Window(onCloseRequest = ::exitApplication, title = "App") {
 *         Material3Docking {
 *             registerDockingWindow(state)
 *             DockArea(state, Modifier.fillMaxSize())
 *         }
 *     }
 *     Material3Docking { FloatingDockWindows(state) }
 * }
 * ```
 *
 * The windows are undecorated: the dockable's own header is their whole chrome, so a torn-off
 * panel keeps the look it had docked instead of gaining an OS title bar above the one it
 * already has. Dragging that header moves the window, and dropping it over another dock area
 * docks it there. Compose Desktop's resizer handles the edges; minimize, maximize and close
 * are the application's to draw (see [com.seanproctor.docking.ui.LocalDockWindow] and
 * [awtWindow]).
 *
 * To decorate the windows yourself - an OS title bar, one you draw, another window toolkit -
 * call the other [FloatingDockWindows], which leaves the window to you, and
 * [DockDragPreviewWindow] for the preview.
 *
 * Empty floating windows are garbage-collected by the core; their window simply leaves
 * the composition.
 */
@Composable
public fun ApplicationScope.FloatingDockWindows(state: DockState) {
    FloatingDockWindows(state) { model ->
        Window(
            onCloseRequest = model.onCloseRequest,
            state = model.state,
            title = model.title,
            icon = model.icon,
            undecorated = true,
            resizable = true,
            // Escape cancels a drag of this window whatever has focus in it.
            onPreviewKeyEvent = state.windowHost::cancelOnEscape,
        ) {
            registerDockingWindow(state, model.windowId)
            DockArea(state, model.windowId, Modifier.fillMaxSize(), headerDragsWindow = true)
        }
    }
    DockDragPreviewWindow(state)
}

/**
 * Calls [window] once per floating entry in the layout, for the application to emit the OS
 * window around it however it likes: decorated or not, a title bar of its own, any window
 * toolkit whose windows take a Compose `WindowState`. A window leaves the composition when the
 * layout drops it.
 *
 * Whatever [window] emits, inside it it must:
 * - show the window with [FloatingWindowModel.state], which is bound to the layout's bounds
 *   both ways (moves and resizes persist; a restored layout moves the window);
 * - register it ([com.seanproctor.docking.window.registerDockingWindow], or
 *   [registerDockingWindow] for an AWT window), so drags can pass between windows;
 * - show `DockArea(state, model.windowId)`.
 *
 * Pass the window's key events to [com.seanproctor.docking.window.DockingWindowHost.cancelOnEscape]
 * (`onPreviewKeyEvent = state.windowHost::cancelOnEscape`), so Escape cancels a drag whatever
 * has focus in the window.
 *
 * Something has to move the window: an OS title bar does, but cannot dock it anywhere when it
 * is let go. A title bar drawn in Compose with
 * [com.seanproctor.docking.window.dockWindowDragArea] moves it and docks it, and so does the
 * header of its lone dockable with `DockArea(..., headerDragsWindow = true)`:
 *
 * ```
 * FloatingDockWindows(state) { model ->
 *     Window(
 *         onCloseRequest = model.onCloseRequest,
 *         state = model.state,
 *         title = model.title,
 *         undecorated = true,
 *         onPreviewKeyEvent = state.windowHost::cancelOnEscape,
 *     ) {
 *         registerDockingWindow(state, model.windowId)
 *         Column {
 *             MyTitleBar(model.title, Modifier.dockWindowDragArea(state, model.windowId))
 *             DockArea(state, model.windowId, Modifier.weight(1f))
 *         }
 *     }
 * }
 * ```
 */
@Composable
public fun FloatingDockWindows(
    state: DockState,
    window: @Composable (model: FloatingWindowModel) -> Unit,
) {
    for (floating in state.layout.floatingWindows) {
        key(floating.id) {
            FloatingDockWindow(state, floating, window)
        }
    }
}

@Composable
private fun FloatingDockWindow(
    state: DockState,
    window: DockWindow,
    emit: @Composable (model: FloatingWindowModel) -> Unit,
) {
    val bounds = window.bounds
    val windowState = rememberWindowState(
        position = bounds?.let { WindowPosition.Absolute(it.x.dp, it.y.dp) }
            ?: WindowPosition.PlatformDefault,
        size = bounds?.let { DpSize(it.width.dp, it.height.dp) } ?: DpSize(400.dp, 300.dp),
    )
    SyncDockWindowBounds(state, window.id, windowState)

    val windowId = window.id
    val spec = window.root?.dockableIds()?.firstOrNull()?.let { state.registry[it] }
    val title = spec?.title?.invoke() ?: "Floating"
    val icon = spec?.icon?.invoke()
    // The one dockable a frame can be said to be titling.
    val loneDockable = (window.root as? DockNode.Leaf)?.dockableId
    // Remembered so the window composable is not handed a new model - and a new set of
    // arguments - on every recomposition the layout provokes. Only what the window actually
    // shows is a key; `window` itself is a fresh DockWindow after any edit to it.
    val model = remember(windowId, title, icon, windowState, state, loneDockable) {
        FloatingWindowModel(
            windowId = windowId,
            title = title,
            icon = icon,
            state = windowState,
            onCloseRequest = { state.closeWindow(windowId) },
            dockState = state,
            dockableId = loneDockable,
        )
    }
    emit(model)
}

/**
 * The undecorated always-on-top window following the pointer while a panel or tab is
 * dragged - ModernDocking's TempFloatingFrame - in place of the in-window ghost card.
 * [FloatingDockWindows] shows it; call it yourself alongside the [FloatingDockWindows] that
 * leaves the windows to you.
 */
@Composable
public fun ApplicationScope.DockDragPreviewWindow(state: DockState) {
    val host = state.windowHost
    DisposableEffect(host) {
        val claim = host.claimDragPreview()
        onDispose { claim.release() }
    }
    val preview = host.dragPreview ?: return
    val windowState = rememberWindowState(
        position = previewPosition(preview.screenPosition),
        size = DpSize.Unspecified,
    )
    LaunchedEffect(host, windowState) {
        snapshotFlow { host.dragPreview?.screenPosition }.collect {
            if (it != null) windowState.position = previewPosition(it)
        }
    }
    Window(
        onCloseRequest = {},
        state = windowState,
        undecorated = true,
        transparent = true,
        resizable = false,
        focusable = false,
        alwaysOnTop = true,
    ) {
        val spec = state.registry[preview.dockableId]
        LocalDockingRenderer.current.DragPreview(
            title = spec?.title?.invoke() ?: preview.dockableId.value,
            icon = spec?.icon?.invoke(),
        )
    }
}

private fun previewPosition(screen: Offset): WindowPosition.Absolute =
    WindowPosition.Absolute((screen.x + 14).dp, (screen.y + 14).dp)
