package com.seanproctor.docking.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.movableContentOf
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.state.DockState

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

    /** Geometry for auto-hide outside-press dismissal (root coordinates). */
    var slideOutPanelBounds: androidx.compose.ui.geometry.Rect? = null
    val autoHideStripBounds = mutableMapOf<com.seanproctor.docking.model.AutoHideSide, androidx.compose.ui.geometry.Rect>()

    /**
     * One `movableContentOf` lambda per dockable. Invoking the same lambda from a new
     * position in the same composition moves the content node — preserving all internal
     * state (remember, scroll, focus, text selection) across tab/split restructuring.
     * The lambda reads the spec from the registry snapshot-state so late (re)registration
     * updates content in place.
     */
    private val movables = mutableMapOf<DockableId, @Composable () -> Unit>()

    @Composable
    fun DockableContent(id: DockableId) {
        val content = movables.getOrPut(id) {
            movableContentOf {
                val spec = state.registry[id]
                if (spec != null) {
                    state.contentStateHolder.SaveableStateProvider(id) {
                        spec.content()
                    }
                }
            }
        }
        content()
    }
}
