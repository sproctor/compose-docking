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
private val C = DockableId("c")
private val TOOLS = AnchorId("tools")
private val PROPS = AnchorId("props")

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

    /**
     * `a` fills the east. `b` and `c` carry *different* anchors and share the 25% west
     * column between them, so undocking both leaves a split of two placeholders - a
     * subtree that is empty without any single node of it being the empty area.
     */
    private fun siblingAnchorState(
        strip: Int,
        visibility: EmptyAnchorVisibility = EmptyAnchorVisibility.Always,
    ): DockState = DockState(
        initialLayout = dockLayout {
            mainWindow {
                dock("a")
                dock("b", region = DockRegion.West, proportion = 0.25f)
                dock("c", target = "b", region = DockRegion.South, proportion = 0.5f)
            }
        },
        settings = DockingSettings(
            collapsedAnchorThickness = strip.dp,
            emptyAnchorVisibility = visibility,
        ),
    ) {
        dockable("a", title = { "Alpha" }) {
            Box(Modifier.fillMaxSize().testTag("pane-a")) { BasicText("content-a") }
        }
        dockable("b", title = { "Beta" }, options = DockableOptions(anchor = TOOLS)) {
            Box(Modifier.fillMaxSize()) { BasicText("content-b") }
        }
        dockable("c", title = { "Gamma" }, options = DockableOptions(anchor = PROPS)) {
            Box(Modifier.fillMaxSize()) { BasicText("content-c") }
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
    fun twoEmptySiblingAnchorsCostOneStripBetweenThem() = runComposeUiTest {
        val state = siblingAnchorState(strip = STRIP)
        setContent { DockArea(state, modifier = Modifier.size(WINDOW.dp)) }

        state.undock(B)
        state.undock(C)
        waitForIdle()

        // Both halves of the west column are placeholders now, so the column as a whole is
        // the empty area and the outer split is what collapses it. Pinning inside the column
        // instead would give one placeholder the strip and the other everything left over -
        // exactly the oversized empty pane this is all meant to get rid of.
        val expected = WINDOW - STRIP - DividerThickness.value
        assertTrue(
            abs(contentWidth() - expected) <= 1f,
            "a split of two empty anchors should cost one strip (~$expected), was ${contentWidth()}",
        )
    }

    @Test
    fun aHiddenSiblingPairLeavesNoDividersBehind() = runComposeUiTest {
        val state = siblingAnchorState(strip = STRIP, visibility = EmptyAnchorVisibility.WhileDragging)
        setContent { DockArea(state, modifier = Modifier.size(WINDOW.dp)) }

        state.undock(B)
        state.undock(C)
        waitForIdle()

        // Neither the column's own divider nor the one splitting the two placeholders inside
        // it may survive: a hidden area costs nothing, however deep it goes.
        assertTrue(
            abs(contentWidth() - WINDOW) <= 1f,
            "a hidden subtree should cost nothing, neighbour was ${contentWidth()} of $WINDOW",
        )
    }

    @Test
    fun aRefilledSiblingAnchorTakesItsColumnBack() = runComposeUiTest {
        val state = siblingAnchorState(strip = STRIP)
        setContent { DockArea(state, modifier = Modifier.size(WINDOW.dp)) }
        val withPanels = contentWidth()

        state.undock(B)
        state.undock(C)
        waitForIdle()
        // The collapsed column is still a live area: its placeholders were kept, so docking
        // into one of them reopens the column at the proportion it always had.
        state.dock(B, DockTarget.Anchor(TOOLS))
        waitForIdle()

        assertTrue(
            abs(contentWidth() - withPanels) <= 1f,
            "refilling should restore the column: $withPanels -> ${contentWidth()}",
        )
        onNodeWithText("content-b").assertExists()
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
