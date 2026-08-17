package com.seanproctor.docking.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.WindowBounds
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget
import com.seanproctor.docking.ui.DockAreaScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val A = DockableId("a")
private val B = DockableId("b")

private fun tornOff(): Pair<DockState, WindowId> {
    val state = DockState {
        dockable("a", title = { "Alpha" }) { }
        dockable("b", title = { "Beta" }) { }
    }
    state.dock(A)
    state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
    state.moveToNewWindow(B, WindowBounds(600f, 100f, 400f, 300f))
    val floatingId = state.layout.floatingWindows.single().id
    // The gesture only drives a drag for a window the controller knows about.
    state.dragController.registerWindow(WindowId.MAIN, DockAreaScope(state, WindowId.MAIN))
    state.dragController.registerWindow(floatingId, DockAreaScope(state, floatingId))
    return state to floatingId
}

/**
 * The header gesture of an undecorated floating window. There is no AWT window behind it
 * here, so nothing moves - what is under test is the session it drives, and in particular
 * that it never leaves one open. An open session turns away every drag after it, so a leak
 * here is not a glitch in one drag but the end of dragging altogether.
 */
@OptIn(ExperimentalTestApi::class)
class WindowDragGestureTest {

    @Test
    fun draggingTheHeaderStartsAWindowDrag() = runComposeUiTest {
        val (state, floatingId) = tornOff()
        setContent {
            Box(Modifier.size(200.dp).windowDragToDock(state, floatingId, B, density = 1f)) {
                BasicText("header")
            }
        }

        onNodeWithText("header").performMouseInput {
            moveTo(center)
            press()
            repeat(6) { moveTo(center + Offset(0f, 15f * (it + 1))) }
        }
        waitForIdle()

        val session = assertNotNull(state.dragController.session, "a drag is running")
        assertTrue(session.movesWindow, "and it is carrying the window, not tearing the panel out")
        assertTrue(state.isDocked(B), "so the panel stays in the window it is riding in")
    }

    @Test
    fun releasingEndsTheSession() = runComposeUiTest {
        val (state, floatingId) = tornOff()
        setContent {
            Box(Modifier.size(200.dp).windowDragToDock(state, floatingId, B, density = 1f)) {
                BasicText("header")
            }
        }

        onNodeWithText("header").performMouseInput {
            moveTo(center)
            press()
            repeat(6) { moveTo(center + Offset(0f, 15f * (it + 1))) }
            release()
        }
        waitForIdle()

        assertNull(state.dragController.session)
    }

    // A drag interrupted by the gesture itself going away - the window closing under it, a
    // recomposition that drops the handle - never reaches a release, and would leave the
    // session open behind it. An open session refuses every drag after it, so the failure is
    // not this drag but every later one.
    @Test
    fun aGestureCancelledMidDragDoesNotLeaveTheSessionOpen() = runComposeUiTest {
        val (state, floatingId) = tornOff()
        var present by mutableStateOf(true)
        setContent {
            if (present) {
                Box(Modifier.size(200.dp).windowDragToDock(state, floatingId, B, density = 1f)) {
                    BasicText("header")
                }
            }
        }

        onNodeWithText("header").performMouseInput {
            moveTo(center)
            press()
            repeat(6) { moveTo(center + Offset(0f, 15f * (it + 1))) }
        }
        waitForIdle()
        assertNotNull(state.dragController.session, "dragging")

        present = false // the gesture leaves composition mid-drag
        waitForIdle()

        assertNull(state.dragController.session, "the drag was ended on the way out")
    }

    // Escape ends the session from outside. The gesture has to notice: left believing it is
    // still dragging it would keep moving the window on every later event, undoing the
    // restore Escape just did, and would then cancel a session that had already ended.
    @Test
    fun aSessionEndedElsewhereDoesNotStrandTheGesture() = runComposeUiTest {
        val (state, floatingId) = tornOff()
        setContent {
            Box(Modifier.size(200.dp).windowDragToDock(state, floatingId, B, density = 1f)) {
                BasicText("header")
            }
        }

        onNodeWithText("header").performMouseInput {
            moveTo(center)
            press()
            repeat(4) { moveTo(center + Offset(0f, 15f * (it + 1))) }
        }
        waitForIdle()
        assertNotNull(state.dragController.session, "dragging")

        state.dragController.cancel() // what Escape does
        onNodeWithText("header").performMouseInput {
            repeat(4) { moveTo(center + Offset(0f, 100f + 15f * (it + 1))) }
            release()
        }
        waitForIdle()

        assertNull(state.dragController.session, "no session was left open")

        // The real proof it did not leak: another drag can still start.
        onNodeWithText("header").performMouseInput {
            moveTo(center)
            press()
            repeat(6) { moveTo(center + Offset(0f, 15f * (it + 1))) }
        }
        waitForIdle()
        assertNotNull(state.dragController.session, "a later drag still works")
    }

    // A press the header's own buttons took is theirs; the window must not run off with it.
    @Test
    fun aConsumedPressDoesNotDragTheWindow() = runComposeUiTest {
        val (state, floatingId) = tornOff()
        setContent {
            Box(Modifier.size(200.dp).windowDragToDock(state, floatingId, B, density = 1f)) {
                Box(
                    Modifier.size(60.dp).pointerInput(Unit) {
                        awaitEachGesture {
                            // What a button does: takes the press, so the parent leaves it alone.
                            awaitFirstDown().consume()
                        }
                    },
                ) { BasicText("button") }
            }
        }

        onNodeWithText("button").performMouseInput {
            moveTo(center)
            press()
            repeat(6) { moveTo(center + Offset(0f, 15f * (it + 1))) }
            release()
        }
        waitForIdle()

        assertNull(state.dragController.session, "the button's press never became a window drag")
        assertEquals(floatingId, state.layout.floatingWindows.single().id, "and nothing moved")
    }
}
