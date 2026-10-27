package com.seanproctor.docking.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An AWT window's geometry as the docking system takes it: AWT's screen positions are the
 * shared virtual-device coordinates already, and moving a window moves its frame, not its
 * content.
 */
class AwtDockHostWindowTest {
    // Not scaled by the window's density: windows on monitors of different densities have to
    // stay in one space, and AWT's virtual-device coordinates are that space.
    @Test
    fun contentBoundsAreAwtsOwnCoordinates() {
        assertEquals(Rect(100f, 50f, 900f, 650f), contentRect(100, 50, 800, 600))
    }

    // An undecorated window: frame and content coincide.
    @Test
    fun anUndecoratedFrameGoesWhereTheContentShould() {
        assertEquals(
            IntOffset(150, 80),
            frameLocationFor(Offset(150f, 80f), frameX = 100, frameY = 50, contentX = 100, contentY = 50),
        )
    }

    // A decorated window: the content sits below a 30-unit title bar and inside a 4-unit
    // border, and stays there - asking for the content at a point puts the frame above it.
    @Test
    fun aDecoratedFrameKeepsItsOffsetFromTheContent() {
        assertEquals(
            IntOffset(146, 50),
            frameLocationFor(Offset(150f, 80f), frameX = 96, frameY = 20, contentX = 100, contentY = 50),
        )
    }
}
