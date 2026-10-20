package com.seanproctor.docking.tao

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A floating window chasing the pointer that drags it, at density 2: the window starts at
 * (100, 100) logical, grabbed at (20, 10) px in it, so the pointer is at (110, 105) on screen.
 * Wherever the pointer goes, the window belongs at the pointer minus (10, 5).
 */
class WindowChaseTest {
    private val start = Offset(100f, 100f)
    private val press = Offset(20f, 10f)

    private fun chase() = WindowChase(start, press, density = 2f)

    @Test
    fun theFirstMoveFollowsThePointerFromThePress() {
        // Pointer +20 px = +10 logical, window not moved yet.
        assertEquals(Offset(110f, 100f), chase().next(Offset(40f, 10f), observed = start))
    }

    @Test
    fun aMoveStillPendingIsNotCountedTwice() {
        val chase = chase()
        chase.next(Offset(40f, 10f), observed = start)
        // The pointer moves another +10 logical before the first move lands: the event is
        // still relative to the window at (100, 100), pointer on screen at (130, 105).
        assertEquals(Offset(120f, 100f), chase.next(Offset(60f, 10f), observed = start))
    }

    @Test
    fun aMoveThatLandedIsCountedOnce() {
        val chase = chase()
        chase.next(Offset(40f, 10f), observed = start)
        // The window is now at (110, 100) and the pointer has moved +10 logical more: it is
        // at (130, 105) on screen, (20, 5) logical = (40, 10) px in the moved window.
        assertEquals(Offset(120f, 100f), chase.next(Offset(40f, 10f), observed = Offset(110f, 100f)))
    }

    @Test
    fun aMoveLandingBetweenEventsIsPickedUpLate() {
        val chase = chase()
        chase.next(Offset(40f, 10f), observed = start)
        chase.next(Offset(60f, 10f), observed = start)
        // Both moves land at once (window at (120, 100)); the pointer has not moved since,
        // so in the window it is back at the press point.
        assertEquals(Offset(120f, 100f), chase.next(press, observed = Offset(120f, 100f)))
    }

    @Test
    fun anUnknownWindowPositionFallsBackToThePointersOwnMovement() {
        val chase = chase()
        assertEquals(Offset(105f, 102f), chase.next(Offset(30f, 14f), observed = null))
    }
}
