package com.seanproctor.docking.material3

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
                        if (tab.onClose != null && (tab.isSelected || hovered)) {
                            GlyphButton("×", onClick = tab.onClose!!)
                        }
                    }
                }
                if (model.dropInsertionIndex == model.tabs.size) TabCaret()
            }
            Box(Modifier.weight(1f).fillMaxHeight().then(model.gutterDragModifier))
            if (model.trailingMenuItems.isNotEmpty()) {
                MenuHost(model.trailingMenuItems) { open ->
                    GlyphButton("⋮", onClick = open)
                }
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
        Surface(color = theme.headerBackground, modifier = modifier) {
            Row(
                modifier = Modifier.height(30.dp).then(model.dragModifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    model.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (model.isActive) colors.onSurface else colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp).weight(1f),
                    maxLines = 1,
                )
                if (model.isMaximized) {
                    Text(
                        "⛶",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                        modifier = Modifier.padding(end = 2.dp),
                    )
                }
                if (model.menuItems.isNotEmpty()) {
                    MenuHost(model.menuItems) { open -> GlyphButton("⋮", onClick = open) }
                }
                if (model.onClose != null) {
                    GlyphButton("×", onClick = model.onClose!!)
                }
            }
        }
    }

    @Composable
    private fun GlyphButton(glyph: String, onClick: () -> Unit) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        Box(
            Modifier
                .padding(horizontal = 2.dp)
                .size(22.dp)
                .hoverable(interaction)
                .background(
                    if (hovered) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                    },
                    MaterialTheme.shapes.extraSmall,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                glyph,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    @Composable
    override fun SplitDivider(model: DividerModel, modifier: Modifier) {
        val colors = MaterialTheme.colorScheme
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val lineColor = when {
            model.isDragging -> colors.primary
            hovered -> colors.outline
            else -> colors.outlineVariant
        }
        Canvas(
            modifier
                .hoverable(interaction)
                .then(model.dragModifier),
        ) {
            if (model.orientation == SplitOrientation.Horizontal) {
                // Vertical divider bar between side-by-side panes.
                val x = size.width / 2f
                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = if (model.isDragging) 3f else 1.5f)
            } else {
                val y = size.height / 2f
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = if (model.isDragging) 3f else 1.5f)
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
            OverlayKind.TabCaret -> MaterialTheme.colorScheme.primary
        }
        Box(modifier.background(color))
    }

    @Composable
    override fun AutoHideButton(model: AutoHideButtonModel, modifier: Modifier) {
        val colors = MaterialTheme.colorScheme
        Box(
            modifier
                .clickable(onClick = model.onClick)
                .background(
                    if (model.isPanelOpen) colors.secondaryContainer else colors.surface.copy(alpha = 0f),
                    MaterialTheme.shapes.extraSmall,
                )
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Text(
                model.title,
                style = MaterialTheme.typography.labelMedium,
                color = if (model.isPanelOpen) colors.onSecondaryContainer else colors.onSurfaceVariant,
            )
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
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                MenuItems(items, indent = 0) { expanded = false }
            }
        }
    }

    @Composable
    private fun MenuItems(items: List<DockMenuItem>, indent: Int, dismiss: () -> Unit) {
        for (item in items) {
            when (item) {
                is DockMenuItem.Action -> DropdownMenuItem(
                    text = {
                        Text(
                            (if (item.selected) "✓ " else "") + item.label,
                            modifier = Modifier.padding(start = (indent * 12).dp),
                        )
                    },
                    enabled = item.enabled,
                    onClick = {
                        dismiss()
                        item.onClick()
                    },
                )
                is DockMenuItem.SubMenu -> {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = (12 + indent * 12).dp,
                            top = 6.dp,
                            bottom = 2.dp,
                        ),
                    )
                    MenuItems(item.items, indent + 1, dismiss)
                }
                DockMenuItem.Separator -> androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
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
