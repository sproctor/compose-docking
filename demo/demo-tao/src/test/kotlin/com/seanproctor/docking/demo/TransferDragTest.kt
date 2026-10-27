package com.seanproctor.docking.demo

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget
import com.seanproctor.docking.ui.DockArea
import com.seanproctor.docking.window.DockDragGrip
import com.seanproctor.docking.window.DockingDragHandover
import com.seanproctor.docking.window.LocalDockingDragHandover
import kotlin.test.Test
import kotlin.test.assertEquals
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
 * The demo's side of a drag the platform carries on native Wayland: what the grip hands to
 * Nucleus's transfer drag. The protocol after that (drop, end, cancel) is the library's, and
 * tested there.
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

    private fun ComposeUiTest.gripOfB(state: DockState): DockDragGrip {
        val handover = RecordingHandover()
        setContent {
            CompositionLocalProvider(LocalDockingDragHandover provides handover) {
                DockArea(state, modifier = Modifier.fillMaxSize())
            }
        }
        waitForIdle()
        return handover.grips.getValue("header:b")
    }

    // A card the size of the whole panel would hide the drop zones it is aimed at.
    @Test
    fun theTitleCardIsHeaderSized() =
        runComposeUiTest {
            val state = split()
            val size = DockTransferDrag(state, gripOfB(state)).ghostSizePx

            assertTrue(size.width <= GhostCardSizePx.width && size.height <= GhostCardSizePx.height, "card: $size")
        }

    @Test
    fun theGestureHandsOverAtTheLastPositionItSaw() =
        runComposeUiTest {
            val gesture = GripGesture(gripOfB(split()))

            gesture.onStart(Offset(3f, 3f))
            assertEquals(Offset(3f, 3f), gesture.lastPosition)
            // A header never stays local: past the slop, the platform session takes over.
            assertTrue(gesture.onDrag(Offset(30f, 4f)))
            assertEquals(Offset(30f, 4f), gesture.lastPosition)
        }
}
