package com.seanproctor.docking.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import com.seanproctor.docking.model.WindowId

/**
 * Takes over the drag half of the dock areas' header, tab and tab-group gestures: provide
 * one around a [com.seanproctor.docking.ui.DockArea] and every grip in it asks
 * [gripModifier] for its drag, while the core keeps handling presses and double clicks.
 *
 * For a window system whose drags must be carried by the platform's drag-and-drop session
 * (native Wayland, where no window can see the pointer outside itself): the grip starts the
 * platform session and the core drag together, through [DockDragGrip.begin]. Each window's
 * drop target then reports to [DockingWindowHost.hoverInWindow], [DockingWindowHost.exitWindow]
 * and [DockingWindowHost.dropInWindow], and the source's end of the session to
 * [DockingWindowHost.endPlatformDrag] (or [DockingWindowHost.cancel]).
 */
public interface DockingDragHandover {
    /** The modifier that drags [grip]'s source. Called once per grip, in composition. */
    @Composable
    public fun gripModifier(grip: DockDragGrip): Modifier
}

/** The [DockingDragHandover] the dock areas below hand their drags to, or null for their own. */
public val LocalDockingDragHandover: ProvidableCompositionLocal<DockingDragHandover?> =
    staticCompositionLocalOf { null }

/**
 * One drag source (a dockable's header, a tab, a tab group's gutter), as a
 * [DockingDragHandover] drives it. Positions are in the grip's own coordinates.
 */
public class DockDragGrip internal constructor(
    /** Stable identity of the source, e.g. for a modifier key. */
    public val key: Any,
    /** The window the grip is in. */
    public val windowId: WindowId,
    /** What the drag is called: the dockable's title, or the selected tab's for a group. */
    public val title: String,
    private val coordinates: () -> LayoutCoordinates?,
    private val sourceBounds: () -> Rect?,
    private val onBegin: (positionInWindow: Offset) -> Boolean,
    private val onStayLocal: (positionInWindow: Offset) -> Boolean,
    private val onEndLocal: (released: Boolean, positionInWindow: Offset?) -> Unit,
) {
    /** The dragged dockable or group, in window-root pixels: what a drag icon pictures. */
    public val sourceBoundsInWindow: Rect? get() = sourceBounds()

    /** [position] in the grip, in window-root pixels. */
    public fun toWindow(position: Offset): Offset = coordinates()?.takeIf { it.isAttached }?.localToRoot(position) ?: position

    /**
     * Starts the core drag at [position] in the grip, carried by the platform from here on
     * (see [DockingDragHandover]). False when no drag started (another is running, or the
     * source cannot be dragged).
     */
    public fun begin(position: Offset): Boolean = onBegin(toWindow(position))

    /**
     * A sample at [position] in the grip before the drag has begun. True keeps the gesture in
     * the grip's hands (a tab reordering within its strip); false is the moment to [begin].
     */
    public fun stayLocal(position: Offset): Boolean = onStayLocal(toWindow(position))

    /** The gesture ended without beginning a drag; [released] tells a release from an abandon. */
    public fun endLocal(
        released: Boolean,
        position: Offset?,
    ) {
        onEndLocal(released, position?.let(::toWindow))
    }
}
