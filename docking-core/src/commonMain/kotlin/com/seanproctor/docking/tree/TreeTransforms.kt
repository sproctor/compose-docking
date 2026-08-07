package com.seanproctor.docking.tree

import com.seanproctor.docking.model.AnchorId
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.NodeId
import com.seanproctor.docking.model.NodeIdGenerator
import com.seanproctor.docking.model.SplitOrientation

/**
 * Shared context for tree transforms.
 *
 * @property alwaysTabs when true (default tab preference is `*Always`), every dockable is
 *   wrapped in a single-tab [DockNode.Tabs] group so tabs are always visible.
 * @property anchorOf resolves the anchor a dockable belongs to, used for anchor-restore
 *   on undock.
 */
internal class TreeContext(
    val ids: NodeIdGenerator,
    val alwaysTabs: Boolean = false,
    val anchorOf: (DockableId) -> AnchorId? = { null },
) {
    /** A freshly docked dockable's node: a leaf, or a single-tab group in alwaysTabs mode. */
    fun newContentNode(dockableId: DockableId): DockNode {
        val leaf = DockNode.Leaf(ids.next(), dockableId)
        return if (alwaysTabs) DockNode.Tabs(ids.next(), listOf(leaf), 0) else leaf
    }
}

/**
 * Docks [dockableId] relative to the whole window tree.
 *
 * [proportion] is the fraction of space given to the newly docked dockable. Matching
 * ModernDocking, [DockRegion.Center] on a non-empty root is a no-op; any region on an
 * empty (`null`) root fills it.
 */
internal fun TreeContext.dockAtRoot(
    root: DockNode?,
    dockableId: DockableId,
    region: DockRegion,
    proportion: Float = 0.25f,
): DockNode {
    if (root == null) return newContentNode(dockableId)
    if (region == DockRegion.Center) return root
    return splitWith(root, dockableId, region, proportion)
}

/**
 * Docks [dockableId] relative to the node [targetNodeId] inside [root].
 *
 * - [DockRegion.Center] on a leaf wraps target and newcomer in a tab group (or appends,
 *   when the target is already tabbed); the new tab is selected.
 * - Edge regions split the target. When the target is a leaf inside a tab group, the
 *   whole group is split (ModernDocking behavior).
 * - [proportion] is the fraction given to the newly docked dockable.
 *
 * Returns `null` when [targetNodeId] is not in the tree.
 */
internal fun TreeContext.dockAt(
    root: DockNode,
    targetNodeId: NodeId,
    dockableId: DockableId,
    region: DockRegion,
    proportion: Float = 0.5f,
): DockNode? = transformDock(root, targetNodeId, dockableId, region, proportion)

private fun TreeContext.transformDock(
    node: DockNode,
    targetNodeId: NodeId,
    dockableId: DockableId,
    region: DockRegion,
    proportion: Float,
): DockNode? {
    if (node.id == targetNodeId) {
        return dockOnto(node, dockableId, region, proportion)
    }
    return when (node) {
        is DockNode.Leaf, is DockNode.Anchor -> null
        is DockNode.Tabs -> {
            if (node.tabs.none { it.id == targetNodeId }) return null
            when (region) {
                // Center onto a member leaf joins the group.
                DockRegion.Center -> appendTab(node, dockableId)
                // Edge onto a member leaf splits the whole group.
                else -> splitWith(node, dockableId, region, proportion)
            }
        }
        is DockNode.Split -> {
            transformDock(node.first, targetNodeId, dockableId, region, proportion)
                ?.let { return node.copy(first = it) }
            transformDock(node.second, targetNodeId, dockableId, region, proportion)
                ?.let { return node.copy(second = it) }
            null
        }
    }
}

private fun TreeContext.dockOnto(
    target: DockNode,
    dockableId: DockableId,
    region: DockRegion,
    proportion: Float,
): DockNode = when (region) {
    DockRegion.Center -> when (target) {
        is DockNode.Leaf -> {
            val newLeaf = DockNode.Leaf(ids.next(), dockableId)
            DockNode.Tabs(ids.next(), listOf(target, newLeaf), selectedIndex = 1)
        }
        is DockNode.Tabs -> appendTab(target, dockableId)
        // Center onto an anchor placeholder fills it.
        is DockNode.Anchor -> newContentNode(dockableId)
        // Center onto a split has no meaning; keep ModernDocking's silent no-op.
        is DockNode.Split -> target
    }
    else -> when (target) {
        // Edge-docking onto an anchor placeholder fills it rather than splitting the
        // empty slot.
        is DockNode.Anchor -> newContentNode(dockableId)
        else -> splitWith(target, dockableId, region, proportion)
    }
}

private fun TreeContext.appendTab(tabs: DockNode.Tabs, dockableId: DockableId): DockNode.Tabs {
    val newLeaf = DockNode.Leaf(ids.next(), dockableId)
    val newTabs = tabs.tabs + newLeaf
    return tabs.copy(tabs = newTabs, selectedIndex = newTabs.lastIndex)
}

/**
 * Wraps [existing] in a split with a new node for [dockableId] on the [region] side.
 * The stored proportion is flipped (`1 - p`) for East/South because [DockNode.Split.proportion]
 * is the fraction of the first (west/north) child.
 */
private fun TreeContext.splitWith(
    existing: DockNode,
    dockableId: DockableId,
    region: DockRegion,
    proportion: Float,
): DockNode.Split = splitWithNode(existing, newContentNode(dockableId), region, proportion)

internal fun TreeContext.splitWithNode(
    existing: DockNode,
    newContent: DockNode,
    region: DockRegion,
    proportion: Float,
): DockNode.Split {
    val newIsFirst = region == DockRegion.West || region == DockRegion.North
    return DockNode.Split(
        id = ids.next(),
        orientation = when (region) {
            DockRegion.East, DockRegion.West -> SplitOrientation.Horizontal
            DockRegion.North, DockRegion.South -> SplitOrientation.Vertical
            DockRegion.Center -> error("Center region cannot create a split")
        },
        first = if (newIsFirst) newContent else existing,
        second = if (newIsFirst) existing else newContent,
        proportion = (if (newIsFirst) proportion else 1f - proportion).coerceIn(0.05f, 0.95f),
    )
}

/**
 * Removes [dockableId] from the tree. Returns the new root, or `null` when the tree is
 * now empty. When the departing dockable belongs to an anchor and no other dockable in
 * this tree carries that anchor, the vacated slot becomes a [DockNode.Anchor] placeholder
 * instead of collapsing.
 */
internal fun TreeContext.undock(root: DockNode, dockableId: DockableId): DockNode? {
    if (!root.containsDockable(dockableId)) return root
    val anchor = anchorOf(dockableId)
    val restoreAnchor = anchor?.takeIf { a ->
        // No other carrier of this anchor remains in the tree after removal.
        root.dockableIds().none { it != dockableId && anchorOf(it) == a } &&
            // Never create a second placeholder for the same anchor.
            root.findAnchorNode(a) == null
    }
    return removeFrom(root, dockableId, restoreAnchor)
}

private fun TreeContext.removeFrom(
    node: DockNode,
    dockableId: DockableId,
    restoreAnchor: AnchorId?,
): DockNode? = when (node) {
    is DockNode.Leaf ->
        if (node.dockableId == dockableId) vacatedSlot(restoreAnchor) else node
    is DockNode.Anchor -> node
    is DockNode.Tabs -> {
        val index = node.tabs.indexOfFirst { it.dockableId == dockableId }
        if (index < 0) {
            node
        } else {
            val remaining = node.tabs.filterIndexed { i, _ -> i != index }
            when {
                remaining.isEmpty() -> vacatedSlot(restoreAnchor)
                remaining.size == 1 && !alwaysTabs -> remaining.single()
                else -> node.copy(
                    tabs = remaining,
                    selectedIndex = when {
                        index < node.selectedIndex -> node.selectedIndex - 1
                        else -> node.selectedIndex.coerceAtMost(remaining.lastIndex)
                    },
                )
            }
        }
    }
    is DockNode.Split -> {
        val newFirst = removeFrom(node.first, dockableId, restoreAnchor)
        if (newFirst != node.first) {
            if (newFirst == null) node.second else node.copy(first = newFirst)
        } else {
            when (val newSecond = removeFrom(node.second, dockableId, restoreAnchor)) {
                node.second -> node
                null -> node.first
                else -> node.copy(second = newSecond)
            }
        }
    }
}

private fun TreeContext.vacatedSlot(restoreAnchor: AnchorId?): DockNode? =
    restoreAnchor?.let { DockNode.Anchor(ids.next(), it) }

/**
 * Docks [dockableId] into anchor [anchorId] using ModernDocking's cascade:
 * 1. an [DockNode.Anchor] placeholder exists → replace it with the dockable;
 * 2. another dockable carrying the anchor is docked → dock Center onto the one with the
 *    largest area fraction;
 * 3. otherwise → `null`; the caller falls back to docking at the window root.
 */
internal fun TreeContext.dockIntoAnchor(
    root: DockNode,
    anchorId: AnchorId,
    dockableId: DockableId,
): DockNode? {
    root.findAnchorNode(anchorId)?.let { placeholder ->
        return replaceNode(root, placeholder.id, newContentNode(dockableId))
    }
    val carrier = root.dockableIds()
        .filter { anchorOf(it) == anchorId }
        .maxByOrNull { root.areaFractionOf(it) ?: 0f }
        ?: return null
    val carrierLeaf = root.findLeaf(carrier) ?: return null
    return dockAt(root, carrierLeaf.id, dockableId, DockRegion.Center)
}

/** Replaces the node [nodeId] with [replacement]. Returns `null` if [nodeId] was not found. */
internal fun replaceNode(root: DockNode, nodeId: NodeId, replacement: DockNode): DockNode? {
    if (root.id == nodeId) return replacement
    return when (root) {
        is DockNode.Leaf, is DockNode.Anchor -> null
        is DockNode.Tabs -> null // tab members can only be leaves; not replaceable wholesale
        is DockNode.Split -> {
            replaceNode(root.first, nodeId, replacement)?.let { return root.copy(first = it) }
            replaceNode(root.second, nodeId, replacement)?.let { return root.copy(second = it) }
            null
        }
    }
}

/** Selects tab [index] of the tab group [tabsNodeId]. No-op if not found or out of range. */
internal fun selectTab(root: DockNode, tabsNodeId: NodeId, index: Int): DockNode =
    transformTabs(root, tabsNodeId) { tabs ->
        if (index in tabs.tabs.indices) tabs.copy(selectedIndex = index) else tabs
    }

/** Moves a tab within its group, keeping the moved tab selected. */
internal fun moveTab(root: DockNode, tabsNodeId: NodeId, fromIndex: Int, toIndex: Int): DockNode =
    transformTabs(root, tabsNodeId) { tabs ->
        if (fromIndex !in tabs.tabs.indices || toIndex !in tabs.tabs.indices || fromIndex == toIndex) {
            tabs
        } else {
            val reordered = tabs.tabs.toMutableList()
            val moved = reordered.removeAt(fromIndex)
            reordered.add(toIndex, moved)
            tabs.copy(tabs = reordered, selectedIndex = toIndex)
        }
    }

/** Sets the proportion of split [splitNodeId]. No-op if not found. */
internal fun setSplitProportion(root: DockNode, splitNodeId: NodeId, proportion: Float): DockNode =
    when (root) {
        is DockNode.Leaf, is DockNode.Anchor, is DockNode.Tabs -> root
        is DockNode.Split ->
            if (root.id == splitNodeId) {
                root.copy(proportion = proportion.coerceIn(0.05f, 0.95f))
            } else {
                val first = setSplitProportion(root.first, splitNodeId, proportion)
                if (first != root.first) {
                    root.copy(first = first)
                } else {
                    val second = setSplitProportion(root.second, splitNodeId, proportion)
                    if (second != root.second) root.copy(second = second) else root
                }
            }
    }

private fun transformTabs(
    root: DockNode,
    tabsNodeId: NodeId,
    transform: (DockNode.Tabs) -> DockNode.Tabs,
): DockNode = when (root) {
    is DockNode.Leaf, is DockNode.Anchor -> root
    is DockNode.Tabs -> if (root.id == tabsNodeId) transform(root) else root
    is DockNode.Split -> {
        val first = transformTabs(root.first, tabsNodeId, transform)
        if (first != root.first) {
            root.copy(first = first)
        } else {
            val second = transformTabs(root.second, tabsNodeId, transform)
            if (second != root.second) root.copy(second = second) else root
        }
    }
}
