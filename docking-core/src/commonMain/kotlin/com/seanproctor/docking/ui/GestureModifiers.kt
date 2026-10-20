@file:OptIn(com.seanproctor.docking.spi.DockingWindowHostApi::class)

package com.seanproctor.docking.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.seanproctor.docking.drag.DragSource
import com.seanproctor.docking.drag.DropTarget
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.NodeId
import com.seanproctor.docking.spi.DockDragGrip
import com.seanproctor.docking.spi.LocalDockingDragHandover
import com.seanproctor.docking.state.DockState

/**
 * Core-built gesture modifiers embedded into renderer models.
 *
 * Selection/activation fire on press (IDE behavior - no double-tap-timeout latency); a
 * quick second press toggles maximize; movement past touch slop starts a drag session
 * (tabs first enter in-strip reordering, escalating to a full drag on leaving the strip).
 * Once a session starts, the origin window's root listener owns move/release handling -
 * the source node may be disposed by the drag's own undock at any time.
 */

private fun DockState.toggleMaximizeIfAllowed(id: DockableId) {
    if (registry.optionsOf(id).maximizable) toggleMaximize(id)
}

/**
 * Per-window double-click detection. Tracking the last-clicked target globally (rather
 * than per gesture handler) is what makes a double click require two *consecutive*
 * clicks on the same element: rapidly clicking tab A, tab B, tab A again must select
 * tabs, not maximize A - B's click breaks A's sequence.
 */
internal class ClickTracker {
    private var lastTarget: Any? = null
    private var lastUpMillis = Long.MIN_VALUE

    /** Records a completed click; returns true when it completes a double click on [target]. */
    fun registerClick(target: Any, upMillis: Long, timeoutMillis: Long): Boolean {
        val isDouble = target == lastTarget && upMillis - lastUpMillis < timeoutMillis
        if (isDouble) {
            reset()
        } else {
            lastTarget = target
            lastUpMillis = upMillis
        }
        return isDouble
    }

    /** Any non-click interaction (drag, press elsewhere) breaks a pending sequence. */
    fun reset() {
        lastTarget = null
        lastUpMillis = Long.MIN_VALUE
    }
}

/**
 * Shared press gesture: [onPress] on down, [onDoubleClick] on the second consecutive
 * click on [clickTarget] (tracked window-wide via [clicks]), [onDragPastSlop] once
 * movement exceeds slop (return true to hand the gesture to the drag session),
 * [onMove]/[onRelease] while an in-gesture mode (tab reorder) is active.
 */
private suspend fun PointerInputScope.dockPointerHandler(
    clicks: ClickTracker,
    clickTarget: Any,
    onPress: () -> Unit,
    onDoubleClick: () -> Unit,
    onDragPastSlop: (down: Offset, current: PointerInputChange) -> Boolean,
    onMove: (current: PointerInputChange) -> Unit = {},
    onRelease: (current: PointerInputChange?) -> Unit = {},
    // False when a DockingDragHandover owns the drag: presses and clicks only, and movement
    // past slop just means this is not a click.
    dragEnabled: Boolean = true,
) {
    awaitEachGesture {
        val down = awaitFirstDown()
        onPress()
        var dragging = false
        var handedOff = false
        var released = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                released = true
                when {
                    handedOff -> Unit // session's root listener owns the release
                    dragging -> onRelease(change)
                    else -> {
                        val isDouble = clicks.registerClick(
                            clickTarget,
                            change.uptimeMillis,
                            viewConfiguration.doubleTapTimeoutMillis,
                        )
                        if (isDouble) onDoubleClick()
                    }
                }
                break
            }
            if (handedOff) continue
            if (!dragging) {
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    dragging = true
                    clicks.reset() // a drag is not a click
                    handedOff = if (dragEnabled) onDragPastSlop(down.position, change) else true
                }
            } else {
                change.consume()
                onMove(change)
            }
        }
        // The pointer stream ended without an up (gesture cancelled): let modes clean up.
        if (dragging && !handedOff && !released) onRelease(null)
    }
}

@Composable
internal fun DockAreaScope.headerGestureModifier(id: DockableId): Modifier {
    val coords = remember { mutableStateOf<LayoutCoordinates?>(null) }
    val handover = LocalDockingDragHandover.current
    val grip =
        handover?.let {
            val title = state.registry[id]?.title?.invoke() ?: id.value
            remember(this, id, title) {
                DockDragGrip(
                    key = "header:" + id.value,
                    windowId = windowId,
                    title = title,
                    coordinates = { coords.value },
                    sourceBounds = { bounds.boundsOf(id) },
                    onBegin = { position -> beginPlatformDrag(DragSource.Header(id), position, bounds.boundsOf(id)?.size) },
                    onStayLocal = { false },
                    onEndLocal = { _, _ -> },
                )
            }
        }
    return Modifier
        .onGloballyPositioned { coords.value = it }
        .let { if (handover != null && grip != null) it.then(handover.gripModifier(grip)) else it }
        .pointerInput(id, handover) {
            dockPointerHandler(
                clicks = clicks,
                clickTarget = "header:" + id.value,
                onPress = { state.activeDockable = id },
                onDoubleClick = { state.toggleMaximizeIfAllowed(id) },
                dragEnabled = handover == null,
                onDragPastSlop = { _, change ->
                    val c = coords.value ?: return@dockPointerHandler false
                    state.dragController.startDrag(
                        source = DragSource.Header(id),
                        positionInWindow = c.localToRoot(change.position),
                        windowId = windowId,
                        sourceSize = bounds.boundsOf(id)?.size,
                    )
                    true
                },
            )
        }
}

@Composable
internal fun DockAreaScope.tabGestureModifier(
    node: DockNode.Tabs,
    id: DockableId,
    index: Int,
): Modifier {
    val currentIndex by rememberUpdatedState(index)
    val nodeId = node.id
    val coords = remember { mutableStateOf<LayoutCoordinates?>(null) }
    val handover = LocalDockingDragHandover.current
    val grip = handover?.let { tabGrip(nodeId, id, coords) { currentIndex } }
    return Modifier
        .onGloballyPositioned {
            coords.value = it
            bounds.updateTab(nodeId, index, it.boundsInRoot())
        }
        .let { if (handover != null && grip != null) it.then(handover.gripModifier(grip)) else it }
        .pointerInput(nodeId, id, handover) {
            fun rootPos(change: PointerInputChange): Offset =
                coords.value?.localToRoot(change.position) ?: change.position

            fun escalate(change: PointerInputChange) {
                tabReorder = null
                state.dragController.startDrag(
                    source = DragSource.Tab(id, nodeId),
                    positionInWindow = rootPos(change),
                    windowId = windowId,
                    sourceSize = bounds.boundsOf(id)?.size,
                )
            }

            var escalated = false
            dockPointerHandler(
                clicks = clicks,
                clickTarget = "tab:" + id.value,
                onPress = {
                    state.selectTab(nodeId, currentIndex)
                    state.activeDockable = id
                },
                onDoubleClick = { state.toggleMaximizeIfAllowed(id) },
                dragEnabled = handover == null,
                onDragPastSlop = { _, change ->
                    escalated = false
                    val strip = bounds.tabStripRects[nodeId]
                    if (strip != null && strip.inflate(stripEscapeSlackPx()).contains(rootPos(change))) {
                        tabReorder = TabReorderState(nodeId, id, currentIndex)
                        false // stay in-gesture: reorder mode
                    } else {
                        escalated = true
                        escalate(change)
                        true
                    }
                },
                onMove = { change ->
                    if (escalated) return@dockPointerHandler
                    val reorder = tabReorder
                    if (reorder != null) {
                        val pos = rootPos(change)
                        val strip = bounds.tabStripRects[nodeId]
                        if (strip == null || !strip.inflate(stripEscapeSlackPx()).contains(pos)) {
                            escalated = true
                            escalate(change)
                        } else {
                            val tabRect = bounds.tabRects[nodeId]?.get(reorder.fromIndex)
                            reorder.offsetX = pos.x - (tabRect?.center?.x ?: pos.x)
                        }
                    }
                },
                onRelease = { change ->
                    if (escalated) return@dockPointerHandler
                    val reorder = tabReorder
                    tabReorder = null
                    if (reorder != null && change != null) {
                        val pos = rootPos(change)
                        val strip = bounds.tabStripRects[reorder.group]
                        if (strip != null && strip.inflate(stripEscapeSlackPx()).contains(pos)) {
                            val target = bounds.reorderTargetIndex(reorder.group, reorder.fromIndex, pos.x)
                            state.moveTab(reorder.group, reorder.fromIndex, target)
                        }
                    }
                },
            )
        }
}

@Composable
internal fun DockAreaScope.gutterGestureModifier(node: DockNode.Tabs): Modifier {
    val nodeId = node.id
    val coords = remember { mutableStateOf<LayoutCoordinates?>(null) }
    val handover = LocalDockingDragHandover.current
    val grip =
        handover?.let {
            val title = state.registry[node.selectedTab.dockableId]?.title?.invoke() ?: node.selectedTab.dockableId.value
            remember(this, nodeId, title) {
                DockDragGrip(
                    key = "gutter:" + nodeId.value,
                    windowId = windowId,
                    title = title,
                    coordinates = { coords.value },
                    sourceBounds = { bounds.boundsOfNode(nodeId) },
                    onBegin = { position ->
                        beginPlatformDrag(DragSource.TabGroup(nodeId), position, bounds.boundsOfNode(nodeId)?.size)
                    },
                    onStayLocal = { false },
                    onEndLocal = { _, _ -> },
                )
            }
        }
    return Modifier
        .onGloballyPositioned { coords.value = it }
        .let { if (handover != null && grip != null) it.then(handover.gripModifier(grip)) else it }
        .pointerInput(nodeId, handover) {
            dockPointerHandler(
                clicks = clicks,
                clickTarget = "gutter:" + nodeId.value,
                onPress = {},
                onDoubleClick = {},
                dragEnabled = handover == null,
                onDragPastSlop = { _, change ->
                    val c = coords.value ?: return@dockPointerHandler false
                    state.dragController.startDrag(
                        source = DragSource.TabGroup(nodeId),
                        positionInWindow = c.localToRoot(change.position),
                        windowId = windowId,
                        sourceSize = bounds.boundsOfNode(nodeId)?.size,
                    )
                    true
                },
            )
        }
}

private fun DockAreaScope.stripEscapeSlackPx(): Float = 8f * density

/**
 * Starts a drag that the platform's drag-and-drop session carries from here on (see
 * [com.seanproctor.docking.spi.DockingDragHandover]): the root listeners leave it to the
 * windows' drop targets.
 */
private fun DockAreaScope.beginPlatformDrag(
    source: DragSource,
    positionInWindow: Offset,
    sourceSize: Size?,
): Boolean {
    val controller = state.dragController
    if (controller.session != null) return false
    controller.startDrag(source = source, positionInWindow = positionInWindow, windowId = windowId, sourceSize = sourceSize)
    val session = controller.session ?: return false
    session.carriedByPlatform = true
    return true
}

/**
 * A tab's grip under a [com.seanproctor.docking.spi.DockingDragHandover]: it reorders within
 * its strip while the pointer stays there, the way the core's own gesture does, and begins
 * the drag the moment the pointer leaves the strip.
 */
@Composable
private fun DockAreaScope.tabGrip(
    nodeId: NodeId,
    id: DockableId,
    coords: androidx.compose.runtime.MutableState<LayoutCoordinates?>,
    currentIndex: () -> Int,
): DockDragGrip {
    val title = state.registry[id]?.title?.invoke() ?: id.value
    return remember(this, nodeId, id, title) {
        fun inStrip(position: Offset): Boolean = bounds.tabStripRects[nodeId]?.inflate(stripEscapeSlackPx())?.contains(position) == true
        DockDragGrip(
            key = "tab:" + id.value,
            windowId = windowId,
            title = title,
            coordinates = { coords.value },
            sourceBounds = { bounds.boundsOf(id) },
            onBegin = { position ->
                tabReorder = null
                beginPlatformDrag(DragSource.Tab(id, nodeId), position, bounds.boundsOf(id)?.size)
            },
            onStayLocal = { position ->
                if (!inStrip(position)) {
                    tabReorder = null
                    false
                } else {
                    val reorder = tabReorder ?: TabReorderState(nodeId, id, currentIndex()).also { tabReorder = it }
                    val tabRect = bounds.tabRects[nodeId]?.get(reorder.fromIndex)
                    reorder.offsetX = position.x - (tabRect?.center?.x ?: position.x)
                    true
                }
            },
            onEndLocal = { released, position ->
                val reorder = tabReorder
                tabReorder = null
                if (released && reorder != null && position != null && inStrip(position)) {
                    val target = bounds.reorderTargetIndex(reorder.group, reorder.fromIndex, position.x)
                    state.moveTab(reorder.group, reorder.fromIndex, target)
                }
            },
        )
    }
}

/** Live x-offset of a tab while it is being reordered. */
internal fun DockAreaScope.tabReorderOffset(nodeId: NodeId, id: DockableId): Float {
    val reorder = tabReorder ?: return 0f
    return if (reorder.group == nodeId && reorder.dockable == id) reorder.offsetX else 0f
}

/** Insertion caret position while a drag hovers this strip. */
internal fun DockAreaScope.tabDropInsertionIndex(nodeId: NodeId): Int? {
    val session = state.dragController.session ?: return null
    val target = session.target
    return if (session.hoveredWindow == windowId &&
        target is DropTarget.TabInsert && target.nodeId == nodeId
    ) {
        target.index
    } else {
        null
    }
}
