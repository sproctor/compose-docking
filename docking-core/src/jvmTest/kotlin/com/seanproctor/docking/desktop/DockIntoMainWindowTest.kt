package com.seanproctor.docking.desktop

import androidx.compose.ui.window.WindowState
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.WindowBounds
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget
import com.seanproctor.docking.tree.windowContaining
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val A = DockableId("a")
private val B = DockableId("b")

private fun uiState(): DockState = DockState {
    dockable("a", title = { "Alpha" }) { }
    dockable("b", title = { "Beta" }) { }
}

private fun modelFor(state: DockState, windowId: WindowId, dockableId: DockableId?) =
    FloatingWindowModel(
        windowId = windowId,
        title = "Beta",
        icon = null,
        state = WindowState(),
        onCloseRequest = {},
        dockState = state,
        dockableId = dockableId,
    )

/**
 * The way back for a panel whose header a window frame replaced. Without it a Jewel
 * decorated window is a one-way trip: the header that carries drag-to-redock is the very
 * thing the frame stands in for.
 */
class DockIntoMainWindowTest {

    @Test
    fun itMovesThePanelBackAndClosesTheWindowBehindIt() {
        val state = uiState()
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
        state.moveToNewWindow(B, WindowBounds(100f, 100f, 400f, 300f))
        val floating = state.layout.floatingWindows.single()
        assertEquals(floating.id, assertNotNull(state.layout.windowContaining(B)).id, "B starts out floating")

        modelFor(state, floating.id, B).dockIntoMainWindow()

        assertTrue(state.layout.floatingWindows.isEmpty(), "the emptied window goes with it")
        assertEquals(WindowId.MAIN, assertNotNull(state.layout.windowContaining(B)).id)
        assertTrue(state.isDocked(A), "the panel already in the main window stays")
    }

    // The model carries no dockable for a window holding a split or a tab group, and the
    // frame draws no header for one either - there is nothing here to put back.
    @Test
    fun itDoesNothingForAWindowWithoutALoneDockable() {
        val state = uiState()
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
        state.moveToNewWindow(B, WindowBounds(100f, 100f, 400f, 300f))
        val floating = state.layout.floatingWindows.single()

        modelFor(state, floating.id, dockableId = null).dockIntoMainWindow()

        assertEquals(1, state.layout.floatingWindows.size, "the window is left alone")
        assertEquals(floating.id, assertNotNull(state.layout.windowContaining(B)).id)
    }
}
