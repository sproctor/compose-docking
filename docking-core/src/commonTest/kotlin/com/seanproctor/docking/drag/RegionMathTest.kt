package com.seanproctor.docking.drag

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.seanproctor.docking.model.DockRegion
import kotlin.test.Test
import kotlin.test.assertEquals

class RegionMathTest {

    private val bounds = Rect(0f, 0f, 200f, 100f)

    @Test
    fun centerOfBoundsIsCenter() {
        assertEquals(DockRegion.Center, resolveRegion(bounds, Offset(100f, 50f)))
    }

    @Test
    fun nearEdgesWithinSensitivityPickEdgeRegions() {
        // 35% of width = 70, of height = 35. The closer axis (normalized) wins.
        assertEquals(DockRegion.West, resolveRegion(bounds, Offset(10f, 50f)))
        assertEquals(DockRegion.East, resolveRegion(bounds, Offset(190f, 50f)))
        assertEquals(DockRegion.North, resolveRegion(bounds, Offset(100f, 5f)))
        assertEquals(DockRegion.South, resolveRegion(bounds, Offset(100f, 95f)))
    }

    @Test
    fun closerAxisWinsInCorners() {
        // Near the top-left corner but much closer (normalized) to the left edge.
        assertEquals(DockRegion.West, resolveRegion(bounds, Offset(2f, 20f)))
        // Near the top-left corner but much closer (normalized) to the top edge.
        assertEquals(DockRegion.North, resolveRegion(bounds, Offset(60f, 2f)))
    }

    @Test
    fun justOutsideSensitivityIsCenter() {
        // hFraction = 80/200 = 0.4 >= 0.35; vFraction = 50/100 = 0.5 -> Center.
        assertEquals(DockRegion.Center, resolveRegion(bounds, Offset(80f, 50f)))
    }

    @Test
    fun degenerateBoundsAreCenter() {
        assertEquals(DockRegion.Center, resolveRegion(Rect.Zero, Offset.Zero))
    }

    @Test
    fun previewRectsCoverHalfTowardEdge() {
        assertEquals(bounds, previewRectFor(bounds, DockRegion.Center))
        assertEquals(Rect(0f, 0f, 100f, 100f), previewRectFor(bounds, DockRegion.West))
        assertEquals(Rect(100f, 0f, 200f, 100f), previewRectFor(bounds, DockRegion.East))
        assertEquals(Rect(0f, 0f, 200f, 50f), previewRectFor(bounds, DockRegion.North))
        assertEquals(Rect(0f, 50f, 200f, 100f), previewRectFor(bounds, DockRegion.South))
    }
}
