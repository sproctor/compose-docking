package com.seanproctor.docking.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.seanproctor.docking.model.TabPreference

/**
 * Behavioral settings for a [DockState]. [defaultTabPreference] is fixed at creation
 * (`*Always` values switch the whole layout into always-display-tabs mode, which changes
 * how tree nodes are created); the rest may be toggled at runtime.
 */
@Stable
public class DockingSettings(
    /** Tab placement used when a dockable doesn't state its own preference. */
    public val defaultTabPreference: TabPreference = TabPreference.Bottom,
    activeHighlighterEnabled: Boolean = true,
) {
    /** Whether the focused dockable gets a highlight border. */
    public var activeHighlighterEnabled: Boolean by mutableStateOf(activeHighlighterEnabled)

    /** True when every dockable renders as a tab group even while alone. */
    public val alwaysDisplayTabs: Boolean
        get() = defaultTabPreference == TabPreference.TopAlways ||
            defaultTabPreference == TabPreference.BottomAlways
}
