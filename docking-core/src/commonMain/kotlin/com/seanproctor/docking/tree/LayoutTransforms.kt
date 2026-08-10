package com.seanproctor.docking.tree

import com.seanproctor.docking.model.AnchorId
import com.seanproctor.docking.model.DockLayout
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockWindow
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.MaximizedState
import com.seanproctor.docking.model.NodeId
import com.seanproctor.docking.model.SplitOrientation
import com.seanproctor.docking.model.WindowBounds
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.model.WindowKind

/**
 * Layout-level transforms over the window list. Like the tree transforms these are pure:
 * they take a [DockLayout] and return a new one, never touching UI state.
 */

internal data class LayoutResult(
    val layout: DockLayout,
    /** Floating windows removed by this operation (for WindowClosed events). */
    val closedWindows: List<WindowId> = emptyList(),
)

/** Where a dockable currently lives inside a window. */
internal enum class DockablePlacement { Docked, BehindMaximize }

internal fun DockWindow.placementOf(dockableId: DockableId): DockablePlacement? = when {
    root?.containsDockable(dockableId) == true -> DockablePlacement.Docked
    maximized?.savedRoot?.containsDockable(dockableId) == true -> DockablePlacement.BehindMaximize
    else -> null
}

internal fun DockLayout.windowContaining(dockableId: DockableId): DockWindow? =
    windows.firstOrNull { it.placementOf(dockableId) != null }

/**
 * Restores a maximized window to its saved tree. Structural operations call this first so
 * the live tree is always the canonical one they act on.
 */
internal fun DockWindow.restoredFromMaximize(): DockWindow =
    if (maximized == null) this else copy(root = maximized.savedRoot, maximized = null)

/** Drops empty floating windows, returning the surviving layout and the removed ids. */
internal fun DockLayout.gcFloatingWindows(): LayoutResult {
    val (kept, removed) = windows.partition { w ->
        w.kind == WindowKind.Main || w.root != null
    }
    return if (removed.isEmpty()) {
        LayoutResult(this)
    } else {
        LayoutResult(DockLayout(kept), removed.map { it.id })
    }
}

/**
 * Removes [dockableId] from its window tree. A maximized containing window is restored
 * first. Empty floating windows are dropped.
 */
internal fun TreeContext.undockFromLayout(
    layout: DockLayout,
    dockableId: DockableId,
): LayoutResult {
    val window = layout.windowContaining(dockableId) ?: return LayoutResult(layout)
    val w = window.restoredFromMaximize().let { it.copy(root = it.root?.let { r -> undock(r, dockableId) }) }
    return layout.replaceWindow(w).gcFloatingWindows()
}

/**
 * Docks [dockableId] into [windowId], onto [targetNodeId] (or the window root when null).
 * The window is restored from maximize first. Returns `null` when the target node no
 * longer exists (callers treat that as a failed drop).
 */
internal fun TreeContext.dockIntoLayout(
    layout: DockLayout,
    dockableId: DockableId,
    windowId: WindowId,
    targetNodeId: NodeId?,
    region: DockRegion,
    proportion: Float,
): DockLayout? {
    val window = layout.window(windowId) ?: return null
    val w = window.restoredFromMaximize()
    val newRoot = if (targetNodeId == null || w.root == null) {
        dockAtRoot(w.root, dockableId, region, proportion)
    } else {
        dockAt(w.root, targetNodeId, dockableId, region, proportion) ?: return null
    }
    return layout.replaceWindow(w.copy(root = newRoot))
}

/**
 * Docks [dockableId] into anchor [anchorId], searching all windows for the placeholder or
 * a docked carrier. Returns `null` when the anchor is nowhere in the layout (callers fall
 * back to the main window root).
 */
internal fun TreeContext.dockIntoAnchorInLayout(
    layout: DockLayout,
    dockableId: DockableId,
    anchorId: AnchorId,
): DockLayout? {
    for (window in layout.windows) {
        val w = window.restoredFromMaximize()
        val root = w.root ?: continue
        val newRoot = dockIntoAnchor(root, anchorId, dockableId) ?: continue
        return layout.replaceWindow(w.copy(root = newRoot))
    }
    return null
}

/**
 * Adds a new floating window containing only [dockableId]. The caller must have undocked
 * it first (drag flow) or use the state-level move operation.
 */
internal fun TreeContext.addFloatingWindow(
    layout: DockLayout,
    windowId: WindowId,
    dockableId: DockableId,
    bounds: WindowBounds?,
): DockLayout = DockLayout(
    layout.windows + DockWindow(
        id = windowId,
        kind = WindowKind.Floating,
        root = newContentNode(dockableId),
        bounds = bounds,
    ),
)

/**
 * Maximizes [dockableId]: the window keeps its tree in [MaximizedState.savedRoot] and
 * shows only the maximized dockable. Returns `null` if the dockable is not docked in a
 * live tree or its window is already maximized.
 */
internal fun TreeContext.maximizeInLayout(layout: DockLayout, dockableId: DockableId): DockLayout? {
    val window = layout.windowContaining(dockableId) ?: return null
    if (window.placementOf(dockableId) != DockablePlacement.Docked) return null
    if (window.maximized != null) return null
    val root = window.root ?: return null
    return layout.replaceWindow(
        window.copy(
            maximized = MaximizedState(dockableId, savedRoot = root),
            root = DockNode.Leaf(ids.next(), dockableId),
        ),
    )
}

/** Restores the maximized window [windowId]. No-op when it is not maximized. */
internal fun restoreMaximizeInLayout(layout: DockLayout, windowId: WindowId): DockLayout {
    val window = layout.window(windowId) ?: return layout
    return layout.replaceWindow(window.restoredFromMaximize())
}

/** Sets the persisted bounds of [windowId]. */
internal fun setWindowBoundsInLayout(
    layout: DockLayout,
    windowId: WindowId,
    bounds: WindowBounds,
): DockLayout {
    val window = layout.window(windowId) ?: return layout
    return layout.replaceWindow(window.copy(bounds = bounds))
}

/**
 * Removes a floating window and everything in it from the layout. The main window cannot
 * be closed this way.
 */
internal fun closeWindowInLayout(layout: DockLayout, windowId: WindowId): LayoutResult {
    val window = layout.window(windowId) ?: return LayoutResult(layout)
    if (window.kind == WindowKind.Main) return LayoutResult(layout)
    return LayoutResult(DockLayout(layout.windows - window), listOf(windowId))
}

/**
 * Grafts every floating window's content into the main window (used when restoring a
 * layout on a platform without floating-window support). Each floating tree is attached
 * as an East split taking 25% of the main window, and the floating windows are dropped.
 */
internal fun TreeContext.mergeFloatingIntoMain(layout: DockLayout): DockLayout {
    if (layout.floatingWindows.isEmpty()) return layout
    var main = layout.mainWindow.restoredFromMaximize()
    for (floating in layout.floatingWindows) {
        val f = floating.restoredFromMaximize()
        f.root?.let { floatingRoot ->
            main = main.copy(
                root = main.root?.let { mainRoot ->
                    DockNode.Split(
                        id = ids.next(),
                        orientation = SplitOrientation.Horizontal,
                        first = mainRoot,
                        second = floatingRoot,
                        proportion = 0.75f,
                    )
                } ?: floatingRoot,
            )
        }
    }
    return DockLayout(listOf(main))
}
