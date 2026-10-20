package com.seanproctor.docking.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.DockableOptions
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget
import com.seanproctor.docking.state.DockingSettings
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val A = DockableId("a")
private val B = DockableId("b")

/** Panes side by side, [A] west of [B], with the given minimums. */
private fun sideBySide(
    aMin: DpSize = DpSize.Unspecified,
    bMin: DpSize = DpSize.Unspecified,
    proportion: Float = 0.5f,
    settings: DockingSettings = DockingSettings(),
): DockState {
    val state = DockState(settings = settings) {
        dockable("a", title = { "Alpha" }, options = DockableOptions(minSize = aMin)) {
            Box(Modifier.fillMaxSize().testTag("pane-a"))
        }
        dockable("b", title = { "Beta" }, options = DockableOptions(minSize = bMin)) {
            Box(Modifier.fillMaxSize().testTag("pane-b"))
        }
    }
    state.dock(A)
    state.dock(B, DockTarget.OnDockable(A), DockRegion.East, proportion = 1f - proportion)
    return state
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.widthOf(tag: String): Float =
    onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.width

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.px(dp: Dp): Float = with(density) { dp.toPx() }

/** Drags the divider just east of pane A by [dx] pixels, in small steps. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.dragDivider(dx: Float) {
    val pane = onNodeWithTag("pane-a").fetchSemanticsNode().boundsInRoot
    val start = Offset(pane.right + px(DividerThickness) / 2, pane.center.y)
    onRoot().performMouseInput {
        moveTo(start)
        press()
        val steps = 20
        for (i in 1..steps) moveTo(start + Offset(dx * i / steps, 0f))
        release()
    }
    waitForIdle()
}

private fun assertNear(expected: Float, actual: Float, message: String) {
    assertTrue(abs(expected - actual) <= 1f, "$message: expected $expected, was $actual")
}

@OptIn(ExperimentalTestApi::class)
class MinPaneSizeTest {

    @Test
    fun aDividerStopsAtTheDockablesMinimum() = runComposeUiTest {
        val state = sideBySide(aMin = DpSize(150.dp, Dp.Unspecified))
        setContent { DockArea(state, modifier = Modifier.size(400.dp)) }

        dragDivider(-px(400.dp))

        assertNear(px(150.dp), widthOf("pane-a"), "pane A was dragged past its minimum")
    }

    @Test
    fun theStoredProportionStaysWithinTheMinimumAfterTheDrag() = runComposeUiTest {
        val state = sideBySide(bMin = DpSize(100.dp, Dp.Unspecified))
        setContent { DockArea(state, modifier = Modifier.size(400.dp)) }

        dragDivider(px(400.dp))

        assertNear(px(100.dp), widthOf("pane-b"), "pane B was dragged past its minimum")
        val split = state.layout.window(WindowId.MAIN)!!.root as DockNode.Split
        val available = px(400.dp) - px(DividerThickness)
        assertNear(available - px(100.dp), split.proportion * available, "committed proportion")
    }

    @Test
    fun withoutOwnMinimumsPanesStopAtTheSettingsDefault() = runComposeUiTest {
        val state = sideBySide(settings = DockingSettings(minPaneSize = DpSize(64.dp, 64.dp)))
        setContent { DockArea(state, modifier = Modifier.size(400.dp)) }

        dragDivider(-px(400.dp))

        assertNear(px(64.dp), widthOf("pane-a"), "pane A ignored the default minimum")
    }

    @Test
    fun aProportionTheMinimumOverridesIsDrawnAtTheMinimumAndDragsFromThere() = runComposeUiTest {
        // Stored at 10% of 400dp, but A needs 150dp: drawn at 150dp, and a drag moves it
        // from there straight away rather than first catching up with the stored 10%.
        val state = sideBySide(aMin = DpSize(150.dp, Dp.Unspecified), proportion = 0.1f)
        setContent { DockArea(state, modifier = Modifier.size(400.dp)) }
        assertNear(px(150.dp), widthOf("pane-a"), "the minimum should override the proportion")

        dragDivider(px(20.dp))

        assertNear(px(170.dp), widthOf("pane-a"), "the drag should start where the divider is drawn")
    }

    @Test
    fun aWindowTooSmallForBothMinimumsSharesItInTheirRatio() = runComposeUiTest {
        val state = sideBySide(
            aMin = DpSize(300.dp, Dp.Unspecified),
            bMin = DpSize(100.dp, Dp.Unspecified),
            proportion = 0.1f,
        )
        val stored = (state.layout.window(WindowId.MAIN)!!.root as DockNode.Split).proportion
        var width by mutableStateOf(600.dp)
        setContent { DockArea(state, modifier = Modifier.size(width, 400.dp)) }
        assertNear(px(300.dp), widthOf("pane-a"), "with room for both, A gets exactly its minimum")

        width = 208.dp
        waitForIdle()

        // 200dp left beside the divider for 400dp of minimums: each side gets half.
        assertNear(px(150.dp), widthOf("pane-a"), "pane A's share")
        assertNear(px(50.dp), widthOf("pane-b"), "pane B's share")
        val split = state.layout.window(WindowId.MAIN)!!.root as DockNode.Split
        assertEquals(stored, split.proportion, "squeezing must not rewrite the stored proportion")
    }
}
