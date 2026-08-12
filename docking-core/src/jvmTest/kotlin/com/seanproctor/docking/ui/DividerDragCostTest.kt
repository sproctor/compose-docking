package com.seanproctor.docking.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val A = DockableId("a")
private val B = DockableId("b")

private const val WINDOW = 400

/**
 * Dragging a divider must cost a re-measure, not a recomposition of everything docked in the
 * split. A dockable's content can be arbitrarily expensive - a text pane with a long scrollback,
 * say - so re-invoking it on every pointer move is what makes a resize feel sluggish.
 */
@OptIn(ExperimentalTestApi::class)
class DividerDragCostTest {

    @Test
    fun draggingADividerDoesNotRecomposePaneContent() = runComposeUiTest {
        val compositionsOfA = AtomicInteger(0)
        val compositionsOfB = AtomicInteger(0)
        val state = DockState {
            dockable("a", title = { "Alpha" }) {
                compositionsOfA.incrementAndGet()
                Box(Modifier.fillMaxSize().testTag("pane-a")) { BasicText("content-a") }
            }
            dockable("b", title = { "Beta" }) {
                compositionsOfB.incrementAndGet()
                Box(Modifier.fillMaxSize()) { BasicText("content-b") }
            }
        }
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East, proportion = 0.5f)
        setContent { DockArea(state, modifier = Modifier.size(WINDOW.dp)) }

        val widthBefore = onNodeWithTag("pane-a").fetchSemanticsNode().boundsInRoot.width
        val settledA = compositionsOfA.get()
        val settledB = compositionsOfB.get()
        assertTrue(settledA > 0, "content should have composed at least once")

        // Walk the divider left in small steps, the way a user drags it.
        val root = onRoot().fetchSemanticsNode().boundsInRoot
        val y = root.center.y
        onRoot().performMouseInput {
            moveTo(Offset(root.center.x, y))
            press()
            for (i in 1..20) moveTo(Offset(root.center.x - 4f * i, y))
            release()
        }
        waitForIdle()

        val widthAfter = onNodeWithTag("pane-a").fetchSemanticsNode().boundsInRoot.width
        assertTrue(widthAfter < widthBefore, "the drag should have moved the divider: $widthBefore -> $widthAfter")

        // Twenty pointer moves, and the panes' content was not re-invoked for any of them.
        assertEquals(settledA, compositionsOfA.get(), "pane A recomposed during the drag")
        assertEquals(settledB, compositionsOfB.get(), "pane B recomposed during the drag")
    }
}
