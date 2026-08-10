package com.seanproctor.docking.spi

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Docking chrome colors - a 1:1 map of ModernDocking's theme keys. Adapters derive these
 * from their design system's theme ([MaterialTheme]/[JewelTheme]); the defaults mirror
 * ModernDocking's hardcoded fallbacks.
 */
@Immutable
public class DockingTheme(
    public val handleBackground: Color = Color.White,
    public val handleForeground: Color = Color.Black,
    public val handleOutline: Color = Color(0xFF666666),
    /** ModernDocking's translucent drop preview: #42c0ff at alpha 85/255. */
    public val overlayBackground: Color = Color(0x5542C0FF),
    public val activeHighlightBorder: Color = Color(0xFF4285F4),
    public val inactiveHighlightBorder: Color = Color(0xFFBDBDBD),
    public val headerBackground: Color = Color(0xFFEEEEEE),
    public val headerForeground: Color = Color(0xFF212121),
    public val toolbarBackground: Color = Color(0xFFF5F5F5),
) {
    public companion object {
        public val Default: DockingTheme = DockingTheme()
    }
}

public val LocalDockingTheme: ProvidableCompositionLocal<DockingTheme> =
    staticCompositionLocalOf { DockingTheme.Default }
