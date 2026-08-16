package com.seanproctor.docking.spi

import androidx.compose.foundation.layout.Box
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

    /**
     * The frame around a whole pane - a dockable's header and body, or a tab group's strip
     * and body - as a [Modifier] applied to the two together.
     *
     * Every other slot renders one part of a pane, so anything that has to enclose the parts
     * has nowhere to go: a border drawn by [DockableHeader] stops at the bottom of the title
     * bar, and one drawn by the body starts below it. This is the slot that wraps both, for
     * a border around the pane, a shared background, a shadow, or an outline that picks out
     * the focused one.
     *
     * Returned as a [Modifier] rather than a wrapping composable so the pane keeps the layout
     * core gave it: the frame decorates, it does not restructure. Applied inside core's own
     * bounds reporting, so the pane's drop target stays the whole slot the layout gave it
     * however the frame decorates it - a frame that insets its content does not carve that
     * inset out of the dock area, leaving a band between panes that drags fall through.
     *
     * Defaults to no frame.
     */
    @Composable
    public fun paneFrame(model: PaneFrameModel): Modifier = Modifier

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

    /**
     * An empty anchor collapsed to a strip, when
     * [com.seanproctor.docking.state.DockingSettings.collapsedAnchorThickness] is set.
     *
     * The strip is the whole of what is left of an area whose dockables have all gone, so
     * it should read as "something belongs here" and stay legible at the configured
     * thickness - a tinted band, a rotated label, an icon. It remains a drop target, so
     * the drag overlay draws over it as usual.
     *
     * Defaults to blank space, which collapses the area without drawing anything.
     */
    @Composable
    public fun CollapsedAnchor(model: CollapsedAnchorModel, modifier: Modifier) {
        Box(modifier)
    }

    /** Shown in place of a dockable that is in the layout but not (yet) registered. */
    @Composable
    public fun MissingDockable(id: DockableId, modifier: Modifier)

    /** The ghost card following the pointer during a drag. */
    @Composable
    public fun DragPreview(title: String, icon: Painter?)
}

public val LocalDockingRenderer: ProvidableCompositionLocal<DockingRenderer> =
    staticCompositionLocalOf { DebugDockingRenderer }
