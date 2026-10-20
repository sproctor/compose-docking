package com.seanproctor.docking.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.DpSize
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.NodeId
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.EmptyAnchorVisibility

/**
 * Per-window context for one [DockArea]: geometry registry and the movable-content host
 * that preserves dockable state across re-parenting within this window.
 */
@Stable
internal class DockAreaScope(
    val state: DockState,
    val windowId: WindowId,
) {
    val bounds = DockBoundsRegistry()


    /** Density of this window's composition, for px-sized drag geometry. */
    var density: Float = 1f

    /** [dockable]'s own minimum pane size, with the settings' default filling any gap. */
    fun paneMinOf(dockable: DockableId): DpSize =
        state.registry.optionsOf(dockable).resolvedMinSize(state.settings.minPaneSize)

    /**
     * The minimum size of every node in this window's tree - see [minSize] - kept up to date
     * as the layout and the registered dockables change, and worked out once per change
     * rather than once per split that asks.
     *
     * An empty area needs whatever it is drawn at when that is fixed: the strip, when empty
     * areas collapse to one - a nested split holding a strip has to leave room for it beside
     * its other side's minimum - and nothing when they are hidden with no strip to come back
     * as. Only a full-size placeholder needs the default pane minimum. The choice holds drag
     * or no drag, although a hidden strip takes no space until one starts: tying it to the
     * drag session would change every neighbouring split's limits the moment a drag starts,
     * and move them under the pointer.
     */
    val minimums: State<Map<NodeId, DpSize>> = derivedStateOf {
        val settings = state.settings
        val emptyMin = when {
            settings.collapsesEmptyAnchors ->
                DpSize(settings.collapsedAnchorThickness, settings.collapsedAnchorThickness)
            settings.emptyAnchorVisibility == EmptyAnchorVisibility.WhileDragging -> DpSize.Zero
            else -> settings.minPaneSize
        }
        state.layout.window(windowId)?.root
            ?.minSizes(::paneMinOf, emptyMin, DividerThickness)
            .orEmpty()
    }

    /** [node]'s entry in [minimums]. */
    fun minSizeOf(node: NodeId): DpSize = minimums.value[node] ?: DpSize.Zero

    /** Window-wide double-click sequencing for headers and tabs. */
    val clicks = ClickTracker()

    /** Live tab-reorder state (before a drag escalates to a full dock drag). */
    var tabReorder: TabReorderState? by mutableStateOf(null)

    /**
     * One `movableContentOf` lambda per dockable. Invoking the same lambda from a new
     * position in the same composition moves the content node - preserving all internal
     * state (remember, scroll, focus, text selection) across tab/split restructuring.
     * The lambda reads the spec from the registry snapshot-state so late (re)registration
     * updates content in place.
     */
    private val movables = mutableMapOf<DockableId, @Composable () -> Unit>()

    @Composable
    fun DockableContent(id: DockableId) {
        movableFor(id)()
    }

    // Must not be @Composable: there the compiler memoizes the lambda by call site, so
    // every dockable shown at the same site would share one instance (issue #5).
    private fun movableFor(id: DockableId): @Composable () -> Unit = movables.getOrPut(id) {
        movableContentOf {
            val spec = state.registry[id]
            if (spec != null) {
                state.contentStateHolder.SaveableStateProvider(id) {
                    spec.content()
                }
            }
        }
    }
}

/** A tab being reordered within its strip. */
@Stable
internal class TabReorderState(
    val group: NodeId,
    val dockable: DockableId,
    val fromIndex: Int,
) {
    var offsetX: Float by mutableStateOf(0f)
}
