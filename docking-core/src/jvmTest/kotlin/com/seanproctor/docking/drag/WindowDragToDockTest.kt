package com.seanproctor.docking.drag

import androidx.compose.ui.geometry.Offset
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.WindowBounds
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget
import com.seanproctor.docking.tree.windowContaining
import com.seanproctor.docking.ui.DockAreaScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val A = DockableId("a")
private val B = DockableId("b")

private fun uiState(): DockState = DockState {
    dockable("a", title = { "Alpha" }) { }
    dockable("b", title = { "Beta" }) { }
}

/** A state with A in the main window and B torn off into its own floating window. */
private fun tornOff(): Pair<DockState, WindowId> {
    val state = uiState()
    state.dock(A)
    state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
    state.moveToNewWindow(B, WindowBounds(600f, 100f, 400f, 300f))
    return state to state.layout.floatingWindows.single().id
}

/**
 * Dragging an undecorated floating window by its header. The window is the drag preview,
 * so unlike every other drag the payload must stay docked in it - undocking would delete
 * the window the gesture lives in, mid-gesture.
 */
class WindowDragToDockTest {

    @Test
    fun theWindowKeepsItsPanelWhileBeingDragged() {
        val (state, floatingId) = tornOff()
        state.dragController.startDrag(
            source = DragSource.Header(B),
            positionInWindow = Offset(20f, 8f),
            windowId = floatingId,
            movesWindow = true,
        )
        assertNotNull(state.dragController.session, "a session is running")
        assertTrue(state.isDocked(B), "B is still in its window, not undocked into a ghost")
        assertEquals(floatingId, assertNotNull(state.layout.windowContaining(B)).id)
    }

    // An ordinary drag still undocks: that is what turns the panel into something following
    // the pointer, and the layout reflowing behind it is deliberate.
    @Test
    fun anOrdinaryDragStillUndocksImmediately() {
        val (state, floatingId) = tornOff()
        state.dragController.startDrag(
            source = DragSource.Header(B),
            positionInWindow = Offset(20f, 8f),
            windowId = floatingId,
        )
        assertTrue(!state.isDocked(B), "B was undocked as the drag started")
    }

    // Released over nothing, a window drag is just a window move - there is nothing to undo,
    // and restoring the pre-drag snapshot would teleport the window back where it started.
    @Test
    fun droppingOverNothingLeavesTheWindowWhereItIs() {
        val (state, floatingId) = tornOff()
        val start = assertNotNull(state.layout.window(floatingId)?.bounds)
        state.dragController.startDrag(
            source = DragSource.Header(B),
            positionInWindow = Offset(20f, 8f),
            windowId = floatingId,
            movesWindow = true,
        )
        // The drag has to actually carry the window somewhere, or a drop that restored the
        // snapshot would land on the same bounds it started from and look like success.
        val dropped = WindowBounds(50f, 400f, 400f, 300f)
        state.setWindowBounds(floatingId, dropped)
        state.dragController.drop()

        assertNull(state.dragController.session)
        assertEquals(floatingId, assertNotNull(state.layout.windowContaining(B)).id, "still floating")
        assertEquals(dropped, state.layout.window(floatingId)?.bounds, "left where it was released")
        assertTrue(dropped != start, "the window really did move")
    }

    // The whole point: let go over the main window's dock area and the panel goes back into
    // it, taking the emptied floating window with it.
    @Test
    fun droppingOnTheMainWindowDocksThePanelAndClosesTheWindow() {
        val (state, floatingId) = tornOff()
        val session = state.dragController.let { controller ->
            controller.startDrag(
                source = DragSource.Header(B),
                positionInWindow = Offset(20f, 8f),
                windowId = floatingId,
                movesWindow = true,
            )
            assertNotNull(controller.session)
        }
        // Stand in for the pointer landing on A: the geometry that normally resolves this
        // comes from a laid-out DockArea, which a headless state has none of.
        session.target = DropTarget.OnDockable(WindowId.MAIN, A, DockRegion.East)
        state.dragController.drop()

        assertEquals(WindowId.MAIN, assertNotNull(state.layout.windowContaining(B)).id)
        assertTrue(state.layout.floatingWindows.isEmpty(), "the emptied window went with it")
        val root = assertNotNull(state.layout.mainWindow.root)
        assertTrue(root is DockNode.Split, "B docked east of A")
    }

    // The dragged window follows the pointer, so it is under it for the whole gesture and
    // would answer every hit test itself. Unless it is looked past, a drag can only ever
    // find the window it started in, and dropping onto anything else is impossible.
    @Test
    fun theDraggedWindowIsLookedPastWhenResolvingATarget() {
        val (state, floatingId) = tornOff()
        val controller = state.dragController
        controller.registerWindow(WindowId.MAIN, DockAreaScope(state, WindowId.MAIN))
        controller.registerWindow(floatingId, DockAreaScope(state, floatingId))
        var excluded: WindowId? = null
        var asked = false
        controller.windowResolver = { _, exclude ->
            asked = true
            excluded = exclude
            null
        }

        controller.startDrag(
            source = DragSource.Header(B),
            positionInWindow = Offset(20f, 8f),
            windowId = floatingId,
            movesWindow = true,
        )

        assertTrue(asked, "the drag resolved a target window")
        assertEquals(floatingId, excluded, "its own window is excluded")
    }

    // Nothing is excluded for an ordinary drag: the panel has already left its window, so
    // that window is a legitimate place to put it back.
    @Test
    fun anOrdinaryDragExcludesNothing() {
        val (state, floatingId) = tornOff()
        val controller = state.dragController
        controller.registerWindow(floatingId, DockAreaScope(state, floatingId))
        var excluded: WindowId? = floatingId
        controller.windowResolver = { _, exclude -> excluded = exclude; null }

        controller.startDrag(
            source = DragSource.Header(B),
            positionInWindow = Offset(20f, 8f),
            windowId = floatingId,
        )

        assertNull(excluded)
    }

    // Escape during a window drag puts everything back, including where the window was:
    // the snapshot carries its pre-drag bounds, and the bounds sync follows the layout.
    @Test
    fun cancellingRestoresTheWindowToWhereTheDragBegan() {
        val (state, floatingId) = tornOff()
        val start = assertNotNull(state.layout.window(floatingId)?.bounds)
        state.dragController.startDrag(
            source = DragSource.Header(B),
            positionInWindow = Offset(20f, 8f),
            windowId = floatingId,
            movesWindow = true,
        )
        state.setWindowBounds(floatingId, WindowBounds(50f, 50f, 400f, 300f))
        state.dragController.cancel()

        assertNull(state.dragController.session)
        assertEquals(start, state.layout.window(floatingId)?.bounds)
        assertEquals(floatingId, assertNotNull(state.layout.windowContaining(B)).id)
    }
}
