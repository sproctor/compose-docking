package com.seanproctor.docking.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import com.seanproctor.docking.drag.DropTarget
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.window.DockDragGrip
import com.seanproctor.docking.window.DockingDragHandover
import com.seanproctor.docking.window.LocalDockingDragHandover
import com.seanproctor.docking.window.windowHost
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget
import com.seanproctor.docking.tree.windowContaining
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val A = DockableId("a")
private val B = DockableId("b")

/** Records every grip the dock area hands over, and adds nothing of its own. */
private class RecordingHandover : DockingDragHandover {
    val grips = linkedMapOf<Any, DockDragGrip>()

    @Composable
    override fun gripModifier(grip: DockDragGrip): Modifier {
        grips[grip.key] = grip
        return Modifier
    }
}

/**
 * Drags handed to a window system that carries them in the platform's drag-and-drop
 * session (native Wayland): the dock area's grips go to the adapter, which begins the core
 * drag, and the windows report the pointer in their own coordinates.
 */
@OptIn(ExperimentalTestApi::class)
class DragHandoverTest {
    private fun uiState(): DockState =
        DockState {
            dockable("a", title = { "Alpha" }) { BasicText("content-a") }
            dockable("b", title = { "Beta" }) { BasicText("content-b") }
        }

    private fun split(): DockState =
        uiState().apply {
            dock(A)
            dock(B, DockTarget.OnDockable(A), DockRegion.East)
        }

    @Test
    fun everyHeaderGripGoesToTheHandover() =
        runComposeUiTest {
            val state = split()
            val handover = RecordingHandover()
            setContent {
                CompositionLocalProvider(LocalDockingDragHandover provides handover) {
                    DockArea(state, modifier = Modifier.fillMaxSize())
                }
            }
            waitForIdle()

            assertTrue("header:a" in handover.grips && "header:b" in handover.grips, "grips: ${handover.grips.keys}")
            assertEquals("Beta", handover.grips.getValue("header:b").title)
        }

    @Test
    fun underAHandoverTheCoreStartsNoDragOfItsOwnButStillTakesThePress() =
        runComposeUiTest {
            val state = split()
            setContent {
                CompositionLocalProvider(LocalDockingDragHandover provides RecordingHandover()) {
                    DockArea(state, modifier = Modifier.fillMaxSize())
                }
            }

            onNodeWithText("Beta").performMouseInput {
                moveTo(center)
                press()
                repeat(8) { moveTo(center + Offset(0f, 20f * (it + 1))) }
                release()
            }
            waitForIdle()

            assertNull(state.dragController.session, "the drag is the handover's to begin")
            assertEquals(B, state.activeDockable, "the press still activates the panel")
            assertTrue(state.isDocked(B))
        }

    @Test
    fun beginStartsADragThePlatformCarries() =
        runComposeUiTest {
            val state = split()
            val handover = RecordingHandover()
            setContent {
                CompositionLocalProvider(LocalDockingDragHandover provides handover) {
                    DockArea(state, modifier = Modifier.fillMaxSize())
                }
            }
            waitForIdle()

            assertTrue(handover.grips.getValue("header:b").begin(Offset(5f, 5f)))

            val session = assertNotNull(state.dragController.session)
            assertTrue(session.carriedByPlatform)
            assertFalse(state.isDocked(B), "the panel undocks to follow the pointer, as with the core's own drag")
            assertFalse(handover.grips.getValue("header:a").begin(Offset(5f, 5f)), "one drag at a time")
        }

    @Test
    fun aDropReportedInTheWindowDocksThere() =
        runComposeUiTest {
            val state = split()
            val handover = RecordingHandover()
            setContent {
                CompositionLocalProvider(LocalDockingDragHandover provides handover) {
                    DockArea(state, modifier = Modifier.fillMaxSize())
                }
            }
            waitForIdle()
            handover.grips.getValue("header:b").begin(Offset(5f, 5f))
            waitForIdle()

            // With B undocked, A fills the window and its Center handle sits at the window's centre.
            val centre = onRoot().fetchSemanticsNode().boundsInRoot.center
            assertTrue(state.windowHost.dropInWindow(centre, WindowId.MAIN))
            assertNotNull(state.dragController.session, "the drop only records; the end applies it")
            state.windowHost.endPlatformDrag()
            waitForIdle()

            val tabs = assertIs<DockNode.Tabs>(state.layout.mainWindow.root)
            assertEquals(listOf(A, B), tabs.tabs.map { it.dockableId })
        }

    @Test
    fun aDragEndingWithNoDropTearsThePanelOff() =
        runComposeUiTest {
            val state = split()
            val handover = RecordingHandover()
            setContent {
                CompositionLocalProvider(LocalDockingDragHandover provides handover) {
                    DockArea(state, modifier = Modifier.fillMaxSize())
                }
            }
            waitForIdle()
            handover.grips.getValue("header:b").begin(Offset(5f, 5f))

            state.windowHost.endPlatformDrag()
            waitForIdle()

            assertNull(state.dragController.session)
            val window = assertNotNull(state.layout.windowContaining(B))
            assertTrue(window.id != WindowId.MAIN, "B is in a window of its own")
        }

    @Test
    fun theWindowsPointerStreamDoesNotDropAPlatformCarriedDrag() =
        runComposeUiTest {
            val state = split()
            val handover = RecordingHandover()
            setContent {
                CompositionLocalProvider(LocalDockingDragHandover provides handover) {
                    DockArea(state, modifier = Modifier.fillMaxSize())
                }
            }
            waitForIdle()
            handover.grips.getValue("header:b").begin(Offset(5f, 5f))

            onRoot().performMouseInput {
                moveTo(center)
                press()
                moveTo(center + Offset(10f, 10f))
                release()
            }
            waitForIdle()

            assertNotNull(state.dragController.session, "only the drop targets end a platform-carried drag")
        }

    @Test
    fun aTabStaysLocalInItsStripAndBeginsOutsideIt() =
        runComposeUiTest {
            val state = uiState()
            state.dock(A)
            state.dock(B, DockTarget.OnDockable(A), DockRegion.Center)
            val handover = RecordingHandover()
            setContent {
                CompositionLocalProvider(LocalDockingDragHandover provides handover) {
                    DockArea(state, modifier = Modifier.fillMaxSize())
                }
            }
            waitForIdle()
            val grip = handover.grips.getValue("tab:b")

            // Positions are the grip's own: just inside the tab, and far above the strip.
            assertTrue(grip.stayLocal(Offset(4f, 4f)), "inside its strip: reorder")
            assertFalse(grip.stayLocal(Offset(4f, -400f)), "well out of the strip: time to begin")
            grip.endLocal(released = false, position = null)
            assertNull(state.dragController.session)
        }

    /** Shows [state] under a recording handover and begins dragging B's header. */
    private fun ComposeUiTest.beginDraggingB(state: DockState) {
        val handover = RecordingHandover()
        setContent {
            CompositionLocalProvider(LocalDockingDragHandover provides handover) {
                DockArea(state, modifier = Modifier.fillMaxSize())
            }
        }
        waitForIdle()
        assertTrue(handover.grips.getValue("header:b").begin(Offset(5f, 5f)))
        waitForIdle()
    }

    private fun ComposeUiTest.centre(): Offset = onRoot().fetchSemanticsNode().boundsInRoot.center

    private fun DockState.assertTabbedWithA() {
        val tabs = assertIs<DockNode.Tabs>(layout.mainWindow.root)
        assertEquals(listOf(A, B), tabs.tabs.map { it.dockableId })
    }

    // Platforms tell the targets the pointer left as the session winds down, after the drop.
    @Test
    fun leavingAfterTheDropDoesNotTearOff() =
        runComposeUiTest {
            val state = split()
            beginDraggingB(state)

            state.windowHost.dropInWindow(centre(), WindowId.MAIN)
            state.windowHost.exitWindow(WindowId.MAIN)
            state.windowHost.endPlatformDrag()
            waitForIdle()

            state.assertTabbedWithA()
        }

    @Test
    fun leavingEveryWindowThenEndingTearsOff() =
        runComposeUiTest {
            val state = split()
            beginDraggingB(state)

            state.windowHost.hoverInWindow(centre(), WindowId.MAIN)
            state.windowHost.exitWindow(WindowId.MAIN)
            state.windowHost.endPlatformDrag()
            waitForIdle()

            val window = assertNotNull(state.layout.windowContaining(B))
            assertTrue(window.id != WindowId.MAIN, "B is in a window of its own")
        }

    // Entering one window and leaving another can arrive in either order; leaving the window
    // the pointer already moved on from must not lose the hover over the one it is in.
    @Test
    fun leavingAWindowNoLongerHoveredKeepsTheHover() =
        runComposeUiTest {
            val state = split()
            beginDraggingB(state)

            state.windowHost.hoverInWindow(centre(), WindowId.MAIN)
            state.windowHost.exitWindow(WindowId("elsewhere"))

            val session = assertNotNull(state.dragController.session)
            assertEquals(WindowId.MAIN, session.hoveredWindow)
            assertIs<DropTarget.OnDockable>(session.target, "still aimed at A")
        }

    @Test
    fun anUnknownPositionLeavesTheHoverAsItWas() =
        runComposeUiTest {
            val state = split()
            beginDraggingB(state)

            state.windowHost.hoverInWindow(centre(), WindowId.MAIN)
            state.windowHost.hoverInWindow(Offset.Unspecified, WindowId.MAIN)
            state.windowHost.dropInWindow(Offset.Unspecified, WindowId.MAIN)
            state.windowHost.endPlatformDrag()
            waitForIdle()

            state.assertTabbedWithA()
        }

    @Test
    fun aCancelledDragPutsThePanelBack() =
        runComposeUiTest {
            val state = split()
            beginDraggingB(state)

            state.windowHost.cancel()
            waitForIdle()

            assertFalse(state.windowHost.isDragging)
            assertTrue(state.isDocked(B))
            assertEquals(1, state.layout.windows.size, "no window was torn off")
        }

    @Test
    fun noDropIsTakenWithoutADockingDrag() {
        assertFalse(split().windowHost.dropInWindow(Offset(10f, 10f), WindowId.MAIN))
    }
}
