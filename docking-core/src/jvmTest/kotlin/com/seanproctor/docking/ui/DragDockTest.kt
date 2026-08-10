package com.seanproctor.docking.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.SplitOrientation
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val A = DockableId("a")
private val B = DockableId("b")

@OptIn(ExperimentalTestApi::class)
class DragDockTest {

    private fun uiState(): DockState = DockState {
        dockable("a", title = { "Alpha" }) { BasicText("content-a") }
        dockable("b", title = { "Beta" }) { BasicText("content-b") }
    }

    @Test
    fun dragHeaderOntoDockableCenterCreatesTabGroup() = runComposeUiTest {
        val state = uiState()
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
        setContent { DockArea(state, modifier = Modifier.fillMaxSize()) }

        val sourceBounds = onNodeWithText("Beta").fetchSemanticsNode().boundsInRoot
        // Once B undocks at drag start, A expands to fill the window - target its
        // post-undock center (the window center), where A's Center handle sits.
        val rootBounds = onRoot().fetchSemanticsNode().boundsInRoot
        onNodeWithText("Beta").performMouseInput {
            moveTo(center)
            press()
            val startLocal = center
            val targetLocal = rootBounds.center - sourceBounds.topLeft
            val steps = 8
            for (i in 1..steps) {
                moveTo(startLocal + (targetLocal - startLocal) * (i.toFloat() / steps))
            }
            release()
        }
        waitForIdle()

        val tabs = assertIs<DockNode.Tabs>(state.layout.mainWindow.root)
        assertEquals(listOf(A, B), tabs.tabs.map { it.dockableId })
    }

    @Test
    fun dragHeaderToSouthEdgeOfDockableSplitsVertically() = runComposeUiTest {
        val state = uiState()
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
        setContent { DockArea(state, modifier = Modifier.fillMaxSize()) }

        val sourceBounds = onNodeWithText("Beta").fetchSemanticsNode().boundsInRoot
        // After B undocks, A fills the window: a point near the bottom edge is in A's
        // South proximity region.
        val rootBounds = onRoot().fetchSemanticsNode().boundsInRoot
        val targetPoint = Offset(rootBounds.center.x, rootBounds.bottom - 2f)
        onNodeWithText("Beta").performMouseInput {
            moveTo(center)
            press()
            val startLocal = center
            val targetLocal = targetPoint - sourceBounds.topLeft
            val steps = 8
            for (i in 1..steps) {
                moveTo(startLocal + (targetLocal - startLocal) * (i.toFloat() / steps))
            }
            release()
        }
        waitForIdle()

        val split = assertIs<DockNode.Split>(state.layout.mainWindow.root)
        assertEquals(SplitOrientation.Vertical, split.orientation)
        assertEquals(A, assertIs<DockNode.Leaf>(split.first).dockableId)
        assertEquals(B, assertIs<DockNode.Leaf>(split.second).dockableId)
    }

    @Test
    fun dragTabWithinStripReorders() = runComposeUiTest {
        val state = uiState()
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.Center)
        setContent { DockArea(state, modifier = Modifier.fillMaxSize()) }

        val alphaBounds = onNodeWithTag("tab:a").fetchSemanticsNode().boundsInRoot
        val betaBounds = onNodeWithTag("tab:b").fetchSemanticsNode().boundsInRoot
        // Drag Alpha horizontally past Beta within the strip.
        onNodeWithTag("tab:a").performMouseInput {
            moveTo(center)
            press()
            val startLocal = center
            val targetLocal = Offset(betaBounds.right + 8f, betaBounds.center.y) - alphaBounds.topLeft
            val steps = 6
            for (i in 1..steps) {
                moveTo(startLocal + (targetLocal - startLocal) * (i.toFloat() / steps))
            }
            release()
        }
        waitForIdle()

        val tabs = assertIs<DockNode.Tabs>(state.layout.mainWindow.root)
        assertEquals(listOf(B, A), tabs.tabs.map { it.dockableId })
    }
}
