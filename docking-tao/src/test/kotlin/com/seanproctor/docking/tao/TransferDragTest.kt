@file:OptIn(com.seanproctor.docking.spi.DockingWindowHostApi::class)

package com.seanproctor.docking.tao

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.spi.DockDragGrip
import com.seanproctor.docking.spi.DockingDragHandover
import com.seanproctor.docking.spi.LocalDockingDragHandover
import com.seanproctor.docking.spi.windowHost
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget
import com.seanproctor.docking.ui.DockArea
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val A = DockableId("a")
private val B = DockableId("b")

private class RecordingHandover : DockingDragHandover {
    val grips = linkedMapOf<Any, DockDragGrip>()

    @Composable
    override fun gripModifier(grip: DockDragGrip): Modifier {
        grips[grip.key] = grip
        return Modifier
    }
}

/**
 * The native-Wayland path: a drag carried by the platform session, driven here the way
 * Nucleus's transfer drag drives it (the target's drop first, then the source's end), with
 * no compositor involved.
 */
@OptIn(ExperimentalTestApi::class)
class TransferDragTest {
    private fun split(): DockState =
        DockState {
            dockable("a", title = { "Alpha" }) { BasicText("content-a") }
            dockable("b", title = { "Beta" }) { BasicText("content-b") }
        }.apply {
            dock(A)
            dock(B, DockTarget.OnDockable(A), DockRegion.East)
        }

    /** Shows [state] under a recording handover and starts dragging B's header. */
    private fun ComposeUiTest.beginDraggingB(state: DockState): Pair<DockDragGrip, DockTransferDrag> {
        val handover = RecordingHandover()
        setContent {
            CompositionLocalProvider(LocalDockingDragHandover provides handover) {
                DockArea(state, modifier = Modifier.fillMaxSize())
            }
        }
        waitForIdle()
        val grip = handover.grips.getValue("header:b")
        val drag = DockTransferDrag(state, grip)
        assertTrue(grip.begin(Offset(5f, 5f)))
        waitForIdle()
        return grip to drag
    }

    private fun ComposeUiTest.centre(): Offset = onRoot().fetchSemanticsNode().boundsInRoot.center

    private fun DockState.assertTabbedWithA() {
        val tabs = assertIs<DockNode.Tabs>(layout.mainWindow.root)
        assertEquals(listOf(A, B), tabs.tabs.map { it.dockableId })
    }

    private fun DockState.assertTornOff() {
        val floating = layout.windows.drop(1).single()
        assertEquals(B, (floating.root as? DockNode.Leaf)?.dockableId, "B is in a window of its own")
    }

    @Test
    fun aDropIsAppliedWhenTheSessionEnds() =
        runComposeUiTest {
            val state = split()
            val (_, drag) = beginDraggingB(state)
            val target = DockTransferTarget(state, WindowId.MAIN)

            // With B undocked, A fills the window and its Center handle is at the centre.
            assertTrue(target.drop(centre()))
            assertTrue(state.windowHost.isDragging, "the drop only records; the end applies it")

            drag.end()
            waitForIdle()

            assertFalse(state.windowHost.isDragging)
            state.assertTabbedWithA()
        }

    @Test
    fun leavingAfterTheDropDoesNotTearOff() =
        runComposeUiTest {
            val state = split()
            val (_, drag) = beginDraggingB(state)
            val target = DockTransferTarget(state, WindowId.MAIN)

            target.drop(centre())
            target.exit()
            drag.end()
            waitForIdle()

            state.assertTabbedWithA()
        }

    @Test
    fun aSessionEndingWithNoDropTearsOff() =
        runComposeUiTest {
            val state = split()
            val (_, drag) = beginDraggingB(state)
            val target = DockTransferTarget(state, WindowId.MAIN)

            target.hover(centre())
            target.exit()
            drag.end()
            waitForIdle()

            assertFalse(state.windowHost.isDragging)
            state.assertTornOff()
        }

    @Test
    fun anUnknownPositionLeavesTheHoverAsItWas() =
        runComposeUiTest {
            val state = split()
            val (_, drag) = beginDraggingB(state)
            val target = DockTransferTarget(state, WindowId.MAIN)

            target.hover(centre())
            target.hover(Offset.Unspecified)
            target.drop(Offset.Unspecified)
            drag.end()
            waitForIdle()
            state.assertTabbedWithA()
        }

    @Test
    fun aCancelledSessionPutsThePanelBack() =
        runComposeUiTest {
            val state = split()
            val (_, drag) = beginDraggingB(state)

            drag.cancel()
            waitForIdle()

            assertFalse(state.windowHost.isDragging)
            assertTrue(state.isDocked(B))
            assertEquals(1, state.layout.windows.size, "no window was torn off")
        }

    @Test
    fun noDropIsTakenWithoutADockingDrag() {
        val state = split()
        assertFalse(DockTransferTarget(state, WindowId.MAIN).drop(Offset(10f, 10f)))
        assertNull(taoIntegration(state).transferDropWindow)
    }

    @Test
    fun theTitleCardIsHeaderSized() =
        runComposeUiTest {
            val state = split()
            val handover = RecordingHandover()
            setContent {
                CompositionLocalProvider(LocalDockingDragHandover provides handover) {
                    DockArea(state, modifier = Modifier.fillMaxSize())
                }
            }
            waitForIdle()
            val grip = handover.grips.getValue("header:b")
            val size = DockTransferDrag(state, grip).ghostSizePx

            assertTrue(size.width <= GhostCardSizePx.width && size.height <= GhostCardSizePx.height, "card: $size")
        }

    @Test
    fun theGestureHandsOverAtTheLastPositionItSaw() =
        runComposeUiTest {
            val state = split()
            val handover = RecordingHandover()
            setContent {
                CompositionLocalProvider(LocalDockingDragHandover provides handover) {
                    DockArea(state, modifier = Modifier.fillMaxSize())
                }
            }
            waitForIdle()
            val gesture = GripGesture(handover.grips.getValue("header:b"))

            gesture.onStart(Offset(3f, 3f))
            assertEquals(Offset(3f, 3f), gesture.lastPosition)
            // A header never stays local: past the slop, the platform session takes over.
            assertTrue(gesture.onDrag(Offset(30f, 4f)))
            assertEquals(Offset(30f, 4f), gesture.lastPosition)
        }
}
