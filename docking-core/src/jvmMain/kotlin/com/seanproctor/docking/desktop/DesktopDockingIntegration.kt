package com.seanproctor.docking.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.FrameWindowScope
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.window.DockHostWindow
import com.seanproctor.docking.window.hostWindow
import kotlin.math.roundToInt

/**
 * A Compose Desktop window as the docking system sees it. AWT's screen positions are already
 * the logical, virtual-device coordinates every window shares, so they pass through unscaled.
 */
internal class AwtDockHostWindow(
    val window: ComposeWindow,
) : DockHostWindow {
    override fun contentBoundsOnScreen(): Rect? =
        runCatching {
            if (!window.isShowing) return null
            val content = window.contentPane
            val location = content.locationOnScreen
            contentRect(location.x, location.y, content.width, content.height)
        }.getOrNull()

    // Compose Desktop renders a window at its graphics configuration's scale.
    override val density: Float
        get() = window.graphicsConfiguration?.defaultTransform?.scaleX?.toFloat() ?: 1f

    override val isFocused: Boolean get() = window.isFocused

    override fun moveContentTo(position: Offset) {
        runCatching {
            val content = window.contentPane.locationOnScreen
            val frame = frameLocationFor(position, window.x, window.y, content.x, content.y)
            window.setLocation(frame.x, frame.y)
        }
    }
}

/** A window's content at ([x], [y]), [width] by [height], as the docking system's bounds. */
internal fun contentRect(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
): Rect = Rect(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat())

/**
 * Where to put a frame at ([frameX], [frameY]) whose content is at ([contentX], [contentY]) so
 * the content lands at [position]. The frame moves, not the content, so it keeps its offset from
 * the content - which also holds for a window with a title bar.
 */
internal fun frameLocationFor(
    position: Offset,
    frameX: Int,
    frameY: Int,
    contentX: Int,
    contentY: Int,
): IntOffset =
    IntOffset(
        frameX + (position.x - contentX).roundToInt(),
        frameY + (position.y - contentY).roundToInt(),
    )

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
    val host = remember(window) { AwtDockHostWindow(window) }
    com.seanproctor.docking.window.registerDockingWindow(state, windowId, host)
}
