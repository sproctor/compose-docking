package com.seanproctor.docking.spi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * Menu content passed to renderers as data: each design system renders it with its own
 * menu component. Core builds the standard dockable menu (view mode, maximize, close);
 * apps can contribute extra items per dockable.
 */
public sealed interface DockMenuItem {

    @Immutable
    public class Action(
        public val label: String,
        public val icon: (@Composable () -> Unit)? = null,
        public val enabled: Boolean = true,
        public val selected: Boolean = false,
        public val onClick: () -> Unit,
    ) : DockMenuItem

    @Immutable
    public class SubMenu(
        public val label: String,
        public val items: List<DockMenuItem>,
    ) : DockMenuItem

    public data object Separator : DockMenuItem
}
