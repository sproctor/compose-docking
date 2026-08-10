package com.seanproctor.docking.material3

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.seanproctor.docking.spi.DockingTheme
import com.seanproctor.docking.spi.LocalDockingRenderer
import com.seanproctor.docking.spi.LocalDockingTheme

/**
 * Installs the Material 3 docking renderer and a [DockingTheme] derived from the ambient
 * [MaterialTheme]. Wrap every docking surface (the main window's content and
 * `FloatingDockWindows`) in this - inside your `MaterialTheme`:
 *
 * ```
 * MaterialTheme(colorScheme) {
 *     Material3Docking {
 *         DockArea(state, Modifier.fillMaxSize())
 *     }
 * }
 * ```
 */
@Composable
public fun Material3Docking(
    theme: DockingTheme = dockingThemeFromMaterial(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalDockingRenderer provides Material3DockingRenderer,
        LocalDockingTheme provides theme,
        content = content,
    )
}

/** Maps ModernDocking's theme keys onto the ambient [MaterialTheme.colorScheme]. */
@Composable
public fun dockingThemeFromMaterial(): DockingTheme {
    val colors = MaterialTheme.colorScheme
    return DockingTheme(
        handleBackground = colors.surfaceContainerHigh,
        handleForeground = colors.onSurface,
        handleOutline = colors.outline,
        overlayBackground = colors.primary.copy(alpha = 0.33f),
        activeHighlightBorder = colors.primary,
        inactiveHighlightBorder = colors.outlineVariant,
        headerBackground = colors.surfaceContainer,
        headerForeground = colors.onSurface,
        toolbarBackground = colors.surfaceContainerLow,
    )
}
