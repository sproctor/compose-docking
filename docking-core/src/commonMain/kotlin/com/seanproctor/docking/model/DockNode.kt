package com.seanproctor.docking.model

import androidx.compose.runtime.Immutable

/**
 * A node in a window's dock layout tree. The tree is immutable: all layout mutations
 * produce a new tree via the pure transforms in the library, and the current tree for a
 * window is read from [com.seanproctor.docking.model.DockWindow.root].
 */
@Immutable
public sealed interface DockNode {
    public val id: NodeId

    /** A single docked dockable. */
    @Immutable
    public data class Leaf(
        override val id: NodeId,
        val dockableId: DockableId,
    ) : DockNode

    /**
     * Two children split [Horizontal][SplitOrientation.Horizontal]ly (side by side) or
     * [Vertical][SplitOrientation.Vertical]ly (stacked). [proportion] is the fraction of
     * space given to [first] (left/top), in `0..1`.
     */
    @Immutable
    public data class Split(
        override val id: NodeId,
        val orientation: SplitOrientation,
        val first: DockNode,
        val second: DockNode,
        val proportion: Float = 0.5f,
    ) : DockNode {
        init {
            require(proportion in 0f..1f) { "proportion must be in 0..1, was $proportion" }
        }
    }

    /**
     * A tab group. Tabs are always leaves; docking a dockable [Center][DockRegion.Center]
     * onto a member appends a tab.
     */
    @Immutable
    public data class Tabs(
        override val id: NodeId,
        val tabs: List<Leaf>,
        val selectedIndex: Int = 0,
    ) : DockNode {
        init {
            require(tabs.isNotEmpty()) { "Tabs node must have at least one tab" }
            require(selectedIndex in tabs.indices) {
                "selectedIndex $selectedIndex out of bounds for ${tabs.size} tabs"
            }
        }

        val selectedTab: Leaf get() = tabs[selectedIndex]
    }

    /**
     * A named placeholder occupying a layout slot. Docking into the anchor replaces this
     * node with real content; when the last dockable carrying the same [anchorId] leaves
     * the window, the placeholder is restored instead of collapsing the slot. This gives
     * layouts stable named regions that survive close-all.
     */
    @Immutable
    public data class Anchor(
        override val id: NodeId,
        val anchorId: AnchorId,
    ) : DockNode
}
