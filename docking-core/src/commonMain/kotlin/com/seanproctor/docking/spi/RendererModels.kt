package com.seanproctor.docking.spi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.SplitOrientation

/** Where the tab strip of a group is placed. */
public enum class TabPlacement { Top, Bottom }

/**
 * One tab of a tab group. [dragModifier] carries the core-built gesture handling
 * (select on click, in-strip reorder, drag-out escalation) - the renderer's only
 * obligation is to apply it (plus [reorderOffsetX] as an x-offset) to the tab element.
 */
@Stable
public class TabItemModel(
    public val id: DockableId,
    public val title: String,
    public val icon: Painter?,
    public val tooltip: String?,
    public val isSelected: Boolean,
    public val onSelect: () -> Unit,
    /**
     * Affordances the application draws inside this tab, from
     * [com.seanproctor.docking.state.DockableSpec.tabActions] - a close button, a dirty
     * marker, whatever it wants. The library contributes none, so an app that draws
     * nothing here gets tabs with no close button.
     */
    public val actions: @Composable () -> Unit,
    public val dragModifier: Modifier,
    /** Live x-offset in px while this tab is being reordered, else 0. */
    public val reorderOffsetX: Float,
)

@Stable
public class TabStripModel(
    public val tabs: List<TabItemModel>,
    public val selectedIndex: Int,
    public val placement: TabPlacement,
    /**
     * The selected dockable's [com.seanproctor.docking.state.DockableSpec.tabStripActions],
     * drawn at the trailing edge of the strip.
     */
    public val trailingActions: @Composable () -> Unit,
    /** Apply to empty strip area: dragging it moves the whole tab group. */
    public val gutterDragModifier: Modifier,
    /** Non-null while a drag hovers the strip: render an insertion caret before this index. */
    public val dropInsertionIndex: Int?,
)

/**
 * A dockable's title bar. [dragModifier] includes click-to-focus, drag-to-undock, and
 * double-click-to-maximize handling.
 */
@Stable
public class HeaderModel(
    public val id: DockableId,
    public val title: String,
    public val icon: Painter?,
    public val isActive: Boolean,
    public val dragModifier: Modifier,
    /**
     * Header affordances supplied by the application via
     * [com.seanproctor.docking.state.DockableSpec.trailingActions] - the overflow menu,
     * a maximized indicator, a close button, or anything else it wants. Renderers place
     * this at the trailing edge of the title bar; the library contributes nothing itself.
     */
    public val trailingActions: @Composable () -> Unit,
    /**
     * Per-dockable title-bar color overrides (ModernDocking's
     * `DockingHeaderUI.setBackgroundOverride`/`setForegroundOverride`). Null means the
     * renderer picks its own color from the ambient [DockingTheme] as usual; a non-null
     * [foreground] applies to the title text and the header's buttons.
     */
    public val background: Color? = null,
    public val foreground: Color? = null,
)

@Stable
public class DividerModel(
    public val orientation: SplitOrientation,
    public val isDragging: Boolean,
    /** Core-built: drag to resize, double-click to reset 50/50, resize cursor. */
    public val dragModifier: Modifier,
)

/** The 10 docking handles: 5 window-root and 5 over the hovered dockable. */
public enum class HandleKind {
    RootCenter, RootNorth, RootSouth, RootEast, RootWest,
    DockableCenter, DockableNorth, DockableSouth, DockableEast, DockableWest,
}

@Stable
public class HandleModel(
    public val kind: HandleKind,
    public val isHovered: Boolean,
    public val isEnabled: Boolean,
)

public enum class OverlayKind {
    /** Translucent area preview: full target for Center, half for edge regions. */
    Area,
    /** Thin caret marking a tab insertion point. */
    TabCaret,
}

@Stable
public class DropOverlayModel(
    public val kind: OverlayKind,
)
