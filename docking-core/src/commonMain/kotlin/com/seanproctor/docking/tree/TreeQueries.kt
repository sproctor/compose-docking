package com.seanproctor.docking.tree

import com.seanproctor.docking.model.AnchorId
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.DockingStyle
import com.seanproctor.docking.model.NodeId

/**
 * Pure, Compose-free queries over [DockNode] trees. This package is the ported
 * ModernDocking engine; nothing here may depend on UI types.
 */

internal fun DockNode.findNode(id: NodeId): DockNode? = when (this) {
    is DockNode.Leaf -> takeIf { it.id == id }
    is DockNode.Anchor -> takeIf { it.id == id }
    is DockNode.Split ->
        if (this.id == id) this else first.findNode(id) ?: second.findNode(id)
    is DockNode.Tabs ->
        if (this.id == id) this else tabs.firstOrNull { it.id == id }
}

internal fun DockNode.findLeaf(dockableId: DockableId): DockNode.Leaf? = when (this) {
    is DockNode.Leaf -> takeIf { it.dockableId == dockableId }
    is DockNode.Anchor -> null
    is DockNode.Split -> first.findLeaf(dockableId) ?: second.findLeaf(dockableId)
    is DockNode.Tabs -> tabs.firstOrNull { it.dockableId == dockableId }
}

internal fun DockNode.findAnchorNode(anchorId: AnchorId): DockNode.Anchor? = when (this) {
    is DockNode.Leaf -> null
    is DockNode.Anchor -> takeIf { it.anchorId == anchorId }
    is DockNode.Split -> first.findAnchorNode(anchorId) ?: second.findAnchorNode(anchorId)
    is DockNode.Tabs -> null
}

internal fun DockNode.dockableIds(): Sequence<DockableId> = sequence {
    when (val node = this@dockableIds) {
        is DockNode.Leaf -> yield(node.dockableId)
        is DockNode.Anchor -> {}
        is DockNode.Split -> {
            yieldAll(node.first.dockableIds())
            yieldAll(node.second.dockableIds())
        }
        is DockNode.Tabs -> yieldAll(node.tabs.asSequence().map { it.dockableId })
    }
}

internal fun DockNode.containsDockable(dockableId: DockableId): Boolean =
    findLeaf(dockableId) != null

/** The tab group containing [dockableId] as a member, if any. */
internal fun DockNode.findTabsContaining(dockableId: DockableId): DockNode.Tabs? = when (this) {
    is DockNode.Leaf, is DockNode.Anchor -> null
    is DockNode.Tabs -> takeIf { tabs.any { it.dockableId == dockableId } }
    is DockNode.Split ->
        first.findTabsContaining(dockableId) ?: second.findTabsContaining(dockableId)
}

/**
 * The fraction of the window's area occupied by the leaf holding [dockableId], computed
 * purely from split proportions (a geometry-free stand-in for pixel-size comparison).
 * Returns `null` when the dockable is not in this tree.
 */
internal fun DockNode.areaFractionOf(dockableId: DockableId): Float? = when (this) {
    is DockNode.Leaf -> if (this.dockableId == dockableId) 1f else null
    is DockNode.Anchor -> null
    is DockNode.Tabs -> if (tabs.any { it.dockableId == dockableId }) 1f else null
    is DockNode.Split ->
        first.areaFractionOf(dockableId)?.let { it * proportion }
            ?: second.areaFractionOf(dockableId)?.let { it * (1f - proportion) }
}

/**
 * Whether this style permits taking part in a dock at [region]. Center is always
 * permitted; edge regions require the matching orientation.
 */
internal fun DockingStyle.allowsRegion(region: DockRegion): Boolean = when (region) {
    DockRegion.Center -> true
    DockRegion.East, DockRegion.West -> this == DockingStyle.Vertical || this == DockingStyle.Both
    DockRegion.North, DockRegion.South -> this == DockingStyle.Horizontal || this == DockingStyle.Both
}

/**
 * ModernDocking's bidirectional region filter: a dock at an edge region is legal only if
 * both the target's and the dragged dockable's styles allow that orientation.
 */
internal fun isRegionAllowed(
    region: DockRegion,
    targetStyle: DockingStyle,
    draggedStyle: DockingStyle,
): Boolean = targetStyle.allowsRegion(region) && draggedStyle.allowsRegion(region)
