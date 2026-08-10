package com.seanproctor.docking.state

import com.seanproctor.docking.model.AnchorId
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.DockableOptions
import com.seanproctor.docking.model.DockingStyle
import com.seanproctor.docking.model.WindowBounds
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.tree.findLeaf
import com.seanproctor.docking.tree.findTabsContaining
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
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

private fun testState(
    options: Map<DockableId, DockableOptions> = emptyMap(),
    canClose: Map<DockableId, Boolean> = emptyMap(),
): DockState = DockState {
    for (id in listOf(A, B, C)) {
        dockable(
            DockableSpec(
                id = id,
                options = options[id] ?: DockableOptions(),
                title = { id.value },
                canClose = { canClose[id] ?: true },
                content = {},
            ),
        )
    }
}

class DockStateOperationsTest {

    @Test
    fun dockToEmptyRootThenRelative() {
        val state = testState()
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East, 0.3f)
        val split = assertIs<DockNode.Split>(state.layout.mainWindow.root)
        assertEquals(0.7f, split.proportion)
        assertTrue(state.isDocked(A))
        assertTrue(state.isDocked(B))
    }

    @Test
    fun dockAlreadyDockedMovesAtomically() {
        val state = testState()
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
        // Move B into a tab group with A.
        state.dock(B, DockTarget.OnDockable(A), DockRegion.Center)
        val tabs = assertIs<DockNode.Tabs>(state.layout.mainWindow.root)
        assertEquals(listOf(A, B), tabs.tabs.map { it.dockableId })
    }

    @Test
    fun dockOntoMissingTargetChangesNothing() {
        val state = testState()
        state.dock(A)
        val before = state.layout
        state.dock(B, DockTarget.OnDockable(C), DockRegion.East)
        assertEquals(before, state.layout)
        assertFalse(state.isDocked(B))
    }

    @Test
    fun undockRemovesAndCollapses() {
        val state = testState()
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
        state.undock(B)
        assertIs<DockNode.Leaf>(state.layout.mainWindow.root)
        assertFalse(state.isOpen(B))
    }

    @Test
    fun closeRespectsVeto() = runTest {
        val state = testState(canClose = mapOf(A to false))
        state.dock(A)
        assertFalse(state.close(A))
        assertTrue(state.isDocked(A))
    }

    @Test
    fun closeRespectsClosableOption() = runTest {
        val state = testState(options = mapOf(A to DockableOptions(closable = false)))
        state.dock(A)
        assertFalse(state.close(A))
        assertTrue(state.isDocked(A))
    }

    @Test
    fun closeRemovesWhenAllowed() = runTest {
        val state = testState()
        state.dock(A)
        assertTrue(state.close(A))
        assertFalse(state.isOpen(A))
    }

    @Test
    fun moveToNewWindowCreatesFloatingWindow() {
        val state = testState()
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
        state.moveToNewWindow(B, WindowBounds(50f, 50f, 400f, 300f))
        assertEquals(2, state.layout.windows.size)
        val floating = state.layout.floatingWindows.single()
        assertEquals(B, assertIs<DockNode.Leaf>(floating.root).dockableId)
        assertEquals(floating.id, state.windowOf(B))
    }

    @Test
    fun moveToNewWindowBlockedWhenNotFloatable() {
        val state = testState(options = mapOf(A to DockableOptions(floatable = false)))
        state.dock(A)
        state.moveToNewWindow(A)
        assertEquals(1, state.layout.windows.size)
    }

    @Test
    fun maximizeAndRestore() {
        val state = testState()
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
        state.maximize(B)
        assertTrue(state.isMaximized(B))
        assertEquals(B, assertIs<DockNode.Leaf>(state.layout.mainWindow.root).dockableId)
        state.toggleMaximize(B)
        assertFalse(state.isMaximized(B))
        assertIs<DockNode.Split>(state.layout.mainWindow.root)
    }




    @Test
    fun showSelectsTabOfDockedDockable() {
        val state = testState()
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.Center)
        // B is selected (last docked); show A.
        state.show(A)
        val tabs = assertNotNull(state.layout.mainWindow.root?.findTabsContaining(A))
        assertEquals(A, tabs.selectedTab.dockableId)
        assertEquals(A, state.activeDockable)
    }

    @Test
    fun showDocksClosedDockableIntoAnchor() {
        val anchor = AnchorId("side")
        val state = testState(options = mapOf(B to DockableOptions(anchor = anchor)))
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.West, 0.3f)
        state.undock(B) // leaves anchor placeholder
        state.show(B)
        assertTrue(state.isDocked(B))
        val split = assertIs<DockNode.Split>(state.layout.mainWindow.root)
        assertEquals(B, assertIs<DockNode.Leaf>(split.first).dockableId)
    }

    @Test
    fun selectTabAndMoveTab() {
        val state = testState()
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.Center)
        state.dock(C, DockTarget.OnDockable(A), DockRegion.Center)
        val tabs = assertIs<DockNode.Tabs>(state.layout.mainWindow.root)
        state.selectTab(tabs.id, 0)
        assertEquals(0, assertIs<DockNode.Tabs>(state.layout.mainWindow.root).selectedIndex)
        state.moveTab(tabs.id, 0, 2)
        assertEquals(
            listOf(B, C, A),
            assertIs<DockNode.Tabs>(state.layout.mainWindow.root).tabs.map { it.dockableId },
        )
    }

    @Test
    fun splitProportionOperations() {
        val state = testState()
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
        val split = assertIs<DockNode.Split>(state.layout.mainWindow.root)
        state.setSplitProportion(split.id, 0.8f)
        assertEquals(0.8f, assertIs<DockNode.Split>(state.layout.mainWindow.root).proportion)
        state.resetSplitProportion(split.id)
        assertEquals(0.5f, assertIs<DockNode.Split>(state.layout.mainWindow.root).proportion)
    }

    @Test
    fun closeWindowDropsFloatingWindow() {
        val state = testState()
        state.dock(A)
        state.moveToNewWindow(B, WindowBounds(0f, 0f, 100f, 100f))
        val windowId = state.windowOf(B)!!
        state.closeWindow(windowId)
        assertEquals(1, state.layout.windows.size)
        assertFalse(state.isOpen(B))
    }
}

class CanDockTest {

    @Test
    fun bidirectionalStyleCheck() {
        val state = testState(
            options = mapOf(
                A to DockableOptions(dockingStyle = DockingStyle.Horizontal),
                B to DockableOptions(dockingStyle = DockingStyle.Both),
            ),
        )
        state.dock(A)
        // B onto A at East: A only splits North/South.
        assertFalse(state.canDock(B, DockTarget.OnDockable(A), DockRegion.East))
        assertTrue(state.canDock(B, DockTarget.OnDockable(A), DockRegion.South))
        assertTrue(state.canDock(B, DockTarget.OnDockable(A), DockRegion.Center))
    }

    @Test
    fun limitedToWindowBlocksCrossWindowTargets() {
        val state = testState(options = mapOf(B to DockableOptions(limitedToWindow = true)))
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
        state.moveToNewWindow(C, WindowBounds(0f, 0f, 100f, 100f))
        val floatId = state.windowOf(C)!!
        assertFalse(state.canDock(B, DockTarget.OnDockable(C), DockRegion.Center))
        assertFalse(state.canDock(B, DockTarget.Root(floatId), DockRegion.West))
        assertTrue(state.canDock(B, DockTarget.OnDockable(A), DockRegion.Center))
    }

    @Test
    fun rootTargetChecksDraggedStyleOnly() {
        val state = testState(
            options = mapOf(A to DockableOptions(dockingStyle = DockingStyle.Vertical)),
        )
        assertTrue(state.canDock(A, DockTarget.Root(), DockRegion.West))
        assertFalse(state.canDock(A, DockTarget.Root(), DockRegion.North))
    }
}

class DockStateEventsTest {

    @Test
    fun moveEmitsTemporaryUndockThenDock() = runTest {
        val state = testState()
        val events = mutableListOf<DockingEvent>()
        val job = launch { state.events.collect { events.add(it) } }
        // Let the collector subscribe.
        testScheduler.runCurrent()

        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.Center) // move
        testScheduler.runCurrent()

        val moveEvents = events.drop(2)
        assertEquals(
            listOf(
                DockingEvent.Undocked(B, isTemporary = true),
                DockingEvent.Docked(B, WindowId.MAIN),
            ),
            moveEvents,
        )
        job.cancel()
    }

    @Test
    fun emptiedFloatingWindowEmitsWindowClosed() = runTest {
        val state = testState()
        state.dock(A)
        state.moveToNewWindow(B, WindowBounds(0f, 0f, 100f, 100f))
        val floatId = state.windowOf(B)!!

        val events = mutableListOf<DockingEvent>()
        val job = launch { state.events.collect { events.add(it) } }
        testScheduler.runCurrent()

        state.dock(B, DockTarget.OnDockable(A), DockRegion.Center)
        testScheduler.runCurrent()

        assertTrue(DockingEvent.WindowClosed(floatId) in events)
        job.cancel()
    }
}
