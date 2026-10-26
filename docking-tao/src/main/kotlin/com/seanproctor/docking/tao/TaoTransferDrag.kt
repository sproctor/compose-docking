@file:OptIn(ExperimentalNucleusApi::class, ExperimentalFoundationApi::class)

package com.seanproctor.docking.tao

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.unit.IntRect
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.spi.DockDragGrip
import com.seanproctor.docking.spi.DockingDragHandover
import com.seanproctor.docking.spi.LocalDockingDragHandover
import com.seanproctor.docking.spi.windowHost
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.ui.DockArea
import dev.nucleusframework.application.NucleusDecoratedWindowScope
import dev.nucleusframework.application.NucleusWindow
import dev.nucleusframework.window.ExperimentalNucleusApi
import dev.nucleusframework.window.tao.workspace.TransferDrag
import dev.nucleusframework.window.tao.workspace.TransferDragGesture
import dev.nucleusframework.window.tao.workspace.TransferGhostSource
import dev.nucleusframework.window.tao.workspace.positionInWindowPx
import dev.nucleusframework.window.tao.workspace.transferDragHandle
import kotlin.math.roundToInt

/**
 * A [DockArea] in a Nucleus window, connected to the docking system: call it instead of
 * [DockArea] plus [registerTaoDockingWindow] in every window that hosts dock areas (floating
 * windows from [TaoFloatingDockWindows] already do).
 *
 * Where windows can be placed (X11, Windows, macOS) drags run in screen coordinates. On
 * native Wayland, where no window can see the pointer outside itself, a drag is carried by
 * the platform's drag-and-drop session instead: the grip starts it, the window under the
 * pointer reports where it is in its own coordinates, and a release over no window tears
 * the panel off into a floating window (placed by the compositor).
 */
@ExperimentalDockingTaoApi
@Composable
public fun NucleusDecoratedWindowScope.TaoDockArea(
    state: DockState,
    windowId: WindowId = WindowId.MAIN,
    modifier: Modifier = Modifier,
) {
    registerTaoDockingWindow(state, windowId)
    val window = nucleusWindow
    if (window.isPlaceable) {
        DockArea(state, windowId, modifier)
        return
    }
    val handover = remember(state, window) { TaoTransferHandover(state, window) }
    CompositionLocalProvider(LocalDockingDragHandover provides handover) {
        DockArea(state, windowId, modifier.transferDropTarget(state, windowId))
    }
}

/** Hands every grip's drag to the platform's drag-and-drop session (Nucleus's transfer drag). */
private class TaoTransferHandover(
    private val state: DockState,
    private val window: NucleusWindow,
) : DockingDragHandover {
    @Composable
    override fun gripModifier(grip: DockDragGrip): Modifier {
        val tao = window.unsafe.taoWindow ?: return Modifier
        val gesture = remember(grip) { GripGesture(grip) }
        return Modifier.transferDragHandle(
            key = grip.key,
            window = tao,
            begin = {
                // The platform session starts here, from the press; the core drag starts with it,
                // at the last position the gesture saw.
                if (grip.begin(gesture.lastPosition ?: Offset.Zero)) {
                    taoIntegration(state).transferDropWindow = null
                    DockTransferDrag(state, grip)
                } else {
                    null
                }
            },
            gesture = gesture,
        )
    }
}

/**
 * What the grip does before the platform session takes the gesture: a tab reorders within
 * its strip, everything else hands over as soon as the touch slop is passed.
 */
internal class GripGesture(
    private val grip: DockDragGrip,
) : TransferDragGesture {
    var lastPosition: Offset? = null
        private set

    override fun onStart(pressPosition: Offset) {
        lastPosition = pressPosition
    }

    override fun onDrag(position: Offset): Boolean {
        lastPosition = position
        return !grip.stayLocal(position)
    }

    override fun onEnd(released: Boolean) {
        grip.endLocal(released, lastPosition)
    }
}

/** The drag icon's title card: a header-sized strip, whatever the size of what is dragged. */
internal val GhostCardSizePx = Size(240f, 48f)

/** The platform session of one docking drag, as Nucleus's transfer drag carries it. */
internal class DockTransferDrag(
    private val state: DockState,
    grip: DockDragGrip,
) : TransferDrag {
    private val bounds = grip.sourceBoundsInWindow

    override val title: String = grip.title

    // Only the card: a picture of the source comes from ghostSource, which Nucleus reduces
    // itself. A card the size of a whole panel would hide the zones it is aimed at.
    override val ghostSizePx: Size =
        bounds?.size?.takeIf { it.width > 0f && it.height > 0f }?.let {
            Size(minOf(it.width, GhostCardSizePx.width), minOf(it.height, GhostCardSizePx.height))
        } ?: GhostCardSizePx

    override val ghostSource: TransferGhostSource =
        bounds?.let {
            TransferGhostSource.Region(
                IntRect(it.left.roundToInt(), it.top.roundToInt(), it.right.roundToInt(), it.bottom.roundToInt()),
            )
        } ?: TransferGhostSource.None

    /**
     * Over: the one place a drag carried by the platform is resolved. The session ends on the
     * source after every target's `onDrop`; a window that took the drop recorded itself, with
     * the pointer already hovered there, and a release over no window tears the payload off.
     */
    override fun end() {
        val integration = taoIntegration(state)
        val droppedIn = integration.transferDropWindow
        integration.transferDropWindow = null
        val host = state.windowHost
        if (!host.isDragging) return
        if (droppedIn != null) host.drop() else host.dropOutside()
    }

    override fun cancel() {
        taoIntegration(state).transferDropWindow = null
        if (state.windowHost.isDragging) state.windowHost.cancel()
    }
}

/**
 * This window as a drop target of the docking drags the platform carries: it reports the
 * pointer in its own coordinates, which is all a window on native Wayland can know of it.
 */
@Composable
private fun Modifier.transferDropTarget(
    state: DockState,
    windowId: WindowId,
): Modifier {
    val target =
        remember(state, windowId) {
            val window = DockTransferTarget(state, windowId)
            object : DragAndDropTarget {
                override fun onEntered(event: DragAndDropEvent) = window.hover(event.positionInWindowPx())

                override fun onMoved(event: DragAndDropEvent) = window.hover(event.positionInWindowPx())

                override fun onExited(event: DragAndDropEvent) = window.exit()

                override fun onDrop(event: DragAndDropEvent): Boolean = window.drop(event.positionInWindowPx())
            }
        }
    return dragAndDropTarget(
        // Only a docking drag in flight: its token is the same as every other transfer drag's.
        shouldStartDragAndDrop = { state.windowHost.isDragging },
        target = target,
    )
}

/**
 * One window's side of a platform-carried drag: hovers the window as the pointer moves over
 * it, and on a drop records the window for [DockTransferDrag.end], which applies it.
 */
internal class DockTransferTarget(
    private val state: DockState,
    private val windowId: WindowId,
) {
    /** The pointer is at [position] in the window's pixels, or nowhere known (Unspecified). */
    fun hover(position: Offset) {
        if (position.isSpecified) state.windowHost.updateDragInWindow(position, windowId)
    }

    fun exit() {
        // Leaving after taking the drop is the session winding down, not the pointer leaving.
        if (taoIntegration(state).transferDropWindow == null) state.windowHost.leaveWindows()
    }

    /** Takes the drop at [position]; false when no docking drag is in flight. */
    fun drop(position: Offset): Boolean {
        if (!state.windowHost.isDragging) return false
        hover(position)
        taoIntegration(state).transferDropWindow = windowId
        return true
    }
}
