package com.seanproctor.docking.desktop

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import com.seanproctor.docking.drag.DragListener
import com.seanproctor.docking.drag.DragSource
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.DockWindow
import com.seanproctor.docking.model.WindowBounds
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.spi.LocalDockingRenderer
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.tree.dockableIds
import com.seanproctor.docking.ui.DockArea
import com.seanproctor.docking.ui.LocalWindowMoveHandle
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

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
 * The windows themselves come from [host], which defaults to whatever
 * [LocalFloatingWindowHost] carries - [DefaultFloatingWindowHost] unless an adapter
 * provided one. That window is undecorated: the dockable's own header is its whole
 * chrome, so a torn-off panel keeps the look it had docked instead of gaining an OS title
 * bar above the one it already has. Supply [host] to build the windows differently.
 *
 * Empty floating windows are garbage-collected by the core; their window simply leaves
 * the composition.
 */
@Composable
public fun ApplicationScope.FloatingDockWindows(
    state: DockState,
    host: FloatingWindowHost = LocalFloatingWindowHost.current,
) {
    for (floating in state.layout.floatingWindows) {
        key(floating.id) {
            FloatingDockWindow(state, floating, host)
        }
    }
    DragPreviewWindow(state)
}

@Composable
private fun FloatingDockWindow(
    state: DockState,
    window: DockWindow,
    host: FloatingWindowHost,
) {
    val bounds = window.bounds
    val windowState = rememberWindowState(
        position = bounds?.let { WindowPosition.Absolute(it.x.dp, it.y.dp) }
            ?: WindowPosition.PlatformDefault,
        size = bounds?.let { DpSize(it.width.dp, it.height.dp) } ?: DpSize(400.dp, 300.dp),
    )
    SyncWindowBounds(state, window.id, windowState)

    val windowId = window.id
    val spec = window.root?.dockableIds()?.firstOrNull()?.let { state.registry[it] }
    val title = spec?.title?.invoke() ?: "Floating"
    val icon = spec?.icon?.invoke()
    // The one dockable a frame can be said to be titling, and the one whose header drags
    // the window - the same condition DockArea applies the move handle under.
    val loneDockable = (window.root as? DockNode.Leaf)?.dockableId
    // Remembered so the host is not handed a new model - and a window composable a new set
    // of arguments - on every recomposition the layout provokes. Only what the window
    // actually shows is a key; `window` itself is a fresh DockWindow after any edit to it.
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

    host.FloatingWindow(model) {
        registerDockingWindow(state, windowId)
        val density = LocalDensity.current.density
        val moveHandle: (@Composable (@Composable () -> Unit) -> Unit)? =
            if (loneDockable == null) {
                null
            } else {
                remember(state, windowId, loneDockable, density) {
                    { header ->
                        Box(Modifier.windowDragToDock(state, windowId, loneDockable, density)) {
                            header()
                        }
                    }
                }
            }
        CompositionLocalProvider(LocalWindowMoveHandle provides moveHandle) {
            DockArea(state, windowId, Modifier.fillMaxSize())
        }
    }
}

/**
 * The gesture on an undecorated floating window's header: it moves the window, and if it
 * is let go over another dock area, docks the panel there.
 *
 * The move is driven from Compose rather than handed to the window manager, which is what
 * makes the docking half possible at all: a compositor-driven move takes the pointer with
 * it, so nothing here would see where the window went or when it was dropped. Every event
 * of the gesture stays in this handler instead.
 *
 * The window follows the pointer by chasing it: each event moves the window by however far
 * the pointer has drifted from where it went down, which the move then cancels out, so the
 * next event starts from the same place. That the pointer's *local* position barely changes
 * during the drag is the point - the screen position, which is what the drop cares about,
 * comes from the window's own position on screen.
 */
private fun Modifier.windowDragToDock(
    state: DockState,
    windowId: WindowId,
    dockableId: DockableId,
    density: Float,
): Modifier = pointerInput(windowId, dockableId, density) {
    val controller = state.dragController
    awaitEachGesture {
        // Unconsumed only: a press the header's own buttons took is theirs, not a drag.
        val down = awaitFirstDown()
        state.activeDockable = dockableId
        val origin = down.position
        var dragging = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                if (dragging) controller.drop()
                break
            }
            if (!dragging &&
                (change.position - origin).getDistance() > viewConfiguration.touchSlop
            ) {
                dragging = true
                controller.startDrag(
                    source = DragSource.Header(dockableId),
                    positionInWindow = change.position,
                    windowId = windowId,
                    movesWindow = true,
                )
            }
            if (dragging) {
                change.consume()
                val drift = change.position - origin
                state.awtWindow(windowId)?.let { window ->
                    window.setLocation(
                        window.x + (drift.x / density).roundToInt(),
                        window.y + (drift.y / density).roundToInt(),
                    )
                }
                controller.updateDrag(change.position, windowId)
            }
        }
    }
}

/**
 * Two-way bounds sync: OS moves/resizes flow into the model (debounced, for
 * persistence); model changes from a layout restore flow back to the OS window. A
 * value-compare echo guard breaks feedback loops.
 */
@OptIn(FlowPreview::class)
@Composable
private fun SyncWindowBounds(state: DockState, windowId: WindowId, windowState: WindowState) {
    LaunchedEffect(state, windowId, windowState) {
        snapshotFlow { windowState.position to windowState.size }
            .debounce(100)
            .collect { (position, size) ->
                if (position is WindowPosition.Absolute) {
                    val next = WindowBounds(
                        position.x.value,
                        position.y.value,
                        size.width.value,
                        size.height.value,
                    )
                    if (!next.approximately(state.layout.window(windowId)?.bounds)) {
                        state.setWindowBounds(windowId, next)
                    }
                }
            }
    }
    LaunchedEffect(state, windowId, windowState) {
        snapshotFlow { state.layout.window(windowId)?.bounds }
            .collect { bounds ->
                if (bounds != null) {
                    val current = windowState.position
                    val matches = current is WindowPosition.Absolute &&
                        WindowBounds(
                            current.x.value, current.y.value,
                            windowState.size.width.value, windowState.size.height.value,
                        ).approximately(bounds)
                    if (!matches) {
                        windowState.position = WindowPosition.Absolute(bounds.x.dp, bounds.y.dp)
                        windowState.size = DpSize(bounds.width.dp, bounds.height.dp)
                    }
                }
            }
    }
}

private fun WindowBounds.approximately(other: WindowBounds?): Boolean =
    other != null &&
        abs(x - other.x) < 1f && abs(y - other.y) < 1f &&
        abs(width - other.width) < 1f && abs(height - other.height) < 1f

/**
 * The undecorated always-on-top window following the pointer during a drag -
 * ModernDocking's TempFloatingFrame. Replaces the in-window ghost card.
 */
@Composable
private fun DragPreviewWindow(state: DockState) {
    DisposableEffect(state) {
        val listener = object : DragListener {
            override val showsOwnPreview: Boolean get() = true
        }
        state.dragController.dragListener = listener
        onDispose {
            if (state.dragController.dragListener === listener) {
                state.dragController.dragListener = null
            }
        }
    }
    val session = state.dragController.session ?: return
    // A window drag carries the real window under the pointer; a ghost of the same panel
    // trailing it would be a second copy of something already on screen.
    if (session.movesWindow) return
    val integration = desktopIntegration(state)
    val density = integration.densities[session.originWindow] ?: 1f
    val position = session.screenPosition
    val windowState = rememberWindowState(
        position = WindowPosition.Absolute(
            (position.x / density + 14).dp,
            (position.y / density + 14).dp,
        ),
        size = DpSize.Unspecified,
    )
    LaunchedEffect(session, windowState) {
        snapshotFlow { session.screenPosition }.collect {
            windowState.position = WindowPosition.Absolute(
                (it.x / density + 14).dp,
                (it.y / density + 14).dp,
            )
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
        val renderer = LocalDockingRenderer.current
        val spec = state.registry[session.payload.primary]
        renderer.DragPreview(
            title = spec?.title?.invoke() ?: session.payload.primary.value,
            icon = spec?.icon?.invoke(),
        )
    }
}
