package com.seanproctor.docking.desktop

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.WindowState
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget

/**
 * What [FloatingDockWindows] knows about one floating window, handed to the application so it
 * can build the OS window around it.
 *
 * [state] is core's - it is two-way bound to the layout's persisted bounds - so pass it to
 * whatever window composable you call rather than remembering your own.
 */
@Stable
public class FloatingWindowModel(
    public val windowId: WindowId,
    /** The first dockable's title, or "Floating" when the window has none. */
    public val title: String,
    public val icon: Painter?,
    public val state: WindowState,
    /** Closes the window through the layout, which undocks what it holds. */
    public val onCloseRequest: () -> Unit,
    public val dockState: DockState,
    /**
     * The one dockable this window holds, or null when it holds a split or a tab group.
     *
     * Non-null exactly when the window's root is a single leaf, which is also the only
     * shape whose header stands in for a title bar: it is the dockable this window is
     * named after, and the one [dockIntoMainWindow] puts back.
     */
    public val dockableId: DockableId?,
)

/**
 * Docks this window's lone dockable back into the main window, closing the window behind
 * it. No-op for a window holding more than one.
 *
 * Where it lands is what [com.seanproctor.docking.state.DockState.show] would pick for a
 * dockable that is not on screen: its declared anchor, or failing that the east quarter of
 * the main window - not necessarily the spot it was torn from, which the layout does not
 * record. Dragging the header back onto a dock area is the other way home, and lands where
 * it is dropped; this is the one to offer where a drag is awkward, or from a menu.
 */
public fun FloatingWindowModel.dockIntoMainWindow() {
    val id = dockableId ?: return
    val anchor = dockState.registry.optionsOf(id).anchor
    when {
        anchor != null -> dockState.dock(id, DockTarget.Anchor(anchor))
        dockState.layout.mainWindow.root == null ->
            dockState.dock(id, DockTarget.Root(WindowId.MAIN), DockRegion.Center)
        else -> dockState.dock(id, DockTarget.Root(WindowId.MAIN), DockRegion.East, 0.25f)
    }
}
