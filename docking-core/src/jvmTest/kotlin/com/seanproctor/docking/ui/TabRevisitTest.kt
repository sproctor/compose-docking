package com.seanproctor.docking.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget
import kotlin.test.Test
import kotlin.test.assertIs

private val A = DockableId("a")
private val B = DockableId("b")
private val C = DockableId("c")

/**
 * Revisiting a previously shown tab must show that tab's content again (issue #5).
 * Every tab's content is composed at the same call site, so a movable-content lambda
 * memoized by slot position leaked the last-created tab's content into revisits.
 */
@OptIn(ExperimentalTestApi::class)
class TabRevisitTest {

    @Test
    fun revisitedTabsShowTheirOwnContent() = runComposeUiTest {
        val state = DockState {
            dockable("a", title = { "Alpha" }) { BasicText("content-a") }
            dockable("b", title = { "Beta" }) { BasicText("content-b") }
            dockable("c", title = { "Gamma" }) { BasicText("content-c") }
        }
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.Center)
        state.dock(C, DockTarget.OnDockable(A), DockRegion.Center)
        setContent { DockArea(state, modifier = Modifier.fillMaxSize()) }
        val tabs = assertIs<DockNode.Tabs>(state.layout.mainWindow.root)

        // Visit each tab once, then revisit them in a different order.
        for (index in listOf(0, 1, 2, 0, 1, 2, 1)) {
            runOnIdle { state.selectTab(tabs.id, index) }
            onNodeWithText("content-${"abc"[index]}").assertIsDisplayed()
        }
    }
}
