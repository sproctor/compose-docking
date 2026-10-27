package com.seanproctor.docking.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.FrameWindowScope
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.window.DockHostWindow
import com.seanproctor.docking.window.hostWindow
import kotlin.math.roundToInt

/**
 * A Compose Desktop window as the docking system sees it. AWT reports positions in logical
 * units, which the window's density turns into the screen pixels drags are carried in.
 */
internal class AwtDockHostWindow(
    val window: ComposeWindow,
    var density: Float,
) : DockHostWindow {
    override fun contentBoundsOnScreen(): Rect? =
        runCatching {
            if (!window.isShowing) return null
            val content = window.contentPane
            val location = content.locationOnScreen
            Rect(
                location.x * density,
                location.y * density,
                (location.x + content.width) * density,
                (location.y + content.height) * density,
            )
        }.getOrNull()

    override val isFocused: Boolean get() = window.isFocused

    override fun moveContentTo(position: Offset) {
        runCatching {
            // The frame moves, not the content: shift it by however far the content is from
            // where it should be, which also holds for a window with a title bar.
            val location = window.contentPane.locationOnScreen
            window.setLocation(
                window.x + (position.x / density - location.x).roundToInt(),
                window.y + (position.y / density - location.y).roundToInt(),
            )
        }
    }
}

/**
 * The AWT window showing [windowId]'s dock area, or null when it is not on screen.
 *
 * A floating window from [FloatingDockWindows] is undecorated, so minimize, maximize and
 * restore are the application's to provide - this is what it acts on. Pair it with
 * [com.seanproctor.docking.ui.LocalDockWindow], which gives the [windowId] of whichever
 * window the calling content is in:
 *
 * ```
 * val windowId = LocalDockWindow.current.windowId
 * IconButton(onClick = { (state.awtWindow(windowId) as? Frame)?.extendedState = Frame.ICONIFIED }) { ... }
 * ```
 *
 * Read it when acting, not to drive composition: this tracks windows as they register,
 * which is not snapshot state and will not recompose a reader when it changes.
 */
public fun DockState.awtWindow(windowId: WindowId = WindowId.MAIN): ComposeWindow? = (hostWindow(windowId) as? AwtDockHostWindow)?.window

/**
 * Connects the surrounding Compose Desktop window to the docking system, for drags between
 * windows: [com.seanproctor.docking.window.registerDockingWindow] for an AWT window. Call once
 * inside every `Window` that hosts a [com.seanproctor.docking.ui.DockArea] - the main window
 * in your own `Window` block; [FloatingDockWindows] does it for the floating windows it emits.
 */
@Composable
public fun FrameWindowScope.registerDockingWindow(
    state: DockState,
    windowId: WindowId = WindowId.MAIN,
) {
    val density = LocalDensity.current.density
    val host = remember(window) { AwtDockHostWindow(window, density) }
    SideEffect { host.density = density }
    com.seanproctor.docking.window.registerDockingWindow(state, windowId, host)
}
