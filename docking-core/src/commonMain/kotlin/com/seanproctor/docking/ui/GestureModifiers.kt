package com.seanproctor.docking.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.NodeId
import com.seanproctor.docking.state.DockState

/**
 * Core-built gesture modifiers embedded into renderer models.
 *
 * Selection/activation fire on press (IDE behavior — no double-tap-timeout latency);
 * a quick second press toggles maximize. The drag controller extends these with
 * drag-to-dock and tab reordering.
 */

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.pressAndDoubleClick(
    onPress: () -> Unit,
    onDoubleClick: () -> Unit,
) {
    var lastUpMillis = Long.MIN_VALUE
    awaitEachGesture {
        val down = awaitFirstDown()
        onPress()
        val up = waitForUpOrCancellation()
        if (up != null) {
            if (up.uptimeMillis - lastUpMillis < viewConfiguration.doubleTapTimeoutMillis) {
                lastUpMillis = Long.MIN_VALUE
                onDoubleClick()
            } else {
                lastUpMillis = up.uptimeMillis
            }
        }
    }
}

private fun DockState.toggleMaximizeIfAllowed(id: DockableId) {
    if (registry.optionsOf(id).maximizable) toggleMaximize(id)
}

@Composable
internal fun DockAreaScope.headerGestureModifier(id: DockableId): Modifier =
    Modifier.pointerInput(id) {
        pressAndDoubleClick(
            onPress = { state.activeDockable = id },
            onDoubleClick = { state.toggleMaximizeIfAllowed(id) },
        )
    }

@Composable
internal fun DockAreaScope.tabGestureModifier(
    node: DockNode.Tabs,
    id: DockableId,
    index: Int,
): Modifier {
    val currentIndex by rememberUpdatedState(index)
    val nodeId = node.id
    return Modifier
        .onGloballyPositioned { bounds.updateTab(nodeId, index, it.boundsInRoot()) }
        .pointerInput(nodeId, id) {
            pressAndDoubleClick(
                onPress = {
                    state.selectTab(nodeId, currentIndex)
                    state.activeDockable = id
                },
                onDoubleClick = { state.toggleMaximizeIfAllowed(id) },
            )
        }
}

@Composable
internal fun DockAreaScope.gutterGestureModifier(node: DockNode.Tabs): Modifier =
    Modifier // whole-group drag arrives with the drag controller

/** Live x-offset of a tab while it is being reordered (0 until the drag controller lands). */
internal fun DockAreaScope.tabReorderOffset(nodeId: NodeId, id: DockableId): Float = 0f

/** Insertion caret position while a drag hovers this strip (null until then). */
internal fun DockAreaScope.tabDropInsertionIndex(nodeId: NodeId): Int? = null
