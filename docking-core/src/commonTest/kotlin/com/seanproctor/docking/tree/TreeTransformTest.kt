package com.seanproctor.docking.tree

import com.seanproctor.docking.model.AnchorId
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.NodeIdGenerator
import com.seanproctor.docking.model.SplitOrientation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private val A = DockableId("a")
private val B = DockableId("b")
private val C = DockableId("c")
private val D = DockableId("d")

private fun ctx(
    alwaysTabs: Boolean = false,
    anchors: Map<DockableId, AnchorId> = emptyMap(),
) = TreeContext(NodeIdGenerator("t-"), alwaysTabs) { anchors[it] }

class DockAtRootTest {

    @Test
    fun dockToEmptyRootCreatesLeaf() {
        val root = ctx().dockAtRoot(null, A, DockRegion.Center)
        val leaf = assertIs<DockNode.Leaf>(root)
        assertEquals(A, leaf.dockableId)
    }

    @Test
    fun dockToEmptyRootWithEdgeRegionAlsoFills() {
        val root = ctx().dockAtRoot(null, A, DockRegion.West)
        assertIs<DockNode.Leaf>(root)
    }

    @Test
    fun dockToEmptyRootInAlwaysTabsModeCreatesSingleTabGroup() {
        val root = ctx(alwaysTabs = true).dockAtRoot(null, A, DockRegion.Center)
        val tabs = assertIs<DockNode.Tabs>(root)
        assertEquals(1, tabs.tabs.size)
        assertEquals(A, tabs.tabs[0].dockableId)
    }

    @Test
    fun centerOnNonEmptyRootIsNoOp() {
        val c = ctx()
        val root = c.dockAtRoot(null, A, DockRegion.Center)
        val after = c.dockAtRoot(root, B, DockRegion.Center)
        assertSame(root, after)
    }

    @Test
    fun dockWestAtRootPutsNewFirstWithGivenProportion() {
        val c = ctx()
        val root = c.dockAtRoot(null, A, DockRegion.Center)
        val split = assertIs<DockNode.Split>(c.dockAtRoot(root, B, DockRegion.West, 0.3f))
        assertEquals(SplitOrientation.Horizontal, split.orientation)
        assertEquals(B, assertIs<DockNode.Leaf>(split.first).dockableId)
        assertEquals(A, assertIs<DockNode.Leaf>(split.second).dockableId)
        assertEquals(0.3f, split.proportion)
    }

    @Test
    fun dockEastAtRootPutsNewSecondWithFlippedProportion() {
        val c = ctx()
        val root = c.dockAtRoot(null, A, DockRegion.Center)
        val split = assertIs<DockNode.Split>(c.dockAtRoot(root, B, DockRegion.East, 0.3f))
        assertEquals(SplitOrientation.Horizontal, split.orientation)
        assertEquals(A, assertIs<DockNode.Leaf>(split.first).dockableId)
        assertEquals(B, assertIs<DockNode.Leaf>(split.second).dockableId)
        assertEquals(0.7f, split.proportion)
    }

    @Test
    fun dockNorthAtRootIsVerticalNewFirst() {
        val c = ctx()
        val root = c.dockAtRoot(null, A, DockRegion.Center)
        val split = assertIs<DockNode.Split>(c.dockAtRoot(root, B, DockRegion.North, 0.2f))
        assertEquals(SplitOrientation.Vertical, split.orientation)
        assertEquals(B, assertIs<DockNode.Leaf>(split.first).dockableId)
        assertEquals(0.2f, split.proportion)
    }

    @Test
    fun dockSouthAtRootIsVerticalNewSecond() {
        val c = ctx()
        val root = c.dockAtRoot(null, A, DockRegion.Center)
        val split = assertIs<DockNode.Split>(c.dockAtRoot(root, B, DockRegion.South, 0.2f))
        assertEquals(SplitOrientation.Vertical, split.orientation)
        assertEquals(B, assertIs<DockNode.Leaf>(split.second).dockableId)
        assertEquals(0.8f, split.proportion)
    }

    @Test
    fun proportionIsClamped() {
        val c = ctx()
        val root = c.dockAtRoot(null, A, DockRegion.Center)
        val split = assertIs<DockNode.Split>(c.dockAtRoot(root, B, DockRegion.West, 0.0f))
        assertEquals(0.05f, split.proportion)
    }
}

class DockAtNodeTest {

    @Test
    fun centerOnLeafCreatesTabGroupPreservingLeafIdentity() {
        val c = ctx()
        val leafA = assertIs<DockNode.Leaf>(c.dockAtRoot(null, A, DockRegion.Center))
        val tabs = assertIs<DockNode.Tabs>(c.dockAt(leafA, leafA.id, B, DockRegion.Center))
        assertEquals(2, tabs.tabs.size)
        assertSame(leafA, tabs.tabs[0])
        assertEquals(B, tabs.tabs[1].dockableId)
        assertEquals(1, tabs.selectedIndex)
    }

    @Test
    fun centerOnTabsNodeAppendsAndSelects() {
        val c = ctx()
        val leafA = assertIs<DockNode.Leaf>(c.dockAtRoot(null, A, DockRegion.Center))
        val tabs = assertIs<DockNode.Tabs>(c.dockAt(leafA, leafA.id, B, DockRegion.Center))
        val tabs2 = assertIs<DockNode.Tabs>(c.dockAt(tabs, tabs.id, C, DockRegion.Center))
        assertEquals(listOf(A, B, C), tabs2.tabs.map { it.dockableId })
        assertEquals(2, tabs2.selectedIndex)
    }

    @Test
    fun centerOnMemberLeafOfTabGroupAppends() {
        val c = ctx()
        val leafA = assertIs<DockNode.Leaf>(c.dockAtRoot(null, A, DockRegion.Center))
        val tabs = assertIs<DockNode.Tabs>(c.dockAt(leafA, leafA.id, B, DockRegion.Center))
        val memberLeafId = tabs.tabs[0].id
        val tabs2 = assertIs<DockNode.Tabs>(c.dockAt(tabs, memberLeafId, C, DockRegion.Center))
        assertEquals(3, tabs2.tabs.size)
    }

    @Test
    fun edgeOnMemberLeafSplitsWholeGroup() {
        val c = ctx()
        val leafA = assertIs<DockNode.Leaf>(c.dockAtRoot(null, A, DockRegion.Center))
        val tabs = assertIs<DockNode.Tabs>(c.dockAt(leafA, leafA.id, B, DockRegion.Center))
        val memberLeafId = tabs.tabs[0].id
        val split = assertIs<DockNode.Split>(c.dockAt(tabs, memberLeafId, C, DockRegion.East, 0.4f))
        assertSame(tabs, split.first)
        assertEquals(C, assertIs<DockNode.Leaf>(split.second).dockableId)
        assertEquals(0.6f, split.proportion)
    }

    @Test
    fun dockDeepInsideNestedSplits() {
        val c = ctx()
        var root: DockNode = c.dockAtRoot(null, A, DockRegion.Center)
        root = c.dockAtRoot(root, B, DockRegion.East, 0.5f)
        val leafB = root.findLeaf(B)!!
        val after = assertNotNull(c.dockAt(root, leafB.id, C, DockRegion.South, 0.3f))
        val outer = assertIs<DockNode.Split>(after)
        val inner = assertIs<DockNode.Split>(outer.second)
        assertEquals(SplitOrientation.Vertical, inner.orientation)
        assertEquals(B, assertIs<DockNode.Leaf>(inner.first).dockableId)
        assertEquals(C, assertIs<DockNode.Leaf>(inner.second).dockableId)
        assertEquals(0.7f, inner.proportion)
        // Untouched sibling keeps identity (structural sharing).
        assertSame(root.findLeaf(A), after.findLeaf(A))
    }

    @Test
    fun dockOntoMissingTargetReturnsNull() {
        val c = ctx()
        val root = c.dockAtRoot(null, A, DockRegion.Center)
        assertNull(c.dockAt(root, com.seanproctor.docking.model.NodeId("nope"), B, DockRegion.Center))
    }
}

class UndockTest {

    @Test
    fun undockSoleLeafEmptiesTree() {
        val c = ctx()
        val root = c.dockAtRoot(null, A, DockRegion.Center)
        assertNull(c.undock(root, A))
    }

    @Test
    fun undockMissingDockableReturnsSameTree() {
        val c = ctx()
        val root = c.dockAtRoot(null, A, DockRegion.Center)
        assertSame(root, c.undock(root, B))
    }

    @Test
    fun undockFromTwoTabGroupCollapsesToLeaf() {
        val c = ctx()
        val leafA = assertIs<DockNode.Leaf>(c.dockAtRoot(null, A, DockRegion.Center))
        val tabs = assertIs<DockNode.Tabs>(c.dockAt(leafA, leafA.id, B, DockRegion.Center))
        val after = c.undock(tabs, B)
        assertSame(leafA, after)
    }

    @Test
    fun undockFromTwoTabGroupInAlwaysTabsModeKeepsGroup() {
        val c = ctx(alwaysTabs = true)
        var root: DockNode = c.dockAtRoot(null, A, DockRegion.Center)
        val groupId = assertIs<DockNode.Tabs>(root).id
        root = assertNotNull(c.dockAt(root, groupId, B, DockRegion.Center))
        val after = assertIs<DockNode.Tabs>(c.undock(root, B))
        assertEquals(1, after.tabs.size)
        assertEquals(A, after.tabs[0].dockableId)
    }

    @Test
    fun undockClampsSelection() {
        val c = ctx()
        val leafA = assertIs<DockNode.Leaf>(c.dockAtRoot(null, A, DockRegion.Center))
        var tabs = assertIs<DockNode.Tabs>(c.dockAt(leafA, leafA.id, B, DockRegion.Center))
        tabs = assertIs(c.dockAt(tabs, tabs.id, C, DockRegion.Center))
        // Selected = C (index 2). Remove selected last tab -> selection moves to new last.
        val afterRemoveSelected = assertIs<DockNode.Tabs>(c.undock(tabs, C))
        assertEquals(1, afterRemoveSelected.selectedIndex)
        // Remove a tab before the selected one -> selection index shifts down to follow it.
        val afterRemoveBefore = assertIs<DockNode.Tabs>(c.undock(tabs, A))
        assertEquals(1, afterRemoveBefore.selectedIndex)
        assertEquals(C, afterRemoveBefore.tabs[afterRemoveBefore.selectedIndex].dockableId)
    }

    @Test
    fun undockLeafUnderSplitPromotesSibling() {
        val c = ctx()
        var root: DockNode = c.dockAtRoot(null, A, DockRegion.Center)
        val leafA = root as DockNode.Leaf
        root = c.dockAtRoot(root, B, DockRegion.East)
        val after = c.undock(root, B)
        assertSame(leafA, after)
    }

    @Test
    fun undockCollapsesNestedSplitChain() {
        val c = ctx()
        var root: DockNode = c.dockAtRoot(null, A, DockRegion.Center)
        root = c.dockAtRoot(root, B, DockRegion.East, 0.5f)
        val leafB = root.findLeaf(B)!!
        root = assertNotNull(c.dockAt(root, leafB.id, C, DockRegion.South, 0.3f))
        // Remove C -> inner split collapses, B promoted into outer split.
        val after = assertIs<DockNode.Split>(c.undock(root, C))
        assertEquals(A, assertIs<DockNode.Leaf>(after.first).dockableId)
        assertEquals(B, assertIs<DockNode.Leaf>(after.second).dockableId)
    }

    @Test
    fun dockThenUndockIsIdentityForAllRegions() {
        for (region in listOf(DockRegion.North, DockRegion.South, DockRegion.East, DockRegion.West, DockRegion.Center)) {
            val c = ctx()
            var root: DockNode = c.dockAtRoot(null, A, DockRegion.Center)
            root = c.dockAtRoot(root, B, DockRegion.East, 0.5f)
            val before = root
            val leafB = root.findLeaf(B)!!
            root = assertNotNull(c.dockAt(root, leafB.id, C, region, 0.3f))
            val after = c.undock(root, C)
            assertEquals(before, after, "dock+undock at $region should restore the tree")
        }
    }

    @Test
    fun noSingleChildSplitsAfterAnySequence() {
        // Build a moderately deep tree then remove everything one by one, asserting the
        // structural invariants hold at every step.
        val c = ctx()
        var root: DockNode? = c.dockAtRoot(null, A, DockRegion.Center)
        root = c.dockAtRoot(root, B, DockRegion.East, 0.4f)
        root = c.dockAt(root!!, root.findLeaf(B)!!.id, C, DockRegion.South, 0.3f)
        root = c.dockAt(root!!, root.findLeaf(A)!!.id, D, DockRegion.Center)
        for (id in listOf(C, A, B, D)) {
            root?.let { assertTreeInvariants(it) }
            root = root?.let { c.undock(it, id) }
        }
        assertNull(root)
    }
}

class TabOperationsTest {

    @Test
    fun selectTabChangesSelection() {
        val c = ctx()
        val leafA = assertIs<DockNode.Leaf>(c.dockAtRoot(null, A, DockRegion.Center))
        val tabs = assertIs<DockNode.Tabs>(c.dockAt(leafA, leafA.id, B, DockRegion.Center))
        val after = assertIs<DockNode.Tabs>(selectTab(tabs, tabs.id, 0))
        assertEquals(0, after.selectedIndex)
    }

    @Test
    fun selectTabOutOfRangeIsNoOp() {
        val c = ctx()
        val leafA = assertIs<DockNode.Leaf>(c.dockAtRoot(null, A, DockRegion.Center))
        val tabs = assertIs<DockNode.Tabs>(c.dockAt(leafA, leafA.id, B, DockRegion.Center))
        assertSame(tabs, selectTab(tabs, tabs.id, 5))
    }

    @Test
    fun moveTabReordersAndFollowsSelection() {
        val c = ctx()
        val leafA = assertIs<DockNode.Leaf>(c.dockAtRoot(null, A, DockRegion.Center))
        var tabs = assertIs<DockNode.Tabs>(c.dockAt(leafA, leafA.id, B, DockRegion.Center))
        tabs = assertIs(c.dockAt(tabs, tabs.id, C, DockRegion.Center))
        val after = assertIs<DockNode.Tabs>(moveTab(tabs, tabs.id, fromIndex = 0, toIndex = 2))
        assertEquals(listOf(B, C, A), after.tabs.map { it.dockableId })
        assertEquals(2, after.selectedIndex)
        assertEquals(A, after.selectedTab.dockableId)
    }

    @Test
    fun setSplitProportionTargetsNestedSplit() {
        val c = ctx()
        var root: DockNode = c.dockAtRoot(null, A, DockRegion.Center)
        root = c.dockAtRoot(root, B, DockRegion.East, 0.5f)
        root = assertNotNull(c.dockAt(root, root.findLeaf(B)!!.id, C, DockRegion.South, 0.5f))
        val inner = assertIs<DockNode.Split>(assertIs<DockNode.Split>(root).second)
        val after = assertIs<DockNode.Split>(setSplitProportion(root, inner.id, 0.8f))
        assertEquals(0.8f, assertIs<DockNode.Split>(after.second).proportion)
        // Outer split untouched.
        assertEquals(0.5f, after.proportion)
    }
}

/** Asserts structural invariants that must hold for every tree the transforms produce. */
internal fun assertTreeInvariants(node: DockNode) {
    when (node) {
        is DockNode.Leaf, is DockNode.Anchor -> Unit
        is DockNode.Tabs -> {
            assertTrue(node.tabs.isNotEmpty(), "Tabs node must not be empty")
            assertTrue(node.selectedIndex in node.tabs.indices, "selection in range")
        }
        is DockNode.Split -> {
            assertTrue(node.proportion in 0f..1f)
            assertTreeInvariants(node.first)
            assertTreeInvariants(node.second)
        }
    }
    // No duplicate dockables anywhere in the tree.
    val ids = node.dockableIds().toList()
    assertEquals(ids.size, ids.distinct().size, "duplicate dockable in tree")
}
