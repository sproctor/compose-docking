package com.seanproctor.docking.state

import androidx.compose.runtime.Stable
import com.seanproctor.docking.model.TabPreference

/**
 * Behavioral settings for a [DockState]. Fixed at creation: the `*Always` values switch
 * the layout into always-display-tabs mode, which changes how tree nodes are built, so
 * flipping it on a live layout would leave existing panes inconsistent with new ones.
 * Pair it with `dockLayout(alwaysDisplayTabs = ...)` so the initial layout is built the
 * same way.
 */
@Stable
public class DockingSettings(
    /** Tab placement used when a dockable doesn't state its own preference. */
    public val defaultTabPreference: TabPreference = TabPreference.Bottom,
) {
    /** True when every dockable renders as a tab group even while alone. */
    public val alwaysDisplayTabs: Boolean
        get() = defaultTabPreference == TabPreference.TopAlways ||
            defaultTabPreference == TabPreference.BottomAlways
}
