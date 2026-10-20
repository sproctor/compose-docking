package com.seanproctor.docking.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.max
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.DockableOptions
import com.seanproctor.docking.model.SplitOrientation

/**
 * True when [node] holds no dockables at all - an anchor's placeholder, or a split of
 * nothing but those. Collapsing is a property of the whole subtree, not of one node: a
 * split whose two sides have both emptied out is itself an empty area, and it is that
 * split's own parent that gives the space back.
 */
internal fun isEmptyArea(node: DockNode): Boolean = when (node) {
    is DockNode.Anchor -> true
    is DockNode.Split -> isEmptyArea(node.first) && isEmptyArea(node.second)
    is DockNode.Leaf, is DockNode.Tabs -> false
}

/**
 * A dockable's own minimum: [DockableOptions.minSize], with each axis it leaves unspecified
 * taken from [default].
 */
internal fun DockableOptions.resolvedMinSize(default: DpSize): DpSize = DpSize(
    width = if (minSize.width.isSpecified) minSize.width else default.width,
    height = if (minSize.height.isSpecified) minSize.height else default.height,
)

/**
 * The smallest size [node] can be laid out at without squeezing any pane in it below its
 * minimum.
 *
 * A tab group needs room for the largest of its tabs, not just the selected one, so
 * switching tabs never moves a divider. A split adds its sides up along its own axis -
 * plus the divider between them - and takes the larger of the two across it. An empty
 * area needs [emptyMin]: the default pane minimum while it holds its slot at full size, and
 * nothing when it collapses to a strip, since a strip's size is fixed rather than shared out.
 */
internal fun DockNode.minSize(
    paneMin: (DockableId) -> DpSize,
    emptyMin: DpSize,
    divider: Dp,
): DpSize = when (this) {
    is DockNode.Leaf -> paneMin(dockableId)
    is DockNode.Tabs -> tabs.fold(DpSize.Zero) { acc, tab ->
        val min = paneMin(tab.dockableId)
        DpSize(max(acc.width, min.width), max(acc.height, min.height))
    }
    is DockNode.Anchor -> emptyMin
    is DockNode.Split -> if (isEmptyArea(this)) {
        emptyMin
    } else {
        val a = first.minSize(paneMin, emptyMin, divider)
        val b = second.minSize(paneMin, emptyMin, divider)
        if (orientation == SplitOrientation.Horizontal) {
            DpSize(a.width + divider + b.width, max(a.height, b.height))
        } else {
            DpSize(max(a.width, b.width), a.height + divider + b.height)
        }
    }
}

/**
 * Where a split's divider sits: [proportion], moved just far enough that the first side keeps
 * [firstMin] of the [available] length and the second keeps [secondMin].
 *
 * When the two minimums do not both fit, neither can be honoured, and the split shares out
 * what there is in the ratio of the minimums - both sides fall short by the same fraction,
 * rather than one disappearing so the other can stay whole. The stored proportion is left
 * alone throughout: this decides only where the divider is drawn, so a window that grows back
 * puts it back where the user left it.
 */
internal fun clampProportion(
    proportion: Float,
    available: Float,
    firstMin: Float,
    secondMin: Float,
): Float {
    val p = proportion.coerceIn(0f, 1f)
    if (available <= 0f) return p
    val mins = firstMin + secondMin
    if (mins >= available) return if (mins > 0f) firstMin / mins else p
    return p.coerceIn(firstMin / available, 1f - secondMin / available)
}
