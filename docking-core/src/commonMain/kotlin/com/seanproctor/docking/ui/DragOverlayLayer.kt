package com.seanproctor.docking.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.seanproctor.docking.drag.DragSession
import com.seanproctor.docking.drag.DropTarget
import com.seanproctor.docking.drag.previewRectFor
import com.seanproctor.docking.model.AutoHideSide
import com.seanproctor.docking.spi.DropOverlayModel
import com.seanproctor.docking.spi.HandleModel
import com.seanproctor.docking.spi.LocalDockingRenderer
import com.seanproctor.docking.spi.OverlayKind
import kotlin.math.roundToInt

/**
 * The top layer of a [DockArea] during a drag: docking handles, the translucent drop
 * preview, and the ghost card following the pointer. Rendering handles inside each
 * window's own composition replaces ModernDocking's transparent always-on-top overlay
 * frames entirely.
 */
@Composable
internal fun DockAreaScope.DragOverlayLayer(modifier: Modifier) {
    val session = state.dragController.session ?: return
    if (session.hoveredWindow != windowId) return
    val renderer = LocalDockingRenderer.current
    val rootOffset = bounds.rootBounds.topLeft
    val density = density

    fun Rect.inLayer(): Rect = translate(-rootOffset.x, -rootOffset.y)

    Box(modifier) {
        // Drop preview under the handles.
        overlayRectFor(session)?.let { (rect, kind) ->
            val r = rect.inLayer()
            renderer.DropOverlay(
                DropOverlayModel(kind),
                Modifier
                    .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                    .size((r.width / density).dp, (r.height / density).dp),
            )
        }

        for (handle in session.handles) {
            val r = handle.rect.inLayer()
            renderer.DockingHandle(
                HandleModel(
                    kind = handle.kind,
                    isHovered = handle.enabled && session.target == handle.target,
                    isEnabled = handle.enabled,
                ),
                Modifier
                    .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                    .size((r.width / density).dp, (r.height / density).dp),
            )
        }

        // Ghost card following the pointer (suppressed when the platform shows its own).
        if (state.dragController.dragListener?.showsOwnPreview != true) {
            val local = state.dragController.fromScreen(windowId, session.screenPosition) - rootOffset
            val spec = state.registry[session.payload.primary]
            Box(
                Modifier.offset {
                    IntOffset(
                        (local.x + 12 * density).roundToInt(),
                        (local.y + 12 * density).roundToInt(),
                    )
                },
            ) {
                renderer.DragPreview(
                    title = spec?.title?.invoke() ?: session.payload.primary.value,
                    icon = spec?.icon?.invoke(),
                )
            }
        }
    }
}

/** The drop-preview rect (window-root coords) and style for the session's current target. */
private fun DockAreaScope.overlayRectFor(session: DragSession): Pair<Rect, OverlayKind>? {
    return when (val target = session.target) {
        is DropTarget.Root -> {
            val rect = bounds.rootBounds
            Pair(previewRectFor(rect, target.region), OverlayKind.Area)
        }
        is DropTarget.OnDockable -> {
            val rect = bounds.boundsOf(target.dockableId) ?: return null
            Pair(previewRectFor(rect, target.region), OverlayKind.Area)
        }
        is DropTarget.OnNode -> {
            val rect = bounds.boundsOfNode(target.nodeId) ?: return null
            Pair(previewRectFor(rect, target.region), OverlayKind.Area)
        }
        is DropTarget.TabInsert -> {
            val strip = bounds.tabStripRects[target.nodeId] ?: return null
            val tabs = bounds.tabRects[target.nodeId].orEmpty().entries.sortedBy { it.key }
            val caretX = when {
                tabs.isEmpty() -> strip.left
                target.index >= tabs.size -> tabs.last().value.right
                else -> tabs[target.index].value.left
            }
            val width = 3f * density
            Pair(
                Rect(caretX - width / 2f, strip.top, caretX + width / 2f, strip.bottom),
                OverlayKind.TabCaret,
            )
        }
        is DropTarget.Pin -> {
            val root = bounds.rootBounds
            val band = 24f * density
            val rect = when (target.side) {
                AutoHideSide.West -> Rect(root.left, root.top, root.left + band, root.bottom)
                AutoHideSide.East -> Rect(root.right - band, root.top, root.right, root.bottom)
                AutoHideSide.South -> Rect(root.left, root.bottom - band, root.right, root.bottom)
            }
            Pair(rect, OverlayKind.Area)
        }
        is DropTarget.NewWindow, DropTarget.None -> null
    }
}

/**
 * The origin window's root listener: once a session is active, moves and the release are
 * handled here (initial pass, consuming) — the source node may be disposed by the drag's
 * own layout reflow, but this root persists and keeps receiving the captured pointer.
 */
internal fun Modifier.dragSessionRootListener(scope: DockAreaScope): Modifier = this
    .pointerInput(scope) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val controller = scope.state.dragController
                if (controller.session == null) continue
                when (event.type) {
                    PointerEventType.Move -> {
                        event.changes.forEach { it.consume() }
                        val position = event.changes.first().position + scope.bounds.rootBounds.topLeft
                        controller.updateDrag(position, scope.windowId)
                    }
                    PointerEventType.Release -> {
                        event.changes.forEach { it.consume() }
                        controller.drop()
                    }
                    else -> Unit
                }
            }
        }
    }
    .onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape &&
            scope.state.dragController.session != null
        ) {
            scope.state.dragController.cancel()
            true
        } else {
            false
        }
    }
