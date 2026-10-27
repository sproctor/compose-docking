package com.seanproctor.docking.window

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.seanproctor.docking.drag.DragSource
import com.seanproctor.docking.drag.DropTarget
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val A = DockableId("a")
private val B = DockableId("b")
private val C = DockableId("c")

/**
 * An OS window that is only a rectangle on a pretend screen. Moving it moves the rectangle,
 * unless [landsMoves] is off: then it only records where it was asked to go.
 */
internal class FakeHostWindow(
    var bounds: Rect?,
    override var isFocused: Boolean = false,
    private val landsMoves: Boolean = true,
) : DockHostWindow {
    val moves = mutableListOf<Offset>()

    override fun contentBoundsOnScreen(): Rect? = bounds

    override fun moveContentTo(position: Offset) {
        moves += position
        if (landsMoves) bounds = bounds?.let { Rect(position, it.size) }
    }
}

/**
 * Drags between OS windows as an application drives them through [DockingWindowHost], with
 * its windows described by [DockHostWindow]s. No OS windows here; the "screen" is a few
 * rectangles.
 */
class DockingWindowHostTest {
    private val mainOnScreen = Rect(0f, 0f, 800f, 600f)
    private val floatingOnScreen = Rect(1000f, 100f, 1400f, 400f)

    private class Setup(
        val state: DockState,
        val floatingId: WindowId,
        val main: FakeHostWindow,
        val floating: FakeHostWindow,
    )

    private fun state(): DockState =
        DockState {
            dockable("a", title = { "Alpha" }) { }
            dockable("b", title = { "Beta" }) { }
            dockable("c", title = { "Gamma" }) { }
        }

    /** Registers [windowId]'s dock area at [size] and its OS window at [onScreen]. */
    private fun DockState.show(
        windowId: WindowId,
        onScreen: Rect?,
        size: Rect,
        focused: Boolean = false,
    ): FakeHostWindow {
        dragController.registerWindow(windowId, DockAreaScope(this, windowId).apply { bounds.rootBounds = size })
        return FakeHostWindow(onScreen, focused).also { dragController.registerHostWindow(windowId, it) }
    }

    /** A in the main window, B torn off on its own. */
    private fun setUp(): Setup {
        val state = state()
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
        state.moveToNewWindow(B, WindowBounds(1000f, 100f, 400f, 300f))
        val floatingId = state.layout.floatingWindows.single().id
        val main = state.show(WindowId.MAIN, mainOnScreen, Rect(0f, 0f, 800f, 600f))
        val floating = state.show(floatingId, floatingOnScreen, Rect(0f, 0f, 400f, 300f))
        return Setup(state, floatingId, main, floating)
    }

    /** Moves [Setup.floating] so the pointer, at (20, 10) in it, is at [screen]. */
    private fun Setup.pointerAt(screen: Offset) {
        floating.bounds = Rect(screen - Offset(20f, 10f), floatingOnScreen.size)
        state.windowHost.updateDrag(Offset(20f, 10f), floatingId)
    }

    private fun Setup.onMainRootHandle(): Offset =
        assertNotNull(state.dragController.session)
            .handles
            .first { it.enabled && it.target is DropTarget.Root }
            .rect.center

    @Test
    fun aWindowDragCarriesTheWindowAndLeavesThePanelInIt() {
        val (state, floatingId) = setUp().let { it.state to it.floatingId }

        assertTrue(state.windowHost.startWindowDrag(floatingId, Offset(20f, 10f)))

        assertTrue(state.windowHost.isDragging)
        assertEquals(floatingId, assertNotNull(state.layout.windowContaining(B)).id, "nothing undocks for a window drag")
        assertNull(state.windowHost.dragPreview, "the real window is the preview")
    }

    @Test
    fun releasedOverTheMainWindowThePanelDocksThere() {
        val setup = setUp()
        val host = setup.state.windowHost
        host.startWindowDrag(setup.floatingId, Offset(20f, 10f))

        // Over main first, so its handles are laid out; then onto one of them.
        setup.pointerAt(mainOnScreen.center)
        setup.pointerAt(setup.onMainRootHandle())
        host.drop()

        assertFalse(host.isDragging)
        assertEquals(WindowId.MAIN, assertNotNull(setup.state.layout.windowContaining(B)).id)
        assertTrue(setup.state.layout.floatingWindows.isEmpty(), "the emptied window closes")
    }

    @Test
    fun releasedOverNothingTheWindowStaysFloating() {
        val setup = setUp()
        setup.state.windowHost.startWindowDrag(setup.floatingId, Offset(20f, 10f))

        setup.pointerAt(Offset(2000f, 900f))
        setup.state.windowHost.drop()

        assertEquals(setup.floatingId, assertNotNull(setup.state.layout.windowContaining(B)).id)
    }

    @Test
    fun aTabGroupWindowDocksAsAWhole() {
        val state = state()
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
        state.moveToNewWindow(B, WindowBounds(1000f, 100f, 400f, 300f))
        val floatingId = state.layout.floatingWindows.single().id
        state.dock(C, DockTarget.OnDockable(B), DockRegion.Center)
        assertIs<DockNode.Tabs>(state.layout.window(floatingId)?.root, "B and C share the floating window")
        val main = state.show(WindowId.MAIN, mainOnScreen, Rect(0f, 0f, 800f, 600f))
        val floating = state.show(floatingId, floatingOnScreen, Rect(0f, 0f, 400f, 300f))
        val setup = Setup(state, floatingId, main, floating)

        assertTrue(state.windowHost.startWindowDrag(floatingId, Offset(20f, 10f)))
        setup.pointerAt(mainOnScreen.center)
        setup.pointerAt(setup.onMainRootHandle())
        state.windowHost.drop()

        assertEquals(WindowId.MAIN, state.layout.windowContaining(B)?.id)
        assertEquals(WindowId.MAIN, state.layout.windowContaining(C)?.id)
        assertTrue(state.layout.floatingWindows.isEmpty())
    }

    // A split has no one pane to speak for the window; dragging it can only move it.
    @Test
    fun aWindowHoldingASplitDocksNowhere() {
        val state = state()
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
        state.moveToNewWindow(B, WindowBounds(1000f, 100f, 400f, 300f))
        val floatingId = state.layout.floatingWindows.single().id
        state.dock(C, DockTarget.OnDockable(B), DockRegion.South)

        assertFalse(state.windowHost.startWindowDrag(floatingId, Offset(20f, 10f)))
        assertFalse(state.windowHost.isDragging)
    }

    // Dragging the main window by a title bar of the application's moves it, and only that:
    // there is nowhere for the main window's content to dock to.
    @Test
    fun theMainWindowDocksNowhere() {
        val setup = setUp()
        assertFalse(setup.state.windowHost.startWindowDrag(WindowId.MAIN, Offset(20f, 10f)))
    }

    @Test
    fun aSecondDragIsRefusedWhileOneRuns() {
        val setup = setUp()
        setup.state.windowHost.startWindowDrag(setup.floatingId, Offset(20f, 10f))

        assertFalse(setup.state.windowHost.startWindowDrag(setup.floatingId, Offset(20f, 10f)))
    }

    @Test
    fun cancelPutsTheLayoutBack() {
        val setup = setUp()
        val host = setup.state.windowHost
        host.startWindowDrag(setup.floatingId, Offset(20f, 10f))
        setup.pointerAt(mainOnScreen.center)

        host.cancel()

        assertFalse(host.isDragging)
        assertEquals(setup.floatingId, assertNotNull(setup.state.layout.windowContaining(B)).id)
    }

    @Test
    fun anOwnPreviewReplacesTheInWindowCard() {
        val state = setUp().state

        state.windowHost.drawsDragPreview = true
        assertTrue(state.windowHost.drawsDragPreview)
        assertTrue(state.dragController.dragListener?.showsOwnPreview == true)

        state.windowHost.drawsDragPreview = false
        assertNull(state.dragController.dragListener)
    }

    // ----- Which window is under the pointer -----

    /** Starts tearing A out of the main window and reports the pointer at [screen]. */
    private fun DockState.hoverAt(screen: Offset): WindowId? {
        if (dragController.session == null) {
            dragController.startDrag(DragSource.Header(A), positionInWindow = Offset(5f, 5f), windowId = WindowId.MAIN)
        }
        dragController.updateDrag(dragController.fromScreen(WindowId.MAIN, screen), WindowId.MAIN)
        return dragController.session?.hoveredWindow
    }

    private fun twoWindows(
        mainFocused: Boolean = false,
        floatingFocused: Boolean = false,
        floatingBounds: Rect? = Rect(100f, 100f, 400f, 300f),
    ): Pair<DockState, WindowId> {
        val state = state()
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
        state.moveToNewWindow(B, WindowBounds(100f, 100f, 300f, 200f))
        val floatingId = state.layout.floatingWindows.single().id
        state.show(WindowId.MAIN, mainOnScreen, Rect(0f, 0f, 800f, 600f), focused = mainFocused)
        state.show(floatingId, floatingBounds, Rect(0f, 0f, 300f, 200f), focused = floatingFocused)
        return state to floatingId
    }

    @Test
    fun aPointOverOneWindowResolvesToIt() {
        val (state, floatingId) = twoWindows()
        assertEquals(WindowId.MAIN, state.hoverAt(Offset(50f, 50f)))
        assertEquals(floatingId, state.hoverAt(Offset(150f, 150f)), "the floating window is above")
        assertNull(state.hoverAt(Offset(900f, 50f)), "over no window")
    }

    @Test
    fun overlappingWindowsPreferTheFocusedOne() {
        val (state, _) = twoWindows(mainFocused = true)
        assertEquals(WindowId.MAIN, state.hoverAt(Offset(150f, 150f)))
    }

    @Test
    fun overlappingUnfocusedWindowsPreferTheLastRegistered() {
        val (state, floatingId) = twoWindows()
        assertEquals(floatingId, state.hoverAt(Offset(150f, 150f)))
    }

    // Native Wayland: no window knows where it is, and the platform session carries drags.
    @Test
    fun aWindowWithoutBoundsIsNeverHit() {
        val (state, _) = twoWindows(floatingFocused = true, floatingBounds = null)
        assertEquals(WindowId.MAIN, state.hoverAt(Offset(150f, 150f)))
    }

    @Test
    fun anOsWindowWithoutADockAreaIsNeverHit() {
        val (state, floatingId) = twoWindows(floatingFocused = true)
        state.dragController.unregisterWindow(floatingId)
        assertEquals(WindowId.MAIN, state.hoverAt(Offset(150f, 150f)))
    }

    @Test
    fun positionsMapThroughTheContentOrigin() {
        val (state, floatingId) = twoWindows()
        assertEquals(Offset(130f, 140f), state.dragController.toScreen(floatingId, Offset(30f, 40f)))
        assertEquals(Offset(30f, 40f), state.dragController.fromScreen(floatingId, Offset(130f, 140f)))
    }

    @Test
    fun unregisteringAReplacedWindowKeepsTheNewOne() {
        val (state, floatingId) = twoWindows()
        val replacement = FakeHostWindow(Rect(500f, 500f, 600f, 600f))
        val old = assertNotNull(state.hostWindow(floatingId))
        state.dragController.registerHostWindow(floatingId, replacement)

        state.dragController.unregisterHostWindow(floatingId, old)

        assertEquals(replacement, state.hostWindow(floatingId))
    }
}

/**
 * A window chasing the pointer that drags it: the window's content starts at (100, 100),
 * grabbed at (20, 10) in it, so the pointer is at (120, 110) on screen. Wherever the pointer
 * goes, the window belongs at the pointer minus (20, 10).
 */
class WindowChaseTest {
    private val start = Offset(100f, 100f)
    private val press = Offset(20f, 10f)

    private fun chase() = WindowChase(start, press)

    @Test
    fun theFirstMoveFollowsThePointerFromThePress() {
        assertEquals(Offset(120f, 100f), chase().next(Offset(40f, 10f), observed = start))
    }

    @Test
    fun aMoveStillPendingIsNotCountedTwice() {
        val chase = chase()
        chase.next(Offset(40f, 10f), observed = start)
        // The pointer moves another +20 before the first move lands: the event is still
        // relative to the window at (100, 100), pointer on screen at (160, 110).
        assertEquals(Offset(140f, 100f), chase.next(Offset(60f, 10f), observed = start))
    }

    @Test
    fun aMoveThatLandedIsCountedOnce() {
        val chase = chase()
        chase.next(Offset(40f, 10f), observed = start)
        // The window is now at (120, 100) and the pointer has moved +20 more: it is at
        // (160, 110) on screen, (40, 10) in the moved window.
        assertEquals(Offset(140f, 100f), chase.next(Offset(40f, 10f), observed = Offset(120f, 100f)))
    }

    @Test
    fun aMoveLandingBetweenEventsIsPickedUpLate() {
        val chase = chase()
        chase.next(Offset(40f, 10f), observed = start)
        chase.next(Offset(60f, 10f), observed = start)
        // Both moves land at once (window at (140, 100)); the pointer has not moved since,
        // so in the window it is back at the press point.
        assertEquals(Offset(140f, 100f), chase.next(press, observed = Offset(140f, 100f)))
    }

    @Test
    fun anUnknownPositionTrustsThePointerAlone() {
        val chase = chase()
        assertEquals(Offset(120f, 100f), chase.next(Offset(40f, 10f), observed = null))
    }
}
