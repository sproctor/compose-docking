package com.seanproctor.docking.jewel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.seanproctor.docking.spi.DockingTheme
import com.seanproctor.docking.spi.LocalDockingRenderer
import com.seanproctor.docking.spi.LocalDockingTheme
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * Installs the Jewel (IntelliJ look-and-feel) docking renderer and a [DockingTheme]
 * derived from the ambient [JewelTheme]. Works under both the standalone `IntUiTheme`
 * and the IDE `SwingBridgeTheme` - this adapter only *reads* from [JewelTheme], never
 * constructs one.
 *
 * ```
 * IntUiTheme(isDark = true) {
 *     JewelDocking {
 *         DockArea(state, Modifier.fillMaxSize())
 *     }
 * }
 * ```
 *
 * Note: Jewel requires the JetBrains Runtime at runtime.
 */
@Composable
public fun JewelDocking(
    theme: DockingTheme = dockingThemeFromJewel(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalDockingRenderer provides JewelDockingRenderer,
        LocalDockingTheme provides theme,
        content = content,
    )
}

/** Maps ModernDocking's theme keys onto the ambient [JewelTheme]. */
@Composable
public fun dockingThemeFromJewel(): DockingTheme {
    val colors = JewelTheme.globalColors
    val isDark = JewelTheme.isDark
    val content = JewelTheme.contentColor
    return DockingTheme(
        handleBackground = colors.panelBackground,
        handleForeground = content,
        handleOutline = colors.borders.normal,
        overlayBackground = colors.outlines.focused.copy(alpha = 0.33f),
        activeHighlightBorder = colors.outlines.focused,
        inactiveHighlightBorder = colors.borders.normal,
        headerBackground = colors.panelBackground,
        headerForeground = content,
        toolbarBackground = colors.panelBackground,
    )
}
