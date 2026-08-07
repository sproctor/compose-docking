package com.seanproctor.docking.model

import androidx.compose.runtime.Immutable

/**
 * Static docking behavior of a dockable. Everything visual or reactive (title, icon,
 * content) lives on [com.seanproctor.docking.state.DockableSpec]; these options gate what
 * the docking engine allows.
 */
@Immutable
public data class DockableOptions(
    /** Optional application-defined category, used to find/dock dockables by type. */
    val type: String? = null,
    /** Whether the dockable shows a close affordance and may be closed. */
    val closable: Boolean = true,
    /** Whether the dockable may be torn off into a floating window. */
    val floatable: Boolean = true,
    /** When true, the dockable may never leave the window it is currently in. */
    val limitedToWindow: Boolean = false,
    /** Which split regions this dockable participates in (checked on both drag sides). */
    val dockingStyle: DockingStyle = DockingStyle.Both,
    /** Which auto-hide toolbars this dockable may hide into. */
    val autoHideStyle: DockingStyle = DockingStyle.Both,
    /** Whether auto-hide is offered at all for this dockable. */
    val autoHideAllowed: Boolean = true,
    /** Whether maximize is offered for this dockable. */
    val maximizable: Boolean = true,
    /** Tab strip placement preference when this dockable is in a tab group. */
    val tabPreference: TabPreference = TabPreference.Default,
    /**
     * The anchor this dockable belongs to. When the last dockable of an anchor leaves a
     * window, an [DockNode.Anchor] placeholder is restored in its slot.
     */
    val anchor: AnchorId? = null,
)
