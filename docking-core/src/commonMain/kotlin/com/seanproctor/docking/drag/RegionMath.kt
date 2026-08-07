package com.seanproctor.docking.drag

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.seanproctor.docking.model.DockRegion

/**
 * ModernDocking's region sensitivity: within 35% of an edge (on the closer axis) the
 * drop targets that edge region; otherwise Center.
 */
public const val REGION_SENSITIVITY: Float = 0.35f

/** The edge-region drop preview covers this fraction of the target (half). */
internal const val DROP_PREVIEW_FRACTION: Float = 0.5f

/**
 * Resolves the dock region for a pointer at [position] over [bounds]: normalized edge
 * distances on both axes; the closer axis wins; within [sensitivity] of that edge the
 * edge region is chosen, else [DockRegion.Center].
 */
public fun resolveRegion(
    bounds: Rect,
    position: Offset,
    sensitivity: Float = REGION_SENSITIVITY,
): DockRegion {
    if (bounds.width <= 0f || bounds.height <= 0f) return DockRegion.Center
    val x = (position.x - bounds.left).coerceIn(0f, bounds.width)
    val y = (position.y - bounds.top).coerceIn(0f, bounds.height)
    val hFraction = minOf(x, bounds.width - x) / bounds.width
    val vFraction = minOf(y, bounds.height - y) / bounds.height
    return if (hFraction < vFraction) {
        when {
            hFraction >= sensitivity -> DockRegion.Center
            x < bounds.width - x -> DockRegion.West
            else -> DockRegion.East
        }
    } else {
        when {
            vFraction >= sensitivity -> DockRegion.Center
            y < bounds.height - y -> DockRegion.North
            else -> DockRegion.South
        }
    }
}

/** The drop-preview rect for docking at [region] of [bounds]: half toward the edge, full for Center. */
internal fun previewRectFor(bounds: Rect, region: DockRegion): Rect = when (region) {
    DockRegion.Center -> bounds
    DockRegion.West -> Rect(bounds.left, bounds.top, bounds.left + bounds.width * DROP_PREVIEW_FRACTION, bounds.bottom)
    DockRegion.East -> Rect(bounds.right - bounds.width * DROP_PREVIEW_FRACTION, bounds.top, bounds.right, bounds.bottom)
    DockRegion.North -> Rect(bounds.left, bounds.top, bounds.right, bounds.top + bounds.height * DROP_PREVIEW_FRACTION)
    DockRegion.South -> Rect(bounds.left, bounds.bottom - bounds.height * DROP_PREVIEW_FRACTION, bounds.right, bounds.bottom)
}
