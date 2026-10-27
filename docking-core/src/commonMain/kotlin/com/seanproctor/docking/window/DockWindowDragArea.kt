package com.seanproctor.docking.window

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.state.DockState

/**
 * Makes this element drag OS window [windowId] the way a title bar does - and, released over
 * another dock area, dock what the window holds there. Put it on the title bar an application
 * draws for a floating window, or let [com.seanproctor.docking.ui.DockArea]'s
 * `headerDragsWindow` put it on the header of a window's lone dockable, or on the tab strip of
 * its lone tab group.
 *
 * The move is driven from Compose rather than handed to the window manager, which is what makes
 * the docking half possible at all: a move the window manager drives takes the pointer with it,
 * so nothing would see where the window went or when it was let go. The window moves through
 * [DockHostWindow.moveContentTo], so it needs [registerDockingWindow]; a window that cannot be
 * placed (no [DockHostWindow.contentBoundsOnScreen]) does not move.
 *
 * A press something inside took first (a button in the title bar) is that thing's, not a drag.
 * The window docks as a whole only while it holds one dockable or one tab group; holding
 * anything else, or being the main window, it is only moved. Escape during the drag puts the
 * window back where it started, when the key reaches the docking system: a dock area with
 * focus, or the window's own key events passed to [DockingWindowHost.cancelOnEscape] - a
 * title bar outside the dock area does not take focus.
 */
public fun Modifier.dockWindowDragArea(
    state: DockState,
    windowId: WindowId,
): Modifier = this then DockWindowDragElement(state, windowId)

private class DockWindowDragElement(
    val state: DockState,
    val windowId: WindowId,
) : ModifierNodeElement<DockWindowDragNode>() {
    override fun create(): DockWindowDragNode = DockWindowDragNode(state, windowId)

    override fun update(node: DockWindowDragNode) {
        node.update(state, windowId)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "dockWindowDragArea"
        properties["windowId"] = windowId
    }

    override fun equals(other: Any?): Boolean =
        other is DockWindowDragElement && other.state === state && other.windowId == windowId

    override fun hashCode(): Int = 31 * state.hashCode() + windowId.hashCode()
}

private class DockWindowDragNode(
    private var state: DockState,
    private var windowId: WindowId,
) : DelegatingNode() {
    private val input = delegate(SuspendingPointerInputModifierNode { dragWindow() })

    fun update(
        state: DockState,
        windowId: WindowId,
    ) {
        if (state === this.state && windowId == this.windowId) return
        this.state = state
        this.windowId = windowId
        input.resetPointerInputHandler()
    }

    private fun toWindow(position: Offset): Offset {
        val coordinates = requireLayoutCoordinates()
        return if (coordinates.isAttached) coordinates.localToRoot(position) else position
    }

    private suspend fun PointerInputScope.dragWindow() {
        val state = state
        val windowId = windowId
        val host = state.windowHost
        awaitEachGesture {
            // Unconsumed only: a press the title bar's own buttons took is theirs, not a drag.
            val down = awaitFirstDown()
            when (val root = state.layout.window(windowId)?.root) {
                is DockNode.Leaf -> state.activeDockable = root.dockableId
                is DockNode.Tabs -> state.activeDockable = root.selectedTab.dockableId
                else -> Unit
            }
            val origin = down.position
            var moving = false
            var docking = false
            var chase: WindowChase? = null
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    // The tracked pointer is gone from the event: the gesture is over and
                    // never reached a release, so there is no drop to apply.
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (docking) {
                            docking = false
                            host.drop()
                        }
                        break
                    }
                    // Something ended the session from outside - Escape, which restores the
                    // bounds the drag began at. Moving the window again would undo that.
                    if (docking && !host.isDragging) {
                        docking = false
                        break
                    }
                    if (!moving && (change.position - origin).getDistance() > viewConfiguration.touchSlop) {
                        moving = true
                        chase = state.hostWindow(windowId)?.contentBoundsOnScreen()?.let { WindowChase(it.topLeft, origin) }
                        // Only ever drive a session this gesture opened: one already running
                        // belongs to someone else, and cancelling it on the way out would end it.
                        docking = host.startWindowDrag(windowId, toWindow(change.position))
                    }
                    if (moving) {
                        change.consume()
                        val window = state.hostWindow(windowId)
                        val next = chase?.next(change.position, window?.contentBoundsOnScreen()?.topLeft)
                        if (next != null) window?.moveContentTo(next)
                        if (docking) host.updateDrag(toWindow(change.position), windowId)
                    }
                }
            } finally {
                // Every other way out of the gesture - the pointer vanishing, this element
                // leaving, a cancellation - would otherwise leave the session open, and an
                // open session turns away every drag after it.
                if (docking) host.cancel()
            }
        }
    }
}

/**
 * Where to move a window dragged by a point inside it, event by event, in screen pixels.
 * Moves land asynchronously, so the window's observed position can lag the one last asked for.
 * Each event's local position is relative to the observed position, so the pointer's movement
 * on screen is its local movement plus the window's observed movement since the last event.
 * That keeps the window under the pointer whether a move is still pending or has landed,
 * without counting one twice.
 */
internal class WindowChase(
    start: Offset,
    pressPosition: Offset,
) {
    private var requested = start
    private var lastObserved = start
    private var lastLocal = pressPosition

    /** The pointer is at [local] in the window, which is observed at [observed] (null: unknown). */
    fun next(
        local: Offset,
        observed: Offset?,
    ): Offset {
        val windowMoved = observed?.let { it - lastObserved } ?: Offset.Zero
        requested += (local - lastLocal) + windowMoved
        lastLocal = local
        if (observed != null) lastObserved = observed
        return requested
    }
}
