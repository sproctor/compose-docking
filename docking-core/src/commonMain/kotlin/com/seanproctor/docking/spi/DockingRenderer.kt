package com.seanproctor.docking.spi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import com.seanproctor.docking.model.DockableId

/**
 * The slot interface design-system adapters implement (Material 3, Jewel, or custom).
 * Core builds the models - including all gesture [Modifier]s - and calls these slots;
 * adapters own structure and styling but never gesture logic.
 *
 * The default is the unstyled [DebugDockingRenderer]; wrap your content in an adapter's
 * provider (e.g. `Material3Docking { ... }`) for a real look.
 */
@Stable
public interface DockingRenderer {

    /** The tab strip of a tab group. Apply each tab's `dragModifier` and `reorderOffsetX`. */
    @Composable
    public fun TabStrip(model: TabStripModel, modifier: Modifier)

    /** A dockable's title bar: [icon][title][maximize indicator][menu][close]. */
    @Composable
    public fun DockableHeader(model: HeaderModel, modifier: Modifier)

    /** The draggable divider between split children. */
    @Composable
    public fun SplitDivider(model: DividerModel, modifier: Modifier)

    /** One 32dp docking handle icon shown during drags. */
    @Composable
    public fun DockingHandle(model: HandleModel, modifier: Modifier)

    /** The translucent drop preview (core positions and sizes it). */
    @Composable
    public fun DropOverlay(model: DropOverlayModel, modifier: Modifier)

    /** Shown when a window's dock area is empty. */
    @Composable
    public fun EmptyRootPlaceholder(modifier: Modifier)

    /** Shown in place of a dockable that is in the layout but not (yet) registered. */
    @Composable
    public fun MissingDockable(id: DockableId, modifier: Modifier)

    /** The ghost card following the pointer during a drag. */
    @Composable
    public fun DragPreview(title: String, icon: Painter?)
}

public val LocalDockingRenderer: ProvidableCompositionLocal<DockingRenderer> =
    staticCompositionLocalOf { DebugDockingRenderer }
