package com.seanproctor.docking.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import com.seanproctor.docking.model.AutoHideSide
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.DockingStyle
import com.seanproctor.docking.spi.DockMenuItem
import com.seanproctor.docking.state.DockState

/**
 * The standard dockable settings menu: View Mode ▸ {Auto Hide, Window}, Maximize/Restore.
 * Entries are omitted when the option or platform capability is absent.
 */
@Composable
internal fun DockAreaScope.buildDockableMenuItems(state: DockState, id: DockableId): List<DockMenuItem> {
    val capabilities = LocalDockCapabilities.current
    val options = state.registry.optionsOf(id)
    val isAutoHidden = state.isAutoHidden(id)
    val isMaximized = state.isMaximized(id)
    return buildList {
        val viewModes = buildList {
            if (options.autoHideAllowed) {
                add(
                    DockMenuItem.Action(
                        label = "Auto Hide",
                        selected = isAutoHidden,
                        onClick = { state.setAutoHide(id, !isAutoHidden, nearestAutoHideSide(id)) },
                    ),
                )
            }
            if (capabilities.floatingWindows && options.floatable) {
                add(
                    DockMenuItem.Action(
                        label = "Window",
                        onClick = { state.moveToNewWindow(id) },
                    ),
                )
            }
        }
        if (viewModes.isNotEmpty()) {
            add(DockMenuItem.SubMenu("View Mode", viewModes))
        }
        if (options.maximizable && !isAutoHidden) {
            add(
                DockMenuItem.Action(
                    label = if (isMaximized) "Restore" else "Maximize",
                    selected = isMaximized,
                    onClick = { state.toggleMaximize(id) },
                ),
            )
        }
    }
}

/**
 * ModernDocking's nearest-side auto-pick: the closest of the West/East/South edge
 * midpoints to the dockable's center, filtered by its `autoHideStyle`. Falls back to
 * null (first-allowed) when no geometry is available.
 */
internal fun DockAreaScope.nearestAutoHideSide(id: DockableId): AutoHideSide? {
    val style = state.registry.optionsOf(id).autoHideStyle
    val rect = bounds.boundsOf(id) ?: return null
    val root = bounds.rootBounds
    if (root.width <= 0f) return null
    val center = rect.center
    val candidates = buildList {
        if (style == DockingStyle.Vertical || style == DockingStyle.Both) {
            add(AutoHideSide.West to Offset(root.left, root.center.y))
            add(AutoHideSide.East to Offset(root.right, root.center.y))
        }
        if (style == DockingStyle.Horizontal || style == DockingStyle.Both) {
            add(AutoHideSide.South to Offset(root.center.x, root.bottom))
        }
    }
    return candidates.minByOrNull { (_, edge) -> (edge - center).getDistanceSquared() }?.first
}
