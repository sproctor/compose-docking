package com.seanproctor.docking.window

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
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
    override var density: Float? = 1f,
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

        val claim = state.windowHost.claimDragPreview()
        assertTrue(state.windowHost.drawsDragPreview)
        assertEquals(1, state.dragController.previewClaims)

        claim.release()
        assertFalse(state.windowHost.drawsDragPreview)
        assertEquals(0, state.dragController.previewClaims)
    }

    // A preview window going away must not switch off one that is still drawing - the in-window
    // card would come back beside it, two previews for one drag.
    @Test
    fun oneOwnerReleasingKeepsAnotherOwnersPreview() {
        val host = setUp().state.windowHost
        val leaving = host.claimDragPreview()
        val staying = host.claimDragPreview()

        leaving.release()
        leaving.release()

        assertTrue(host.drawsDragPreview, "the second release of the same claim counts for nothing")
        staying.release()
        assertFalse(host.drawsDragPreview)
    }

    // Tearing the last thing out of a floating window closes the window and its dock area goes
    // with it; the torn-off window is still sized from that window's pixels.
    @Test
    fun aTornOffWindowIsSizedWithTheDensityTheDragBeganWith() {
        val setup = setUp()
        val state = setup.state
        setup.floating.density = 2f
        state.dragController.startDrag(
            DragSource.Header(B),
            positionInWindow = Offset(20f, 10f),
            windowId = setup.floatingId,
            sourceSize = androidx.compose.ui.geometry.Size(600f, 400f),
        )
        state.dragController.unregisterWindow(setup.floatingId)

        state.dragController.dropOutside()

        val bounds = assertNotNull(state.layout.windowContaining(B)?.bounds)
        assertEquals(300f to 200f, bounds.width to bounds.height, "600x400 px at 2x is 300x200")
    }

    // Desktop's KeyEvent factory is the one way to make a key event outside a window.
    @OptIn(InternalComposeUiApi::class)
    @Test
    fun escapeCancelsADrag() {
        val setup = setUp()
        val host = setup.state.windowHost
        host.startWindowDrag(setup.floatingId, Offset(20f, 10f))

        assertFalse(host.cancelOnEscape(KeyEvent(Key.A, KeyEventType.KeyDown)), "other keys pass")
        assertFalse(host.cancelOnEscape(KeyEvent(Key.Escape, KeyEventType.KeyUp)), "only the press")
        assertTrue(host.isDragging)

        assertTrue(host.cancelOnEscape(KeyEvent(Key.Escape, KeyEventType.KeyDown)))
        assertFalse(host.isDragging)
        assertFalse(host.cancelOnEscape(KeyEvent(Key.Escape, KeyEventType.KeyDown)), "nothing left to cancel")
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

    // A window that cannot say where it is (native Wayland) shares no space with the others, but
    // a drag inside it must still find it - not tear the panel off because no window was hit.
    @Test
    fun aWindowWithoutBoundsStillTakesDragsWithinItself() {
        val state = state()
        state.dock(A)
        state.dock(C, DockTarget.OnDockable(A), DockRegion.East)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
        state.moveToNewWindow(B, WindowBounds(100f, 100f, 300f, 200f))
        val floatingId = state.layout.floatingWindows.single().id
        state.show(WindowId.MAIN, onScreen = null, size = Rect(0f, 0f, 800f, 600f))
        // Placeable, focused, and where main's own positions would land in screen space.
        state.show(floatingId, Rect(100f, 100f, 400f, 300f), Rect(0f, 0f, 300f, 200f), focused = true)

        assertEquals(WindowId.MAIN, state.hoverAt(Offset(150f, 150f)), "its positions are its own")
        assertNull(state.hoverAt(Offset(900f, 700f)), "and off its dock area, over nothing")
    }

    @Test
    fun anOsWindowWithoutADockAreaIsNeverHit() {
        val (state, floatingId) = twoWindows(floatingFocused = true)
        state.dragController.unregisterWindow(floatingId)
        assertEquals(WindowId.MAIN, state.hoverAt(Offset(150f, 150f)))
    }

    // Windows on monitors of different densities share one space: a window's pixels are turned
    // into it with that window's own density, so a point dragged out of a 2x window lands on the
    // 1x window that is really there.
    @Test
    fun windowsOfDifferentDensitiesShareOneSpace() {
        val (state, floatingId) = twoWindows(floatingBounds = Rect(700f, 0f, 1000f, 200f))
        (state.hostWindow(floatingId) as FakeHostWindow).density = 2f

        // 100 px left of the floating window's origin at 2x is 50 units: (650, 50), over main.
        // Scaling the origin by the density too, as AWT's pixels used to be, would put the
        // point at (1300, 50), past main's right edge at 800.
        assertEquals(Offset(650f, 50f), state.dragController.toScreen(floatingId, Offset(-100f, 100f)))
        assertEquals(Offset(-100f, 100f), state.dragController.fromScreen(floatingId, Offset(650f, 50f)))
        state.dragController.startDrag(DragSource.Header(B), positionInWindow = Offset(20f, 20f), windowId = floatingId)
        state.dragController.updateDrag(Offset(-100f, 100f), floatingId)
        assertEquals(WindowId.MAIN, state.dragController.session?.hoveredWindow)
    }

    // An app zooming its UI overrides LocalDensity around the dock area; the window still has
    // the pixels its monitor gives it, and those are what pointer positions are counted in.
    @Test
    fun theWindowsOwnScaleWinsOverTheDockAreasDensity() {
        val (state, floatingId) = twoWindows(floatingBounds = Rect(700f, 0f, 1000f, 200f))
        (state.hostWindow(floatingId) as FakeHostWindow).density = 2f
        state.dragController.window(floatingId)?.scope?.density = 2.5f

        assertEquals(Offset(750f, 50f), state.dragController.toScreen(floatingId, Offset(100f, 100f)))
    }

    // A window that cannot say its scale yet is converted with its dock area's density, not 1.
    @Test
    fun anUnknownWindowScaleFallsBackToTheDockArea() {
        val (state, floatingId) = twoWindows(floatingBounds = Rect(700f, 0f, 1000f, 200f))
        (state.hostWindow(floatingId) as FakeHostWindow).density = null
        state.dragController.window(floatingId)?.scope?.density = 2f

        assertEquals(Offset(750f, 50f), state.dragController.toScreen(floatingId, Offset(100f, 100f)))
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
 * A window chasing the pointer that drags it, at density 2: the window's content starts at
 * (100, 100), grabbed at (20, 10) px in it, so the pointer is at (110, 105) on screen. Wherever
 * the pointer goes, the window belongs at the pointer minus (10, 5).
 */
class WindowChaseTest {
    private val start = Offset(100f, 100f)
    private val press = Offset(20f, 10f)

    private fun chase() = WindowChase(start, press)

    @Test
    fun theFirstMoveFollowsThePointerFromThePress() {
        // Pointer +20 px = +10 units, window not moved yet.
        assertEquals(Offset(110f, 100f), chase().next(Offset(40f, 10f), observed = start, density = 2f))
    }

    @Test
    fun aMoveStillPendingIsNotCountedTwice() {
        val chase = chase()
        chase.next(Offset(40f, 10f), observed = start, density = 2f)
        // The pointer moves another +10 units before the first move lands: the event is still
        // relative to the window at (100, 100), pointer on screen at (130, 105).
        assertEquals(Offset(120f, 100f), chase.next(Offset(60f, 10f), observed = start, density = 2f))
    }

    @Test
    fun aMoveThatLandedIsCountedOnce() {
        val chase = chase()
        chase.next(Offset(40f, 10f), observed = start, density = 2f)
        // The window is now at (110, 100) and the pointer has moved +10 units more: it is at
        // (130, 105) on screen, (20, 5) units = (40, 10) px in the moved window.
        assertEquals(Offset(120f, 100f), chase.next(Offset(40f, 10f), observed = Offset(110f, 100f), density = 2f))
    }

    @Test
    fun aMoveLandingBetweenEventsIsPickedUpLate() {
        val chase = chase()
        chase.next(Offset(40f, 10f), observed = start, density = 2f)
        chase.next(Offset(60f, 10f), observed = start, density = 2f)
        // Both moves land at once (window at (120, 100)); the pointer has not moved since, so
        // in the window it is back at the press point.
        assertEquals(Offset(120f, 100f), chase.next(press, observed = Offset(120f, 100f), density = 2f))
    }

    // Dragged onto a 1x monitor mid-drag: from there on, pixels are one to a unit.
    @Test
    fun aChangeOfDensityMidDragIsFollowed() {
        val chase = chase()
        chase.next(Offset(40f, 10f), observed = start, density = 2f)
        assertEquals(Offset(130f, 100f), chase.next(Offset(60f, 10f), observed = start, density = 1f))
    }

    @Test
    fun anUnknownPositionTrustsThePointerAlone() {
        assertEquals(Offset(110f, 100f), chase().next(Offset(40f, 10f), observed = null, density = 2f))
    }
}
