package com.seanproctor.docking.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.NodeId
import com.seanproctor.docking.model.SplitOrientation
import com.seanproctor.docking.model.TabPreference
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.spi.DividerModel
import com.seanproctor.docking.spi.HeaderModel
import com.seanproctor.docking.spi.LocalDockingRenderer
import com.seanproctor.docking.spi.LocalDockingTheme
import com.seanproctor.docking.spi.TabItemModel
import com.seanproctor.docking.spi.TabPlacement
import com.seanproctor.docking.spi.TabStripModel
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockableSpec
import kotlinx.coroutines.launch

/**
 * Thickness of a split divider: its hit area, and the room a renderer has to draw a drag
 * handle. Wide enough to leave some space around the handle rather than hugging it.
 */
internal val DividerThickness = 8.dp

/**
 * Renders one window's docking layout: the tree of splits, tab groups and dockables,
 * plus (during drags) the docking overlay.
 *
 * Call once per window - the main window and each floating window - with that window's
 * [windowId]. Dockable content and metadata come from [DockState.registry].
 */
@Composable
public fun DockArea(
    state: DockState,
    windowId: WindowId = WindowId.MAIN,
    modifier: Modifier = Modifier,
) {
    val window = state.layout.window(windowId) ?: return
    val scope = remember(state, windowId) { DockAreaScope(state, windowId) }
    scope.density = androidx.compose.ui.platform.LocalDensity.current.density
    val renderer = LocalDockingRenderer.current
    DisposableEffect(scope) {
        state.dragController.registerWindow(windowId, scope)
        onDispose { state.dragController.unregisterWindow(windowId) }
    }
    Box(
        modifier
            .onGloballyPositioned { scope.bounds.rootBounds = it.boundsInRoot() }
            .dragSessionRootListener(scope),
    ) {
        val root = window.root
        if (root == null) {
            renderer.EmptyRootPlaceholder(Modifier.fillMaxSize())
        } else {
            key(root.id) {
                scope.RenderNode(root, Modifier.fillMaxSize())
            }
        }
        scope.DragOverlayLayer(Modifier.fillMaxSize())
    }
}

// ----- Tree rendering -----

@Composable
internal fun DockAreaScope.RenderNode(node: DockNode, modifier: Modifier = Modifier) {
    when (node) {
        is DockNode.Leaf -> RenderLeaf(node, showHeader = true, modifier)
        is DockNode.Anchor -> RenderAnchor(node, modifier)
        is DockNode.Split -> RenderSplit(node, modifier)
        is DockNode.Tabs -> RenderTabs(node, modifier)
    }
}

@Composable
private fun DockAreaScope.RenderAnchor(node: DockNode.Anchor, modifier: Modifier) {
    NodeBoundsEffect(node.id)
    LocalDockingRenderer.current.EmptyRootPlaceholder(
        modifier.onGloballyPositioned { bounds.updateNode(node.id, it.boundsInRoot()) },
    )
}

@Composable
internal fun DockAreaScope.RenderLeaf(
    leaf: DockNode.Leaf,
    showHeader: Boolean,
    modifier: Modifier,
) {
    val renderer = LocalDockingRenderer.current
    val spec = state.registry[leaf.dockableId]
    NodeBoundsEffect(leaf.id)
    DockableBoundsEffect(leaf.dockableId)
    Column(
        modifier
            .onGloballyPositioned {
                val rect = it.boundsInRoot()
                bounds.updateNode(leaf.id, rect)
                bounds.updateDockable(leaf.dockableId, rect)
            },
    ) {
        if (spec == null) {
            renderer.MissingDockable(leaf.dockableId, Modifier.weight(1f).fillMaxWidth())
        } else {
            if (showHeader) {
                renderer.DockableHeader(buildHeaderModel(spec), Modifier.fillMaxWidth())
            }
            DockableContentBox(spec.id, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

/**
 * The content area of a dockable: clipping + focus tracking + content.
 *
 * Content is clipped to its pane. A dockable can be resized to any size by a divider
 * drag, and content that does not fit would otherwise paint over its neighbours -
 * a panel's drawing must never escape the space the layout gave it.
 */
@Composable
internal fun DockAreaScope.DockableContentBox(id: DockableId, modifier: Modifier) {
    Box(
        modifier
            .clipToBounds()
            .onFocusChanged { if (it.hasFocus) state.activeDockable = id }
            .pointerInput(id) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) {
                            state.activeDockable = id
                            clicks.reset() // content press breaks header/tab double-click sequences
                        }
                    }
                }
            },
    ) {
        DockableContent(id)
    }
}

// ----- Tabs -----

@Composable
internal fun DockAreaScope.RenderTabs(node: DockNode.Tabs, modifier: Modifier) {
    val renderer = LocalDockingRenderer.current
    NodeBoundsEffect(node.id)
    val selected = node.selectedTab
    val placement = resolveTabPlacement(node)
    Column(modifier.onGloballyPositioned { bounds.updateNode(node.id, it.boundsInRoot()) }) {
        val strip: @Composable () -> Unit = {
            renderer.TabStrip(
                buildTabStripModel(node, placement),
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { bounds.updateTabStrip(node.id, it.boundsInRoot()) },
            )
        }
        if (placement == TabPlacement.Top) strip()
        val spec = state.registry[selected.dockableId]
        // ModernDocking's DisplayPanel rule: a tabbed dockable keeps its title bar unless
        // the tabs are on top (where the tab itself reads as the title) or the layout is
        // in always-display-tabs mode (where the strip carries the affordances instead).
        if (spec != null && placement == TabPlacement.Bottom && !state.settings.alwaysDisplayTabs) {
            renderer.DockableHeader(buildHeaderModel(spec), Modifier.fillMaxWidth())
        }
        DockableBoundsEffect(selected.dockableId)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { bounds.updateDockable(selected.dockableId, it.boundsInRoot()) },
        ) {
            if (spec == null) {
                renderer.MissingDockable(selected.dockableId, Modifier.fillMaxSize())
            } else {
                DockableContentBox(spec.id, Modifier.fillMaxSize())
            }
        }
        if (placement == TabPlacement.Bottom) strip()
    }
}

@Composable
private fun DockAreaScope.resolveTabPlacement(node: DockNode.Tabs): TabPlacement {
    val preference = state.registry.optionsOf(node.selectedTab.dockableId).tabPreference
    val effective = if (preference == TabPreference.Default) {
        state.settings.defaultTabPreference
    } else {
        preference
    }
    return when (effective) {
        TabPreference.Top, TabPreference.TopAlways -> TabPlacement.Top
        else -> TabPlacement.Bottom
    }
}

@Composable
private fun DockAreaScope.buildTabStripModel(
    node: DockNode.Tabs,
    placement: TabPlacement,
): TabStripModel {
    val tabs = node.tabs.mapIndexed { index, tab ->
        val spec = state.registry[tab.dockableId]
        TabItemModel(
            id = tab.dockableId,
            title = spec?.title?.invoke() ?: tab.dockableId.value,
            icon = spec?.icon?.invoke(),
            tooltip = spec?.tooltip?.invoke(),
            isSelected = index == node.selectedIndex,
            onSelect = { state.selectTab(node.id, index) },
            actions = spec?.tabActions ?: {},
            dragModifier = tabGestureModifier(node, tab.dockableId, index),
            reorderOffsetX = tabReorderOffset(node.id, tab.dockableId),
        )
    }
    return TabStripModel(
        tabs = tabs,
        selectedIndex = node.selectedIndex,
        placement = placement,
        trailingActions = state.registry[selectedDockableOf(node)]?.tabStripActions ?: {},
        gutterDragModifier = gutterGestureModifier(node),
        dropInsertionIndex = tabDropInsertionIndex(node.id),
    )
}

private fun selectedDockableOf(node: DockNode.Tabs): DockableId = node.selectedTab.dockableId

// ----- Header -----

@Composable
internal fun DockAreaScope.buildHeaderModel(spec: DockableSpec): HeaderModel = HeaderModel(
    id = spec.id,
    title = spec.title(),
    icon = spec.icon?.invoke(),
    isActive = state.activeDockable == spec.id,
    dragModifier = headerGestureModifier(spec.id),
    trailingActions = spec.trailingActions,
    background = spec.headerBackground?.invoke(),
    foreground = spec.headerForeground?.invoke(),
)

// ----- Splits -----

@Composable
internal fun DockAreaScope.RenderSplit(node: DockNode.Split, modifier: Modifier) {
    NodeBoundsEffect(node.id)
    val currentNode by rememberUpdatedState(node)
    var dragProportion by remember { mutableStateOf<Float?>(null) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val horizontal = node.orientation == SplitOrientation.Horizontal
    val renderer = LocalDockingRenderer.current

    val dividerDrag = Modifier
        .pointerInput(node.id) {
            detectDragGestures(
                onDragStart = { dragProportion = currentNode.proportion },
                onDrag = { change, amount ->
                    change.consume()
                    val thicknessPx = DividerThickness.toPx()
                    val total = if (currentNode.orientation == SplitOrientation.Horizontal) {
                        containerSize.width - thicknessPx
                    } else {
                        containerSize.height - thicknessPx
                    }
                    if (total > 0f) {
                        val delta = if (currentNode.orientation == SplitOrientation.Horizontal) {
                            amount.x
                        } else {
                            amount.y
                        }
                        dragProportion = ((dragProportion ?: currentNode.proportion) + delta / total)
                            .coerceIn(0.05f, 0.95f)
                    }
                },
                onDragEnd = {
                    dragProportion?.let { state.setSplitProportion(currentNode.id, it) }
                    dragProportion = null
                },
                onDragCancel = { dragProportion = null },
            )
        }
        .pointerInput(node.id) {
            detectTapGestures(onDoubleTap = { state.resetSplitProportion(currentNode.id) })
        }
        .pointerHoverIcon(PointerIcon.Hand)

    val proportion = (dragProportion ?: node.proportion).coerceIn(0.05f, 0.95f)

    Layout(
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .onGloballyPositioned { bounds.updateNode(node.id, it.boundsInRoot()) },
        content = {
            key(node.first.id) { RenderNode(node.first) }
            renderer.SplitDivider(
                DividerModel(node.orientation, dragProportion != null, dividerDrag),
                Modifier,
            )
            key(node.second.id) { RenderNode(node.second) }
        },
    ) { measurables, constraints ->
        require(measurables.size == 3) { "split expects exactly [first, divider, second]" }
        val thickness = DividerThickness.roundToPx()
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        if (horizontal) {
            val available = (width - thickness).coerceAtLeast(0)
            val firstWidth = (available * proportion).toInt()
            val secondWidth = available - firstWidth
            val firstPlaceable = measurables[0].measure(Constraints.fixed(firstWidth, height))
            val dividerPlaceable = measurables[1].measure(Constraints.fixed(thickness, height))
            val secondPlaceable = measurables[2].measure(Constraints.fixed(secondWidth, height))
            layout(width, height) {
                firstPlaceable.place(0, 0)
                dividerPlaceable.place(firstWidth, 0)
                secondPlaceable.place(firstWidth + thickness, 0)
            }
        } else {
            val available = (height - thickness).coerceAtLeast(0)
            val firstHeight = (available * proportion).toInt()
            val secondHeight = available - firstHeight
            val firstPlaceable = measurables[0].measure(Constraints.fixed(width, firstHeight))
            val dividerPlaceable = measurables[1].measure(Constraints.fixed(width, thickness))
            val secondPlaceable = measurables[2].measure(Constraints.fixed(width, secondHeight))
            layout(width, height) {
                firstPlaceable.place(0, 0)
                dividerPlaceable.place(0, firstHeight)
                secondPlaceable.place(0, firstHeight + thickness)
            }
        }
    }
}

// ----- Bounds lifecycle -----

@Composable
private fun DockAreaScope.NodeBoundsEffect(id: NodeId) {
    DisposableEffect(id) {
        onDispose { bounds.removeNode(id) }
    }
}

@Composable
private fun DockAreaScope.DockableBoundsEffect(id: DockableId) {
    DisposableEffect(id) {
        onDispose { bounds.removeDockable(id) }
    }
}
