package com.seanproctor.docking.tree

import com.seanproctor.docking.model.AnchorId
import com.seanproctor.docking.model.DockLayout
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockWindow
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.NodeIdGenerator
import com.seanproctor.docking.model.WindowBounds
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.model.WindowKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val A = DockableId("a")
private val B = DockableId("b")
private val C = DockableId("c")
private val FLOAT_1 = WindowId("float-1")

private fun ctx(anchors: Map<DockableId, AnchorId> = emptyMap()) =
    TreeContext(NodeIdGenerator("t-")) { anchors[it] }

/** main window with A and B (B east of A), plus a floating window holding C. */
private fun TreeContext.sampleLayout(): DockLayout {
    var root: DockNode = dockAtRoot(null, A, DockRegion.Center)
    root = dockAtRoot(root, B, DockRegion.East, 0.4f)
    return DockLayout(
        listOf(
            DockWindow(WindowId.MAIN, WindowKind.Main, root),
            DockWindow(
                FLOAT_1, WindowKind.Floating, newContentNode(C),
                bounds = WindowBounds(10f, 10f, 300f, 200f),
            ),
        ),
    )
}

class UndockFromLayoutTest {

    @Test
    fun undockFromMainWindowKeepsWindow() {
        val c = ctx()
        val layout = c.sampleLayout()
        val result = c.undockFromLayout(layout, B)
        assertTrue(result.closedWindows.isEmpty())
        assertEquals(A, assertIs<DockNode.Leaf>(result.layout.mainWindow.root).dockableId)
    }

    @Test
    fun undockLastDockableOfFloatingWindowClosesIt() {
        val c = ctx()
        val layout = c.sampleLayout()
        val result = c.undockFromLayout(layout, C)
        assertEquals(listOf(FLOAT_1), result.closedWindows)
        assertEquals(1, result.layout.windows.size)
    }

    @Test
    fun undockEmptyMainWindowSurvives() {
        val c = ctx()
        var layout = c.sampleLayout()
        layout = c.undockFromLayout(layout, A).layout
        layout = c.undockFromLayout(layout, B).layout
        assertNull(layout.mainWindow.root)
        assertEquals(WindowKind.Main, layout.mainWindow.kind)
    }

}

class DockIntoLayoutTest {

    @Test
    fun dockOntoNodeInFloatingWindow() {
        val c = ctx()
        val layout = c.sampleLayout()
        val targetLeaf = layout.window(FLOAT_1)!!.root!!.findLeaf(C)!!
        val after = assertNotNull(
            c.dockIntoLayout(layout, DockableId("new"), FLOAT_1, targetLeaf.id, DockRegion.Center, 0.5f),
        )
        val tabs = assertIs<DockNode.Tabs>(after.window(FLOAT_1)!!.root)
        assertEquals(listOf(C, DockableId("new")), tabs.tabs.map { it.dockableId })
    }

    @Test
    fun dockOntoMissingNodeReturnsNull() {
        val c = ctx()
        val layout = c.sampleLayout()
        assertNull(
            c.dockIntoLayout(
                layout, DockableId("new"), WindowId.MAIN,
                com.seanproctor.docking.model.NodeId("gone"), DockRegion.Center, 0.5f,
            ),
        )
    }

    @Test
    fun dockToRootOfEmptyWindow() {
        val c = ctx()
        var layout = c.sampleLayout()
        layout = c.undockFromLayout(layout, A).layout
        layout = c.undockFromLayout(layout, B).layout
        val after = assertNotNull(
            c.dockIntoLayout(layout, A, WindowId.MAIN, null, DockRegion.Center, 0.5f),
        )
        assertEquals(A, assertIs<DockNode.Leaf>(after.mainWindow.root).dockableId)
    }
}

class MaximizeTest {

    @Test
    fun maximizeSwapsRootAndRestores() {
        val c = ctx()
        val layout = c.sampleLayout()
        val originalRoot = layout.mainWindow.root
        val maximized = assertNotNull(c.maximizeInLayout(layout, A))
        val window = maximized.mainWindow
        assertEquals(A, assertIs<DockNode.Leaf>(window.root).dockableId)
        assertEquals(A, window.maximized?.dockableId)
        assertEquals(originalRoot, window.maximized?.savedRoot)

        val restored = restoreMaximizeInLayout(maximized, WindowId.MAIN)
        assertEquals(originalRoot, restored.mainWindow.root)
        assertNull(restored.mainWindow.maximized)
    }

    @Test
    fun maximizeWhileAlreadyMaximizedReturnsNull() {
        val c = ctx()
        val layout = assertNotNull(c.maximizeInLayout(c.sampleLayout(), A))
        assertNull(c.maximizeInLayout(layout, B))
    }

    @Test
    fun structuralOperationOnMaximizedWindowRestoresFirst() {
        val c = ctx()
        val layout = assertNotNull(c.maximizeInLayout(c.sampleLayout(), A))
        // Undocking B (hidden behind the maximize) restores the window first.
        val result = c.undockFromLayout(layout, B)
        val window = result.layout.mainWindow
        assertNull(window.maximized)
        assertEquals(A, assertIs<DockNode.Leaf>(window.root).dockableId)
    }
}


class WindowManagementTest {

    @Test
    fun addFloatingWindowAndGc() {
        val c = ctx()
        var layout = c.sampleLayout()
        layout = c.undockFromLayout(layout, B).layout
        layout = c.addFloatingWindow(layout, WindowId("float-2"), B, WindowBounds(0f, 0f, 100f, 100f))
        assertEquals(3, layout.windows.size)
        val result = c.undockFromLayout(layout, B)
        assertEquals(listOf(WindowId("float-2")), result.closedWindows)
    }

    @Test
    fun closeFloatingWindowRemovesIt() {
        val c = ctx()
        val result = closeWindowInLayout(c.sampleLayout(), FLOAT_1)
        assertEquals(listOf(FLOAT_1), result.closedWindows)
        assertEquals(1, result.layout.windows.size)
    }

    @Test
    fun closeMainWindowIsNoOp() {
        val c = ctx()
        val layout = c.sampleLayout()
        val result = closeWindowInLayout(layout, WindowId.MAIN)
        assertEquals(layout, result.layout)
    }

    @Test
    fun setWindowBounds() {
        val c = ctx()
        val layout = setWindowBoundsInLayout(c.sampleLayout(), FLOAT_1, WindowBounds(1f, 2f, 3f, 4f))
        assertEquals(WindowBounds(1f, 2f, 3f, 4f), layout.window(FLOAT_1)?.bounds)
    }

    @Test
    fun mergeFloatingIntoMainGraftsTree() {
        val c = ctx()
        val merged = c.mergeFloatingIntoMain(c.sampleLayout())
        assertEquals(1, merged.windows.size)
        val root = assertIs<DockNode.Split>(merged.mainWindow.root)
        assertEquals(0.75f, root.proportion)
        assertNotNull(root.findLeaf(C))
        assertNotNull(root.findLeaf(A))
        assertNotNull(root.findLeaf(B))
    }
}

class AreaFractionTest {

    @Test
    fun nestedFractionsMultiply() {
        val c = ctx()
        var root: DockNode = c.dockAtRoot(null, A, DockRegion.Center)
        root = c.dockAtRoot(root, B, DockRegion.West, 0.25f)
        root = assertNotNull(c.dockAt(root, root.findLeaf(A)!!.id, C, DockRegion.South, 0.5f))
        assertEquals(0.25f, root.areaFractionOf(B))
        assertEquals(0.75f * 0.5f, root.areaFractionOf(A)!!, 0.0001f)
        assertEquals(0.75f * 0.5f, root.areaFractionOf(C)!!, 0.0001f)
        assertNull(root.areaFractionOf(DockableId("missing")))
    }
}

private fun assertEquals(expected: Float, actual: Float, tolerance: Float) {
    assertTrue(
        kotlin.math.abs(expected - actual) <= tolerance,
        "expected $expected within $tolerance of $actual",
    )
}
