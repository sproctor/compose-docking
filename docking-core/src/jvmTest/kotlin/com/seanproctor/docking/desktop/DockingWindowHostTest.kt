package com.seanproctor.docking.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.WindowBounds
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.spi.DockingWindowHostApi
import com.seanproctor.docking.spi.windowHost
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget
import com.seanproctor.docking.tree.windowContaining
import com.seanproctor.docking.ui.DockAreaScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val A = DockableId("a")
private val B = DockableId("b")

/**
 * The seam a window-system adapter outside the core (docking-tao) drives cross-window drags
 * through: converters into a shared space, a window resolver, and a window drag carried by a
 * floating window's header. No OS windows here; the "screen" is two rectangles side by side.
 */
@OptIn(DockingWindowHostApi::class)
class DockingWindowHostTest {
    private val mainOnScreen = Rect(0f, 0f, 800f, 600f)
    private val floatingOnScreen = Rect(1000f, 100f, 1400f, 400f)

    private fun setUp(): Pair<DockState, WindowId> {
        val state =
            DockState {
                dockable("a", title = { "Alpha" }) { }
                dockable("b", title = { "Beta" }) { }
            }
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
        state.moveToNewWindow(B, WindowBounds(1000f, 100f, 400f, 300f))
        val floatingId = state.layout.floatingWindows.single().id
        val main = DockAreaScope(state, WindowId.MAIN).apply { bounds.rootBounds = Rect(0f, 0f, 800f, 600f) }
        val floating = DockAreaScope(state, floatingId).apply { bounds.rootBounds = Rect(0f, 0f, 400f, 300f) }
        state.dragController.registerWindow(WindowId.MAIN, main)
        state.dragController.registerWindow(floatingId, floating)
        val host = state.windowHost
        host.setScreenConverter(WindowId.MAIN, { it + mainOnScreen.topLeft }, { it - mainOnScreen.topLeft })
        host.setScreenConverter(floatingId, { it + floatingOnScreen.topLeft }, { it - floatingOnScreen.topLeft })
        host.windowResolver = { screen, exclude ->
            when {
                floatingId != exclude && floatingOnScreen.contains(screen) -> floatingId
                WindowId.MAIN != exclude && mainOnScreen.contains(screen) -> WindowId.MAIN
                else -> null
            }
        }
        return state to floatingId
    }

    @Test
    fun aWindowDragCarriesTheWindowAndLeavesThePanelInIt() {
        val (state, floatingId) = setUp()

        assertTrue(state.windowHost.startWindowDrag(B, floatingId, Offset(20f, 10f)))

        assertTrue(state.windowHost.isDragging)
        assertEquals(floatingId, assertNotNull(state.layout.windowContaining(B)).id, "nothing undocks for a window drag")
        assertNull(state.windowHost.dragPreview, "the real window is the preview")
    }

    @Test
    fun releasedOverTheMainWindowThePanelDocksThere() {
        val (state, floatingId) = setUp()
        val host = state.windowHost
        host.startWindowDrag(B, floatingId, Offset(20f, 10f))

        // The adapter moved the window over main; the pointer is still at (20, 10) in the
        // window, which the converter now puts over main's centre on the shared space.
        host.setScreenConverter(floatingId, { it + Offset(380f, 290f) }, { it - Offset(380f, 290f) })
        host.updateDrag(Offset(20f, 10f), floatingId)
        // A drop lands on a docking handle: move onto one of main's root-edge handles.
        val handle =
            assertNotNull(state.dragController.session)
                .handles
                .first { it.enabled && it.target is com.seanproctor.docking.drag.DropTarget.Root }
        val onHandle = handle.rect.center - Offset(20f, 10f)
        host.setScreenConverter(floatingId, { it + onHandle }, { it - onHandle })
        host.updateDrag(Offset(20f, 10f), floatingId)
        host.drop()

        assertFalse(host.isDragging)
        assertEquals(WindowId.MAIN, assertNotNull(state.layout.windowContaining(B)).id)
    }

    @Test
    fun releasedOverNothingTheWindowStaysFloating() {
        val (state, floatingId) = setUp()
        val host = state.windowHost
        host.startWindowDrag(B, floatingId, Offset(20f, 10f))

        host.setScreenConverter(floatingId, { it + Offset(2000f, 900f) }, { it - Offset(2000f, 900f) })
        host.updateDrag(Offset(20f, 10f), floatingId)
        host.drop()

        assertEquals(floatingId, assertNotNull(state.layout.windowContaining(B)).id)
    }

    @Test
    fun aSecondDragIsRefusedWhileOneRuns() {
        val (state, floatingId) = setUp()
        state.windowHost.startWindowDrag(B, floatingId, Offset(20f, 10f))

        assertFalse(state.windowHost.startWindowDrag(B, floatingId, Offset(20f, 10f)))
    }

    @Test
    fun cancelPutsTheLayoutBack() {
        val (state, floatingId) = setUp()
        val host = state.windowHost
        host.startWindowDrag(B, floatingId, Offset(20f, 10f))
        host.setScreenConverter(floatingId, { it + Offset(380f, 290f) }, { it - Offset(380f, 290f) })
        host.updateDrag(Offset(20f, 10f), floatingId)

        host.cancel()

        assertFalse(host.isDragging)
        assertEquals(floatingId, assertNotNull(state.layout.windowContaining(B)).id)
    }

    @Test
    fun theAdapterPreviewReplacesTheInWindowCard() {
        val (state, _) = setUp()

        state.windowHost.drawsDragPreview = true
        assertTrue(state.windowHost.drawsDragPreview)
        assertTrue(state.dragController.dragListener?.showsOwnPreview == true)

        state.windowHost.drawsDragPreview = false
        assertNull(state.dragController.dragListener)
    }
}
