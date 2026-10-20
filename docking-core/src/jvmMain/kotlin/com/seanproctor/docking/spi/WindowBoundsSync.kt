package com.seanproctor.docking.spi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.seanproctor.docking.model.WindowBounds
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.state.DockState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlin.math.abs

/**
 * Keeps floating window [windowId]'s OS window and its layout entry in step, both ways: OS
 * moves and resizes flow into the layout (debounced, for persistence), and layout changes
 * from a restore flow back to the window. A value-compare echo guard breaks the loop.
 *
 * Call it once per floating window, with the [WindowState] its OS window is shown with.
 */
@DockingWindowHostApi
@OptIn(FlowPreview::class)
@Composable
public fun SyncDockWindowBounds(
    state: DockState,
    windowId: WindowId,
    windowState: WindowState,
) {
    LaunchedEffect(state, windowId, windowState) {
        snapshotFlow { windowState.position to windowState.size }
            .debounce(100)
            .collect { (position, size) ->
                if (position is WindowPosition.Absolute) {
                    val next = WindowBounds(position.x.value, position.y.value, size.width.value, size.height.value)
                    if (!next.approximately(state.layout.window(windowId)?.bounds)) {
                        state.setWindowBounds(windowId, next)
                    }
                }
            }
    }
    LaunchedEffect(state, windowId, windowState) {
        snapshotFlow { state.layout.window(windowId)?.bounds }
            .collect { bounds ->
                if (bounds != null) {
                    val current = windowState.position
                    val matches =
                        current is WindowPosition.Absolute &&
                            WindowBounds(
                                current.x.value,
                                current.y.value,
                                windowState.size.width.value,
                                windowState.size.height.value,
                            ).approximately(bounds)
                    if (!matches) {
                        windowState.position = WindowPosition.Absolute(bounds.x.dp, bounds.y.dp)
                        windowState.size = DpSize(bounds.width.dp, bounds.height.dp)
                    }
                }
            }
    }
}

private fun WindowBounds.approximately(other: WindowBounds?): Boolean =
    other != null &&
        abs(x - other.x) < 1f &&
        abs(y - other.y) < 1f &&
        abs(width - other.width) < 1f &&
        abs(height - other.height) < 1f
