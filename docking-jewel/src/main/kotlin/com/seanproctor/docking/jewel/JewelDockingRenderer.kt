package com.seanproctor.docking.jewel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.SplitOrientation
import com.seanproctor.docking.spi.AutoHideButtonModel
import com.seanproctor.docking.spi.DividerModel
import com.seanproctor.docking.spi.DockMenuItem
import com.seanproctor.docking.spi.DockingRenderer
import com.seanproctor.docking.spi.DropOverlayModel
import com.seanproctor.docking.spi.HandleKind
import com.seanproctor.docking.spi.HandleModel
import com.seanproctor.docking.spi.HeaderModel
import com.seanproctor.docking.spi.LocalDockingTheme
import com.seanproctor.docking.spi.OverlayKind
import com.seanproctor.docking.spi.TabStripModel
import kotlin.math.roundToInt
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * Jewel implementation of the docking renderer: IDE-style tool-window headers (flat
 * panel background, compact hover-square buttons) and editor-style tabs with a selection
 * underline. Renders its own tab strip — Jewel's `TabStrip` has no drag/reorder hooks —
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
            Row(
                Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
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
                        if (tab.onClose != null && (tab.isSelected || hovered)) {
                            SquareButton("×", onClick = tab.onClose!!)
                        }
                    }
                }
                if (model.dropInsertionIndex == model.tabs.size) TabCaret()
            }
            Box(Modifier.weight(1f).fillMaxHeight().then(model.gutterDragModifier))
            if (model.trailingMenuItems.isNotEmpty()) {
                MenuHost(model.trailingMenuItems) { open -> SquareButton("⋮", onClick = open) }
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
        Row(
            modifier = modifier
                .height(28.dp)
                .background(theme.headerBackground)
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
                color = theme.headerForeground,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp).weight(1f),
                maxLines = 1,
            )
            if (model.isMaximized) {
                Text("⛶", color = theme.activeHighlightBorder, modifier = Modifier.padding(end = 2.dp))
            }
            if (model.menuItems.isNotEmpty()) {
                MenuHost(model.menuItems) { open -> SquareButton("⋮", onClick = open) }
            }
            if (model.onClose != null) {
                SquareButton("×", onClick = model.onClose!!)
            }
        }
    }

    /** The 16px hover-square icon button IntelliJ uses in tool-window headers. */
    @Composable
    private fun SquareButton(glyph: String, onClick: () -> Unit) {
        val theme = LocalDockingTheme.current
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        Box(
            Modifier
                .padding(horizontal = 2.dp)
                .size(20.dp)
                .hoverable(interaction)
                .background(
                    if (hovered) theme.headerForeground.copy(alpha = 0.1f) else theme.headerBackground.copy(alpha = 0f),
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, color = theme.headerForeground.copy(alpha = 0.8f))
        }
    }

    @Composable
    override fun SplitDivider(model: DividerModel, modifier: Modifier) {
        val theme = LocalDockingTheme.current
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val color = when {
            model.isDragging -> theme.activeHighlightBorder
            hovered -> theme.handleForeground.copy(alpha = 0.4f)
            else -> theme.handleOutline
        }
        Canvas(modifier.hoverable(interaction).then(model.dragModifier)) {
            if (model.orientation == SplitOrientation.Horizontal) {
                val x = size.width / 2f
                drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = if (model.isDragging) 2f else 1f)
            } else {
                val y = size.height / 2f
                drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = if (model.isDragging) 2f else 1f)
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
                HandleKind.RootSouth, HandleKind.DockableSouth, HandleKind.PinSouth ->
                    drawRect(fg, Offset(inset, size.height - inset - bar), Size(size.width - 2 * inset, bar))
                HandleKind.RootWest, HandleKind.DockableWest, HandleKind.PinWest ->
                    drawRect(fg, Offset(inset, inset), Size(bar, size.height - 2 * inset))
                HandleKind.RootEast, HandleKind.DockableEast, HandleKind.PinEast ->
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
    override fun AutoHideButton(model: AutoHideButtonModel, modifier: Modifier) {
        val theme = LocalDockingTheme.current
        Box(
            modifier
                .clickable(onClick = model.onClick)
                .background(
                    if (model.isPanelOpen) {
                        theme.activeHighlightBorder.copy(alpha = 0.2f)
                    } else {
                        theme.toolbarBackground.copy(alpha = 0f)
                    },
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(model.title, color = theme.headerForeground.copy(alpha = 0.85f))
        }
    }

    @Composable
    override fun MenuHost(
        items: List<DockMenuItem>,
        anchor: @Composable (openMenu: () -> Unit) -> Unit,
    ) {
        var expanded by remember { mutableStateOf(false) }
        Box {
            anchor { expanded = true }
            if (expanded) {
                val theme = LocalDockingTheme.current
                Popup(onDismissRequest = { expanded = false }) {
                    Column(
                        Modifier
                            .background(theme.headerBackground)
                            .border(1.dp, theme.handleOutline)
                            .padding(vertical = 4.dp),
                    ) {
                        MenuItems(items, indent = 0) { expanded = false }
                    }
                }
            }
        }
    }

    @Composable
    private fun MenuItems(items: List<DockMenuItem>, indent: Int, dismiss: () -> Unit) {
        val theme = LocalDockingTheme.current
        for (item in items) {
            when (item) {
                is DockMenuItem.Action -> {
                    val interaction = remember { MutableInteractionSource() }
                    val hovered by interaction.collectIsHoveredAsState()
                    Text(
                        (if (item.selected) "✓ " else "") + item.label,
                        color = theme.headerForeground.copy(alpha = if (item.enabled) 1f else 0.4f),
                        modifier = Modifier
                            .fillMaxSize()
                            .hoverable(interaction)
                            .background(
                                if (hovered && item.enabled) {
                                    theme.activeHighlightBorder.copy(alpha = 0.2f)
                                } else {
                                    theme.headerBackground.copy(alpha = 0f)
                                },
                            )
                            .clickable(enabled = item.enabled) {
                                dismiss()
                                item.onClick()
                            }
                            .padding(start = (12 + indent * 14).dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
                    )
                }
                is DockMenuItem.SubMenu -> {
                    Text(
                        item.label,
                        color = theme.headerForeground.copy(alpha = 0.55f),
                        modifier = Modifier.padding(
                            start = (12 + indent * 14).dp,
                            end = 12.dp,
                            top = 5.dp,
                            bottom = 2.dp,
                        ),
                    )
                    MenuItems(item.items, indent + 1, dismiss)
                }
                DockMenuItem.Separator -> Box(
                    Modifier
                        .padding(vertical = 3.dp, horizontal = 8.dp)
                        .height(1.dp)
                        .width(140.dp)
                        .background(theme.handleOutline),
                )
            }
        }
    }

    @Composable
    override fun EmptyRootPlaceholder(modifier: Modifier) {
        val theme = LocalDockingTheme.current
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No tool windows", color = theme.headerForeground.copy(alpha = 0.5f))
        }
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
