package com.seanproctor.docking.persistence

import com.seanproctor.docking.model.AnchorId
import com.seanproctor.docking.model.DockLayout
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockWindow
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.MaximizedState
import com.seanproctor.docking.model.NodeId
import com.seanproctor.docking.model.SplitOrientation
import com.seanproctor.docking.model.WindowBounds
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.model.WindowKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * The serialized layout schema. Deliberately decoupled from the runtime model so the
 * runtime can evolve without breaking saved layouts; [version] plus ignore-unknown-keys
 * covers additive changes, [LayoutMigration] covers breaking ones.
 */
@Serializable
public data class PersistedApplicationLayout(
    val version: Int = CURRENT_LAYOUT_VERSION,
    val windows: List<PersistedWindow> = emptyList(),
) {
    public companion object {
        public const val CURRENT_LAYOUT_VERSION: Int = 1
    }
}

private const val CURRENT_LAYOUT_VERSION = PersistedApplicationLayout.CURRENT_LAYOUT_VERSION

@Serializable
public data class PersistedWindow(
    val id: String,
    val kind: String, // "main" | "floating"
    val bounds: WindowBounds? = null,
    val root: PersistedNode? = null,
    val maximizedDockable: String? = null,
    val maximizedSavedRoot: PersistedNode? = null,
)

@Serializable
public sealed interface PersistedNode {

    @Serializable
    @SerialName("leaf")
    public data class Leaf(
        val nodeId: String,
        val dockableId: String,
        /** Per-dockable state from [com.seanproctor.docking.state.DockableSpec.saveState]. */
        val properties: JsonElement? = null,
    ) : PersistedNode

    @Serializable
    @SerialName("split")
    public data class Split(
        val nodeId: String,
        val orientation: String, // "horizontal" | "vertical"
        val first: PersistedNode,
        val second: PersistedNode,
        val proportion: Float,
    ) : PersistedNode

    @Serializable
    @SerialName("tabs")
    public data class Tabs(
        val nodeId: String,
        val tabs: List<Leaf>,
        val selectedIndex: Int = 0,
    ) : PersistedNode

    @Serializable
    @SerialName("anchor")
    public data class Anchor(
        val nodeId: String,
        val anchorId: String,
    ) : PersistedNode
}

// ----- Runtime -> persisted -----

internal fun DockLayout.toPersisted(
    propertiesOf: (DockableId) -> JsonElement?,
): PersistedApplicationLayout = PersistedApplicationLayout(
    windows = windows.map { it.toPersisted(propertiesOf) },
)

private fun DockWindow.toPersisted(
    propertiesOf: (DockableId) -> JsonElement?,
): PersistedWindow = PersistedWindow(
    id = id.value,
    kind = if (kind == WindowKind.Main) "main" else "floating",
    bounds = bounds,
    root = root?.toPersisted(propertiesOf),
    maximizedDockable = maximized?.dockableId?.value,
    maximizedSavedRoot = maximized?.savedRoot?.toPersisted(propertiesOf),
)

private fun DockNode.toPersisted(
    propertiesOf: (DockableId) -> JsonElement?,
): PersistedNode = when (this) {
    is DockNode.Leaf -> PersistedNode.Leaf(id.value, dockableId.value, propertiesOf(dockableId))
    is DockNode.Anchor -> PersistedNode.Anchor(id.value, anchorId.value)
    is DockNode.Split -> PersistedNode.Split(
        nodeId = id.value,
        orientation = if (orientation == SplitOrientation.Horizontal) "horizontal" else "vertical",
        first = first.toPersisted(propertiesOf),
        second = second.toPersisted(propertiesOf),
        proportion = proportion,
    )
    is DockNode.Tabs -> PersistedNode.Tabs(
        nodeId = id.value,
        tabs = tabs.map { it.toPersisted(propertiesOf) as PersistedNode.Leaf },
        selectedIndex = selectedIndex,
    )
}

// ----- Persisted -> runtime -----

internal fun PersistedApplicationLayout.toRuntime(): DockLayout {
    val windows = windows.map { it.toRuntime() }
    // Guarantee the main-window invariant even for hand-edited files.
    val main = windows.firstOrNull { it.kind == WindowKind.Main }
        ?: DockWindow(WindowId.MAIN, WindowKind.Main, root = null)
    return DockLayout(listOf(main) + windows.filter { it.kind == WindowKind.Floating })
}

private fun PersistedWindow.toRuntime(): DockWindow {
    val savedRoot = maximizedSavedRoot?.toRuntime()
    return DockWindow(
        id = WindowId(id),
        kind = if (kind == "main") WindowKind.Main else WindowKind.Floating,
        root = root?.toRuntime(),
        maximized = if (maximizedDockable != null && savedRoot != null) {
            MaximizedState(DockableId(maximizedDockable), savedRoot)
        } else {
            null
        },
        bounds = bounds,
    )
}

private fun PersistedNode.toRuntime(): DockNode = when (this) {
    is PersistedNode.Leaf -> DockNode.Leaf(NodeId(nodeId), DockableId(dockableId))
    is PersistedNode.Anchor -> DockNode.Anchor(NodeId(nodeId), AnchorId(anchorId))
    is PersistedNode.Split -> DockNode.Split(
        id = NodeId(nodeId),
        orientation = if (orientation == "horizontal") SplitOrientation.Horizontal else SplitOrientation.Vertical,
        first = first.toRuntime(),
        second = second.toRuntime(),
        proportion = proportion.coerceIn(0f, 1f),
    )
    is PersistedNode.Tabs -> DockNode.Tabs(
        id = NodeId(nodeId),
        tabs = tabs.map { it.toRuntime() as DockNode.Leaf },
        selectedIndex = selectedIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0)),
    )
}

/** All dockable ids referenced anywhere in a persisted layout. */
internal fun PersistedApplicationLayout.referencedDockables(): Map<DockableId, JsonElement?> {
    val result = linkedMapOf<DockableId, JsonElement?>()
    fun visit(node: PersistedNode?) {
        when (node) {
            null -> Unit
            is PersistedNode.Leaf -> result[DockableId(node.dockableId)] = node.properties
            is PersistedNode.Anchor -> Unit
            is PersistedNode.Split -> {
                visit(node.first)
                visit(node.second)
            }
            is PersistedNode.Tabs -> node.tabs.forEach(::visit)
        }
    }
    for (window in windows) {
        visit(window.root)
        visit(window.maximizedSavedRoot)
    }
    return result
}
