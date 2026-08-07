package com.seanproctor.docking.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import com.seanproctor.docking.model.DockLayout
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.DockableOptions
import kotlinx.serialization.json.JsonElement

/** Declares the dockables of a [DockState]. */
public class DockStateBuilder internal constructor(
    private val registry: DockableRegistry,
) {
    public fun dockable(spec: DockableSpec) {
        registry.register(spec)
    }

    public fun dockable(
        id: String,
        title: @Composable () -> String,
        options: DockableOptions = DockableOptions(),
        icon: (@Composable () -> Painter)? = null,
        tooltip: (@Composable () -> String)? = null,
        canClose: suspend () -> Boolean = { true },
        saveState: (() -> JsonElement)? = null,
        restoreState: ((JsonElement) -> Unit)? = null,
        content: @Composable () -> Unit,
    ) {
        registry.register(
            DockableSpec(
                id = DockableId(id),
                options = options,
                title = title,
                icon = icon,
                tooltip = tooltip,
                canClose = canClose,
                saveState = saveState,
                restoreState = restoreState,
                content = content,
            ),
        )
    }
}

/**
 * Creates a [DockState] outside composition (for DI or app-scoped holders).
 *
 * ```
 * val state = DockState(initialLayout = dockLayout { ... }) {
 *     dockable("project", title = { "Project" }) { ProjectTree() }
 * }
 * ```
 */
public fun DockState(
    initialLayout: DockLayout = DockLayout.empty(),
    settings: DockingSettings = DockingSettings(),
    builder: DockStateBuilder.() -> Unit,
): DockState {
    val state = DockState(initialLayout, settings)
    DockStateBuilder(state.registry).builder()
    return state
}

/**
 * Creates and remembers a [DockState]. [initialLayout] is only evaluated once.
 */
@Composable
public fun rememberDockState(
    settings: DockingSettings = DockingSettings(),
    initialLayout: () -> DockLayout = { DockLayout.empty() },
    builder: DockStateBuilder.() -> Unit,
): DockState = remember { DockState(initialLayout(), settings, builder) }
