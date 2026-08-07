package com.seanproctor.docking.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What the current platform's docking integration supports. Floating OS windows and
 * cross-window drag exist only on desktop (JVM); everything else is identical everywhere.
 */
@Immutable
public class DockCapabilities(
    public val floatingWindows: Boolean,
    public val crossWindowDrag: Boolean,
) {
    public companion object {
        public val None: DockCapabilities = DockCapabilities(
            floatingWindows = false,
            crossWindowDrag = false,
        )
    }
}

public expect val platformDockCapabilities: DockCapabilities

public val LocalDockCapabilities: ProvidableCompositionLocal<DockCapabilities> =
    staticCompositionLocalOf { platformDockCapabilities }
