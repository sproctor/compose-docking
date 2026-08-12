package com.seanproctor.docking.state

import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Dp
import com.seanproctor.docking.model.TabPreference

/**
 * Behavioral settings for a [DockState]. Fixed at creation: the `*Always` values switch
 * the layout into always-display-tabs mode, which changes how tree nodes are built, so
 * flipping it on a live layout would leave existing panes inconsistent with new ones.
 * Pair it with `dockLayout(alwaysDisplayTabs = ...)` so the initial layout is built the
 * same way.
 */
@Stable
public class DockingSettings(
    /** Tab placement used when a dockable doesn't state its own preference. */
    public val defaultTabPreference: TabPreference = TabPreference.Bottom,
    /**
     * How thick an empty anchor's slot is drawn along its split's axis, collapsing it to
     * a strip instead of letting the placeholder take the whole proportional share.
     *
     * An anchor holds its slot open after its last dockable leaves
     * ([com.seanproctor.docking.model.DockNode.Anchor]), which is what makes the area
     * still there to drag something back into. At full size that is a large empty pane
     * asserting itself in a layout the user just emptied; collapsed, it is a strip that
     * stays a drop target while giving its space back to the neighbour, and re-expands
     * to the split's proportion the moment something docks into it.
     *
     * [Dp.Unspecified] (the default) leaves the placeholder at full size. The split's
     * proportion is untouched either way, so collapsing changes nothing about where the
     * area reopens - only what it costs while empty. Renderers draw the strip through
     * [com.seanproctor.docking.spi.DockingRenderer.CollapsedAnchor].
     */
    public val collapsedAnchorThickness: Dp = Dp.Unspecified,
    /**
     * When an empty anchor takes up space at all.
     *
     * [EmptyAnchorVisibility.WhileDragging] gives the area back completely while nothing is
     * being dragged - no strip, no divider, the neighbour takes the whole split - and
     * brings it back the moment a drag starts, which is the only time an empty area is of
     * any use. Pair it with [collapsedAnchorThickness]: without one the area reappears at
     * its full proportional size, which moves the layout under the pointer mid-drag.
     */
    public val emptyAnchorVisibility: EmptyAnchorVisibility = EmptyAnchorVisibility.Always,
) {
    /** True when empty anchors collapse to a strip rather than filling their slot. */
    public val collapsesEmptyAnchors: Boolean
        get() = collapsedAnchorThickness != Dp.Unspecified && collapsedAnchorThickness.value > 0f

    /** True when every dockable renders as a tab group even while alone. */
    public val alwaysDisplayTabs: Boolean
        get() = defaultTabPreference == TabPreference.TopAlways ||
            defaultTabPreference == TabPreference.BottomAlways
}

/** When an empty [com.seanproctor.docking.model.DockNode.Anchor] occupies space in the layout. */
public enum class EmptyAnchorVisibility {
    /** The placeholder always holds its slot, whether or not anything is being dragged. */
    Always,

    /**
     * The placeholder holds its slot only while a drag is in flight, and takes no space at
     * all otherwise. The area still exists the whole time - what changes is that an empty
     * one costs nothing to keep until there is something to drop into it.
     */
    WhileDragging,
}
