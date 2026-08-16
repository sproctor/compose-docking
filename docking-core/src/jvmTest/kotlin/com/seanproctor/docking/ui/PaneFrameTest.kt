package com.seanproctor.docking.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.spi.DebugDockingRenderer
import com.seanproctor.docking.spi.DockingRenderer
import com.seanproctor.docking.spi.LocalDockingRenderer
import com.seanproctor.docking.spi.PaneFrameModel
import com.seanproctor.docking.spi.TabPlacement
import androidx.compose.runtime.CompositionLocalProvider
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val A = DockableId("a")
private val B = DockableId("b")

private fun uiState(): DockState = DockState {
    dockable("a", title = { "Alpha" }) { BasicText("content-a") }
    dockable("b", title = { "Beta" }) { BasicText("content-b") }
}

/** Records every pane it is asked to frame, and applies a real modifier so the call is not elided. */
private class RecordingRenderer : DockingRenderer by DebugDockingRenderer {
    val panes = mutableListOf<PaneFrameModel>()

    @Composable
    override fun paneFrame(model: PaneFrameModel): Modifier {
        panes += model
        return Modifier.padding(1.dp)
    }
}

@OptIn(ExperimentalTestApi::class)
class PaneFrameTest {

    @Test
    fun everyLeafIsOfferedAFrame() = runComposeUiTest {
        val state = uiState()
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
        val renderer = RecordingRenderer()
        setContent {
            CompositionLocalProvider(LocalDockingRenderer provides renderer) { DockArea(state) }
        }
        val framed = renderer.panes.map { it.dockableId }.toSet()
        assertEquals(setOf(A, B), framed)
        assertTrue(renderer.panes.none { it.isTabGroup }, "two split leaves are not tab groups")
    }

    // The frame is meant to enclose a pane whatever it holds, so a tab group gets one too - reported
    // against the selected tab, the only dockable whose body is on screen.
    @Test
    fun aTabGroupIsFramedAsOnePaneAroundItsSelectedTab() = runComposeUiTest {
        val state = uiState()
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.Center)
        val renderer = RecordingRenderer()
        setContent {
            CompositionLocalProvider(LocalDockingRenderer provides renderer) { DockArea(state) }
        }
        assertEquals(1, renderer.panes.map { it.dockableId }.distinct().size, "one pane, not one per tab")
        val pane = renderer.panes.last()
        assertTrue(pane.isTabGroup)
        assertEquals(B, pane.dockableId, "B was docked last, so it is the selected tab")
        // ModernDocking's default: the strip sits under the body, with a title bar above it.
        assertEquals(TabPlacement.Bottom, pane.tabPlacement)
    }

    @Test
    fun theFocusedPaneIsMarkedActive() = runComposeUiTest {
        val state = uiState()
        state.dock(A)
        state.dock(B, DockTarget.OnDockable(A), DockRegion.East)
        state.activeDockable = B
        val renderer = RecordingRenderer()
        setContent {
            CompositionLocalProvider(LocalDockingRenderer provides renderer) { DockArea(state) }
        }
        assertTrue(renderer.panes.filter { it.dockableId == B }.all { it.isActive })
        assertTrue(renderer.panes.filter { it.dockableId == A }.none { it.isActive })
    }
}
