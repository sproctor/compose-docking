package com.seanproctor.docking.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.seanproctor.docking.model.AnchorId
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.DockableOptions
import com.seanproctor.docking.model.NodeId
import com.seanproctor.docking.model.SplitOrientation
import kotlin.test.Test
import kotlin.test.assertEquals

private val A = DockableId("a")
private val B = DockableId("b")
private val C = DockableId("c")

private fun leaf(id: DockableId) = DockNode.Leaf(NodeId("leaf-${id.value}"), id)

class PaneMinimumsTest {

    private val mins = mapOf(
        A to DpSize(100.dp, 50.dp),
        B to DpSize(200.dp, 30.dp),
        C to DpSize(40.dp, 80.dp),
    )

    private fun DockNode.min(emptyMin: DpSize = DpSize(10.dp, 10.dp)) =
        minSize(paneMin = { mins.getValue(it) }, emptyMin = emptyMin, divider = 8.dp)

    @Test
    fun leafIsItsDockablesMinimum() {
        assertEquals(DpSize(100.dp, 50.dp), leaf(A).min())
    }

    @Test
    fun tabGroupNeedsRoomForItsLargestTabOnEachAxis() {
        val tabs = DockNode.Tabs(NodeId("t"), listOf(leaf(A), leaf(C)), selectedIndex = 0)
        assertEquals(DpSize(100.dp, 80.dp), tabs.min())
    }

    @Test
    fun splitAddsAlongItsAxisAndTakesTheLargerAcrossIt() {
        val sideBySide = DockNode.Split(NodeId("s"), SplitOrientation.Horizontal, leaf(A), leaf(B))
        assertEquals(DpSize(308.dp, 50.dp), sideBySide.min())
        val stacked = DockNode.Split(NodeId("s"), SplitOrientation.Vertical, leaf(A), leaf(B))
        assertEquals(DpSize(200.dp, 88.dp), stacked.min())
    }

    @Test
    fun nestedSplitsCompose() {
        val inner = DockNode.Split(NodeId("in"), SplitOrientation.Vertical, leaf(A), leaf(C))
        val outer = DockNode.Split(NodeId("out"), SplitOrientation.Horizontal, inner, leaf(B))
        // inner = (100, 50 + 8 + 80); outer = (100 + 8 + 200, max(138, 30)).
        assertEquals(DpSize(308.dp, 138.dp), outer.min())
    }

    @Test
    fun oneWalkRecordsEveryNodesMinimum() {
        val inner = DockNode.Split(NodeId("in"), SplitOrientation.Vertical, leaf(A), leaf(C))
        val outer = DockNode.Split(NodeId("out"), SplitOrientation.Horizontal, inner, leaf(B))
        val sizes = outer.minSizes(paneMin = { mins.getValue(it) }, emptyMin = DpSize.Zero, divider = 8.dp)
        assertEquals(DpSize(308.dp, 138.dp), sizes[NodeId("out")])
        assertEquals(DpSize(100.dp, 138.dp), sizes[NodeId("in")])
        assertEquals(DpSize(200.dp, 30.dp), sizes[NodeId("leaf-b")])
    }

    @Test
    fun emptyAreasTakeTheEmptyMinimumWholeRatherThanSumming() {
        val anchor = DockNode.Anchor(NodeId("x"), AnchorId("x"))
        val emptySplit = DockNode.Split(
            NodeId("e"),
            SplitOrientation.Horizontal,
            anchor,
            DockNode.Anchor(NodeId("y"), AnchorId("y")),
        )
        assertEquals(DpSize(10.dp, 10.dp), emptySplit.min())
        // Collapsed to a strip, an empty area asks nothing of its neighbour.
        val withLeaf = DockNode.Split(NodeId("s"), SplitOrientation.Horizontal, anchor, leaf(A))
        assertEquals(DpSize(108.dp, 50.dp), withLeaf.min(emptyMin = DpSize.Zero))
    }

    @Test
    fun unspecifiedAxesFallBackToTheDefaultOneAtATime() {
        val default = DpSize(48.dp, 48.dp)
        assertEquals(default, DockableOptions().resolvedMinSize(default))
        assertEquals(
            DpSize(300.dp, 48.dp),
            DockableOptions(minSize = DpSize(300.dp, Dp.Unspecified)).resolvedMinSize(default),
        )
        assertEquals(DpSize.Zero, DockableOptions(minSize = DpSize.Zero).resolvedMinSize(default))
    }
}

class ClampProportionTest {

    @Test
    fun proportionInsideTheMinimumsIsUntouched() {
        assertEquals(0.5f, clampProportion(0.5f, available = 1000f, firstMin = 100f, secondMin = 100f))
    }

    @Test
    fun proportionIsPushedOutOfEitherMinimum() {
        assertEquals(0.2f, clampProportion(0.05f, available = 1000f, firstMin = 200f, secondMin = 100f))
        assertEquals(0.9f, clampProportion(0.99f, available = 1000f, firstMin = 200f, secondMin = 100f))
    }

    @Test
    fun minimumsThatDoNotFitShareTheSpaceInTheirRatio() {
        // 300 + 100 of minimums in 200: both get half of what they asked for, whatever the
        // proportion says.
        assertEquals(0.75f, clampProportion(0.1f, available = 200f, firstMin = 300f, secondMin = 100f))
        assertEquals(0.75f, clampProportion(0.9f, available = 200f, firstMin = 300f, secondMin = 100f))
    }

    @Test
    fun noMinimumsLeavesAnyProportion() {
        assertEquals(0f, clampProportion(0f, available = 1000f, firstMin = 0f, secondMin = 0f))
        assertEquals(1f, clampProportion(1f, available = 1000f, firstMin = 0f, secondMin = 0f))
    }

    @Test
    fun nothingToShareKeepsTheProportion() {
        assertEquals(0.3f, clampProportion(0.3f, available = 0f, firstMin = 100f, secondMin = 100f))
    }
}
