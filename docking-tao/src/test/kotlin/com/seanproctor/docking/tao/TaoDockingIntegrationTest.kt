package com.seanproctor.docking.tao

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpSize
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.state.DockState
import dev.nucleusframework.application.NucleusWindow
import dev.nucleusframework.application.NucleusWindowBounds
import dev.nucleusframework.application.NucleusWindowUnsafe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val FLOATING = WindowId("floating")

/**
 * Which window a cross-window drag is over. Placeable windows are hit-tested in the shared
 * screen space (physical pixels: logical bounds times the window's density); on native
 * Wayland there is no shared space, and nothing is.
 */
class TaoDockingIntegrationTest {
    private fun integration(placeable: Boolean): TaoDockingIntegration =
        TaoDockingIntegration(DockState { }, placeable = { placeable })

    @Test
    fun aPointOverOneWindowResolvesToIt() {
        val integration = integration(placeable = true)
        integration.add(WindowId.MAIN, FakeWindow(NucleusWindowBounds(100f, 100f, 800f, 600f)), density = 2f)

        // (100, 100) logical at density 2 starts at (200, 200) physical.
        assertEquals(WindowId.MAIN, integration.resolveWindowAt(Offset(250f, 250f), exclude = null))
        assertNull(integration.resolveWindowAt(Offset(150f, 150f), exclude = null))
    }

    @Test
    fun overlappingWindowsPreferTheFocusedOne() {
        val integration = integration(placeable = true)
        integration.add(WindowId.MAIN, FakeWindow(NucleusWindowBounds(0f, 0f, 800f, 600f), focused = true), density = 1f)
        integration.add(FLOATING, FakeWindow(NucleusWindowBounds(100f, 100f, 300f, 200f)), density = 1f)

        assertEquals(WindowId.MAIN, integration.resolveWindowAt(Offset(150f, 150f), exclude = null))
    }

    @Test
    fun overlappingUnfocusedWindowsPreferTheLastRegistered() {
        val integration = integration(placeable = true)
        integration.add(WindowId.MAIN, FakeWindow(NucleusWindowBounds(0f, 0f, 800f, 600f)), density = 1f)
        integration.add(FLOATING, FakeWindow(NucleusWindowBounds(100f, 100f, 300f, 200f)), density = 1f)

        assertEquals(FLOATING, integration.resolveWindowAt(Offset(150f, 150f), exclude = null))
    }

    @Test
    fun theDraggedWindowIsLookedPast() {
        val integration = integration(placeable = true)
        integration.add(WindowId.MAIN, FakeWindow(NucleusWindowBounds(0f, 0f, 800f, 600f)), density = 1f)
        integration.add(FLOATING, FakeWindow(NucleusWindowBounds(100f, 100f, 300f, 200f), focused = true), density = 1f)

        assertEquals(WindowId.MAIN, integration.resolveWindowAt(Offset(150f, 150f), exclude = FLOATING))
    }

    @Test
    fun theOriginIsTheWindowsPhysicalTopLeft() {
        val integration = integration(placeable = true)
        integration.add(WindowId.MAIN, FakeWindow(NucleusWindowBounds(30f, 40f, 800f, 600f)), density = 1.5f)

        assertEquals(Offset(45f, 60f), integration.originOf(WindowId.MAIN))
    }

    @Test
    fun windowsThatCannotBePlacedAreNeverHit() {
        val integration = integration(placeable = false)
        integration.add(WindowId.MAIN, FakeWindow(NucleusWindowBounds(0f, 0f, 800f, 600f), focused = true), density = 1f)

        // Native Wayland: the platform session carries drags there, not the shared space.
        assertNull(integration.resolveWindowAt(Offset(150f, 150f), exclude = null))
        assertEquals(false, integration.windowsPlaceable)
    }

    @Test
    fun placeabilityIsUnknownUntilAWindowRegisters() {
        val integration = integration(placeable = true)
        assertNull(integration.windowsPlaceable)

        integration.add(FLOATING, FakeWindow(NucleusWindowBounds(0f, 0f, 300f, 200f)), density = 1f)
        assertEquals(true, integration.windowsPlaceable, "any window answers, not only the main one")
    }

    @Test
    fun aWindowBeingMovedIsWhereItWasAskedToGo() {
        val integration = integration(placeable = true)
        // The window system has not confirmed the move yet: the bounds still say (100, 100).
        integration.add(FLOATING, FakeWindow(NucleusWindowBounds(100f, 100f, 300f, 200f)), density = 2f)
        integration.movingOrigins[FLOATING] = Offset(140f, 120f)

        assertEquals(Offset(280f, 240f), integration.originOf(FLOATING))
        assertEquals(FLOATING, integration.resolveWindowAt(Offset(285f, 245f), exclude = null))
        assertNull(integration.resolveWindowAt(Offset(210f, 210f), exclude = null))
    }

    private fun TaoDockingIntegration.add(
        id: WindowId,
        window: NucleusWindow,
        density: Float,
    ) {
        windows[id] = window
        densities[id] = density
    }
}

private class FakeWindow(
    private val bounds: NucleusWindowBounds?,
    focused: Boolean = false,
) : NucleusWindow {
    override val isFocused: Boolean = focused
    override val isMinimized: Boolean = false
    override val isMaximized: Boolean = false
    override val isFullscreen: Boolean = false
    override val focusFlow: StateFlow<Boolean> = MutableStateFlow(focused)
    override val minimizedFlow: StateFlow<Boolean> = MutableStateFlow(false)
    override val maximizedFlow: StateFlow<Boolean> = MutableStateFlow(false)
    override val fullscreenFlow: StateFlow<Boolean> = MutableStateFlow(false)
    override val unsafe: NucleusWindowUnsafe = object : NucleusWindowUnsafe {}

    override fun boundsOnScreen(): NucleusWindowBounds? = bounds

    override fun show() = Unit

    override fun hide() = Unit

    override fun toFront() = Unit

    override fun requestFocus() = Unit

    override fun setMinimized(minimized: Boolean) = Unit

    override fun setMaximized(maximized: Boolean) = Unit

    override fun setFullscreen(fullscreen: Boolean) = Unit

    override fun setAlwaysOnTop(alwaysOnTop: Boolean) = Unit

    override fun setIcon(icon: Painter?) = Unit

    override fun setMinimumSize(size: DpSize?) = Unit

    override fun close() = Unit
}
