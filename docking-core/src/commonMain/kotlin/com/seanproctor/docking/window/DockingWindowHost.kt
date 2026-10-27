package com.seanproctor.docking.window

import androidx.compose.ui.geometry.Offset
import com.seanproctor.docking.drag.DragListener
import com.seanproctor.docking.drag.DragSource
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.model.WindowKind
import com.seanproctor.docking.state.DockState

/**
 * A [DockState]'s drags between OS windows, as the application drives them when it handles a
 * window's decorations itself.
 *
 * Most applications need none of it: [dockWindowDragArea] drives a window drag, and a
 * [DockingDragHandover] gets the platform-carried calls through [DockDragGrip]. This is for the
 * rest - a title bar the application draws outside Compose, a drag-and-drop session of its own
 * - and for drawing a drag preview window ([dragPreview]).
 *
 * Positions in a window are in pixels of its Compose root.
 */
public class DockingWindowHost internal constructor(private val state: DockState) {
    private val controller get() = state.dragController

    /**
     * Starts dragging floating window [windowId] by a point at [positionInWindow] in it: its
     * title bar, or the header standing in for one. The application moves the OS window as the
     * pointer moves and reports each position to [updateDrag]; released ([drop]) over another
     * dock area, what the window holds docks there and the window closes, else the window stays
     * where it was let go. [cancel] puts the layout back, the window's bounds included.
     *
     * Only a window holding one dockable or one tab group can dock as a whole. False when no
     * drag started: the window holds anything else, it is the main window, or another drag is
     * running. The window may still be moved; it just docks nowhere.
     */
    public fun startWindowDrag(
        windowId: WindowId,
        positionInWindow: Offset,
    ): Boolean {
        if (controller.session != null) return false
        val window = state.layout.window(windowId) ?: return false
        if (window.kind != WindowKind.Floating) return false
        val source =
            when (val root = window.root) {
                is DockNode.Leaf -> DragSource.Header(root.dockableId)
                is DockNode.Tabs -> DragSource.TabGroup(root.id)
                else -> return false
            }
        controller.startDrag(source = source, positionInWindow = positionInWindow, windowId = windowId, movesWindow = true)
        return controller.session != null
    }

    /** The pointer driving the drag is at [positionInWindow] in [windowId]. */
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
     * A drag the platform carries ([DockDragGrip.begin]) is over [windowId], at
     * [positionInWindow] as that window's drop target reports it. An unspecified position (a
     * platform that did not say) leaves the hover as it was.
     */
    public fun hoverInWindow(
        positionInWindow: Offset,
        windowId: WindowId,
    ) {
        controller.hoverInWindow(positionInWindow, windowId)
    }

    /** A drag the platform carries left [windowId]'s drop target. */
    public fun exitWindow(windowId: WindowId) {
        controller.exitWindow(windowId)
    }

    /**
     * [windowId]'s drop target takes the drop of a drag the platform carries, at
     * [positionInWindow]. Recorded, not applied: platforms finish a drop on the target before
     * they end the drag on the source, and [endPlatformDrag] applies it. False when no docking
     * drag is running, so the target can turn the drop down.
     */
    public fun dropInWindow(
        positionInWindow: Offset,
        windowId: WindowId,
    ): Boolean = controller.dropInWindow(positionInWindow, windowId)

    /**
     * The platform ended a drag it carried, on the source: applies the drop a window took, or,
     * when none did, tears the payload off into a new floating window (placed by the platform)
     * if it may float, and puts it back if not. For a drag the platform abandoned, [cancel].
     */
    public fun endPlatformDrag() {
        controller.endPlatformDrag()
    }

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
                density = controller.window(session.originWindow)?.scope?.density ?: 1f,
                dockableId = session.payload.primary,
            )
        }

    /**
     * On while the application draws its own preview window from [dragPreview]: the dock
     * areas then stop drawing the in-window ghost card. Off by default, and where windows
     * cannot be placed on screen (native Wayland), where the in-window card or the platform's
     * drag icon is the only preview there can be.
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
public class DragPreviewInfo(
    /** The pointer on screen, in pixels. */
    public val screenPosition: Offset,
    /** The density of the window the drag started in, for turning [screenPosition] into dp. */
    public val density: Float,
    /** The dockable to picture: its title and icon are in [DockState.registry]. */
    public val dockableId: DockableId,
)

/** This state's drags between OS windows. */
public val DockState.windowHost: DockingWindowHost
    get() = windowHostBridge
