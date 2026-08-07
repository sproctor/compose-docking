package com.seanproctor.docking.persistence

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockingEvent
import com.seanproctor.docking.state.LayoutChangeKind

/**
 * A registry of named layout snapshots (ModernDocking's `DockingLayouts`), snapshot-state
 * backed so menus listing [names] update reactively.
 */
@Stable
public class NamedLayouts internal constructor(
    private val state: DockState,
) {
    private val layouts = mutableStateMapOf<String, PersistedApplicationLayout>()

    public val names: List<String> get() = layouts.keys.sorted()

    /** Snapshots the current layout under [name]. */
    public fun save(name: String) {
        layouts[name] = state.captureLayout()
        state.emit(DockingEvent.LayoutChanged(LayoutChangeKind.Added, name))
    }

    /** Restores the layout saved under [name]. Returns false when it doesn't exist. */
    public fun restore(name: String, resolver: DockableResolver? = null): Boolean {
        val layout = layouts[name] ?: return false
        state.restoreLayout(layout, resolver)
        return true
    }

    public fun remove(name: String) {
        if (layouts.remove(name) != null) {
            state.emit(DockingEvent.LayoutChanged(LayoutChangeKind.Removed, name))
        }
    }

    public operator fun get(name: String): PersistedApplicationLayout? = layouts[name]

    public fun put(name: String, layout: PersistedApplicationLayout) {
        layouts[name] = layout
        state.emit(DockingEvent.LayoutChanged(LayoutChangeKind.Added, name))
    }

    /** All named layouts, for serializing alongside the application layout. */
    public fun asMap(): Map<String, PersistedApplicationLayout> = layouts.toMap()

    public fun putAll(map: Map<String, PersistedApplicationLayout>) {
        layouts.putAll(map)
    }
}
