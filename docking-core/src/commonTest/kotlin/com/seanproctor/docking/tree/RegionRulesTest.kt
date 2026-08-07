package com.seanproctor.docking.tree

import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockingStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegionRulesTest {

    @Test
    fun centerAlwaysAllowed() {
        for (style in DockingStyle.entries) {
            assertTrue(style.allowsRegion(DockRegion.Center), "$style should allow Center")
        }
    }

    @Test
    fun styleRegionMatrix() {
        // Vertical dockables (tall panels) split East/West; Horizontal split North/South.
        val cases = mapOf(
            DockingStyle.Both to mapOf(
                DockRegion.North to true, DockRegion.South to true,
                DockRegion.East to true, DockRegion.West to true,
            ),
            DockingStyle.Vertical to mapOf(
                DockRegion.North to false, DockRegion.South to false,
                DockRegion.East to true, DockRegion.West to true,
            ),
            DockingStyle.Horizontal to mapOf(
                DockRegion.North to true, DockRegion.South to true,
                DockRegion.East to false, DockRegion.West to false,
            ),
            DockingStyle.CenterOnly to mapOf(
                DockRegion.North to false, DockRegion.South to false,
                DockRegion.East to false, DockRegion.West to false,
            ),
        )
        for ((style, regions) in cases) {
            for ((region, expected) in regions) {
                assertEquals(expected, style.allowsRegion(region), "$style at $region")
            }
        }
    }

    @Test
    fun regionAllowedIsBidirectional() {
        // Both sides must permit the orientation.
        assertTrue(isRegionAllowed(DockRegion.East, DockingStyle.Both, DockingStyle.Vertical))
        assertFalse(isRegionAllowed(DockRegion.East, DockingStyle.Both, DockingStyle.Horizontal))
        assertFalse(isRegionAllowed(DockRegion.East, DockingStyle.Horizontal, DockingStyle.Both))
        assertTrue(isRegionAllowed(DockRegion.Center, DockingStyle.CenterOnly, DockingStyle.CenterOnly))
    }
}
