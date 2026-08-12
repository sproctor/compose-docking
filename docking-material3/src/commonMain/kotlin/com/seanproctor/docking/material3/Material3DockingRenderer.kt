package com.seanproctor.docking.material3

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.SplitOrientation
import com.seanproctor.docking.spi.*
import kotlin.math.roundToInt

/**
 * Material 3 implementation of the docking renderer. Deliberately avoids `TabRow`
 * (no per-tab close affordance, indicator animation fights reordering) in favor of a
 * custom strip styled with M3 tokens.
 */
public object Material3DockingRenderer : DockingRenderer {

    @Composable
    override fun TabStrip(model: TabStripModel, modifier: Modifier) {
        val colors = MaterialTheme.colorScheme
        val theme = LocalDockingTheme.current
        Row(
            modifier = modifier.height(32.dp).background(theme.toolbarBackground),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabsThenGutter(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                tabs = {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        model.tabs.forEachIndexed { index, tab ->
                            if (model.dropInsertionIndex == index) TabCaret()
                            Row(
                                modifier = Modifier
                                    .offset { IntOffset(tab.reorderOffsetX.roundToInt(), 0) }
                                    .then(tab.dragModifier)
                                    .fillMaxHeight()
                                    .background(if (tab.isSelected) colors.surface else theme.toolbarBackground)
                                    .drawSelectionUnderline(tab.isSelected, colors.primary)
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    tab.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (tab.isSelected) FontWeight.Medium else FontWeight.Normal,
                                    color = if (tab.isSelected) colors.onSurface else colors.onSurfaceVariant,
                                )
                                tab.actions()
                            }
                        }
                        if (model.dropInsertionIndex == model.tabs.size) TabCaret()
                    }
                },
                gutter = { Box(Modifier.fillMaxHeight().then(model.gutterDragModifier)) },
            )
            model.trailingActions()
        }
    }

    /**
     * Tabs at their natural width (scrolling only when they exceed the whole strip),
     * with the gutter given exactly the leftover width. A plain `Row` with weights
     * would clip the tabs to a fraction of the strip; layering the gutter under the
     * tabs would make tab drags also start gutter (whole-group) drags.
     */
    @Composable
    private fun TabsThenGutter(
        modifier: Modifier,
        tabs: @Composable () -> Unit,
        gutter: @Composable () -> Unit,
    ) {
        Layout(contents = listOf(tabs, gutter), modifier = modifier) { measurables, constraints ->
            val tabsPlaceable = measurables[0].first().measure(constraints.copy(minWidth = 0))
            val gutterWidth = (constraints.maxWidth - tabsPlaceable.width).coerceAtLeast(0)
            val gutterPlaceable = measurables[1].first()
                .measure(Constraints.fixed(gutterWidth, constraints.maxHeight))
            layout(constraints.maxWidth, constraints.maxHeight) {
                tabsPlaceable.placeRelative(0, 0)
                gutterPlaceable.placeRelative(tabsPlaceable.width, 0)
            }
        }
    }

    @Composable
    private fun TabCaret() {
        Box(
            Modifier
                .width(3.dp)
                .height(24.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
    }

    @Composable
    override fun DockableHeader(model: HeaderModel, modifier: Modifier) {
        val colors = MaterialTheme.colorScheme
        val theme = LocalDockingTheme.current
        val foreground = model.foreground
            ?: if (model.isActive) colors.onSurface else colors.onSurfaceVariant
        Surface(color = model.background ?: theme.headerBackground, modifier = modifier) {
            Row(
                modifier = Modifier.height(30.dp).then(model.dragModifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    model.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = foreground,
                    modifier = Modifier.padding(horizontal = 10.dp).weight(1f),
                    maxLines = 1,
                )
                model.trailingActions()
            }
        }
    }

    /**
     * The Material 3 drag handle alone: a full-corner capsule, 4dp thick and 48dp long,
     * `outline` at rest and `onSurface` while hovered or dragged. No separator line -
     * each dockable already draws its own border, so a hairline here would only add a
     * third parallel line between panes.
     *
     * The spec also grows the capsule to 12dp x 52dp when pressed or dragged. The core
     * measures dividers at a fixed thickness well under 12dp, so growing the thickness
     * would just clip; only the length and color change here.
     */
    @Composable
    override fun SplitDivider(model: DividerModel, modifier: Modifier) {
        val colors = MaterialTheme.colorScheme
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val engaged = model.isDragging || hovered
        val handleColor = if (engaged) colors.onSurface else colors.outline
        Canvas(
            modifier
                .hoverable(interaction)
                .then(model.dragModifier),
        ) {
            val thickness = 4.dp.toPx()
            val length = (if (engaged) 52.dp else 48.dp).toPx()
            val radius = CornerRadius(thickness / 2f, thickness / 2f)
            if (model.orientation == SplitOrientation.Horizontal) {
                // Vertical divider between side-by-side panes.
                val handleLength = length.coerceAtMost(size.height)
                drawRoundRect(
                    color = handleColor,
                    topLeft = Offset((size.width - thickness) / 2f, (size.height - handleLength) / 2f),
                    size = Size(thickness, handleLength),
                    cornerRadius = radius,
                )
            } else {
                val handleLength = length.coerceAtMost(size.width)
                drawRoundRect(
                    color = handleColor,
                    topLeft = Offset((size.width - handleLength) / 2f, (size.height - thickness) / 2f),
                    size = Size(handleLength, thickness),
                    cornerRadius = radius,
                )
            }
        }
    }

    @Composable
    override fun DockingHandle(model: HandleModel, modifier: Modifier) {
        val theme = LocalDockingTheme.current
        val colors = MaterialTheme.colorScheme
        Canvas(modifier.size(32.dp)) {
            val bg = when {
                !model.isEnabled -> theme.handleBackground.copy(alpha = 0.35f)
                model.isHovered -> colors.primaryContainer
                else -> theme.handleBackground
            }
            drawRoundRect(color = bg, cornerRadius = CornerRadius(8f, 8f))
            drawRoundRect(
                color = if (model.isHovered) colors.primary else theme.handleOutline,
                cornerRadius = CornerRadius(8f, 8f),
                style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
            )
            val inset = size.width * 0.28f
            val bar = size.width * 0.2f
            val fg = (if (model.isHovered) colors.onPrimaryContainer else theme.handleForeground)
                .copy(alpha = if (model.isEnabled) 1f else 0.35f)
            when (model.kind) {
                HandleKind.RootCenter, HandleKind.DockableCenter ->
                    drawRect(fg, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset))
                HandleKind.RootNorth, HandleKind.DockableNorth ->
                    drawRect(fg, Offset(inset, inset), Size(size.width - 2 * inset, bar))
                HandleKind.RootSouth, HandleKind.DockableSouth ->
                    drawRect(fg, Offset(inset, size.height - inset - bar), Size(size.width - 2 * inset, bar))
                HandleKind.RootWest, HandleKind.DockableWest ->
                    drawRect(fg, Offset(inset, inset), Size(bar, size.height - 2 * inset))
                HandleKind.RootEast, HandleKind.DockableEast ->
                    drawRect(fg, Offset(size.width - inset - bar, inset), Size(bar, size.height - 2 * inset))
            }
        }
    }

    @Composable
    override fun DropOverlay(model: DropOverlayModel, modifier: Modifier) {
        val theme = LocalDockingTheme.current
        val color = when (model.kind) {
            OverlayKind.Area -> theme.overlayBackground
            OverlayKind.TabCaret -> MaterialTheme.colorScheme.primary
        }
        Box(modifier.background(color))
    }

    @Composable
    override fun EmptyRootPlaceholder(modifier: Modifier) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No panels",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    @Composable
    override fun CollapsedAnchor(model: CollapsedAnchorModel, modifier: Modifier) {
        // Surface-variant reads as a recess rather than a panel, which is what an empty
        // area should look like. The primary tint marks it as the live drop target.
        val background = if (model.isDropTarget) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
        Box(modifier.fillMaxSize().background(background))
    }

    @Composable
    override fun MissingDockable(id: DockableId, modifier: Modifier) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Panel unavailable: ${id.value}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    @Composable
    override fun DragPreview(title: String, icon: Painter?) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

private fun Modifier.drawSelectionUnderline(
    selected: Boolean,
    color: androidx.compose.ui.graphics.Color,
): Modifier = if (!selected) {
    this
} else {
    then(
        Modifier.drawBehind {
            drawRect(
                color,
                topLeft = Offset(0f, size.height - 2.dp.toPx()),
                size = Size(size.width, 2.dp.toPx()),
            )
        },
    )
}
