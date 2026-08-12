package com.seanproctor.docking.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.seanproctor.docking.layout.dockLayout
import com.seanproctor.docking.model.AnchorId
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.DockableOptions
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget
import com.seanproctor.docking.state.DockingSettings
import com.seanproctor.docking.state.EmptyAnchorVisibility
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

private val A = DockableId("a")
private val B = DockableId("b")
private val TOOLS = AnchorId("tools")

private const val WINDOW = 400
private const val STRIP = 24

@OptIn(ExperimentalTestApi::class)
class CollapsedAnchorTest {

    /**
     * `a` fills the window, `b` is anchored and starts in a 25% west column. Undocking `b`
     * leaves the anchor's placeholder holding that column.
     */
    private fun uiState(
        strip: Int?,
        visibility: EmptyAnchorVisibility = EmptyAnchorVisibility.Always,
    ): DockState {
        val settings = DockingSettings(
            collapsedAnchorThickness = if (strip == null) androidx.compose.ui.unit.Dp.Unspecified else strip.dp,
            emptyAnchorVisibility = visibility,
        )
        return DockState(
            initialLayout = dockLayout {
                mainWindow {
                    dock("a")
                    dock("b", region = DockRegion.West, proportion = 0.25f)
                }
            },
            settings = settings,
        ) {
            dockable("a", title = { "Alpha" }) {
                Box(Modifier.fillMaxSize().testTag("pane-a")) { BasicText("content-a") }
            }
            dockable(
                "b",
                title = { "Beta" },
                options = DockableOptions(anchor = TOOLS),
            ) { Box(Modifier.fillMaxSize()) { BasicText("content-b") } }
        }
    }

    /** Width of the pane holding `a`, which is everything the anchor did not take. */
    private fun androidx.compose.ui.test.ComposeUiTest.contentWidth(): Float =
        onNodeWithTag("pane-a").fetchSemanticsNode().boundsInRoot.width

    @Test
    fun emptyAnchorTakesOnlyTheStrip() = runComposeUiTest {
        val state = uiState(strip = STRIP)
        setContent { DockArea(state, modifier = Modifier.size(WINDOW.dp)) }
        val withPanel = contentWidth()

        state.undock(B)
        waitForIdle()
        val withStrip = contentWidth()

        // The area is still there - undocking the last carrier leaves the placeholder - but
        // it has handed its column back, keeping only the strip and the divider.
        assertTrue(state.isOpen(A), "a should still be docked")
        assertTrue(withStrip > withPanel, "collapsing should give space back: $withPanel -> $withStrip")
        val expected = WINDOW - STRIP - DividerThickness.value
        assertTrue(
            abs(withStrip - expected) <= 1f,
            "expected the neighbour to take all but the strip ($expected), was $withStrip",
        )
    }

    @Test
    fun anchorKeepsItsFullSlotWhenCollapsingIsOff() = runComposeUiTest {
        val state = uiState(strip = null)
        setContent { DockArea(state, modifier = Modifier.size(WINDOW.dp)) }
        val withPanel = contentWidth()

        state.undock(B)
        waitForIdle()

        // The default is unchanged from before the feature: the placeholder holds the whole
        // 25% column, so its neighbour is exactly as wide as it was with the panel in it.
        assertTrue(
            abs(contentWidth() - withPanel) <= 1f,
            "an uncollapsed anchor should keep its slot: $withPanel -> ${contentWidth()}",
        )
    }

    @Test
    fun fillingAStrippedAnchorRestoresItsSlot() = runComposeUiTest {
        val state = uiState(strip = STRIP)
        setContent { DockArea(state, modifier = Modifier.size(WINDOW.dp)) }
        val withPanel = contentWidth()

        state.undock(B)
        waitForIdle()
        state.dock(B, com.seanproctor.docking.state.DockTarget.Anchor(TOOLS))
        waitForIdle()

        // Collapsing never touched the split's proportion, so the area reopens the size it was.
        assertTrue(
            abs(contentWidth() - withPanel) <= 1f,
            "refilling should restore the column: $withPanel -> ${contentWidth()}",
        )
        onNodeWithText("content-b").assertExists()
    }

    @Test
    fun aDragOnlyAnchorCostsNothingWhileIdle() = runComposeUiTest {
        val state = uiState(strip = STRIP, visibility = EmptyAnchorVisibility.WhileDragging)
        setContent { DockArea(state, modifier = Modifier.size(WINDOW.dp)) }

        state.undock(B)
        waitForIdle()

        // Not even the divider is left behind: with nothing being dragged, an empty area
        // takes no space at all and its neighbour has the whole split.
        assertTrue(
            abs(contentWidth() - WINDOW) <= 1f,
            "a hidden area should cost nothing, neighbour was ${contentWidth()} of $WINDOW",
        )
    }

    @Test
    fun aDragOnlyAnchorComesBackForTheDrag() = runComposeUiTest {
        val state = uiState(strip = STRIP, visibility = EmptyAnchorVisibility.WhileDragging)
        setContent { DockArea(state, modifier = Modifier.size(WINDOW.dp)) }
        state.undock(B)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.South)
        waitForIdle()

        // Pick "b" up by its header and hold the drag partway, without releasing: "a" is
        // the only anchored dockable's neighbour, so the left area is empty for the
        // duration and its strip has to be there to be dropped on.
        var widthMidDrag = -1f
        onNodeWithText("Beta").performMouseInput {
            moveTo(center)
            press()
            for (i in 1..6) moveTo(center + Offset(0f, -12f * i))
        }
        waitForIdle()
        widthMidDrag = contentWidth()
        onNodeWithText("Beta").performMouseInput { release() }
        waitForIdle()

        val expected = WINDOW - STRIP - DividerThickness.value
        assertTrue(
            abs(widthMidDrag - expected) <= 1f,
            "the area should reappear as a strip for the drag (expected ~$expected, was $widthMidDrag)",
        )
    }

    @Test
    fun aStripNeverCrowdsOutItsNeighbour() = runComposeUiTest {
        // A strip thicker than the window would otherwise leave nothing for the content.
        val state = uiState(strip = WINDOW * 2)
        setContent { DockArea(state, modifier = Modifier.size(WINDOW.dp)) }
        state.undock(B)
        waitForIdle()
        assertTrue(contentWidth() >= (WINDOW - DividerThickness.value) / 2f - 1f, "was ${contentWidth()}")
    }
}
