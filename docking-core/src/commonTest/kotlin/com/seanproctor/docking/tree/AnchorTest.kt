package com.seanproctor.docking.tree

import com.seanproctor.docking.model.AnchorId
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.NodeIdGenerator
import com.seanproctor.docking.model.DockableOptions
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private val A = DockableId("a")
private val B = DockableId("b")
private val C = DockableId("c")
private val TOOLS = AnchorId("tools")

private fun anchoredCtx(anchored: Set<DockableId> = emptySet()) =
    TreeContext(NodeIdGenerator("t-")) { id -> TOOLS.takeIf { id in anchored } }

class AnchorRestoreTest {

    @Test
    fun undockLastCarrierLeavesPlaceholder() {
        val c = anchoredCtx(setOf(B))
        var root: DockNode = c.dockAtRoot(null, A, DockRegion.Center)
        root = c.dockAtRoot(root, B, DockRegion.West, 0.3f)
        val after = assertIs<DockNode.Split>(c.undock(root, B))
        val anchor = assertIs<DockNode.Anchor>(after.first)
        assertEquals(TOOLS, anchor.anchorId)
        // The split survives because the slot did not collapse.
        assertEquals(0.3f, after.proportion)
    }

    @Test
    fun undockCarrierWithRemainingCarrierCollapsesNormally() {
        val c = anchoredCtx(setOf(B, C))
        var root: DockNode = c.dockAtRoot(null, A, DockRegion.Center)
        root = c.dockAtRoot(root, B, DockRegion.West, 0.3f)
        root = assertNotNull(c.dockAt(root, root.findLeaf(B)!!.id, C, DockRegion.Center))
        // B leaves but C still carries the anchor -> no placeholder, tab group collapses.
        val after = assertIs<DockNode.Split>(c.undock(root, B))
        assertEquals(C, assertIs<DockNode.Leaf>(after.first).dockableId)
        assertNull(after.findAnchorNode(TOOLS))
    }

    @Test
    fun undockLastTabOfGroupRestoresPlaceholderInGroupSlot() {
        val c = anchoredCtx(setOf(B))
        var root: DockNode = c.dockAtRoot(null, A, DockRegion.Center)
        root = c.dockAtRoot(root, B, DockRegion.West, 0.3f)
        val after = assertIs<DockNode.Split>(c.undock(root, B))
        assertIs<DockNode.Anchor>(after.first)
    }

    @Test
    fun noDuplicatePlaceholderWhenOneAlreadyExists() {
        val c = anchoredCtx(setOf(B))
        var root: DockNode = c.dockAtRoot(null, A, DockRegion.Center)
        root = c.dockAtRoot(root, B, DockRegion.West, 0.3f)
        // Restore placeholder once.
        root = assertNotNull(c.undock(root, B))
        assertNotNull(root.findAnchorNode(TOOLS))
        // Dock B somewhere else (east), then undock again: a placeholder already exists,
        // so the east slot must collapse instead of adding a second one.
        root = c.dockAtRoot(root, B, DockRegion.East, 0.3f)
        root = assertNotNull(c.undock(root, B))
        var count = 0
        fun countAnchors(n: DockNode) {
            when (n) {
                is DockNode.Anchor -> if (n.anchorId == TOOLS) count++
                is DockNode.Split -> { countAnchors(n.first); countAnchors(n.second) }
                else -> Unit
            }
        }
        countAnchors(root)
        assertEquals(1, count)
    }
}

class DockIntoAnchorTest {

    @Test
    fun replacesPlaceholder() {
        val c = anchoredCtx(setOf(B))
        var root: DockNode = c.dockAtRoot(null, A, DockRegion.Center)
        root = c.dockAtRoot(root, B, DockRegion.West, 0.3f)
        root = assertNotNull(c.undock(root, B))
        val after = assertIs<DockNode.Split>(c.dockIntoAnchor(root, TOOLS, B))
        assertEquals(B, assertIs<DockNode.Leaf>(after.first).dockableId)
        assertNull(after.findAnchorNode(TOOLS))
    }

    @Test
    fun docksOntoLargestCarrierWhenNoPlaceholder() {
        val c = anchoredCtx(setOf(B, C))
        var root: DockNode = c.dockAtRoot(null, A, DockRegion.Center)
        // B gets 30% on the west; C gets 50% of the remainder (35%) -> C is larger.
        root = c.dockAtRoot(root, B, DockRegion.West, 0.3f)
        root = c.dockAtRoot(root, C, DockRegion.East, 0.35f)
        val after = assertNotNull(c.dockIntoAnchor(root, TOOLS, DockableId("new")))
        // The new dockable tabbed together with C, the largest carrier.
        val leafNew = assertNotNull(after.findLeaf(DockableId("new")))
        val tabs = assertNotNull(findTabsContaining(after, leafNew.id))
        assertEquals(listOf(C, DockableId("new")), tabs.tabs.map { it.dockableId })
    }

    @Test
    fun returnsNullWhenAnchorAbsent() {
        val c = anchoredCtx()
        val root = c.dockAtRoot(null, A, DockRegion.Center)
        assertNull(c.dockIntoAnchor(root, TOOLS, B))
    }

    @Test
    fun edgeDockOntoPlaceholderFillsIt() {
        val c = anchoredCtx(setOf(B))
        var root: DockNode = c.dockAtRoot(null, A, DockRegion.Center)
        root = c.dockAtRoot(root, B, DockRegion.West, 0.3f)
        root = assertNotNull(c.undock(root, B))
        val placeholder = assertNotNull(root.findAnchorNode(TOOLS))
        val after = assertNotNull(c.dockAt(root, placeholder.id, C, DockRegion.North))
        val split = assertIs<DockNode.Split>(after)
        assertEquals(C, assertIs<DockNode.Leaf>(split.first).dockableId)
        assertNull(after.findAnchorNode(TOOLS))
    }
}

private fun findTabsContaining(root: DockNode, leafId: com.seanproctor.docking.model.NodeId): DockNode.Tabs? =
    when (root) {
        is DockNode.Tabs -> root.takeIf { it.tabs.any { t -> t.id == leafId } }
        is DockNode.Split ->
            findTabsContaining(root.first, leafId) ?: findTabsContaining(root.second, leafId)
        else -> null
    }

class EnsureAnchorTest {

    private fun state() = DockState {
        dockable("a", title = { "A" }) {}
        dockable("b", title = { "B" }, options = DockableOptions(anchor = TOOLS)) {}
    }

    @Test
    fun addsAMissingAnchorToTheWindowRoot() {
        val state = state()
        state.dock(A)
        // A layout restored from a snapshot that predates the area: no placeholder, no carrier.
        assertTrue(state.ensureAnchor(TOOLS, DockRegion.West, 0.3f))
        val split = assertIs<DockNode.Split>(state.layout.mainWindow.root)
        assertEquals(TOOLS, assertIs<DockNode.Anchor>(split.first).anchorId)
        assertEquals(0.3f, split.proportion)
    }

    @Test
    fun leavesAnExistingPlaceholderAlone() {
        val state = state()
        state.dock(A)
        assertTrue(state.ensureAnchor(TOOLS, DockRegion.West, 0.3f))
        val before = state.layout
        // Idempotent: calling it again must not stack a second empty area onto the layout.
        assertFalse(state.ensureAnchor(TOOLS, DockRegion.West, 0.3f))
        assertEquals(before, state.layout)
    }

    @Test
    fun leavesAnOccupiedAreaAlone() {
        val state = state()
        state.dock(A)
        state.dock(B, DockTarget.Root(), DockRegion.West, 0.3f)
        val before = state.layout
        // The area exists because a dockable carrying it is docked; adding a placeholder
        // would give the layout two of the same area.
        assertFalse(state.ensureAnchor(TOOLS, DockRegion.West, 0.3f))
        assertEquals(before, state.layout)
    }
}
