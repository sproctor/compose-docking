package com.seanproctor.docking.drag

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockLayout
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.DockableOptions
import com.seanproctor.docking.model.DockingStyle
import com.seanproctor.docking.model.NodeId
import com.seanproctor.docking.model.WindowBounds
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.spi.HandleKind
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget
import com.seanproctor.docking.tree.allowsRegion
import com.seanproctor.docking.tree.findNode
import com.seanproctor.docking.ui.DockAreaScope
import com.seanproctor.docking.ui.platformDockCapabilities

/** What is being dragged, from where. */
internal sealed interface DragSource {
    class Header(val dockable: DockableId) : DragSource
    class Tab(val dockable: DockableId, val group: NodeId) : DragSource
    class TabGroup(val group: NodeId) : DragSource
}

internal class DragPayload(
    val primary: DockableId,
    /** All dragged dockables in tab order ([primary] included). */
    val all: List<DockableId>,
    /** Effective options: style of [primary]; floatable/limitedToWindow merged over the group. */
    val options: DockableOptions,
    /** Pixel size of the dragged panel at drag start, for floating-window spawn sizing. */
    val sourceSize: Size?,
)

/** A drag-handle icon positioned in a window's root coordinates. */
internal class PositionedHandle(
    val kind: HandleKind,
    val rect: Rect,
    val enabled: Boolean,
    val target: DropTarget,
)

/** Live state of one drag, observed by every window's overlay layer. */
@Stable
internal class DragSession(
    val source: DragSource,
    val payload: DragPayload,
    val originWindow: WindowId,
    internal val snapshot: DockLayout,
) {
    var screenPosition: Offset by mutableStateOf(Offset.Zero)
    var hoveredWindow: WindowId? by mutableStateOf(null)
    var target: DropTarget by mutableStateOf(DropTarget.None)
    var handles: List<PositionedHandle> by mutableStateOf(emptyList())
}

/** Hooks for the desktop layer (preview window, watchdog). */
internal interface DragListener {
    /** True when the platform shows its own drag preview (suppresses the in-window ghost). */
    val showsOwnPreview: Boolean get() = false
    fun onDragStarted(session: DragSession) {}
    fun onDragMoved(screenPosition: Offset) {}
    fun onDragEnded() {}
}

/**
 * The single authority for drag-to-dock: one per [DockState]. Gesture modifiers start
 * sessions; the origin window's root listener feeds moves and the release; every
 * window's overlay layer observes [session] to draw handles and the drop preview.
 *
 * The pre-drag layout is snapshotted and the dragged dockables are undocked immediately
 * (the layout visibly reflows, ModernDocking behavior); a failed or cancelled drop
 * restores the snapshot - the whole drag is transactional.
 */
@Stable
internal class DragController(private val state: DockState) {

    internal class RegisteredWindow(
        val windowId: WindowId,
        val scope: DockAreaScope,
    )

    private val windows = linkedMapOf<WindowId, RegisteredWindow>()

    /**
     * Window-root <-> shared drag space converters, set by the platform window host
     * (identity when absent - the single-window case). Kept separate from window
     * registration so host and DockArea effects may run in any order.
     */
    private val converters = mutableMapOf<WindowId, ScreenConverter>()

    internal class ScreenConverter(
        val toScreen: (Offset) -> Offset,
        val fromScreen: (Offset) -> Offset,
    )

    fun setScreenConverter(
        windowId: WindowId,
        toScreen: (Offset) -> Offset,
        fromScreen: (Offset) -> Offset,
    ) {
        converters[windowId] = ScreenConverter(toScreen, fromScreen)
    }

    fun clearScreenConverter(windowId: WindowId) {
        converters.remove(windowId)
    }

    internal fun toScreen(windowId: WindowId, position: Offset): Offset =
        converters[windowId]?.toScreen?.invoke(position) ?: position

    internal fun fromScreen(windowId: WindowId, position: Offset): Offset =
        converters[windowId]?.fromScreen?.invoke(position) ?: position

    internal fun registeredWindows(): List<RegisteredWindow> = windows.values.toList()

    /** Desktop layer override: resolve the topmost registered window at a screen point. */
    var windowResolver: ((Offset) -> WindowId?)? = null

    var dragListener: DragListener? = null

    var session: DragSession? by mutableStateOf(null)
        private set

    fun registerWindow(windowId: WindowId, scope: DockAreaScope): RegisteredWindow =
        RegisteredWindow(windowId, scope).also { windows[windowId] = it }

    fun unregisterWindow(windowId: WindowId) {
        windows.remove(windowId)
    }

    fun window(windowId: WindowId): RegisteredWindow? = windows[windowId]

    // ----- Session lifecycle -----

    fun startDrag(
        source: DragSource,
        positionInWindow: Offset,
        windowId: WindowId,
        sourceSize: Size? = null,
    ) {
        if (session != null) return
        val payload = buildPayload(source, windowId, sourceSize) ?: return
        val snapshot = state.layout
        payload.all.forEach { state.undockTemporarily(it) }
        val newSession = DragSession(source, payload, windowId, snapshot)
        session = newSession
        dragListener?.onDragStarted(newSession)
        updateDrag(positionInWindow, windowId)
    }

    fun updateDrag(positionInWindow: Offset, windowId: WindowId) {
        val s = session ?: return
        if (windowId !in windows) return
        val screen = toScreen(windowId, positionInWindow)
        s.screenPosition = screen
        dragListener?.onDragMoved(screen)

        val hoveredId = resolveWindowAt(screen)
        if (hoveredId == null) {
            s.hoveredWindow = null
            s.handles = emptyList()
            s.target = if (payloadMayFloat(s.payload)) DropTarget.NewWindow(screen) else DropTarget.None
            return
        }
        if (s.payload.options.limitedToWindow && hoveredId != s.originWindow) {
            s.hoveredWindow = hoveredId
            s.handles = emptyList()
            s.target = DropTarget.None
            return
        }
        val hovered = windows[hoveredId] ?: return
        val local = fromScreen(hoveredId, screen)
        s.hoveredWindow = hoveredId
        s.handles = computeHandles(hovered, local, s.payload)
        s.target = resolveTarget(hovered, local, s)
    }

    fun drop() {
        val s = session ?: return
        session = null
        val applied = applyTarget(s)
        if (!applied || !state.isOpen(s.payload.primary)) {
            state.restoreSnapshot(s.snapshot)
        } else if (s.payload.all.size > 1) {
            state.show(s.payload.primary)
        }
        dragListener?.onDragEnded()
    }

    fun cancel() {
        val s = session ?: return
        session = null
        state.restoreSnapshot(s.snapshot)
        dragListener?.onDragEnded()
    }

    // ----- Payload -----

    private fun buildPayload(
        source: DragSource,
        windowId: WindowId,
        sourceSize: Size?,
    ): DragPayload? = when (source) {
        is DragSource.Header -> singlePayload(source.dockable, sourceSize)
        is DragSource.Tab -> singlePayload(source.dockable, sourceSize)
        is DragSource.TabGroup -> {
            val tabs = state.layout.window(windowId)?.root
                ?.findNode(source.group) as? DockNode.Tabs
            if (tabs == null) {
                null
            } else {
                val ids = tabs.tabs.map { it.dockableId }
                val primary = tabs.selectedTab.dockableId
                val primaryOptions = state.registry.optionsOf(primary)
                DragPayload(
                    primary = primary,
                    all = ids,
                    options = primaryOptions.copy(
                        floatable = ids.all { state.registry.optionsOf(it).floatable },
                        limitedToWindow = ids.any { state.registry.optionsOf(it).limitedToWindow },
                    ),
                    sourceSize = sourceSize,
                )
            }
        }
    }

    private fun singlePayload(id: DockableId, sourceSize: Size?): DragPayload =
        DragPayload(
            primary = id,
            all = listOf(id),
            options = state.registry.optionsOf(id),
            sourceSize = sourceSize,
        )

    private fun payloadMayFloat(payload: DragPayload): Boolean =
        payload.options.floatable &&
            !payload.options.limitedToWindow &&
            platformDockCapabilities.floatingWindows

    // ----- Window resolution -----

    private fun resolveWindowAt(screen: Offset): WindowId? {
        windowResolver?.let { return it(screen) }
        return windows.values.firstOrNull { reg ->
            reg.scope.bounds.rootBounds.contains(fromScreen(reg.windowId, screen))
        }?.windowId
    }

    // ----- Handles -----

    private fun computeHandles(
        window: RegisteredWindow,
        local: Offset,
        payload: DragPayload,
    ): List<PositionedHandle> {
        val density = window.scope.density
        val size = HANDLE_SIZE_DP * density
        val gap = HANDLE_GAP_DP * density
        val root = window.scope.bounds.rootBounds
        if (root.width <= 0f) return emptyList()
        val windowId = window.windowId
        val rootEmpty = state.layout.window(windowId)?.root == null
        val dragged = payload.options.dockingStyle
        val handles = mutableListOf<PositionedHandle>()

        fun add(kind: HandleKind, center: Offset, enabled: Boolean, target: DropTarget) {
            handles += PositionedHandle(
                kind = kind,
                rect = Rect(
                    center.x - size / 2f,
                    center.y - size / 2f,
                    center.x + size / 2f,
                    center.y + size / 2f,
                ),
                enabled = enabled,
                target = target,
            )
        }

        val margin = size / 2f + gap
        // Window-root handles at the edge midpoints (Center only fills an empty root).
        add(
            HandleKind.RootCenter, root.center, rootEmpty,
            DropTarget.Root(windowId, DockRegion.Center),
        )
        add(
            HandleKind.RootNorth, Offset(root.center.x, root.top + margin),
            !rootEmpty && dragged.allowsRegion(DockRegion.North),
            DropTarget.Root(windowId, DockRegion.North),
        )
        add(
            HandleKind.RootSouth, Offset(root.center.x, root.bottom - margin),
            !rootEmpty && dragged.allowsRegion(DockRegion.South),
            DropTarget.Root(windowId, DockRegion.South),
        )
        add(
            HandleKind.RootWest, Offset(root.left + margin, root.center.y),
            !rootEmpty && dragged.allowsRegion(DockRegion.West),
            DropTarget.Root(windowId, DockRegion.West),
        )
        add(
            HandleKind.RootEast, Offset(root.right - margin, root.center.y),
            !rootEmpty && dragged.allowsRegion(DockRegion.East),
            DropTarget.Root(windowId, DockRegion.East),
        )

        // Handles over the hovered dockable.
        val hoveredDockable = window.scope.bounds.dockableAt(local)
        if (hoveredDockable != null) {
            val bounds = window.scope.bounds.boundsOf(hoveredDockable)
            if (bounds != null) {
                val step = size + gap
                fun dockableHandle(kind: HandleKind, offset: Offset, region: DockRegion) {
                    add(
                        kind, bounds.center + offset,
                        state.canDock(payload.primary, DockTarget.OnDockable(hoveredDockable), region),
                        DropTarget.OnDockable(windowId, hoveredDockable, region),
                    )
                }
                dockableHandle(HandleKind.DockableCenter, Offset.Zero, DockRegion.Center)
                dockableHandle(HandleKind.DockableNorth, Offset(0f, -step), DockRegion.North)
                dockableHandle(HandleKind.DockableSouth, Offset(0f, step), DockRegion.South)
                dockableHandle(HandleKind.DockableWest, Offset(-step, 0f), DockRegion.West)
                dockableHandle(HandleKind.DockableEast, Offset(step, 0f), DockRegion.East)
            }
        }
        return handles
    }

    // ----- Drop resolution (ModernDocking's exact precedence) -----

    private fun resolveTarget(
        window: RegisteredWindow,
        local: Offset,
        s: DragSession,
    ): DropTarget {
        val windowId = window.windowId
        val bounds = window.scope.bounds
        val density = window.scope.density
        val hitInflation = HANDLE_HIT_INFLATION_DP * density

        // 1..3: handles (root, dockable, pin - the list already encodes all of them).
        s.handles.firstOrNull { it.enabled && it.rect.inflate(hitInflation).contains(local) }
            ?.let { return it.target }

        // 4: tab strip insertion.
        bounds.tabStripAt(local)?.let { hit ->
            return DropTarget.TabInsert(windowId, hit.nodeId, hit.insertionIndex)
        }

        // 5: proximity region over a dockable.
        bounds.dockableAt(local)?.let { dockable ->
            val rect = bounds.boundsOf(dockable) ?: return@let
            var region = resolveRegion(rect, local)
            if (!state.canDock(s.payload.primary, DockTarget.OnDockable(dockable), region)) {
                region = DockRegion.Center
            }
            return DropTarget.OnDockable(windowId, dockable, region)
        }

        // 5b: anchor placeholders are drop targets too.
        anchorNodeAt(windowId, local, bounds)?.let { anchorNode ->
            return DropTarget.OnNode(windowId, anchorNode, DockRegion.Center)
        }

        // 5c: an empty window accepts the drop as its first content.
        if (state.layout.window(windowId)?.root == null) {
            return DropTarget.Root(windowId, DockRegion.Center)
        }

        return DropTarget.None
    }

    private fun anchorNodeAt(
        windowId: WindowId,
        local: Offset,
        bounds: com.seanproctor.docking.ui.DockBoundsRegistry,
    ): NodeId? {
        val root = state.layout.window(windowId)?.root ?: return null
        val anchorIds = buildList {
            fun visit(node: DockNode) {
                when (node) {
                    is DockNode.Anchor -> add(node.id)
                    is DockNode.Split -> {
                        visit(node.first)
                        visit(node.second)
                    }
                    else -> Unit
                }
            }
            visit(root)
        }
        return anchorIds.firstOrNull { bounds.boundsOfNode(it)?.contains(local) == true }
    }

    // ----- Applying the drop -----

    private fun applyTarget(s: DragSession): Boolean {
        val primary = s.payload.primary
        val rest = s.payload.all.filter { it != primary }
        fun dockRest() = rest.forEach {
            state.dock(it, DockTarget.OnDockable(primary), DockRegion.Center)
        }
        return when (val target = s.target) {
            is DropTarget.Root -> {
                state.dock(primary, DockTarget.Root(target.windowId), target.region)
                dockRest()
                true
            }
            is DropTarget.OnDockable -> {
                state.dock(primary, DockTarget.OnDockable(target.dockableId), target.region)
                dockRest()
                true
            }
            is DropTarget.OnNode -> {
                state.dock(primary, DockTarget.OnNode(target.windowId, target.nodeId), target.region)
                dockRest()
                true
            }
            is DropTarget.TabInsert -> {
                var index = target.index
                var anyDocked = false
                for (id in s.payload.all) {
                    if (state.dockIntoTabsAt(id, target.windowId, target.nodeId, index)) {
                        anyDocked = true
                        index++
                    }
                }
                anyDocked
            }
            is DropTarget.NewWindow -> {
                val originDensity = windows[s.originWindow]?.scope?.density ?: 1f
                val sizeDp = s.payload.sourceSize?.let { Size(it.width / originDensity, it.height / originDensity) }
                state.moveToNewWindow(
                    primary,
                    WindowBounds(
                        x = target.screenPosition.x / originDensity - SPAWN_CURSOR_OFFSET_X_DP,
                        y = target.screenPosition.y / originDensity - SPAWN_CURSOR_OFFSET_Y_DP,
                        width = sizeDp?.width ?: DEFAULT_FLOAT_WIDTH_DP,
                        height = sizeDp?.height ?: DEFAULT_FLOAT_HEIGHT_DP,
                    ),
                )
                dockRest()
                true
            }
            DropTarget.None -> false
        }
    }

    private companion object {
        const val HANDLE_SIZE_DP = 32f
        const val HANDLE_GAP_DP = 8f
        const val HANDLE_HIT_INFLATION_DP = 4f
        const val SPAWN_CURSOR_OFFSET_X_DP = 40f
        const val SPAWN_CURSOR_OFFSET_Y_DP = 12f
        const val DEFAULT_FLOAT_WIDTH_DP = 400f
        const val DEFAULT_FLOAT_HEIGHT_DP = 300f
    }
}
