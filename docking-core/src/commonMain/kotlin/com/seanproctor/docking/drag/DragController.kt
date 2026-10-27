package com.seanproctor.docking.drag

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
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
import com.seanproctor.docking.window.DockHostWindow

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
    /**
     * True when the drag is carrying [originWindow] itself - an undecorated floating
     * window dragged by the header that stands in for its title bar.
     *
     * The window is the preview, so the payload stays docked in it and follows the
     * pointer bodily rather than being undocked into a ghost. Released over a dock area
     * it docks there and the window goes; released anywhere else it simply stays where it
     * was dropped, which is what dragging a window is normally for.
     */
    val movesWindow: Boolean = false,
    /**
     * [originWindow]'s density as the drag began, for sizing a torn-off window from the
     * dragged panel's pixels. Kept here because the window can close mid-drag - tearing out
     * the last thing in a floating window empties it - and with it goes the dock area that
     * knew the density.
     */
    val originDensity: Float = 1f,
) {
    var screenPosition: Offset by mutableStateOf(Offset.Zero)

    /**
     * Carried by the platform's drag-and-drop session (native Wayland) rather than by the
     * origin window's pointer stream: the windows' drop targets report the pointer and
     * the drop, so the root listeners leave the session alone.
     */
    var carriedByPlatform: Boolean = false

    /**
     * The window whose drop target took a platform-carried drag's drop: recorded there, applied
     * when the platform ends the drag on the source ([DragController.endPlatformDrag]).
     */
    var platformDropWindow: WindowId? = null
    var hoveredWindow: WindowId? by mutableStateOf(null)
    var target: DropTarget by mutableStateOf(DropTarget.None)
    var handles: List<PositionedHandle> by mutableStateOf(emptyList())
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
     * The OS windows the application registered ([com.seanproctor.docking.window.registerDockingWindow]),
     * in registration order: where each one's content is on screen, for mapping its root-local
     * positions into the shared drag space and for finding the window under the pointer. A
     * window registered here before or after its dock area is the same; the two are joined by id.
     */
    private val hostWindows = linkedMapOf<WindowId, DockHostWindow>()

    fun registerHostWindow(windowId: WindowId, window: DockHostWindow) {
        hostWindows[windowId] = window
    }

    fun unregisterHostWindow(windowId: WindowId, window: DockHostWindow) {
        if (hostWindows[windowId] === window) hostWindows.remove(windowId)
    }

    fun hostWindow(windowId: WindowId): DockHostWindow? = hostWindows[windowId]

    /**
     * Where each registered OS window's content is on screen, asked once. Reading a window's
     * position can be a round trip to the display server, so a pointer move reads them all
     * once and works from this.
     */
    private fun screenBounds(): Map<WindowId, Rect?> = hostWindows.mapValues { it.value.contentBoundsOnScreen() }

    /** Where [windowId]'s root origin is in the shared space; null when it cannot say. */
    private fun originOf(windowId: WindowId, bounds: Map<WindowId, Rect?>? = null): Offset? =
        (if (bounds != null) bounds[windowId] else hostWindows[windowId]?.contentBoundsOnScreen())?.topLeft

    /**
     * Pixels of [windowId]'s Compose root per unit of the shared space: the OS window's own
     * scale, or - with no OS window registered - its dock area's density, which then has
     * only itself to agree with.
     */
    private fun densityOf(windowId: WindowId): Float =
        hostWindows[windowId]?.density ?: windows[windowId]?.scope?.density ?: 1f

    /**
     * [position] in [windowId]'s root, in pixels, as a point in the shared space: screen
     * coordinates in the window toolkit's logical units, which every window shares whatever
     * the density of the monitor it is on. A window that cannot say where it is maps from its
     * own origin, so its points are only ever compared with its own.
     */
    internal fun toScreen(windowId: WindowId, position: Offset, bounds: Map<WindowId, Rect?>? = null): Offset =
        (originOf(windowId, bounds) ?: Offset.Zero) + position / densityOf(windowId)

    internal fun fromScreen(windowId: WindowId, position: Offset, bounds: Map<WindowId, Rect?>? = null): Offset =
        (position - (originOf(windowId, bounds) ?: Offset.Zero)) * densityOf(windowId)

    internal fun registeredWindows(): List<RegisteredWindow> = windows.values.toList()

    /**
     * How many preview windows the application is drawing
     * ([com.seanproctor.docking.window.DockingWindowHost.claimDragPreview]): while any is, the
     * dock areas leave out their in-window ghost card.
     */
    var previewClaims: Int = 0

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
        movesWindow: Boolean = false,
    ) {
        if (session != null) return
        val payload = buildPayload(source, windowId, sourceSize) ?: return
        val snapshot = state.layout
        val originDensity = densityOf(windowId)
        // Undocking is what turns a panel into a ghost following the pointer. A window
        // drag has the real window to follow it, and undocking would delete that window
        // out from under the gesture, so the payload stays where it is until the drop.
        if (!movesWindow) payload.all.forEach { state.undockTemporarily(it) }
        val newSession = DragSession(source, payload, windowId, snapshot, movesWindow, originDensity)
        session = newSession
        updateDrag(positionInWindow, windowId)
    }

    fun updateDrag(positionInWindow: Offset, windowId: WindowId) {
        val s = session ?: return
        if (windowId !in windows) return
        val bounds = screenBounds()
        val screen = toScreen(windowId, positionInWindow, bounds)
        s.screenPosition = screen

        // The dragged window sits under the pointer for the whole gesture - it is following
        // it - so it has to be looked past to find what is underneath, and it is never a
        // target for itself.
        val hoveredId = resolveWindowAt(
            screen,
            bounds,
            exclude = s.originWindow.takeIf { s.movesWindow },
            from = windowId,
        )
        if (hoveredId == null) {
            s.hoveredWindow = null
            s.handles = emptyList()
            // A window dropped over nothing is already floating, exactly where the drop
            // left it; there is no new window to spawn and nothing to move.
            s.target = when {
                s.movesWindow -> DropTarget.None
                payloadMayFloat(s.payload) -> DropTarget.NewWindow(screen)
                else -> DropTarget.None
            }
            return
        }
        if (s.payload.options.limitedToWindow && hoveredId != s.originWindow) {
            s.hoveredWindow = hoveredId
            s.handles = emptyList()
            s.target = DropTarget.None
            return
        }
        val hovered = windows[hoveredId] ?: return
        val local = fromScreen(hoveredId, screen, bounds)
        s.hoveredWindow = hoveredId
        s.handles = computeHandles(hovered, local, s.payload)
        s.target = resolveTarget(hovered, local, s)
    }

    /**
     * The pointer is at [positionInWindow] in [windowId]'s root, as that window's drop
     * target reports it: no shared screen space, the window is simply the hovered one.
     */
    private fun updateDragInWindow(positionInWindow: Offset, windowId: WindowId) {
        val s = session ?: return
        val hovered = windows[windowId] ?: return
        val screen = toScreen(windowId, positionInWindow)
        s.screenPosition = screen
        if (s.payload.options.limitedToWindow && windowId != s.originWindow) {
            s.hoveredWindow = windowId
            s.handles = emptyList()
            s.target = DropTarget.None
            return
        }
        s.hoveredWindow = windowId
        s.handles = computeHandles(hovered, positionInWindow, s.payload)
        s.target = resolveTarget(hovered, positionInWindow, s)
    }

    /** The pointer left every window: a release now tears the payload off, if it may float. */
    fun leaveWindows() {
        val s = session ?: return
        s.hoveredWindow = null
        s.handles = emptyList()
        s.target = if (!s.movesWindow && payloadMayFloat(s.payload)) DropTarget.NewWindow(s.screenPosition) else DropTarget.None
    }

    /** Released over no window: tear the payload off into a new window, or put it back. */
    fun dropOutside() {
        if (session == null) return
        leaveWindows()
        drop()
    }

    // ----- Drags the platform carries (see DockingDragHandover) -----

    /** A window's drop target reports the pointer at [positionInWindow]; Unspecified is ignored. */
    fun hoverInWindow(positionInWindow: Offset, windowId: WindowId) {
        if (positionInWindow.isSpecified) updateDragInWindow(positionInWindow, windowId)
    }

    /**
     * The pointer left [windowId]. Ignored once a window took the drop - targets are told
     * the pointer left as the session winds down - and when the pointer is already reported
     * over another window, whose enter may arrive before this one's exit.
     */
    fun exitWindow(windowId: WindowId) {
        val s = session ?: return
        if (s.platformDropWindow != null || s.hoveredWindow != windowId) return
        leaveWindows()
    }

    /** [windowId]'s drop target takes the drop at [positionInWindow]: recorded, not applied. */
    fun dropInWindow(positionInWindow: Offset, windowId: WindowId): Boolean {
        val s = session ?: return false
        hoverInWindow(positionInWindow, windowId)
        s.platformDropWindow = windowId
        return true
    }

    /** The platform ended the drag on the source: applies the recorded drop, or tears off. */
    fun endPlatformDrag() {
        val s = session ?: return
        if (s.platformDropWindow != null) drop() else dropOutside()
    }

    fun drop() {
        val s = session ?: return
        session = null
        if (s.movesWindow) {
            // Nothing was undocked, so there is nothing to put back: a drop that finds no
            // target leaves the window where the user let go of it. Restoring the snapshot
            // here would undo the move itself and snap the window back.
            applyTarget(s)
            return
        }
        val applied = applyTarget(s)
        if (!applied || !state.isOpen(s.payload.primary)) {
            state.restoreSnapshot(s.snapshot)
        } else if (s.payload.all.size > 1) {
            state.show(s.payload.primary)
        }
    }

    fun cancel() {
        val s = session ?: return
        session = null
        state.restoreSnapshot(s.snapshot)
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

    /**
     * The window under a shared-space point reported by window [from], ignoring [exclude]
     * (the window being dragged, which is always under the pointer). With OS windows
     * registered, the topmost of those whose content is there: the focused one when it is
     * hit, else the most recently registered - floating windows register after the main one
     * and usually stack above it. Without any (a single window, or tests), each dock area's
     * own bounds.
     *
     * A window that cannot say where it is on screen (no [DockHostWindow.contentBoundsOnScreen],
     * or no OS window registered for it) shares no space with the others: its positions pass
     * through as they are, so a point it reports can only be over itself, tested against its
     * own dock area. Drags out of such a window into another need the platform to carry them.
     */
    private fun resolveWindowAt(
        screen: Offset,
        bounds: Map<WindowId, Rect?>,
        exclude: WindowId?,
        from: WindowId,
    ): WindowId? {
        if (hostWindows.isEmpty()) {
            return windows.values.firstOrNull { reg ->
                reg.windowId != exclude &&
                    reg.scope.bounds.rootBounds.contains(fromScreen(reg.windowId, screen, bounds))
            }?.windowId
        }
        if (bounds[from] == null) {
            if (from == exclude) return null
            return from.takeIf { windows[from]?.scope?.bounds?.rootBounds?.contains(fromScreen(from, screen, bounds)) == true }
        }
        val hits = hostWindows.entries.filter { (id, _) ->
            id != exclude && id in windows && bounds[id]?.contains(screen) == true
        }
        if (hits.isEmpty()) return null
        return (hits.firstOrNull { it.value.isFocused } ?: hits.last()).key
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
                val originDensity = s.originDensity
                val sizeDp = s.payload.sourceSize?.let { Size(it.width / originDensity, it.height / originDensity) }
                state.moveToNewWindow(
                    primary,
                    WindowBounds(
                        // The shared space is already in the units window bounds are kept in.
                        x = target.screenPosition.x - SPAWN_CURSOR_OFFSET_X_DP,
                        y = target.screenPosition.y - SPAWN_CURSOR_OFFSET_Y_DP,
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
