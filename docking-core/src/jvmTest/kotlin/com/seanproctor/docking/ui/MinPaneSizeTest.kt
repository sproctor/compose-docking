package com.seanproctor.docking.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.seanproctor.docking.layout.dockLayout
import com.seanproctor.docking.model.AnchorId
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.DockableOptions
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.spi.DebugDockingRenderer
import com.seanproctor.docking.spi.DockingRenderer
import com.seanproctor.docking.spi.DropOverlayModel
import com.seanproctor.docking.spi.LocalDockingRenderer
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget
import com.seanproctor.docking.state.DockingSettings
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val A = DockableId("a")
private val B = DockableId("b")
private val C = DockableId("c")

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

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.heightOf(tag: String): Float =
    onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.height

/** Drags the divider just east of pane A by [dx] pixels, in small steps. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.dragDivider(dx: Float) {
    val pane = onNodeWithTag("pane-a").fetchSemanticsNode().boundsInRoot
    dragFrom(Offset(pane.right + px(DividerThickness) / 2, pane.center.y), Offset(dx, 0f))
}

/** Drags the divider just south of pane A by [dy] pixels, in small steps. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.dragDividerDown(dy: Float) {
    val pane = onNodeWithTag("pane-a").fetchSemanticsNode().boundsInRoot
    dragFrom(Offset(pane.center.x, pane.bottom + px(DividerThickness) / 2), Offset(0f, dy))
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.dragFrom(start: Offset, by: Offset) {
    onRoot().performMouseInput {
        moveTo(start)
        press()
        val steps = 20
        for (i in 1..steps) moveTo(start + by * (i.toFloat() / steps))
        release()
    }
    waitForIdle()
}

/** The debug renderer, with its drop preview tagged so a test can measure it. */
private object TaggedOverlayRenderer : DockingRenderer by DebugDockingRenderer {
    @androidx.compose.runtime.Composable
    override fun DropOverlay(model: DropOverlayModel, modifier: Modifier) {
        DebugDockingRenderer.DropOverlay(model, modifier.testTag("drop-overlay"))
    }
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

    @Test
    fun aDragWhileTooSmallForBothMinimumsKeepsTheStoredProportion() = runComposeUiTest {
        val state = sideBySide(
            aMin = DpSize(300.dp, Dp.Unspecified),
            bMin = DpSize(100.dp, Dp.Unspecified),
            proportion = 0.1f,
        )
        val stored = (state.layout.window(WindowId.MAIN)!!.root as DockNode.Split).proportion
        setContent { DockArea(state, modifier = Modifier.size(208.dp, 400.dp)) }

        // The divider is pinned at the minimums' ratio, so the drag cannot move it - and
        // releasing it must not save that ratio over the user's 10%.
        dragDivider(px(40.dp))

        assertNear(px(150.dp), widthOf("pane-a"), "the squeezed divider should not move")
        val split = state.layout.window(WindowId.MAIN)!!.root as DockNode.Split
        assertEquals(stored, split.proportion, "a squeezed drag must not rewrite the stored proportion")
    }

    @Test
    fun aStackedDividerStopsAtTheHeightMinimum() = runComposeUiTest {
        val state = DockState {
            dockable("a", title = { "Alpha" }, options = DockableOptions(minSize = DpSize(Dp.Unspecified, 150.dp))) {
                Box(Modifier.fillMaxSize().testTag("pane-a"))
            }
            dockable("b", title = { "Beta" }) { Box(Modifier.fillMaxSize().testTag("pane-b")) }
        }
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.South, proportion = 0.5f)
        setContent { DockArea(state, modifier = Modifier.size(400.dp).testTag("area")) }

        dragDividerDown(-px(400.dp))

        // The minimum is the whole pane's, header included: the pane runs from the top of the
        // window to the bottom of A's content.
        val paneHeight = onNodeWithTag("pane-a").fetchSemanticsNode().boundsInRoot.bottom -
            onNodeWithTag("area").fetchSemanticsNode().boundsInRoot.top
        assertNear(px(150.dp), paneHeight, "pane A was dragged past its height minimum")
    }

    @Test
    fun aTabGroupStopsAtItsLargestTabsMinimumWhicheverIsSelected() = runComposeUiTest {
        val state = DockState {
            dockable("a", title = { "Alpha" }) { Box(Modifier.fillMaxSize().testTag("pane-a")) }
            dockable("c", title = { "Gamma" }, options = DockableOptions(minSize = DpSize(200.dp, Dp.Unspecified))) {
                Box(Modifier.fillMaxSize().testTag("pane-c"))
            }
            dockable("b", title = { "Beta" }) { Box(Modifier.fillMaxSize().testTag("pane-b")) }
        }
        state.dock(A)
        state.dock(C, DockTarget.OnDockable(A), DockRegion.Center)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East, proportion = 0.5f)
        val tabs = (state.layout.window(WindowId.MAIN)!!.root as DockNode.Split).first as DockNode.Tabs
        state.selectTab(tabs.id, 0)
        setContent { DockArea(state, modifier = Modifier.size(400.dp)) }

        dragDivider(-px(400.dp))

        assertNear(px(200.dp), widthOf("pane-a"), "the group should keep Gamma's minimum while Alpha is selected")
    }

    @Test
    fun aCollapsedStripBesideAPaneStillLeavesThePaneItsMinimum() = runComposeUiTest {
        // a | (tools strip | b): the outer divider has to leave room for the strip as well as
        // for b, or the strip takes its thickness out of b's minimum.
        val tools = AnchorId("tools")
        val state = DockState(
            initialLayout = dockLayout {
                mainWindow {
                    dock("a")
                    dock("b", target = "a", region = DockRegion.East, proportion = 0.5f)
                    dock("c", target = "b", region = DockRegion.West, proportion = 0.5f)
                }
            },
            settings = DockingSettings(collapsedAnchorThickness = 24.dp),
        ) {
            dockable("a", title = { "Alpha" }) { Box(Modifier.fillMaxSize().testTag("pane-a")) }
            dockable("b", title = { "Beta" }, options = DockableOptions(minSize = DpSize(200.dp, Dp.Unspecified))) {
                Box(Modifier.fillMaxSize().testTag("pane-b"))
            }
            dockable("c", title = { "Gamma" }, options = DockableOptions(anchor = tools)) {
                Box(Modifier.fillMaxSize())
            }
        }
        state.undock(C)
        setContent { DockArea(state, modifier = Modifier.size(400.dp)) }

        dragDivider(px(400.dp))

        assertNear(px(200.dp), widthOf("pane-b"), "the strip ate into pane B's minimum")
    }

    @Test
    fun theDropPreviewShowsTheShareTheMinimumsWillGive() = runComposeUiTest {
        val state = sideBySide(bMin = DpSize(300.dp, Dp.Unspecified))
        setContent {
            CompositionLocalProvider(LocalDockingRenderer provides TaggedOverlayRenderer) {
                DockArea(state, modifier = Modifier.size(400.dp).testTag("area"))
            }
        }
        val area = onNodeWithTag("area").fetchSemanticsNode().boundsInRoot
        val source = onNodeWithText("Beta").fetchSemanticsNode().boundsInRoot
        // Once B undocks, A fills the window; a point by the east edge targets its East region.
        val target = Offset(area.right - 2f, area.center.y)
        var previewWidth = 0f
        onNodeWithText("Beta").performMouseInput {
            moveTo(center)
            press()
            val steps = 8
            for (i in 1..steps) moveTo(center + (target - source.topLeft - center) * (i.toFloat() / steps))
        }
        waitForIdle()
        previewWidth = widthOf("drop-overlay")
        onRoot().performMouseInput { release() }
        waitForIdle()

        // B docks east with half the window asked for, but needs 300dp of it.
        assertNear(px(300.dp), previewWidth, "the preview should show B at its minimum")
        assertNear(px(300.dp), widthOf("pane-b"), "the drop should give B what the preview showed")
    }
}
