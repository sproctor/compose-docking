package com.seanproctor.docking.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.WindowBounds
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.spi.DebugDockingRenderer
import com.seanproctor.docking.spi.DockingRenderer
import com.seanproctor.docking.spi.HeaderModel
import com.seanproctor.docking.spi.LocalDockingRenderer
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val A = DockableId("a")
private val B = DockableId("b")

private fun uiState(): DockState = DockState {
    dockable("a", title = { "Alpha" }) { BasicText("content-a") }
    dockable("b", title = { "Beta" }) { BasicText("content-b") }
}

/** A with B torn off into a floating window of its own - where move handles live. */
private fun tornOff(): Pair<DockState, WindowId> {
    val state = uiState()
    state.dock(A)
    state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
    state.moveToNewWindow(B, WindowBounds(10f, 10f, 300f, 200f))
    return state to state.layout.floatingWindows.single().id
}

/** Records which dockables were asked for a header. */
private class HeaderRecordingRenderer : DockingRenderer by DebugDockingRenderer {
    val headers = mutableListOf<DockableId>()

    @Composable
    override fun DockableHeader(model: HeaderModel, modifier: Modifier) {
        headers += model.id
        DebugDockingRenderer.DockableHeader(model, modifier)
    }
}

/**
 * A floating window is undecorated, so the dockable's own header is the only one it has -
 * there is no OS title bar above it to duplicate, and nothing is suppressed to avoid one.
 * The header a torn-off panel shows is the header it showed docked.
 */
@OptIn(ExperimentalTestApi::class)
class FloatingWindowHeaderTest {

    @Test
    fun everyDockableDrawsItsOwnHeader() = runComposeUiTest {
        val state = uiState()
        state.dock(A)
        val renderer = HeaderRecordingRenderer()
        setContent {
            CompositionLocalProvider(LocalDockingRenderer provides renderer) { DockArea(state) }
        }
        assertEquals(listOf(A), renderer.headers.distinct())
    }

    @Test
    fun aSplitKeepsEveryHeader() = runComposeUiTest {
        val state = uiState()
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
        val renderer = HeaderRecordingRenderer()
        setContent {
            CompositionLocalProvider(LocalDockingRenderer provides renderer) { DockArea(state) }
        }
        assertEquals(setOf(A, B), renderer.headers.toSet())
    }

    // The window's lone dockable gets its header wrapped in the move handle, which is what
    // drags an undecorated window. Wrapping must not cost it its header.
    @Test
    fun theMoveHandleWrapsTheHeaderRatherThanReplacingIt() = runComposeUiTest {
        val (state, floatingId) = tornOff()
        val renderer = HeaderRecordingRenderer()
        var wrapped = 0
        setContent {
            CompositionLocalProvider(
                LocalDockingRenderer provides renderer,
                LocalWindowMoveHandle provides { content -> wrapped++; Box { content() } },
            ) { DockArea(state, floatingId) }
        }
        assertEquals(listOf(B), renderer.headers.distinct(), "still drawn")
        assertTrue(wrapped > 0, "and drawn inside the move handle")
    }

    // Only the lone dockable's header stands where a title bar would. A split has no single
    // pane to speak for the window, so none of its headers may swallow the window drag.
    @Test
    fun aSplitGetsNoMoveHandle() = runComposeUiTest {
        val state = uiState()
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
        var wrapped = 0
        setContent {
            CompositionLocalProvider(
                LocalDockingRenderer provides HeaderRecordingRenderer(),
                LocalWindowMoveHandle provides { content -> wrapped++; Box { content() } },
            ) { DockArea(state) }
        }
        assertEquals(0, wrapped, "no header in a split drags the window")
    }

    // Two drag gestures on one header would race, and the header's own - being the inner
    // one - wins, tearing the panel out of the window the user was only trying to move.
    // So where a move handle owns the drag, the header is built without one.
    @Test
    fun aFramedHeaderDoesNotAlsoStartATearOutDrag() = runComposeUiTest {
        val (state, floatingId) = tornOff()
        setContent {
            CompositionLocalProvider(
                LocalDockingRenderer provides HeaderRecordingRenderer(),
                LocalWindowMoveHandle provides { content -> Box { content() } },
            ) { DockArea(state, floatingId, Modifier.fillMaxSize()) }
        }

        onNodeWithText("Beta").performMouseInput {
            moveTo(center)
            press()
            repeat(8) { moveTo(center + Offset(0f, 20f * (it + 1))) }
            release()
        }
        waitForIdle()

        assertNull(state.dragController.session, "no drag session was started by the header")
        assertTrue(state.isDocked(B), "and the panel was not torn out of its window")
    }

    // Without a handle - every docked pane - the header keeps its own drag, or nothing could
    // be dragged out of the main window at all.
    @Test
    fun anUnframedHeaderKeepsItsTearOutDrag() = runComposeUiTest {
        val state = uiState()
        state.dock(A)
        setContent {
            CompositionLocalProvider(LocalDockingRenderer provides HeaderRecordingRenderer()) {
                DockArea(state, modifier = Modifier.fillMaxSize())
            }
        }

        onNodeWithText("Alpha").performMouseInput {
            moveTo(center)
            press()
            repeat(8) { moveTo(center + Offset(0f, 20f * (it + 1))) }
        }
        waitForIdle()

        assertNotNull(state.dragController.session, "the header started a drag of its own")
    }

    // What an application reads to tell a torn-off panel from a docked one, so it can put
    // window buttons in a floating window's header - the library draws none itself. Read
    // through a renderer, which is where an application's own header content composes too.
    @Test
    fun contentReportsWhichWindowItIsIn() = runComposeUiTest {
        val state = uiState()
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
        state.moveToNewWindow(B, WindowBounds(10f, 10f, 300f, 200f))
        val floatingId = state.layout.floatingWindows.single().id
        var insideFloating: DockWindowInfo? = null
        var insideMain: DockWindowInfo? = null
        val capture = object : DockingRenderer by DebugDockingRenderer {
            @Composable
            override fun DockableHeader(model: HeaderModel, modifier: Modifier) {
                if (model.id == B) insideFloating = LocalDockWindow.current
                if (model.id == A) insideMain = LocalDockWindow.current
                DebugDockingRenderer.DockableHeader(model, modifier)
            }
        }
        setContent {
            CompositionLocalProvider(LocalDockingRenderer provides capture) {
                DockArea(state, floatingId)
                DockArea(state)
            }
        }
        assertEquals(true, insideFloating?.isFloating, "B is in the torn-off window")
        assertEquals(floatingId, insideFloating?.windowId)
        assertEquals(false, insideMain?.isFloating, "A stayed in the main window")
    }
}
