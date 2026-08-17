package com.seanproctor.docking.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.seanproctor.docking.layout.dockLayout
import com.seanproctor.docking.model.AnchorId
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.DockableOptions
import com.seanproctor.docking.spi.DebugDockingRenderer
import com.seanproctor.docking.spi.DividerModel
import com.seanproctor.docking.spi.DockingRenderer
import com.seanproctor.docking.spi.LocalDockingRenderer
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockingSettings
import com.seanproctor.docking.state.EmptyAnchorVisibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val B = DockableId("b")
private val TOOLS = AnchorId("tools")

/**
 * Counts how often the divider is placed. Placement is the thing that matters here rather than the
 * size it is placed at: a renderer draws into the space it is handed without clipping to it - the
 * grip in the Jewel adapter draws a circle centred in its canvas - so a divider placed at zero
 * thickness still paints, either side of a line that is not there.
 */
private class DividerProbe : DockingRenderer by DebugDockingRenderer {
    var placements = 0

    @Composable
    override fun SplitDivider(model: DividerModel, modifier: Modifier) {
        Box(modifier.onGloballyPositioned { placements++ })
    }
}

@OptIn(ExperimentalTestApi::class)
class HiddenDividerTest {

    /** `a` fills the window; `b` holds an anchored 25% west column until it is undocked. */
    private fun uiState(visibility: EmptyAnchorVisibility): DockState =
        DockState(
            initialLayout = dockLayout {
                mainWindow {
                    dock("a")
                    dock("b", region = DockRegion.West, proportion = 0.25f)
                }
            },
            settings = DockingSettings(
                collapsedAnchorThickness = 24.dp,
                emptyAnchorVisibility = visibility,
            ),
        ) {
            dockable("a", title = { "Alpha" }) { Box(Modifier) }
            dockable("b", title = { "Beta" }, options = DockableOptions(anchor = TOOLS)) { Box(Modifier) }
        }

    // An area hidden until a drag gives its space back completely, and the divider beside it has to
    // go with it: a divider with nothing on one side is a line the user cannot explain, and cannot
    // drag either, since it is inert while pinned.
    @Test
    fun anAreaHiddenUntilADragLeavesNoDividerBehind() = runComposeUiTest {
        val renderer = DividerProbe()
        val state = uiState(EmptyAnchorVisibility.WhileDragging)
        setContent {
            CompositionLocalProvider(LocalDockingRenderer provides renderer) {
                DockArea(state, modifier = Modifier.fillMaxSize())
            }
        }
        assertTrue(renderer.placements > 0, "the divider between two live panes is placed")

        renderer.placements = 0
        state.undock(B)
        waitForIdle()
        assertEquals(0, renderer.placements, "a hidden area must not leave its divider on screen")
    }

    // The strip form keeps its divider: there is something there to see, so the line explains itself.
    @Test
    fun anAreaCollapsedToAStripKeepsItsDivider() = runComposeUiTest {
        val renderer = DividerProbe()
        val state = uiState(EmptyAnchorVisibility.Always)
        setContent {
            CompositionLocalProvider(LocalDockingRenderer provides renderer) {
                DockArea(state, modifier = Modifier.fillMaxSize())
            }
        }
        renderer.placements = 0
        state.undock(B)
        waitForIdle()
        assertTrue(renderer.placements > 0, "a strip is visible, so its divider stays")
    }
}
