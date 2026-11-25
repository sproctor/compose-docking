package com.seanproctor.docking.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget
import com.seanproctor.docking.state.DockingSettings
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val A = DockableId("a")
private val B = DockableId("b")
private val C = DockableId("c")

/** The settings Android and iOS default to: a 24dp gap with a 48dp grab area. */
private val touchSettings = DockingSettings(dividerThickness = 24.dp, dividerTouchTarget = 48.dp)

private fun sideBySide(settings: DockingSettings): DockState {
    val state = DockState(settings = settings) {
        dockable("a", title = { "Alpha" }) { Box(Modifier.fillMaxSize().testTag("pane-a")) }
        dockable("b", title = { "Beta" }) { Box(Modifier.fillMaxSize().testTag("pane-b")) }
    }
    state.dock(A)
    state.dock(B, DockTarget.OnDockable(A), DockRegion.East, proportion = 0.5f)
    return state
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.px(dp: Dp): Float = with(density) { dp.toPx() }

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.widthOf(tag: String): Float =
    onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.width

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.touchDrag(start: Offset, by: Offset, onMidDrag: () -> Unit = {}) {
    onRoot().performTouchInput {
        down(start)
        val steps = 20
        for (i in 1..steps) moveTo(start + by * (i.toFloat() / steps))
    }
    waitForIdle()
    onMidDrag()
    onRoot().performTouchInput { up() }
    waitForIdle()
}

/**
 * The divider follows the pointer once it passes touch slop, so a drag moves it by less than
 * [by] - by up to one more of [touchDrag]'s steps, depending on where in a step slop is crossed.
 */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.movedBy(by: Float): Float = by - onRoot().fetchSemanticsNode().layoutInfo.viewConfiguration.touchSlop

private fun assertNear(expected: Float, actual: Float, message: String, tolerance: Float = 1f) {
    assertTrue(abs(expected - actual) <= tolerance, "$message: expected $expected, was $actual")
}

@OptIn(ExperimentalTestApi::class)
class DividerTouchTargetTest {

    @Test
    fun theGapBetweenPanesIsTheDividerThickness() = runComposeUiTest {
        val state = sideBySide(touchSettings)
        setContent { DockArea(state, modifier = Modifier.size(400.dp)) }

        assertNear(px(400.dp) - px(24.dp), widthOf("pane-a") + widthOf("pane-b"), "panes plus gap")
    }

    @Test
    fun aPressOverThePaneEdgeWithinTheTouchTargetDragsTheDivider() = runComposeUiTest {
        val state = sideBySide(touchSettings)
        setContent { DockArea(state, modifier = Modifier.size(400.dp)) }
        val pane = onNodeWithTag("pane-a").fetchSemanticsNode().boundsInRoot
        val before = pane.width

        // 8dp inside pane A: outside the 24dp gap, inside the 48dp grab area.
        touchDrag(Offset(pane.right - px(8.dp), pane.center.y), Offset(px(40.dp), 0f))

        assertNear(before + movedBy(px(40.dp)), widthOf("pane-a"), "pane A after dragging its edge", tolerance = px(40.dp) / 20 + 1f)
    }

    @Test
    fun theTouchTargetTakesPressesBeforeTheHeaderBesideIt() = runComposeUiTest {
        val state = sideBySide(touchSettings)
        setContent { DockArea(state, modifier = Modifier.size(400.dp)) }
        val pane = onNodeWithTag("pane-a").fetchSemanticsNode().boundsInRoot
        val header = onNodeWithText("Alpha").fetchSemanticsNode().boundsInRoot
        val before = pane.width

        touchDrag(
            Offset(pane.right - px(8.dp), header.center.y),
            Offset(px(40.dp), 0f),
            onMidDrag = { assertNull(state.dragController.session, "the header started a dock drag") },
        )

        assertNear(before + movedBy(px(40.dp)), widthOf("pane-a"), "pane A after dragging beside its header", tolerance = px(40.dp) / 20 + 1f)
        val split = assertIs<DockNode.Split>(state.layout.mainWindow.root)
        assertEquals(A, assertIs<DockNode.Leaf>(split.first).dockableId)
        assertEquals(B, assertIs<DockNode.Leaf>(split.second).dockableId)
    }

    @Test
    fun withoutATouchTargetThePaneKeepsItsEdge() = runComposeUiTest {
        val state = sideBySide(DockingSettings(dividerThickness = 8.dp, dividerTouchTarget = 8.dp))
        setContent { DockArea(state, modifier = Modifier.size(400.dp)) }
        val pane = onNodeWithTag("pane-a").fetchSemanticsNode().boundsInRoot
        val before = pane.width

        touchDrag(Offset(pane.right - px(8.dp), pane.center.y), Offset(px(40.dp), 0f))

        assertNear(before, widthOf("pane-a"), "pane A after a drag on its content")
    }

    @Test
    fun aTapOverThePaneEdgeReachesThePane() = runComposeUiTest {
        var taps = 0
        val state = DockState(settings = touchSettings) {
            dockable("a", title = { "Alpha" }) {
                Box(Modifier.fillMaxSize().testTag("pane-a").clickable { taps++ })
            }
            dockable("b", title = { "Beta" }) { Box(Modifier.fillMaxSize().testTag("pane-b")) }
        }
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East, proportion = 0.5f)
        setContent { DockArea(state, modifier = Modifier.size(400.dp)) }
        val pane = onNodeWithTag("pane-a").fetchSemanticsNode().boundsInRoot
        val before = pane.width

        // Inside the grab area, but a tap is not a drag: the pane's own click handler gets it.
        onRoot().performTouchInput { click(Offset(pane.right - px(8.dp), pane.center.y)) }
        waitForIdle()

        assertEquals(1, taps, "taps reaching pane A")
        assertNear(before, widthOf("pane-a"), "pane A after a tap")
    }

    @Test
    fun nestedSplitsEachGrabTheirOwnDivider() = runComposeUiTest {
        val state = DockState(settings = touchSettings) {
            dockable("a", title = { "Alpha" }) { Box(Modifier.fillMaxSize().testTag("pane-a")) }
            dockable("b", title = { "Beta" }) { Box(Modifier.fillMaxSize().testTag("pane-b")) }
            dockable("c", title = { "Gamma" }) { Box(Modifier.fillMaxSize().testTag("pane-c")) }
        }
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East, proportion = 0.5f)
        state.dock(C, DockTarget.OnDockable(B), DockRegion.South, proportion = 0.5f)
        setContent { DockArea(state, modifier = Modifier.size(400.dp)) }
        val b = onNodeWithTag("pane-b").fetchSemanticsNode().boundsInRoot
        val aWidth = widthOf("pane-a")

        // Just above the gap between B and C, well clear of the A|B divider.
        touchDrag(Offset(b.center.x, b.bottom - px(8.dp)), Offset(0f, px(40.dp)))

        val after = onNodeWithTag("pane-b").fetchSemanticsNode().boundsInRoot
        assertNear(b.height + movedBy(px(40.dp)), after.height, "pane B after dragging the inner divider", tolerance = px(40.dp) / 20 + 1f)
        assertNear(aWidth, widthOf("pane-a"), "pane A while the inner divider moved")
    }
}

class DockableNearTest {
    private val registry = DockBoundsRegistry().apply {
        updateDockable(A, Rect(0f, 0f, 100f, 100f))
        updateDockable(B, Rect(124f, 0f, 224f, 100f))
    }

    @Test
    fun aPointInTheGapResolvesToTheNearerPane() {
        assertEquals(A, registry.dockableNear(Offset(105f, 50f), slack = 24f))
        assertEquals(B, registry.dockableNear(Offset(120f, 50f), slack = 24f))
    }

    @Test
    fun aPointFartherThanTheSlackResolvesToNothing() {
        assertNull(registry.dockableNear(Offset(50f, 140f), slack = 24f))
    }
}
