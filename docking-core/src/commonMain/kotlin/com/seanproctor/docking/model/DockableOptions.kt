package com.seanproctor.docking.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.DpSize

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
    /** Whether maximize is offered for this dockable. */
    val maximizable: Boolean = true,
    /** Tab strip placement preference when this dockable is in a tab group. */
    val tabPreference: TabPreference = TabPreference.Default,
    /**
     * The anchor this dockable belongs to. When the last dockable of an anchor leaves a
     * window, an [DockNode.Anchor] placeholder is restored in its slot.
     */
    val anchor: AnchorId? = null,
    /**
     * The smallest this dockable's pane - header, tab strip and content together - may be
     * made by dragging a divider or by the window around it shrinking. In a tab group the
     * largest minimum of its tabs applies, whichever is selected.
     *
     * Either axis left [Dp.Unspecified][androidx.compose.ui.unit.Dp.Unspecified] (the
     * default) takes [com.seanproctor.docking.state.DockingSettings.minPaneSize]'s.
     * A window too small to hold every pane's minimum squeezes them all by the same
     * fraction of it.
     */
    val minSize: DpSize = DpSize.Unspecified,
)
