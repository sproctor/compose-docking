package com.seanproctor.docking.ui

import androidx.compose.runtime.Composable
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.spi.DockMenuItem
import com.seanproctor.docking.state.DockState

/**
 * The standard dockable settings menu: View Mode ▸ {Auto Hide, Window}, Maximize/Restore.
 * Entries are omitted when the option or platform capability is absent.
 */
@Composable
internal fun buildDockableMenuItems(state: DockState, id: DockableId): List<DockMenuItem> {
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
                        onClick = { state.setAutoHide(id, !isAutoHidden) },
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
