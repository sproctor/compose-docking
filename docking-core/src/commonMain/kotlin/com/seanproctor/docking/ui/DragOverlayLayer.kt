package com.seanproctor.docking.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.seanproctor.docking.window.windowHost
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.seanproctor.docking.drag.DragSession
import com.seanproctor.docking.drag.DropTarget
import com.seanproctor.docking.drag.DROP_PREVIEW_FRACTION
import com.seanproctor.docking.drag.previewRectFor
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.NodeId
import com.seanproctor.docking.model.SplitOrientation
import com.seanproctor.docking.tree.findLeaf
import com.seanproctor.docking.tree.findTabsContaining
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
        if (state.dragController.previewClaims == 0) {
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
            val split = state.layout.window(target.windowId)?.root?.id
            Pair(dropPreviewRect(rect, target.region, split, session), OverlayKind.Area)
        }
        is DropTarget.OnDockable -> {
            val rect = bounds.boundsOf(target.dockableId) ?: return null
            // An edge drop onto a tab splits the whole group, so it is the group that has
            // to keep its minimum beside the new pane.
            val root = state.layout.window(windowId)?.root
            val split = (root?.findTabsContaining(target.dockableId) ?: root?.findLeaf(target.dockableId))?.id
            Pair(dropPreviewRect(rect, target.region, split, session), OverlayKind.Area)
        }
        is DropTarget.OnNode -> {
            val rect = bounds.boundsOfNode(target.nodeId) ?: return null
            Pair(dropPreviewRect(rect, target.region, target.nodeId, session), OverlayKind.Area)
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
        is DropTarget.NewWindow, DropTarget.None -> null
    }
}

/**
 * The drop preview for docking the session's payload at [region] of [rect], which splits
 * node [split]: the share the new pane will actually get once the split's minimums are
 * applied, rather than a flat half that the layout would then overrule on release.
 */
private fun DockAreaScope.dropPreviewRect(
    rect: Rect,
    region: DockRegion,
    split: NodeId?,
    session: DragSession,
): Rect {
    if (region == DockRegion.Center) return rect
    val orientation = if (region == DockRegion.East || region == DockRegion.West) {
        SplitOrientation.Horizontal
    } else {
        SplitOrientation.Vertical
    }
    val axis = if (orientation == SplitOrientation.Horizontal) rect.width else rect.height
    val available = axis - DividerThickness.value * density
    if (available <= 0f) return previewRectFor(rect, region)
    // The payload docks as one group, so it needs its largest member's minimum.
    val newMin = session.payload.all
        .fold(DpSize.Zero) { acc, id -> acc.coerceAtLeast(paneMinOf(id)) }
        .along(orientation).value * density
    val existingMin = (split?.let { minSizeOf(it) } ?: DpSize.Zero).along(orientation).value * density
    val share = clampProportion(DROP_PREVIEW_FRACTION, available, newMin, existingMin)
    return previewRectFor(rect, region, fraction = available * share / axis)
}

/**
 * The origin window's root listener: once a session is active, moves and the release are
 * handled here (initial pass, consuming) - the source node may be disposed by the drag's
 * own layout reflow, but this root persists and keeps receiving the captured pointer.
 */
internal fun Modifier.dragSessionRootListener(scope: DockAreaScope): Modifier = this
    .pointerInput(scope) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val controller = scope.state.dragController
                val session = controller.session ?: continue
                // The platform's drag-and-drop session owns the pointer: the drop targets
                // report it, and a stray event here must not drop the payload early.
                if (session.carriedByPlatform) continue
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
    .onPreviewKeyEvent { event -> scope.state.windowHost.cancelOnEscape(event) }
