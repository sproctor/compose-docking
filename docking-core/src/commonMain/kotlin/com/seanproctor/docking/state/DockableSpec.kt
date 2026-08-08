package com.seanproctor.docking.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import com.seanproctor.docking.model.AnchorId
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.DockableOptions
import kotlinx.serialization.json.JsonElement

/**
 * Everything the library needs to know about one dockable: identity, behavior options,
 * reactive presentation (composable lambdas re-read state, unlike ModernDocking's
 * constant-return contract), per-dockable persisted state, and the content itself.
 *
 * Content state note: within a window, content keeps all its internal state when the
 * dockable is re-parented (tab ↔ split moves). Moves *between windows* restart the
 * composition; state hoisted into `rememberSaveable` (or the app's own holders) survives
 * those too.
 */
@Stable
public class DockableSpec(
    public val id: DockableId,
    public val options: DockableOptions = DockableOptions(),
    /** Tab/header title; re-evaluated whenever state it reads changes. */
    public val title: @Composable () -> String,
    public val icon: (@Composable () -> Painter)? = null,
    public val tooltip: (@Composable () -> String)? = null,
    /**
     * Overrides the title bar's colors for this dockable (ModernDocking's
     * `setBackgroundOverride`/`setForegroundOverride`). Null keeps the renderer's theme
     * colors; [headerForeground] covers the title text and the header's buttons.
     */
    public val headerBackground: (@Composable () -> Color)? = null,
    public val headerForeground: (@Composable () -> Color)? = null,
    /**
     * Close veto (ModernDocking's `requestClose`). May suspend — e.g. to show a
     * confirmation dialog. Return false to keep the dockable open.
     */
    public val canClose: suspend () -> Boolean = { true },
    /** Serializes per-dockable state into persisted layouts (replaces `@DockingProperty`). */
    public val saveState: (() -> JsonElement)? = null,
    /** Restores per-dockable state when a layout is loaded. */
    public val restoreState: ((JsonElement) -> Unit)? = null,
    public val content: @Composable () -> Unit,
)

/**
 * The shared registry of dockables for one [DockState]. Snapshot-state backed: late
 * registration reactively fills in any missing-dockable placeholders already on screen.
 */
@Stable
public class DockableRegistry internal constructor() {
    private val specs = mutableStateMapOf<DockableId, DockableSpec>()

    public fun register(spec: DockableSpec) {
        specs[spec.id] = spec
    }

    public fun unregister(id: DockableId) {
        specs.remove(id)
    }

    public operator fun get(id: DockableId): DockableSpec? = specs[id]

    public fun isRegistered(id: DockableId): Boolean = specs.containsKey(id)

    public val all: Collection<DockableSpec> get() = specs.values

    internal fun anchorOf(id: DockableId): AnchorId? = specs[id]?.options?.anchor

    internal fun optionsOf(id: DockableId): DockableOptions =
        specs[id]?.options ?: DockableOptions()
}
