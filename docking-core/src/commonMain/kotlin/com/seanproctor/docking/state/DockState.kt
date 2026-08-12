package com.seanproctor.docking.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.seanproctor.docking.model.AnchorId
import com.seanproctor.docking.model.DockLayout
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.DockingStyle
import com.seanproctor.docking.model.NodeId
import com.seanproctor.docking.model.NodeIdGenerator
import com.seanproctor.docking.model.WindowBounds
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.tree.DockablePlacement
import com.seanproctor.docking.tree.TreeContext
import com.seanproctor.docking.tree.addFloatingWindow
import com.seanproctor.docking.tree.allowsRegion
import com.seanproctor.docking.tree.closeWindowInLayout
import com.seanproctor.docking.tree.containsDockable
import com.seanproctor.docking.tree.dockIntoAnchorInLayout
import com.seanproctor.docking.tree.dockIntoLayout
import com.seanproctor.docking.tree.findLeaf
import com.seanproctor.docking.tree.findNode
import com.seanproctor.docking.tree.findTabsContaining
import com.seanproctor.docking.tree.isRegionAllowed
import com.seanproctor.docking.tree.maximizeInLayout
import com.seanproctor.docking.tree.findAnchorNode
import com.seanproctor.docking.tree.dockableIds
import com.seanproctor.docking.tree.splitWithNode
import com.seanproctor.docking.tree.moveTab
import com.seanproctor.docking.tree.placementOf
import com.seanproctor.docking.tree.insertTabAt
import com.seanproctor.docking.tree.restoreMaximizeInLayout
import com.seanproctor.docking.tree.restoredFromMaximize
import com.seanproctor.docking.tree.selectTab
import com.seanproctor.docking.tree.setSplitProportion
import com.seanproctor.docking.tree.setWindowBoundsInLayout
import com.seanproctor.docking.tree.undockFromLayout
import com.seanproctor.docking.tree.windowContaining
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The single source of truth for a docking layout: the immutable [layout] value plus the
 * dockable [registry], transient UI state, and every mutation operation.
 *
 * Every operation computes the entire next [DockLayout] and assigns it once, so observers
 * never see intermediate states (a drag-move is one snapshot write, not undock + dock).
 */
@Stable
public class DockState(
    initialLayout: DockLayout = DockLayout.empty(),
    public val settings: DockingSettings = DockingSettings(),
) {
    public var layout: DockLayout by mutableStateOf(initialLayout)
        internal set

    public val registry: DockableRegistry = DockableRegistry()

    private val _events = MutableSharedFlow<DockingEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    public val events: SharedFlow<DockingEvent> = _events.asSharedFlow()

    internal val nodeIds: NodeIdGenerator = NodeIdGenerator()
    private var floatCounter = 0

    /** Named layout snapshots (save/restore/list). */
    public val layouts: com.seanproctor.docking.persistence.NamedLayouts by lazy {
        com.seanproctor.docking.persistence.NamedLayouts(this)
    }

    /** Cross-window rememberSaveable bridge for dockable content. */
    internal val contentStateHolder: DockableSaveableStateHolder = DockableSaveableStateHolder()

    /** The drag-to-dock authority for this state. */
    internal val dragController: com.seanproctor.docking.drag.DragController by lazy {
        com.seanproctor.docking.drag.DragController(this)
    }

    // ----- Transient UI state (never persisted) -----

    /** The dockable with input focus, for the active highlighter. Set by focus/press listeners. */
    public var activeDockable: DockableId? by mutableStateOf(null)

    // ----- Queries -----

    /** Docked in a window tree (including behind a maximized sibling); not auto-hidden. */
    public fun isDocked(id: DockableId): Boolean =
        layout.windowContaining(id)?.placementOf(id).let {
            it == DockablePlacement.Docked || it == DockablePlacement.BehindMaximize
        }

    /** Docked, auto-hidden, or maximized anywhere in the layout. */
    public fun isOpen(id: DockableId): Boolean = layout.windowContaining(id) != null

    public fun isMaximized(id: DockableId): Boolean =
        layout.windows.any { it.maximized?.dockableId == id }

    public fun windowOf(id: DockableId): WindowId? = layout.windowContaining(id)?.id

    /**
     * Whether docking [dockableId] at [target]/[region] is legal: bidirectional
     * [DockingStyle] check, `limitedToWindow`, and floatability are all enforced.
     */
    public fun canDock(dockableId: DockableId, target: DockTarget, region: DockRegion): Boolean {
        val dragged = registry.optionsOf(dockableId)
        val targetWindow = when (target) {
            is DockTarget.OnDockable -> windowOf(target.dockableId)
            is DockTarget.OnNode -> target.windowId
            is DockTarget.Root -> target.windowId
            is DockTarget.Anchor -> null
        }
        val currentWindow = windowOf(dockableId)
        if (dragged.limitedToWindow && targetWindow != null && currentWindow != null &&
            targetWindow != currentWindow
        ) {
            return false
        }
        return when (target) {
            is DockTarget.OnDockable -> isRegionAllowed(
                region,
                registry.optionsOf(target.dockableId).dockingStyle,
                dragged.dockingStyle,
            )
            is DockTarget.OnNode -> {
                val node = layout.window(target.windowId)?.root?.findNode(target.nodeId)
                val targetStyle = when (node) {
                    is DockNode.Leaf -> registry.optionsOf(node.dockableId).dockingStyle
                    is DockNode.Tabs -> registry.optionsOf(node.selectedTab.dockableId).dockingStyle
                    else -> DockingStyle.Both
                }
                isRegionAllowed(region, targetStyle, dragged.dockingStyle)
            }
            is DockTarget.Root -> dragged.dockingStyle.allowsRegion(region)
            is DockTarget.Anchor -> true
        }
    }

    public fun canFloat(id: DockableId): Boolean = registry.optionsOf(id).floatable

    // ----- Operations -----

    /**
     * Docks (or moves) [dockableId] at [target]. Default proportions match ModernDocking:
     * 0.25 for window-root docks, 0.5 relative to other dockables. If the target no
     * longer exists, nothing changes.
     */
    public fun dock(
        dockableId: DockableId,
        target: DockTarget = DockTarget.Root(),
        region: DockRegion = DockRegion.Center,
        proportion: Float? = null,
    ) {
        val ctx = treeContext()
        var working = layout
        val closed = mutableListOf<WindowId>()
        val wasOpen = working.windowContaining(dockableId) != null
        if (wasOpen) {
            val result = ctx.undockFromLayout(working, dockableId)
            working = result.layout
            closed += result.closedWindows
        }
        val docked = applyDockTarget(ctx, working, dockableId, target, region, proportion)
            ?: return // target vanished - abort atomically, nothing changed
        layout = docked
        if (wasOpen) emit(DockingEvent.Undocked(dockableId, isTemporary = true))
        closed.forEach { emit(DockingEvent.WindowClosed(it)) }
        emit(DockingEvent.Docked(dockableId, docked.windowContaining(dockableId)!!.id))
    }

    /** Removes [dockableId] from the layout entirely. */
    public fun undock(dockableId: DockableId) {
        if (!isOpen(dockableId)) return
        val result = treeContext().undockFromLayout(layout, dockableId)
        layout = result.layout
        emit(DockingEvent.Undocked(dockableId))
        result.closedWindows.forEach { emit(DockingEvent.WindowClosed(it)) }
    }

    /**
     * Closes [dockableId] after running its [DockableSpec.canClose] veto. Returns true
     * when the dockable was closed.
     */
    public suspend fun close(dockableId: DockableId): Boolean {
        if (!isOpen(dockableId)) return true
        val spec = registry[dockableId]
        if (spec != null && !spec.options.closable) return false
        if (spec != null && !spec.canClose()) return false
        undock(dockableId)
        return true
    }

    /** Moves [dockableId] into a new floating window. No-op when it is not floatable. */
    public fun moveToNewWindow(dockableId: DockableId, bounds: WindowBounds? = null) {
        if (!canFloat(dockableId)) return
        val ctx = treeContext()
        var working = layout
        val closed = mutableListOf<WindowId>()
        val wasOpen = working.windowContaining(dockableId) != null
        if (wasOpen) {
            val result = ctx.undockFromLayout(working, dockableId)
            working = result.layout
            closed += result.closedWindows
        }
        val windowId = newWindowId(working)
        layout = ctx.addFloatingWindow(working, windowId, dockableId, bounds)
        if (wasOpen) emit(DockingEvent.Undocked(dockableId, isTemporary = true))
        closed.forEach { emit(DockingEvent.WindowClosed(it)) }
        emit(DockingEvent.WindowOpened(windowId))
        emit(DockingEvent.Docked(dockableId, windowId))
    }

    /** Maximizes [dockableId] in its window. */
    public fun maximize(dockableId: DockableId) {
        if (!registry.optionsOf(dockableId).maximizable) return
        val next = treeContext().maximizeInLayout(layout, dockableId) ?: return
        layout = next
        emit(DockingEvent.Maximized(dockableId))
    }

    /** Restores a maximized window to its saved tree. */
    public fun restore(windowId: WindowId) {
        val maximized = layout.window(windowId)?.maximized ?: return
        layout = restoreMaximizeInLayout(layout, windowId)
        emit(DockingEvent.MaximizeRestored(maximized.dockableId))
    }

    public fun toggleMaximize(dockableId: DockableId) {
        if (isMaximized(dockableId)) {
            windowOf(dockableId)?.let { restore(it) }
        } else {
            maximize(dockableId)
        }
    }

    /** Selects tab [index] of the tab group [nodeId], emitting Shown/Hidden events. */
    public fun selectTab(nodeId: NodeId, index: Int) {
        transformTrees(
            beforeAfter = { window, before, after ->
                val oldTabs = before.findNode(nodeId) as? DockNode.Tabs ?: return@transformTrees null
                val newTabs = after.findNode(nodeId) as? DockNode.Tabs ?: return@transformTrees null
                if (oldTabs.selectedIndex != newTabs.selectedIndex) {
                    listOf(
                        DockingEvent.Hidden(oldTabs.selectedTab.dockableId),
                        DockingEvent.Shown(newTabs.selectedTab.dockableId),
                    )
                } else {
                    null
                }
            },
        ) { root -> selectTab(root, nodeId, index) }
    }

    /** Reorders a tab within its group. */
    public fun moveTab(nodeId: NodeId, fromIndex: Int, toIndex: Int) {
        transformTrees { root -> moveTab(root, nodeId, fromIndex, toIndex) }
    }

    /** Sets a split's proportion (committed value; live divider drag is UI-transient). */
    public fun setSplitProportion(nodeId: NodeId, proportion: Float) {
        transformTrees { root -> setSplitProportion(root, nodeId, proportion) }
    }

    /** Double-click-divider reset to an even split. */
    public fun resetSplitProportion(nodeId: NodeId) {
        setSplitProportion(nodeId, 0.5f)
    }

    /**
     * Makes sure [anchorId] names somewhere in the main window, adding a placeholder for it at
     * [region] of the window root if it is missing. Returns true when one was added.
     *
     * Idempotent, and does nothing when the anchor is already accounted for - either its
     * placeholder is in the tree, or a dockable carrying it is docked, in which case the area
     * exists and re-adding it would give the layout two of the same area.
     *
     * This is the counterpart to declaring anchors in [com.seanproctor.docking.layout.dockLayout]:
     * a layout restored from JSON replaces the one the builder made, so a snapshot saved before an
     * area was introduced - or by a version of the app that did not have it - comes back without
     * it. Call this after
     * [restoreLayout][com.seanproctor.docking.persistence.restoreLayout] for each area the
     * application expects to have, and the missing ones are filled in without disturbing the rest.
     */
    public fun ensureAnchor(
        anchorId: AnchorId,
        region: DockRegion,
        proportion: Float = 0.25f,
    ): Boolean {
        val window = layout.mainWindow
        val root = window.root
        if (root != null &&
            (root.findAnchorNode(anchorId) != null || root.dockableIds().any { registry.anchorOf(it) == anchorId })
        ) {
            return false
        }
        val ctx = treeContext()
        val anchorNode = DockNode.Anchor(nodeIds.next(), anchorId)
        val newRoot = if (root == null) {
            anchorNode
        } else {
            ctx.splitWithNode(root, anchorNode, region, proportion)
        }
        layout = layout.replaceWindow(window.copy(root = newRoot))
        return true
    }

    /**
     * Brings [dockableId] to the user's attention: selects its tab if docked, docks it if
     * closed (into its anchor when it has one).
     */
    public fun show(dockableId: DockableId) {
        when {
            isDocked(dockableId) -> {
                val window = layout.windowContaining(dockableId) ?: return
                if (window.maximized != null && window.maximized.dockableId != dockableId) {
                    restore(window.id)
                }
                val root = layout.windowContaining(dockableId)?.root ?: return
                root.findTabsContaining(dockableId)?.let { tabs ->
                    val index = tabs.tabs.indexOfFirst { it.dockableId == dockableId }
                    selectTab(tabs.id, index)
                }
            }
            else -> {
                val anchor = registry.optionsOf(dockableId).anchor
                if (anchor != null) {
                    dock(dockableId, DockTarget.Anchor(anchor))
                } else if (layout.mainWindow.root == null) {
                    dock(dockableId, DockTarget.Root(), DockRegion.Center)
                } else {
                    dock(dockableId, DockTarget.Root(), DockRegion.East, 0.25f)
                }
            }
        }
        activeDockable = dockableId
    }

    /** Called by the platform window host when the OS moves/resizes a window. */
    public fun setWindowBounds(windowId: WindowId, bounds: WindowBounds) {
        layout = setWindowBoundsInLayout(layout, windowId, bounds)
    }

    /** Closes a floating window, dropping everything in it. */
    public fun closeWindow(windowId: WindowId) {
        val result = closeWindowInLayout(layout, windowId)
        if (result.closedWindows.isEmpty()) return
        layout = result.layout
        result.closedWindows.forEach { emit(DockingEvent.WindowClosed(it)) }
    }

    // ----- Internal operations for the drag controller -----

    /** Undocks marking events as drag churn ([DockingEvent.Undocked.isTemporary]). */
    internal fun undockTemporarily(dockableId: DockableId) {
        if (!isOpen(dockableId)) return
        val result = treeContext().undockFromLayout(layout, dockableId)
        layout = result.layout
        emit(DockingEvent.Undocked(dockableId, isTemporary = true))
        result.closedWindows.forEach { emit(DockingEvent.WindowClosed(it)) }
    }

    /** Docks a currently-closed dockable into a tab group at a specific index (strip drop). */
    internal fun dockIntoTabsAt(
        dockableId: DockableId,
        windowId: WindowId,
        tabsNodeId: NodeId,
        index: Int,
    ): Boolean {
        val window = layout.window(windowId) ?: return false
        val root = window.restoredFromMaximize().root ?: return false
        val newRoot = treeContext().insertTabAt(root, tabsNodeId, dockableId, index) ?: return false
        layout = layout.replaceWindow(window.restoredFromMaximize().copy(root = newRoot))
        emit(DockingEvent.Docked(dockableId, windowId))
        return true
    }

    /** Restores a pre-drag snapshot without emitting layout events (drag cancel). */
    internal fun restoreSnapshot(snapshot: DockLayout) {
        layout = snapshot
    }

    // ----- Internals -----

    internal fun treeContext(): TreeContext =
        TreeContext(nodeIds, settings.alwaysDisplayTabs, registry::anchorOf)

    internal fun emit(event: DockingEvent) {
        _events.tryEmit(event)
    }

    internal fun newWindowId(current: DockLayout = layout): WindowId {
        while (true) {
            val candidate = WindowId("float-${++floatCounter}")
            if (current.window(candidate) == null) return candidate
        }
    }

    private fun applyDockTarget(
        ctx: TreeContext,
        working: DockLayout,
        dockableId: DockableId,
        target: DockTarget,
        region: DockRegion,
        proportion: Float?,
    ): DockLayout? = when (target) {
        is DockTarget.Root ->
            ctx.dockIntoLayout(working, dockableId, target.windowId, null, region, proportion ?: 0.25f)
        is DockTarget.OnNode ->
            ctx.dockIntoLayout(working, dockableId, target.windowId, target.nodeId, region, proportion ?: 0.5f)
        is DockTarget.OnDockable -> {
            val window = working.windows.firstOrNull {
                it.root?.containsDockable(target.dockableId) == true
            }
            val leaf = window?.root?.findLeaf(target.dockableId)
            if (window == null || leaf == null) {
                null
            } else {
                ctx.dockIntoLayout(working, dockableId, window.id, leaf.id, region, proportion ?: 0.5f)
            }
        }
        is DockTarget.Anchor ->
            ctx.dockIntoAnchorInLayout(working, dockableId, target.anchorId)
                ?: run {
                    // Anchor absent anywhere: fall back to the main window.
                    val main = working.mainWindow
                    if (main.root == null) {
                        ctx.dockIntoLayout(working, dockableId, main.id, null, DockRegion.Center, 0.25f)
                    } else {
                        ctx.dockIntoLayout(working, dockableId, main.id, null, DockRegion.East, 0.25f)
                    }
                }
    }

    /**
     * Applies a tree transform to every window (first match wins), optionally deriving
     * events from the before/after trees.
     */
    private fun transformTrees(
        beforeAfter: ((WindowId, DockNode, DockNode) -> List<DockingEvent>?)? = null,
        transform: (DockNode) -> DockNode,
    ) {
        for (window in layout.windows) {
            val root = window.root ?: continue
            val newRoot = transform(root)
            if (newRoot != root) {
                layout = layout.replaceWindow(window.copy(root = newRoot))
                beforeAfter?.invoke(window.id, root, newRoot)?.forEach { emit(it) }
                return
            }
        }
    }
}
