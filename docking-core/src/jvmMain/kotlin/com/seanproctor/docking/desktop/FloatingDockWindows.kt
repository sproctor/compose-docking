package com.seanproctor.docking.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import com.seanproctor.docking.drag.DragListener
import com.seanproctor.docking.model.DockWindow
import com.seanproctor.docking.model.WindowBounds
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.spi.LocalDockingRenderer
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.tree.dockableIds
import com.seanproctor.docking.ui.DockArea
import kotlin.math.abs
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

/** Receiver for custom floating-window chrome. */
public interface FloatingWindowScope : FrameWindowScope {
    public val windowId: WindowId
    public val dockState: DockState
}

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
 * Windows are decorated by default (free OS move/resize/snap). Supplying [chrome] makes
 * them undecorated with your composable as the frame - e.g. Jewel's `DecoratedWindow`.
 * Empty floating windows are garbage-collected by the core; their `Window` simply leaves
 * the composition.
 */
@Composable
public fun ApplicationScope.FloatingDockWindows(
    state: DockState,
    chrome: (@Composable FloatingWindowScope.(content: @Composable () -> Unit) -> Unit)? = null,
) {
    for (floating in state.layout.floatingWindows) {
        key(floating.id) {
            FloatingDockWindow(state, floating, chrome)
        }
    }
    DragPreviewWindow(state)
}

@Composable
private fun FloatingDockWindow(
    state: DockState,
    window: DockWindow,
    chrome: (@Composable FloatingWindowScope.(content: @Composable () -> Unit) -> Unit)?,
) {
    val bounds = window.bounds
    val windowState = rememberWindowState(
        position = bounds?.let { WindowPosition.Absolute(it.x.dp, it.y.dp) }
            ?: WindowPosition.PlatformDefault,
        size = bounds?.let { DpSize(it.width.dp, it.height.dp) } ?: DpSize(400.dp, 300.dp),
    )
    SyncWindowBounds(state, window.id, windowState)

    val title = window.root?.dockableIds()?.firstOrNull()
        ?.let { state.registry[it]?.title?.invoke() }
        ?: "Floating"

    Window(
        onCloseRequest = { state.closeWindow(window.id) },
        state = windowState,
        title = title,
        undecorated = chrome != null,
    ) {
        registerDockingWindow(state, window.id)
        if (chrome != null) {
            val scope = remember(this, window.id) {
                object : FloatingWindowScope, FrameWindowScope by this {
                    override val windowId: WindowId = window.id
                    override val dockState: DockState = state
                }
            }
            scope.chrome {
                DockArea(state, window.id, Modifier.fillMaxSize())
            }
        } else {
            DockArea(state, window.id, Modifier.fillMaxSize())
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
