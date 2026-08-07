package com.seanproctor.docking.model

/**
 * A region of a docking target. Docking [Center] joins/creates a tab group; the edge
 * regions split the target.
 */
public enum class DockRegion {
    Center,
    North,
    South,
    East,
    West,
}

/**
 * Which split regions a dockable participates in, checked bidirectionally: a drop into an
 * edge region is allowed only if both the drop target's style and the dragged dockable's
 * style permit that orientation.
 *
 * - [Horizontal]: may take part in North/South splits (and Center).
 * - [Vertical]: may take part in East/West splits (and Center).
 * - [Both]: any region.
 * - [CenterOnly]: tabs only; never splits.
 */
public enum class DockingStyle {
    Horizontal,
    Vertical,
    Both,
    CenterOnly,
}

/**
 * The window edges that can host auto-hide toolbars. There is deliberately no North
 * toolbar, matching ModernDocking.
 */
public enum class AutoHideSide {
    West,
    East,
    South,
}

/**
 * Where a dockable's tab strip is placed when it is part of a tab group.
 *
 * The `*Always` variants additionally force the dockable to render as a (single-tab)
 * tab group even when alone. When used as the default preference in
 * [com.seanproctor.docking.state.DockingSettings], `*Always` enables always-display-tabs
 * mode for the whole layout.
 */
public enum class TabPreference {
    Default,
    Top,
    Bottom,
    TopAlways,
    BottomAlways,
}

/**
 * Orientation of a split: [Horizontal] places children side by side (East/West splits),
 * [Vertical] stacks them (North/South splits).
 */
public enum class SplitOrientation {
    Horizontal,
    Vertical,
}

/** Whether a docking window is the application main window or a floating window. */
public enum class WindowKind {
    Main,
    Floating,
}
