package com.seanproctor.docking.spi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.ui.geometry.Offset
import com.seanproctor.docking.drag.DragListener
import com.seanproctor.docking.drag.DragSource
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.ui.LocalWindowMoveHandle

/**
 * Marks the seam between the docking core and a window system: what an adapter that puts
 * dock areas into OS windows (the built-in AWT layer, `docking-tao` for Nucleus windows)
 * needs from the core, and nothing an application calls.
 */
@RequiresOptIn(
    message = "For window-system adapters that host dock areas in OS windows, not for applications.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class DockingWindowHostApi

/**
 * What a window-system adapter sees of one [DockState]'s cross-window drags.
 *
 * Drags run in one shared "screen" space. Each OS window hosting a dock area maps its
 * root-local positions into that space and back ([setScreenConverter]), and the adapter
 * says which window is under a screen point ([windowResolver]). A drop over no window
 * opens a floating window there; the adapter emits one OS window per entry in
 * [DockState.layout]'s floating windows and drags it by its lone dockable's header
 * ([LocalWindowMoveHandle], [startWindowDrag]).
 *
 * Positions are in the shared space's units, which the adapter chooses (physical pixels in
 * both built-in adapters); a window's root-local positions are in its own pixels.
 */
@DockingWindowHostApi
public class DockingWindowHost internal constructor(private val state: DockState) {
    private val controller get() = state.dragController

    /** Maps [windowId]'s root-local positions to the shared space and back. */
    public fun setScreenConverter(
        windowId: WindowId,
        toScreen: (Offset) -> Offset,
        fromScreen: (Offset) -> Offset,
    ) {
        controller.setScreenConverter(windowId, toScreen, fromScreen)
    }

    /** Forgets [windowId]'s converters, when its OS window goes away. */
    public fun clearScreenConverter(windowId: WindowId) {
        controller.clearScreenConverter(windowId)
    }

    /**
     * The topmost window at a shared-space point, ignoring `exclude` (the window being
     * dragged, which is always under the pointer). Null: the core tests every registered
     * window's bounds itself, in registration order, which knows nothing about stacking.
     */
    public var windowResolver: ((screen: Offset, exclude: WindowId?) -> WindowId?)?
        get() = controller.windowResolver
        set(value) {
            controller.windowResolver = value
        }

    /**
     * Starts dragging floating window [windowId] by the header of its lone dockable
     * [dockableId], at [positionInWindow]. The adapter moves the OS window as the pointer
     * moves and reports each position to [updateDrag]; released over another dock area,
     * the panel docks there, else the window stays where it was let go.
     *
     * Returns false when no drag started: another is in progress, or the dockable cannot
     * be dragged.
     */
    public fun startWindowDrag(
        dockableId: DockableId,
        windowId: WindowId,
        positionInWindow: Offset,
    ): Boolean {
        if (controller.session != null) return false
        controller.startDrag(
            source = DragSource.Header(dockableId),
            positionInWindow = positionInWindow,
            windowId = windowId,
            movesWindow = true,
        )
        return controller.session != null
    }

    /** The pointer is at [positionInWindow] in [windowId]'s root. */
    public fun updateDrag(
        positionInWindow: Offset,
        windowId: WindowId,
    ) {
        controller.updateDrag(positionInWindow, windowId)
    }

    /** Released: applies the drop. */
    public fun drop() {
        controller.drop()
    }

    /** Abandoned: puts everything back as it was when the drag started. */
    public fun cancel() {
        controller.cancel()
    }

    /** Whether any drag (a window drag, or a panel or tab being torn out) is in progress. */
    public val isDragging: Boolean get() = controller.session != null

    /**
     * The drag to draw a preview window for, while [drawsDragPreview] is on: null when
     * nothing is dragged, or when a window drag carries the real window under the pointer.
     * Snapshot state, so a composable reading it follows the drag.
     */
    public val dragPreview: DragPreviewInfo?
        get() {
            val session = controller.session ?: return null
            if (session.movesWindow) return null
            return DragPreviewInfo(
                screenPosition = session.screenPosition,
                originWindow = session.originWindow,
                dockableId = session.payload.primary,
            )
        }

    /**
     * On while the adapter draws its own preview window from [dragPreview]: the dock areas
     * then stop drawing the in-window ghost card. Off by default, and on platforms whose
     * windows cannot be placed on screen (native Wayland), where the in-window card is the
     * only preview there can be.
     */
    public var drawsDragPreview: Boolean
        get() = controller.dragListener?.showsOwnPreview == true
        set(value) {
            controller.dragListener = if (value) ownPreviewListener else null
        }

    private val ownPreviewListener =
        object : DragListener {
            override val showsOwnPreview: Boolean get() = true
        }
}

/** A drag's preview, as [DockingWindowHost.dragPreview] reports it. */
@DockingWindowHostApi
public class DragPreviewInfo(
    /** The pointer, in the shared drag space. */
    public val screenPosition: Offset,
    /** The window the drag started in, whose pixel density the position is in. */
    public val originWindow: WindowId,
    /** The dockable to picture: its title and icon are in [DockState.registry]. */
    public val dockableId: DockableId,
)

/** The adapter-facing view of this state's cross-window drags. */
@DockingWindowHostApi
public val DockState.windowHost: DockingWindowHost
    get() = windowHostBridge

/**
 * Wraps the header of a floating window's lone dockable, so dragging it drags the window:
 * the adapter provides it inside each floating window's dock area. Null everywhere else,
 * which is how a header keeps its own drag gesture.
 */
@DockingWindowHostApi
public val LocalDockingWindowMoveHandle: ProvidableCompositionLocal<(@Composable (@Composable () -> Unit) -> Unit)?>
    get() = LocalWindowMoveHandle
