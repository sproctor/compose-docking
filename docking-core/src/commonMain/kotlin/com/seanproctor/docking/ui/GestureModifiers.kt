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
import com.seanproctor.docking.state.DockState

/**
 * Core-built gesture modifiers embedded into renderer models.
 *
 * Selection/activation fire on press (IDE behavior — no double-tap-timeout latency); a
 * quick second press toggles maximize; movement past touch slop starts a drag session
 * (tabs first enter in-strip reordering, escalating to a full drag on leaving the strip).
 * Once a session starts, the origin window's root listener owns move/release handling —
 * the source node may be disposed by the drag's own undock at any time.
 */

private fun DockState.toggleMaximizeIfAllowed(id: DockableId) {
    if (registry.optionsOf(id).maximizable) toggleMaximize(id)
}

/**
 * Shared press gesture: [onPress] on down, [onDoubleClick] on quick second click,
 * [onDragPastSlop] once movement exceeds slop (return true to hand the gesture to the
 * drag session), [onMove]/[onRelease] while an in-gesture mode (tab reorder) is active.
 */
private suspend fun PointerInputScope.dockPointerHandler(
    onPress: () -> Unit,
    onDoubleClick: () -> Unit,
    onDragPastSlop: (down: Offset, current: PointerInputChange) -> Boolean,
    onMove: (current: PointerInputChange) -> Unit = {},
    onRelease: (current: PointerInputChange?) -> Unit = {},
) {
    var lastUpMillis = Long.MIN_VALUE
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
                        if (change.uptimeMillis - lastUpMillis < viewConfiguration.doubleTapTimeoutMillis) {
                            lastUpMillis = Long.MIN_VALUE
                            onDoubleClick()
                        } else {
                            lastUpMillis = change.uptimeMillis
                        }
                    }
                }
                break
            }
            if (handedOff) continue
            if (!dragging) {
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    dragging = true
                    handedOff = onDragPastSlop(down.position, change)
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
    return Modifier
        .onGloballyPositioned { coords.value = it }
        .pointerInput(id) {
            dockPointerHandler(
                onPress = { state.activeDockable = id },
                onDoubleClick = { state.toggleMaximizeIfAllowed(id) },
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
    return Modifier
        .onGloballyPositioned {
            coords.value = it
            bounds.updateTab(nodeId, index, it.boundsInRoot())
        }
        .pointerInput(nodeId, id) {
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
                onPress = {
                    state.selectTab(nodeId, currentIndex)
                    state.activeDockable = id
                },
                onDoubleClick = { state.toggleMaximizeIfAllowed(id) },
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
    return Modifier
        .onGloballyPositioned { coords.value = it }
        .pointerInput(nodeId) {
            dockPointerHandler(
                onPress = {},
                onDoubleClick = {},
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
