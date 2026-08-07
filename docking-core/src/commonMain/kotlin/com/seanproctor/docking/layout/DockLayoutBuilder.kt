package com.seanproctor.docking.layout

import com.seanproctor.docking.model.AnchorId
import com.seanproctor.docking.model.AutoHideEntry
import com.seanproctor.docking.model.AutoHideSide
import com.seanproctor.docking.model.AutoHideState
import com.seanproctor.docking.model.DockLayout
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockWindow
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.NodeIdGenerator
import com.seanproctor.docking.model.WindowBounds
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.model.WindowKind
import com.seanproctor.docking.tree.TreeContext
import com.seanproctor.docking.tree.dockAt
import com.seanproctor.docking.tree.dockAtRoot
import com.seanproctor.docking.tree.findLeaf
import com.seanproctor.docking.tree.findTabsContaining
import com.seanproctor.docking.tree.selectTab
import com.seanproctor.docking.tree.splitWithNode

/**
 * Builds an initial [DockLayout] imperatively, executing the same tree transforms as
 * runtime docking so the semantics are identical (the port of ModernDocking's
 * `WindowLayoutBuilder`).
 *
 * ```
 * val layout = dockLayout {
 *     mainWindow {
 *         dock("project")
 *         dock("editor", target = "project", region = DockRegion.East, proportion = 0.75f)
 *         dock("terminal", target = "editor", region = DockRegion.South, proportion = 0.3f)
 *         dock("problems", target = "terminal", region = DockRegion.Center)
 *         display("terminal")
 *         autoHide("todo", AutoHideSide.South)
 *     }
 *     floatingWindow(WindowBounds(100f, 100f, 400f, 600f)) { dock("palette") }
 * }
 * ```
 */
public fun dockLayout(
    alwaysDisplayTabs: Boolean = false,
    builder: DockLayoutBuilder.() -> Unit,
): DockLayout = DockLayoutBuilder(alwaysDisplayTabs).apply(builder).build()

@DslMarker
public annotation class DockLayoutDsl

@DockLayoutDsl
public class DockLayoutBuilder internal constructor(alwaysDisplayTabs: Boolean) {
    private val ctx = TreeContext(NodeIdGenerator(), alwaysDisplayTabs)
    private var main: DockWindow = DockWindow(WindowId.MAIN, WindowKind.Main, root = null)
    private val floating = mutableListOf<DockWindow>()
    private var floatCounter = 0

    public fun mainWindow(builder: DockWindowBuilder.() -> Unit) {
        main = DockWindowBuilder(ctx, WindowId.MAIN, WindowKind.Main, bounds = null)
            .apply(builder)
            .build()
    }

    public fun floatingWindow(
        bounds: WindowBounds,
        id: String = "float-${++floatCounter}",
        builder: DockWindowBuilder.() -> Unit,
    ) {
        floating += DockWindowBuilder(ctx, WindowId(id), WindowKind.Floating, bounds)
            .apply(builder)
            .build()
    }

    internal fun build(): DockLayout = DockLayout(listOf(main) + floating)
}

@DockLayoutDsl
public class DockWindowBuilder internal constructor(
    private val ctx: TreeContext,
    private val windowId: WindowId,
    private val kind: WindowKind,
    private val bounds: WindowBounds?,
) {
    private var root: DockNode? = null
    private var autoHide = AutoHideState.Empty

    /** Docks [id] at the window root. [proportion] is the new dockable's share. */
    public fun dock(id: String, region: DockRegion = DockRegion.Center, proportion: Float = 0.25f) {
        root = ctx.dockAtRoot(root, DockableId(id), region, proportion)
    }

    /** Docks [id] relative to the already-docked [target]. */
    public fun dock(
        id: String,
        target: String,
        region: DockRegion = DockRegion.Center,
        proportion: Float = 0.5f,
    ) {
        val currentRoot = checkNotNull(root) { "Cannot dock onto '$target': window is empty" }
        val targetLeaf = checkNotNull(currentRoot.findLeaf(DockableId(target))) {
            "Cannot dock onto '$target': it is not docked in this window"
        }
        root = checkNotNull(ctx.dockAt(currentRoot, targetLeaf.id, DockableId(id), region, proportion))
    }

    /** Places an anchor placeholder at the window root's [region]. */
    public fun anchorAtRoot(
        anchorId: String,
        region: DockRegion,
        proportion: Float = 0.25f,
    ) {
        val anchorNode = DockNode.Anchor(ctx.ids.next(), AnchorId(anchorId))
        root = root?.let { existing ->
            if (region == DockRegion.Center) anchorNode else ctx.splitWithNode(existing, anchorNode, region, proportion)
        } ?: anchorNode
    }

    /** Puts [id] into the [side] auto-hide toolbar. */
    public fun autoHide(
        id: String,
        side: AutoHideSide,
        slideProportion: Float = AutoHideEntry.DEFAULT_SLIDE_PROPORTION,
    ) {
        autoHide = autoHide.with(
            side,
            autoHide[side] + AutoHideEntry(DockableId(id), slideProportion),
        )
    }

    /** Selects the tab containing [id] in its tab group. */
    public fun display(id: String) {
        val currentRoot = root ?: return
        val tabs = currentRoot.findTabsContaining(DockableId(id)) ?: return
        val index = tabs.tabs.indexOfFirst { it.dockableId == DockableId(id) }
        root = selectTab(currentRoot, tabs.id, index)
    }

    internal fun build(): DockWindow =
        DockWindow(windowId, kind, root, autoHide, maximized = null, bounds = bounds)
}
