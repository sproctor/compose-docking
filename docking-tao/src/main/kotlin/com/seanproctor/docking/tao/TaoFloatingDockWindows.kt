package com.seanproctor.docking.tao

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.seanproctor.docking.desktop.FloatingWindowModel
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockWindow
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.spi.LocalDockingRenderer
import com.seanproctor.docking.spi.SyncDockWindowBounds
import com.seanproctor.docking.spi.LocalDockingWindowMoveHandle
import com.seanproctor.docking.spi.windowHost
import com.seanproctor.docking.state.DockState
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.application.NucleusDecoratedWindowScope
import dev.nucleusframework.application.NucleusWindow

/**
 * Creates the OS window for a floating dock area, the Nucleus counterpart of
 * [com.seanproctor.docking.desktop.FloatingWindowHost]. Implement it to use a different window
 * (a decorated one, a design system's own) than [DefaultTaoFloatingWindowHost].
 *
 * The host owns the window; the docking layer owns what goes in it. Whatever window the
 * host opens, it must call [content] inside it: that is the dock area, along with the
 * registration that lets cross-window drags find the window.
 */
@Stable
public interface TaoFloatingWindowHost {
    /** Emit the window, once per floating window in the layout. */
    @Composable
    public fun NucleusApplicationScope.FloatingWindow(
        model: FloatingWindowModel,
        content: @Composable NucleusDecoratedWindowScope.() -> Unit,
    )
}

/**
 * An undecorated [DecoratedWindow]: the dockable's own header is its whole chrome, so a torn-off
 * panel keeps the look it had docked. Moving it is the header's job (see [TaoFloatingDockWindows]);
 * minimize, maximize and close are the application's, through
 * [com.seanproctor.docking.ui.LocalDockWindow] and [nucleusWindow].
 */
public object DefaultTaoFloatingWindowHost : TaoFloatingWindowHost {
    @Composable
    override fun NucleusApplicationScope.FloatingWindow(
        model: FloatingWindowModel,
        content: @Composable NucleusDecoratedWindowScope.() -> Unit,
    ) {
        DecoratedWindow(
            onCloseRequest = model.onCloseRequest,
            state = model.state,
            title = model.title,
            icon = model.icon,
            undecorated = true,
            resizable = true,
        ) {
            content()
        }
    }
}

/** The [TaoFloatingWindowHost] [TaoFloatingDockWindows] uses unless given one. */
public val LocalTaoFloatingWindowHost: ProvidableCompositionLocal<TaoFloatingWindowHost> =
    staticCompositionLocalOf { DefaultTaoFloatingWindowHost }

/**
 * Emits one Nucleus window per floating entry in the layout, plus the window that follows
 * the pointer during a drag. Call from your `nucleusApplication` scope, inside the same
 * theme and renderer providers as the main window:
 *
 * ```
 * nucleusApplication {
 *     val state = rememberDockState(...) { ... }
 *     DecoratedWindow(onCloseRequest = ::exitApplication, title = "App") {
 *         JewelDocking {
 *             TaoDockArea(state, modifier = Modifier.fillMaxSize())
 *         }
 *     }
 *     JewelDocking { TaoFloatingDockWindows(state) }
 * }
 * ```
 *
 * On native Wayland, where windows cannot be placed, drags ride the platform's
 * drag-and-drop session (see [TaoDockArea]): the compositor draws the drag icon, so there is
 * no preview window, and a floating window's header drags its panel rather than the window,
 * so it can be dropped back into another one. Moving a floating window itself is then the
 * compositor's (Super+drag on GNOME), and where a torn-off window opens is too.
 */
@Composable
public fun NucleusApplicationScope.TaoFloatingDockWindows(
    state: DockState,
    host: TaoFloatingWindowHost = LocalTaoFloatingWindowHost.current,
) {
    for (floating in state.layout.floatingWindows) {
        key(floating.id) {
            FloatingDockWindow(state, floating, host)
        }
    }
    DragPreviewWindow(state)
}

@Composable
private fun NucleusApplicationScope.FloatingDockWindow(
    state: DockState,
    window: DockWindow,
    host: TaoFloatingWindowHost,
) {
    val bounds = window.bounds
    val windowState =
        rememberWindowState(
            position =
                bounds?.let { WindowPosition.Absolute(it.x.dp, it.y.dp) }
                    ?: WindowPosition.PlatformDefault,
            size = bounds?.let { DpSize(it.width.dp, it.height.dp) } ?: DpSize(400.dp, 300.dp),
        )
    SyncDockWindowBounds(state, window.id, windowState)

    val windowId = window.id
    val spec = window.root?.firstDockable()?.let { state.registry[it] }
    val title = spec?.title?.invoke() ?: "Floating"
    val icon = spec?.icon?.invoke()
    // The one dockable a window can be said to be titling, and the one whose header drags
    // the window: the same condition DockArea applies the move handle under.
    val loneDockable = (window.root as? DockNode.Leaf)?.dockableId
    val model =
        remember(windowId, title, icon, windowState, state, loneDockable) {
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

    with(host) {
        FloatingWindow(model) {
            val density = LocalDensity.current.density
            val nucleusWindow = nucleusWindow
            // On native Wayland the header drags the panel through the platform session
            // instead, which is what lets it be dropped back into another window; moving
            // the window itself is then the compositor's (Super+drag on GNOME).
            val moveHandle: (@Composable (@Composable () -> Unit) -> Unit)? =
                if (loneDockable == null || !nucleusWindow.isPlaceable) {
                    null
                } else {
                    remember(state, windowId, loneDockable, density, nucleusWindow) {
                        { header ->
                            Box(Modifier.windowDragToDock(state, windowId, loneDockable, density, nucleusWindow)) {
                                header()
                            }
                        }
                    }
                }
            CompositionLocalProvider(LocalDockingWindowMoveHandle provides moveHandle) {
                TaoDockArea(state, windowId, Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * Moves the window by chasing the pointer: each event moves it by however far the pointer
 * drifted from where it went down, which the move then cancels out, so the pointer's local
 * position barely changes. The drop cares about the screen position, which comes from the
 * window's own position on screen.
 */
private fun Modifier.windowDragToDock(
    state: DockState,
    windowId: WindowId,
    dockableId: DockableId,
    density: Float,
    window: NucleusWindow,
): Modifier =
    pointerInput(windowId, dockableId, density, window) {
        val host = state.windowHost
        awaitEachGesture {
            // Unconsumed only: a press the header's own buttons took is theirs, not a drag.
            val down = awaitFirstDown()
            state.activeDockable = dockableId
            val origin = down.position
            val integration = taoIntegration(state)
            var dragging = false
            // Where the window was asked to go: reading it back from the window system would
            // re-add drift a move still in flight already covers.
            var requested: Offset? = null
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (dragging) {
                            dragging = false
                            host.drop()
                        }
                        break
                    }
                    // Ended from outside (Escape restores the bounds the drag began at):
                    // moving the window again would undo that restore.
                    if (dragging && !host.isDragging) {
                        dragging = false
                        break
                    }
                    if (!dragging && (change.position - origin).getDistance() > viewConfiguration.touchSlop) {
                        dragging = host.startWindowDrag(dockableId, windowId, change.position)
                        requested = window.boundsOnScreen()?.let { Offset(it.x, it.y) }
                    }
                    if (dragging) {
                        change.consume()
                        val drift = change.position - origin
                        val tao = window.unsafe.taoWindow
                        val from = requested
                        if (from != null && tao != null) {
                            val next = from + drift / density
                            requested = next
                            integration.movingOrigins[windowId] = next
                            tao.setOuterPosition(next.x.toDouble(), next.y.toDouble())
                        }
                        host.updateDrag(change.position, windowId)
                    }
                }
            } finally {
                integration.movingOrigins.remove(windowId)
                // Every other way out (the pointer vanishing, the composable leaving) would
                // leave the session open, and an open session turns away every later drag.
                if (dragging) host.cancel()
            }
        }
    }

/** The first dockable in [this] subtree, in layout order: what a floating window is named after. */
private fun DockNode.firstDockable(): DockableId? =
    when (this) {
        is DockNode.Leaf -> dockableId
        is DockNode.Tabs -> tabs.firstOrNull()?.dockableId
        is DockNode.Split -> first.firstDockable() ?: second.firstDockable()
        is DockNode.Anchor -> null
    }

/** Size of the window following the pointer during a drag; the renderer's card sits inside it. */
private val DragPreviewSize = DpSize(240.dp, 48.dp)

/**
 * The small undecorated always-on-top window following the pointer while a panel or tab is
 * dragged. Not drawn where windows cannot be placed: the dock area's in-window card is the
 * preview there.
 */
@Composable
private fun NucleusApplicationScope.DragPreviewWindow(state: DockState) {
    val integration = taoIntegration(state)
    // Both ways the preview is the adapter's: this window where windows can be placed, the
    // compositor's drag icon (the platform session's) where they cannot.
    DisposableEffect(state) {
        state.windowHost.drawsDragPreview = true
        onDispose { state.windowHost.drawsDragPreview = false }
    }
    val preview = state.windowHost.dragPreview ?: return
    // Asked once a drag runs, when the window it started in is registered.
    if (integration.windowsPlaceable != true) return
    val density = integration.densities[preview.originWindow] ?: 1f
    val windowState =
        rememberWindowState(
            position = previewPosition(preview.screenPosition.x, preview.screenPosition.y, density),
            size = DragPreviewSize,
        )
    LaunchedEffect(state, windowState, density) {
        snapshotFlow { state.windowHost.dragPreview?.screenPosition }.collect { position ->
            if (position != null) windowState.position = previewPosition(position.x, position.y, density)
        }
    }
    DecoratedWindow(
        onCloseRequest = {},
        state = windowState,
        undecorated = true,
        resizable = false,
        focusable = false,
        alwaysOnTop = true,
        hiddenFromDock = true,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            val spec = state.registry[preview.dockableId]
            LocalDockingRenderer.current.DragPreview(
                title = spec?.title?.invoke() ?: preview.dockableId.value,
                icon = spec?.icon?.invoke(),
            )
        }
    }
}

private fun previewPosition(
    x: Float,
    y: Float,
    density: Float,
): WindowPosition.Absolute = WindowPosition.Absolute((x / density + 14).dp, (y / density + 14).dp)
