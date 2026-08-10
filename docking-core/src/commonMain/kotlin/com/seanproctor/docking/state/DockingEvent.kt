package com.seanproctor.docking.state

import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.WindowId

/**
 * Events emitted by [DockState] after layout changes. For most reactive needs, observing
 * `snapshotFlow { state.layout }` is simpler and complete; events exist for imperative
 * listeners (logging, analytics, app reactions to specific transitions).
 */
public sealed interface DockingEvent {

    /**
     * [isTemporary] marks the transient undock/dock pair produced by a move (including
     * drags), so listeners can ignore the churn - ModernDocking's `isTemporary` flag.
     */
    public data class Docked(
        val dockableId: DockableId,
        val windowId: WindowId,
        val isTemporary: Boolean = false,
    ) : DockingEvent

    public data class Undocked(
        val dockableId: DockableId,
        val isTemporary: Boolean = false,
    ) : DockingEvent

    /** The dockable became the selected tab of its group. */
    public data class Shown(val dockableId: DockableId) : DockingEvent

    /** The dockable stopped being the selected tab of its group. */
    public data class Hidden(val dockableId: DockableId) : DockingEvent

    public data class Maximized(val dockableId: DockableId) : DockingEvent

    public data class MaximizeRestored(val dockableId: DockableId) : DockingEvent

    /** A floating window was added to the layout. */
    public data class WindowOpened(val windowId: WindowId) : DockingEvent

    /** A floating window was removed (closed or emptied). */
    public data class WindowClosed(val windowId: WindowId) : DockingEvent

    /** A named layout or the whole layout changed via persistence operations. */
    public data class LayoutChanged(val kind: LayoutChangeKind, val name: String? = null) : DockingEvent
}

public enum class LayoutChangeKind { Added, Removed, Restored, Persisted }
