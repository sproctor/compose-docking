package com.seanproctor.docking.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget

/**
 * What [FloatingDockWindows] knows about one floating window, handed to a
 * [FloatingWindowHost] so it can build the OS window around it.
 *
 * [state] is core's - it is two-way bound to the layout's persisted bounds - so a host
 * must pass it to whatever window composable it calls rather than remembering its own.
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

/**
 * Creates the OS window for a floating dock area. Implement this to replace the plain
 * [Window] core uses by default - with a design system's own window type, or with an
 * undecorated window whose frame you draw yourself.
 *
 * The host owns the window; core owns what goes in it. Whatever window composable the
 * host calls, it must invoke [content] somewhere inside it: that is the dock area, along
 * with the registration that makes cross-window drags find this window.
 *
 * Install one for the whole application by providing [LocalFloatingWindowHost], or pass
 * one to a single [FloatingDockWindows] call.
 */
@Stable
public interface FloatingWindowHost {

    /**
     * Emit the window. Called from an `application` scope, once per floating window in
     * the layout, and left when the layout drops the window.
     */
    @Composable
    public fun FloatingWindow(
        model: FloatingWindowModel,
        content: @Composable FrameWindowScope.() -> Unit,
    )
}

/**
 * An undecorated [Window]: the dockable's own header is the whole of its chrome, so a
 * torn-off panel looks like the panel it was and not like an OS window wrapped around one.
 *
 * There is no OS title bar to duplicate the header, no second name, and no theme the
 * library does not control. What that costs is everything the title bar used to supply,
 * which the window now gets elsewhere:
 *
 * - **Moving it.** Core makes the header of a window's lone dockable drag the window, the
 *   way a title bar does.
 * - **Resizing it.** Compose Desktop's own resizer handles an undecorated resizable
 *   window's edges; nothing here has to draw them.
 * - **Minimize, maximize, close.** Nobody draws these for you. The library contributes no
 *   header buttons at all - a dockable's are its
 *   [com.seanproctor.docking.state.DockableSpec.trailingActions] - so an application that
 *   wants them reads [com.seanproctor.docking.ui.LocalDockWindow] and draws them when the
 *   window is floating.
 */
public object DefaultFloatingWindowHost : FloatingWindowHost {

    @Composable
    override fun FloatingWindow(
        model: FloatingWindowModel,
        content: @Composable FrameWindowScope.() -> Unit,
    ) {
        Window(
            onCloseRequest = model.onCloseRequest,
            state = model.state,
            title = model.title,
            icon = model.icon,
            undecorated = true,
            resizable = true,
        ) {
            content()
        }
    }
}

public val LocalFloatingWindowHost: ProvidableCompositionLocal<FloatingWindowHost> =
    staticCompositionLocalOf { DefaultFloatingWindowHost }
