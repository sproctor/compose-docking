package com.seanproctor.docking.jewel

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.SplitOrientation
import com.seanproctor.docking.spi.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import kotlin.math.roundToInt

/**
 * Jewel implementation of the docking renderer: IDE-style tool-window headers (flat
 * panel background, compact hover-square buttons) and editor-style tabs with a selection
 * underline. Renders its own tab strip - Jewel's `TabStrip` has no drag/reorder hooks -
 * but reads all colors from the ambient [JewelTheme], so it follows both standalone
 * IntUi themes and the IDE LaF bridge.
 */
public object JewelDockingRenderer : DockingRenderer {

    @Composable
    override fun TabStrip(model: TabStripModel, modifier: Modifier) {
        val theme = LocalDockingTheme.current
        val underline = theme.activeHighlightBorder
        Row(
            modifier = modifier
                .height(30.dp)
                .background(theme.toolbarBackground)
                .drawBehind {
                    // Hairline under the whole strip, IDE-style.
                    drawLine(
                        theme.handleOutline,
                        Offset(0f, size.height - 0.5f),
                        Offset(size.width, size.height - 0.5f),
                    )
                },
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
                            val interaction = remember { MutableInteractionSource() }
                            val hovered by interaction.collectIsHoveredAsState()
                            Row(
                                modifier = Modifier
                                    .offset { IntOffset(tab.reorderOffsetX.roundToInt(), 0) }
                                    .then(tab.dragModifier)
                                    .hoverable(interaction)
                                    .fillMaxHeight()
                                    .background(
                                        when {
                                            tab.isSelected -> theme.headerBackground
                                            hovered -> theme.headerForeground.copy(alpha = 0.06f)
                                            else -> theme.toolbarBackground
                                        },
                                    )
                                    .drawBehind {
                                        if (tab.isSelected) {
                                            drawRect(
                                                underline,
                                                topLeft = Offset(0f, size.height - 3.dp.toPx()),
                                                size = Size(size.width, 3.dp.toPx()),
                                            )
                                        }
                                    }
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    tab.title,
                                    color = theme.headerForeground.copy(alpha = if (tab.isSelected) 1f else 0.75f),
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
                .height(22.dp)
                .background(LocalDockingTheme.current.activeHighlightBorder),
        )
    }

    @Composable
    override fun DockableHeader(model: HeaderModel, modifier: Modifier) {
        val theme = LocalDockingTheme.current
        val foreground = model.foreground ?: theme.headerForeground
        Row(
            modifier = modifier
                .height(28.dp)
                .background(model.background ?: theme.headerBackground)
                .drawBehind {
                    drawLine(
                        theme.handleOutline,
                        Offset(0f, size.height - 0.5f),
                        Offset(size.width, size.height - 0.5f),
                    )
                    if (model.isActive) {
                        // IDE tool windows mark the focused header with a top accent.
                        drawRect(
                            theme.activeHighlightBorder,
                            topLeft = Offset.Zero,
                            size = Size(size.width, 2.dp.toPx()),
                        )
                    }
                }
                .then(model.dragModifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                model.title,
                color = foreground,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp).weight(1f),
                maxLines = 1,
            )
            model.trailingActions()
        }
    }

    /**
     * IntelliJ's three-dot grip, centred on an otherwise invisible divider: three 3dp
     * dots spaced 5dp apart along it, brightening while hovered or dragged. No separator
     * line - panels meet directly, as the IDE draws them.
     */
    @Composable
    override fun SplitDivider(model: DividerModel, modifier: Modifier) {
        val theme = LocalDockingTheme.current
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val dotColor = when {
            model.isDragging -> theme.activeHighlightBorder
            hovered -> theme.handleForeground
            else -> theme.handleForeground.copy(alpha = 0.7f)
        }
        Canvas(modifier.hoverable(interaction).then(model.dragModifier)) {
            val radius = 1.5.dp.toPx()
            val step = 5.dp.toPx()
            if (model.orientation == SplitOrientation.Horizontal) {
                val x = size.width / 2f
                val centerY = size.height / 2f
                for (i in -1..1) {
                    drawCircle(dotColor, radius, Offset(x, centerY + i * step))
                }
            } else {
                val y = size.height / 2f
                val centerX = size.width / 2f
                for (i in -1..1) {
                    drawCircle(dotColor, radius, Offset(centerX + i * step, y))
                }
            }
        }
    }

    @Composable
    override fun DockingHandle(model: HandleModel, modifier: Modifier) {
        val theme = LocalDockingTheme.current
        Canvas(modifier.size(32.dp)) {
            val bg = when {
                !model.isEnabled -> theme.handleBackground.copy(alpha = 0.35f)
                model.isHovered -> theme.activeHighlightBorder.copy(alpha = 0.25f)
                else -> theme.handleBackground
            }
            drawRoundRect(color = bg, cornerRadius = CornerRadius(6f, 6f))
            drawRoundRect(
                color = if (model.isHovered) theme.activeHighlightBorder else theme.handleOutline,
                cornerRadius = CornerRadius(6f, 6f),
                style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
            )
            val inset = size.width * 0.28f
            val bar = size.width * 0.2f
            val fg = theme.handleForeground.copy(alpha = if (model.isEnabled) 1f else 0.35f)
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
            OverlayKind.TabCaret -> theme.activeHighlightBorder
        }
        Box(modifier.background(color))
    }

    @Composable
    override fun EmptyRootPlaceholder(modifier: Modifier) {
        val theme = LocalDockingTheme.current
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No tool windows", color = theme.headerForeground.copy(alpha = 0.5f))
        }
    }

    @Composable
    override fun CollapsedAnchor(model: CollapsedAnchorModel, modifier: Modifier) {
        val theme = LocalDockingTheme.current
        // A recessed band, the way IntelliJ leaves a tool-window stripe: enough to read as
        // a place that takes windows, quiet enough to ignore. It lights up while a drag is
        // over it, which is the only time it is asking for attention.
        val background = if (model.isDropTarget) theme.overlayBackground else theme.toolbarBackground
        Box(
            modifier
                .fillMaxSize()
                .background(background)
                .border(Dp.Hairline, theme.headerForeground.copy(alpha = 0.15f)),
        )
    }

    @Composable
    override fun MissingDockable(id: DockableId, modifier: Modifier) {
        val theme = LocalDockingTheme.current
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Unavailable: ${id.value}", color = theme.headerForeground.copy(alpha = 0.5f))
        }
    }

    @Composable
    override fun DragPreview(title: String, icon: Painter?) {
        val theme = LocalDockingTheme.current
        Box(
            Modifier
                .background(theme.headerBackground.copy(alpha = 0.95f))
                .border(1.dp, theme.activeHighlightBorder),
        ) {
            Text(
                title,
                color = theme.headerForeground,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}
