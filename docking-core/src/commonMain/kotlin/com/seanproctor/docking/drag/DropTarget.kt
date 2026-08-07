package com.seanproctor.docking.drag

import androidx.compose.ui.geometry.Offset
import com.seanproctor.docking.model.AutoHideSide
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.NodeId
import com.seanproctor.docking.model.WindowId

/** What a drag would do if released right now (ModernDocking's drop resolution result). */
public sealed interface DropTarget {

    public data class Root(val windowId: WindowId, val region: DockRegion) : DropTarget

    public data class OnDockable(
        val windowId: WindowId,
        val dockableId: DockableId,
        val region: DockRegion,
    ) : DropTarget

    /** Dock onto a specific node (anchor placeholders). */
    public data class OnNode(
        val windowId: WindowId,
        val nodeId: NodeId,
        val region: DockRegion,
    ) : DropTarget

    /** Drop into an auto-hide toolbar via a pin handle. */
    public data class Pin(val windowId: WindowId, val side: AutoHideSide) : DropTarget

    /** Insert into a tab strip at [index]. */
    public data class TabInsert(
        val windowId: WindowId,
        val nodeId: NodeId,
        val index: Int,
    ) : DropTarget

    /** Spawn a floating window at the drop position (screen coordinates). */
    public data class NewWindow(val screenPosition: Offset) : DropTarget

    /** No valid target: the drag restores the pre-drag layout. */
    public data object None : DropTarget
}
