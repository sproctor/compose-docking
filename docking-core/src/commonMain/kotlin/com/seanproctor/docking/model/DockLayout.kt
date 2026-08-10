package com.seanproctor.docking.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Window position and size in density-independent pixels, in screen coordinates on
 * desktop (or the coordinate space of the host layer on other platforms).
 */
@Immutable
@Serializable
public data class WindowBounds(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

/**
 * A maximized dockable in a window. The pre-maximize tree is stored in [savedRoot], so
 * restoring is a pure value swap and the maximized state survives persistence.
 */
@Immutable
public data class MaximizedState(
    val dockableId: DockableId,
    val savedRoot: DockNode,
)

/**
 * One docking window: the main window or a floating window. A window with a `null` [root]
 * is empty (renderers show a placeholder).
 */
@Immutable
public data class DockWindow(
    val id: WindowId,
    val kind: WindowKind,
    val root: DockNode?,
    val maximized: MaximizedState? = null,
    val bounds: WindowBounds? = null,
)

/**
 * The complete dock layout of the application: the main window plus any floating windows.
 * This value contains exactly what is persisted; transient UI state lives on
 * [com.seanproctor.docking.state.DockState].
 */
@Immutable
public data class DockLayout(
    val windows: List<DockWindow>,
) {
    init {
        require(windows.isNotEmpty()) { "DockLayout must contain at least the main window" }
        require(windows.first().kind == WindowKind.Main) {
            "windows[0] must be the main window"
        }
        require(windows.drop(1).all { it.kind == WindowKind.Floating }) {
            "only windows[0] may be the main window"
        }
        require(windows.distinctBy { it.id }.size == windows.size) {
            "duplicate window ids in layout"
        }
    }

    public val mainWindow: DockWindow get() = windows.first()

    public val floatingWindows: List<DockWindow> get() = windows.drop(1)

    public fun window(id: WindowId): DockWindow? = windows.firstOrNull { it.id == id }

    public fun requireWindow(id: WindowId): DockWindow =
        window(id) ?: throw NoSuchElementException("No window with id '$id' in layout")

    public fun replaceWindow(window: DockWindow): DockLayout =
        copy(windows = windows.map { if (it.id == window.id) window else it })

    public companion object {
        /** A layout with a single empty main window. */
        public fun empty(): DockLayout = DockLayout(
            windows = listOf(
                DockWindow(id = WindowId.MAIN, kind = WindowKind.Main, root = null),
            ),
        )
    }
}
