package com.seanproctor.docking.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val A = DockableId("a")
private val B = DockableId("b")

/**
 * Double-click-to-maximize must require two *consecutive* clicks on the same element.
 * Regression coverage for rapid tab switching falsely maximizing (per-handler,
 * time-only detection missed the intervening click on the other tab).
 */
@OptIn(ExperimentalTestApi::class)
class TabClickTest {

    private fun uiState(): DockState = DockState {
        dockable("a", title = { "Alpha" }) { BasicText("content-a") }
        dockable("b", title = { "Beta" }) { BasicText("content-b") }
    }

    private fun tabbedState(): DockState = uiState().also { state ->
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.Center)
    }

    @Test
    fun rapidAlternatingTabClicksSelectWithoutMaximizing() = runComposeUiTest {
        val state = tabbedState()
        setContent { DockArea(state, modifier = Modifier.fillMaxSize()) }

        val alpha = onNodeWithTag("tab:a").fetchSemanticsNode().boundsInRoot.center
        val beta = onNodeWithTag("tab:b").fetchSemanticsNode().boundsInRoot.center
        // A -> B -> A, all well inside the double-tap timeout.
        onRoot().performMouseInput {
            moveTo(alpha)
            press()
            release()
            advanceEventTime(80)
            moveTo(beta)
            press()
            release()
            advanceEventTime(80)
            moveTo(alpha)
            press()
            release()
        }
        waitForIdle()

        assertFalse(state.isMaximized(A), "alternating clicks must not maximize")
        assertFalse(state.isMaximized(B))
        val tabs = assertIs<DockNode.Tabs>(state.layout.mainWindow.root)
        assertEquals(A, tabs.selectedTab.dockableId, "last click selects Alpha")
    }

    @Test
    fun doubleClickOnTabMaximizes() = runComposeUiTest {
        val state = tabbedState()
        setContent { DockArea(state, modifier = Modifier.fillMaxSize()) }

        val alpha = onNodeWithText("Alpha").fetchSemanticsNode().boundsInRoot.center
        onRoot().performMouseInput {
            moveTo(alpha)
            press()
            release()
            advanceEventTime(80)
            press()
            release()
        }
        waitForIdle()

        assertTrue(state.isMaximized(A), "consecutive same-tab clicks maximize")
    }

    @Test
    fun doubleClickOnUnselectedTabSelectsThenMaximizes() = runComposeUiTest {
        val state = tabbedState() // B is selected (docked last)
        setContent { DockArea(state, modifier = Modifier.fillMaxSize()) }

        val alpha = onNodeWithText("Alpha").fetchSemanticsNode().boundsInRoot.center
        onRoot().performMouseInput {
            moveTo(alpha)
            press()
            release()
            advanceEventTime(80)
            press()
            release()
        }
        waitForIdle()

        assertTrue(state.isMaximized(A))
    }

    @Test
    fun slowSecondClickDoesNotMaximize() = runComposeUiTest {
        val state = tabbedState()
        setContent { DockArea(state, modifier = Modifier.fillMaxSize()) }

        val alpha = onNodeWithText("Alpha").fetchSemanticsNode().boundsInRoot.center
        onRoot().performMouseInput {
            moveTo(alpha)
            press()
            release()
            advanceEventTime(2_000) // well past any double-tap timeout
            press()
            release()
        }
        waitForIdle()

        assertFalse(state.isMaximized(A))
    }

    @Test
    fun contentClickBetweenTabClicksBreaksSequence() = runComposeUiTest {
        val state = tabbedState()
        setContent { DockArea(state, modifier = Modifier.fillMaxSize()) }

        val alpha = onNodeWithText("Alpha").fetchSemanticsNode().boundsInRoot.center
        val content = onNodeWithText("content-b").fetchSemanticsNode().boundsInRoot.center
        onRoot().performMouseInput {
            moveTo(alpha)
            press()
            release()
            advanceEventTime(80)
            moveTo(content)
            press()
            release()
            advanceEventTime(80)
            moveTo(alpha)
            press()
            release()
        }
        waitForIdle()

        assertFalse(state.isMaximized(A))
    }

    @Test
    fun alternatingHeaderClicksDoNotMaximize() = runComposeUiTest {
        val state = uiState()
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
        setContent { DockArea(state, modifier = Modifier.fillMaxSize()) }

        val alphaHeader = onNodeWithText("Alpha").fetchSemanticsNode().boundsInRoot.center
        val betaHeader = onNodeWithText("Beta").fetchSemanticsNode().boundsInRoot.center
        onRoot().performMouseInput {
            moveTo(alphaHeader)
            press()
            release()
            advanceEventTime(80)
            moveTo(betaHeader)
            press()
            release()
            advanceEventTime(80)
            moveTo(alphaHeader)
            press()
            release()
        }
        waitForIdle()

        assertFalse(state.isMaximized(A))
        assertFalse(state.isMaximized(B))
    }
}
