package com.seanproctor.docking.state

import com.seanproctor.docking.model.AnchorId
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.NodeId
import com.seanproctor.docking.model.WindowId

/** Where to dock a dockable. */
public sealed interface DockTarget {

    /** Dock relative to another (currently docked) dockable. */
    public data class OnDockable(val dockableId: DockableId) : DockTarget

    /** Dock relative to a specific tree node (used by drag-and-drop resolution). */
    public data class OnNode(val windowId: WindowId, val nodeId: NodeId) : DockTarget

    /** Dock relative to a window's whole root. */
    public data class Root(val windowId: WindowId = WindowId.MAIN) : DockTarget

    /**
     * Dock into an anchor region: replaces the placeholder if present, else joins the
     * largest docked dockable sharing the anchor, else falls back to the main window.
     */
    public data class Anchor(val anchorId: AnchorId) : DockTarget
}
