package com.seanproctor.docking.persistence

import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.tree.mergeFloatingIntoMain
import com.seanproctor.docking.state.DockableSpec
import com.seanproctor.docking.state.DockingEvent
import com.seanproctor.docking.state.LayoutChangeKind
import kotlinx.serialization.json.JsonElement

/**
 * Creates dockables referenced by a saved layout that aren't registered yet - the port of
 * ModernDocking's dynamic dockable factory. Resolved specs are auto-registered.
 */
public fun interface DockableResolver {
    public fun resolve(id: DockableId, properties: JsonElement?): DockableSpec?
}

/** Captures the current layout, including per-dockable state from [DockableSpec.saveState]. */
public fun DockState.captureLayout(): PersistedApplicationLayout =
    layout.toPersisted { id -> registry[id]?.saveState?.invoke() }

/**
 * Replaces the current layout with [persisted].
 *
 * Referenced dockables missing from the registry are resolved via [resolver] (and
 * auto-registered); leaves that still can't be resolved are *retained* in the tree and
 * rendered as missing-dockable placeholders - late registration fills them in reactively,
 * and re-persisting round-trips them losslessly. Registered dockables get their
 * [DockableSpec.restoreState] called with the persisted properties.
 *
 * When [mergeFloatingWindows] is true (platforms without floating-window support), the
 * contents of floating windows are grafted into the main window instead.
 */
public fun DockState.restoreLayout(
    persisted: PersistedApplicationLayout,
    resolver: DockableResolver? = null,
    mergeFloatingWindows: Boolean = false,
) {
    for ((id, properties) in persisted.referencedDockables()) {
        var spec = registry[id]
        if (spec == null && resolver != null) {
            spec = resolver.resolve(id, properties)
            if (spec != null) registry.register(spec)
        }
        if (spec != null && properties != null) {
            spec.restoreState?.invoke(properties)
        }
    }
    var runtime = persisted.toRuntime()
    if (mergeFloatingWindows) {
        runtime = treeContext().mergeFloatingIntoMain(runtime)
    }
    layout = runtime
    emit(DockingEvent.LayoutChanged(LayoutChangeKind.Restored))
}
