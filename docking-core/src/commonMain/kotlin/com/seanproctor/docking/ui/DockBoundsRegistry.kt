package com.seanproctor.docking.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.NodeId

/**
 * Per-window geometry registry, fed by `onGloballyPositioned` on every rendered node.
 * All rects are in the window composition's root coordinates. Feeds drag hit-testing,
 * and tab-insertion indices. Docking trees are tens of
 * nodes, so linear scans are fine.
 */
public class DockBoundsRegistry internal constructor() {

    internal val nodeBounds = mutableMapOf<NodeId, Rect>()
    internal val dockableBounds = mutableMapOf<DockableId, Rect>()
    internal val tabStripRects = mutableMapOf<NodeId, Rect>()
    internal val tabRects = mutableMapOf<NodeId, MutableMap<Int, Rect>>()
    internal var rootBounds: Rect = Rect.Zero

    internal fun updateNode(id: NodeId, bounds: Rect) {
        nodeBounds[id] = bounds
    }

    internal fun removeNode(id: NodeId) {
        nodeBounds.remove(id)
        tabStripRects.remove(id)
        tabRects.remove(id)
    }

    internal fun updateDockable(id: DockableId, bounds: Rect) {
        dockableBounds[id] = bounds
    }

    internal fun removeDockable(id: DockableId) {
        dockableBounds.remove(id)
    }

    internal fun updateTabStrip(nodeId: NodeId, bounds: Rect) {
        tabStripRects[nodeId] = bounds
    }

    internal fun updateTab(nodeId: NodeId, index: Int, bounds: Rect) {
        tabRects.getOrPut(nodeId) { mutableMapOf() }[index] = bounds
    }

    /** The dockable whose rendered area contains [position]. */
    internal fun dockableAt(position: Offset): DockableId? =
        dockableBounds.entries.firstOrNull { it.value.contains(position) }?.key

    /**
     * The dockable containing [position], or failing that the nearest one within [slack] of
     * it. The slack covers the gaps splits leave between panes for their dividers, so a drop
     * in one lands on a pane beside it instead of on nothing.
     */
    internal fun dockableNear(position: Offset, slack: Float): DockableId? =
        dockableAt(position) ?: dockableBounds.entries
            .map { (id, rect) -> id to distance(rect, position) }
            .filter { it.second <= slack }
            .minByOrNull { it.second }
            ?.first

    private fun distance(rect: Rect, position: Offset): Float {
        val dx = maxOf(rect.left - position.x, 0f, position.x - rect.right)
        val dy = maxOf(rect.top - position.y, 0f, position.y - rect.bottom)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    internal fun boundsOf(id: DockableId): Rect? = dockableBounds[id]

    internal fun boundsOfNode(id: NodeId): Rect? = nodeBounds[id]

    /**
     * The index a tab dragged from [fromIndex] should move to for a pointer at [x],
     * computed over the *other* tabs (the dragged tab's own rect follows the pointer and
     * would corrupt the count).
     */
    internal fun reorderTargetIndex(nodeId: NodeId, fromIndex: Int, x: Float): Int {
        val others = tabRects[nodeId].orEmpty().entries
            .filter { it.key != fromIndex }
            .sortedBy { it.key }
        return others.count { x > it.value.center.x }
    }

    /** The tab strip containing [position], with the insertion index at that x. */
    internal fun tabStripAt(position: Offset): TabStripHit? {
        val (nodeId, _) = tabStripRects.entries
            .firstOrNull { it.value.contains(position) } ?: return null
        val tabs = tabRects[nodeId].orEmpty().entries.sortedBy { it.key }
        val index = tabs.count { position.x > it.value.center.x }
        return TabStripHit(nodeId, index.coerceAtMost(tabs.size))
    }
}

internal class TabStripHit(val nodeId: NodeId, val insertionIndex: Int)
