package com.seanproctor.docking.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget
import kotlin.test.Test

private val A = DockableId("a")
private val B = DockableId("b")
private val C = DockableId("c")

private fun uiState(): DockState = DockState {
    dockable("a", title = { "Alpha" }) { BasicText("content-a") }
    dockable("b", title = { "Beta" }) { BasicText("content-b") }
    dockable("c", title = { "Gamma" }) { BasicText("content-c") }
}

@OptIn(ExperimentalTestApi::class)
class DockAreaSmokeTest {

    @Test
    fun rendersSplitsHeadersAndContent() = runComposeUiTest {
        val state = uiState()
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
        setContent { DockArea(state) }
        onNodeWithText("content-a").assertExists()
        onNodeWithText("content-b").assertExists()
        onNodeWithText("Alpha").assertExists()
        onNodeWithText("Beta").assertExists()
    }

    @Test
    fun tabGroupShowsSelectedAndSwitchesOnClick() = runComposeUiTest {
        val state = uiState()
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.Center)
        setContent { DockArea(state) }
        // B was docked last -> selected; A's content hidden.
        onNodeWithText("content-b").assertExists()
        onAllNodesWithText("content-a").assertCountEquals(0)
        // Click A's tab.
        onNodeWithText("Alpha").performClick()
        onNodeWithText("content-a").assertExists()
        onAllNodesWithText("content-b").assertCountEquals(0)
    }

    @Test
    fun emptyRootShowsPlaceholder() = runComposeUiTest {
        val state = uiState()
        setContent { DockArea(state) }
        onNodeWithText("No dockables").assertExists()
    }

    @Test
    fun missingDockableShowsPlaceholder() = runComposeUiTest {
        val state = DockState { }
        state.dock(A)
        setContent { DockArea(state) }
        onNodeWithText("Unavailable: a").assertExists()
    }

    @Test
    fun layoutChangePreservesContentAcrossReparenting() = runComposeUiTest {
        val state = uiState()
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
        setContent { DockArea(state) }
        onNodeWithText("content-a").assertExists()
        // Restructure: B joins A's tab group -> tree rebuilds around A.
        state.dock(B, DockTarget.OnDockable(A), DockRegion.Center)
        waitForIdle()
        onNodeWithText("content-b").assertExists()
    }


    @Test
    fun maximizeShowsOnlyMaximizedDockable() = runComposeUiTest {
        val state = uiState()
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
        setContent { DockArea(state) }
        state.maximize(A)
        waitForIdle()
        onNodeWithText("content-a").assertExists()
        onAllNodesWithText("content-b").assertCountEquals(0)
        state.restore(com.seanproctor.docking.model.WindowId.MAIN)
        waitForIdle()
        onNodeWithText("content-b").assertExists()
    }
}
