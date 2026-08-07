package com.seanproctor.docking.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints

/**
 * Rotates content ±90° while swapping its measured dimensions, so a horizontal row of
 * buttons renders as a vertical toolbar strip (ModernDocking's rotated auto-hide
 * buttons). `Modifier.rotate` alone doesn't swap the measured size — a custom layout is
 * required.
 *
 * @param clockwise true rotates +90° (text reads top-to-bottom; East strips),
 *   false rotates -90° (text reads bottom-to-top; West strips).
 */
internal fun Modifier.rotateVertically(clockwise: Boolean): Modifier = this
    .graphicsLayer { rotationZ = if (clockwise) 90f else -90f }
    .layout { measurable, constraints ->
        val placeable = measurable.measure(
            Constraints(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth,
            ),
        )
        layout(placeable.height, placeable.width) {
            placeable.place(
                x = -(placeable.width - placeable.height) / 2,
                y = (placeable.width - placeable.height) / 2,
            )
        }
    }
