package com.seanproctor.docking.spi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.seanproctor.docking.model.DockableId
import kotlin.math.roundToInt

/**
 * Plain, dependency-free renderer used as the default when no design-system adapter is
 * installed. Functional but deliberately unstyled — wrap content in `Material3Docking`
 * or `JewelDocking` for a real look.
 */
public object DebugDockingRenderer : DockingRenderer {

    @Composable
    override fun TabStrip(model: TabStripModel, modifier: Modifier) {
        val theme = LocalDockingTheme.current
        Row(
            modifier = modifier
                .height(28.dp)
                .background(theme.toolbarBackground)
                .then(model.gutterDragModifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            model.tabs.forEachIndexed { index, tab ->
                if (model.dropInsertionIndex == index) TabCaret()
                Row(
                    modifier = Modifier
                        .offset { IntOffset(tab.reorderOffsetX.roundToInt(), 0) }
                        .then(tab.dragModifier)
                        .background(if (tab.isSelected) theme.headerBackground else Color.Transparent)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(tab.title, style = textStyle(theme.headerForeground))
                    if (tab.onClose != null) {
                        BasicText(
                            "×",
                            style = textStyle(theme.headerForeground),
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .clickable { tab.onClose.invoke() },
                        )
                    }
                }
            }
            if (model.dropInsertionIndex == model.tabs.size) TabCaret()
            Box(Modifier.weight(1f))
            if (model.trailingMenuItems.isNotEmpty()) {
                MenuHost(model.trailingMenuItems) { open ->
                    BasicText(
                        "⋮",
                        style = textStyle(theme.headerForeground),
                        modifier = Modifier.clickable(onClick = open).padding(6.dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun TabCaret() {
        Box(
            Modifier
                .width(2.dp)
                .height(20.dp)
                .background(LocalDockingTheme.current.activeHighlightBorder),
        )
    }

    @Composable
    override fun DockableHeader(model: HeaderModel, modifier: Modifier) {
        val theme = LocalDockingTheme.current
        Row(
            modifier = modifier
                .height(26.dp)
                .background(theme.headerBackground)
                .then(model.dragModifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                model.title + if (model.isMaximized) "  ⛶" else "",
                style = textStyle(theme.headerForeground),
                modifier = Modifier.padding(horizontal = 8.dp).weight(1f),
            )
            if (model.menuItems.isNotEmpty()) {
                MenuHost(model.menuItems) { open ->
                    BasicText(
                        "⋮",
                        style = textStyle(theme.headerForeground),
                        modifier = Modifier.clickable(onClick = open).padding(6.dp),
                    )
                }
            }
            if (model.onClose != null) {
                BasicText(
                    "×",
                    style = textStyle(theme.headerForeground),
                    modifier = Modifier.clickable { model.onClose.invoke() }.padding(6.dp),
                )
            }
        }
    }

    @Composable
    override fun SplitDivider(model: DividerModel, modifier: Modifier) {
        val theme = LocalDockingTheme.current
        Box(
            modifier
                .background(if (model.isDragging) theme.activeHighlightBorder else theme.toolbarBackground)
                .then(model.dragModifier),
        )
    }

    @Composable
    override fun DockingHandle(model: HandleModel, modifier: Modifier) {
        val theme = LocalDockingTheme.current
        Canvas(modifier.size(32.dp)) {
            val bg = when {
                !model.isEnabled -> theme.handleBackground.copy(alpha = 0.3f)
                model.isHovered -> theme.overlayBackground.copy(alpha = 1f)
                else -> theme.handleBackground
            }
            drawRoundRect(color = bg, cornerRadius = CornerRadius(6f, 6f))
            drawRoundRect(
                color = theme.handleOutline,
                cornerRadius = CornerRadius(6f, 6f),
                style = Stroke(
                    width = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
                ),
            )
            // Inner glyph: a bar on the side the handle docks to (centered square for Center).
            val inset = size.width * 0.28f
            val barThickness = size.width * 0.2f
            val fg = theme.handleForeground.copy(alpha = if (model.isEnabled) 1f else 0.3f)
            when (model.kind) {
                HandleKind.RootCenter, HandleKind.DockableCenter -> drawRect(
                    color = fg,
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset),
                )
                HandleKind.RootNorth, HandleKind.DockableNorth -> drawRect(
                    color = fg,
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - 2 * inset, barThickness),
                )
                HandleKind.RootSouth, HandleKind.DockableSouth, HandleKind.PinSouth -> drawRect(
                    color = fg,
                    topLeft = Offset(inset, size.height - inset - barThickness),
                    size = androidx.compose.ui.geometry.Size(size.width - 2 * inset, barThickness),
                )
                HandleKind.RootWest, HandleKind.DockableWest, HandleKind.PinWest -> drawRect(
                    color = fg,
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(barThickness, size.height - 2 * inset),
                )
                HandleKind.RootEast, HandleKind.DockableEast, HandleKind.PinEast -> drawRect(
                    color = fg,
                    topLeft = Offset(size.width - inset - barThickness, inset),
                    size = androidx.compose.ui.geometry.Size(barThickness, size.height - 2 * inset),
                )
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
                .background(if (model.isPanelOpen) theme.headerBackground else Color.Transparent)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            BasicText(model.title, style = textStyle(theme.headerForeground))
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
                Popup(onDismissRequest = { expanded = false }) {
                    val theme = LocalDockingTheme.current
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
                is DockMenuItem.Action -> BasicText(
                    (if (item.selected) "✓ " else "") + item.label,
                    style = textStyle(
                        theme.headerForeground.copy(alpha = if (item.enabled) 1f else 0.4f),
                    ),
                    modifier = Modifier
                        .clickable(enabled = item.enabled) {
                            dismiss()
                            item.onClick()
                        }
                        .padding(
                            start = (12 + indent * 16).dp,
                            end = 12.dp,
                            top = 4.dp,
                            bottom = 4.dp,
                        ),
                )
                is DockMenuItem.SubMenu -> {
                    BasicText(
                        item.label,
                        style = textStyle(theme.headerForeground.copy(alpha = 0.6f)),
                        modifier = Modifier.padding(
                            start = (12 + indent * 16).dp,
                            end = 12.dp,
                            top = 4.dp,
                            bottom = 2.dp,
                        ),
                    )
                    MenuItems(item.items, indent + 1, dismiss)
                }
                DockMenuItem.Separator -> Box(
                    Modifier
                        .padding(vertical = 3.dp)
                        .height(1.dp)
                        .width(120.dp)
                        .background(theme.handleOutline),
                )
            }
        }
    }

    @Composable
    override fun EmptyRootPlaceholder(modifier: Modifier) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BasicText(
                "No dockables",
                style = textStyle(LocalDockingTheme.current.inactiveHighlightBorder),
            )
        }
    }

    @Composable
    override fun MissingDockable(id: DockableId, modifier: Modifier) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BasicText(
                "Unavailable: ${id.value}",
                style = textStyle(LocalDockingTheme.current.inactiveHighlightBorder),
            )
        }
    }

    @Composable
    override fun DragPreview(title: String, icon: Painter?) {
        val theme = LocalDockingTheme.current
        Box(
            Modifier
                .background(theme.headerBackground.copy(alpha = 0.9f))
                .border(1.dp, theme.activeHighlightBorder)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            BasicText(title, style = textStyle(theme.headerForeground))
        }
    }

    private fun textStyle(color: Color): TextStyle = TextStyle(color = color, fontSize = 12.sp)
}
