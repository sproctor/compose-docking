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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.NodeId
import com.seanproctor.docking.model.SplitOrientation
import com.seanproctor.docking.model.TabPreference
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.drag.DropTarget
import com.seanproctor.docking.spi.CollapsedAnchorModel
import com.seanproctor.docking.spi.DividerModel
import com.seanproctor.docking.spi.HeaderModel
import com.seanproctor.docking.spi.LocalDockingRenderer
import com.seanproctor.docking.spi.LocalDockingTheme
import com.seanproctor.docking.spi.PaneFrameModel
import com.seanproctor.docking.spi.TabItemModel
import com.seanproctor.docking.spi.TabPlacement
import com.seanproctor.docking.spi.TabStripModel
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockableSpec
import com.seanproctor.docking.state.EmptyAnchorVisibility
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
internal fun DockAreaScope.RenderNode(
    node: DockNode,
    modifier: Modifier = Modifier,
    /**
     * The axis of the strip this node is being drawn inside, set by [RenderSplit] for an
     * empty area whose slot it collapsed. A split passes it on to its own sides, so the
     * anchors under a collapsed subtree are drawn as strips rather than full placeholders.
     */
    collapsedIn: SplitOrientation? = null,
) {
    when (node) {
        is DockNode.Leaf -> RenderLeaf(node, showHeader = true, modifier)
        is DockNode.Anchor -> RenderAnchor(node, modifier, collapsedIn)
        is DockNode.Split -> RenderSplit(node, modifier, collapsedIn)
        is DockNode.Tabs -> RenderTabs(node, modifier)
    }
}

@Composable
private fun DockAreaScope.RenderAnchor(
    node: DockNode.Anchor,
    modifier: Modifier,
    collapsedIn: SplitOrientation?,
) {
    NodeBoundsEffect(node.id)
    val positioned = modifier.onGloballyPositioned { bounds.updateNode(node.id, it.boundsInRoot()) }
    val renderer = LocalDockingRenderer.current
    if (collapsedIn == null) {
        renderer.EmptyRootPlaceholder(positioned)
    } else {
        val isDropTarget = (state.dragController.session?.target as? DropTarget.OnNode)?.nodeId == node.id
        renderer.CollapsedAnchor(
            CollapsedAnchorModel(node.anchorId, collapsedIn, isDropTarget),
            positioned,
        )
    }
}

/**
 * True when [node] holds no dockables at all - an anchor's placeholder, or a split of
 * nothing but those. Collapsing is a property of the whole subtree, not of one node: a
 * split whose two sides have both emptied out is itself an empty area, and it is that
 * split's own parent that gives the space back.
 */
private fun isEmptyArea(node: DockNode): Boolean = when (node) {
    is DockNode.Anchor -> true
    is DockNode.Split -> isEmptyArea(node.first) && isEmptyArea(node.second)
    is DockNode.Leaf, is DockNode.Tabs -> false
}

/**
 * The fixed thickness an empty area takes along its split's axis, or null when empty areas
 * are left at their proportional size. Zero while one is hidden until a drag.
 *
 * Reading the drag session here is what makes a hidden area reappear: the read is recorded
 * in composition, so starting a drag relayouts the split with the strip in it.
 * See [com.seanproctor.docking.state.DockingSettings.collapsedAnchorThickness] and
 * [com.seanproctor.docking.state.DockingSettings.emptyAnchorVisibility].
 */
private fun DockAreaScope.emptyAreaThickness(): Dp? {
    val settings = state.settings
    if (settings.emptyAnchorVisibility == EmptyAnchorVisibility.WhileDragging &&
        state.dragController.session == null
    ) {
        return 0.dp
    }
    return settings.collapsedAnchorThickness.takeIf { settings.collapsesEmptyAnchors }
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
            .then(
                renderer.paneFrame(
                    PaneFrameModel(
                        dockableId = leaf.dockableId,
                        isActive = state.activeDockable == leaf.dockableId,
                        isTabGroup = false,
                        tabPlacement = null,
                    ),
                ),
            ).onGloballyPositioned {
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
    Column(
        modifier
            .then(
                renderer.paneFrame(
                    PaneFrameModel(
                        dockableId = selected.dockableId,
                        isActive = state.activeDockable == selected.dockableId,
                        isTabGroup = true,
                        tabPlacement = placement,
                    ),
                ),
            ).onGloballyPositioned { bounds.updateNode(node.id, it.boundsInRoot()) },
    ) {
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
internal fun DockAreaScope.RenderSplit(
    node: DockNode.Split,
    modifier: Modifier,
    collapsedIn: SplitOrientation? = null,
) {
    NodeBoundsEffect(node.id)
    val currentNode by rememberUpdatedState(node)
    // The live proportion while the divider is being dragged, held as a raw state object and
    // read only from the measure lambda below. Reading it in the composable body - as
    // `by remember` invites - recomposes this split, and so re-invokes the content lambdas of
    // everything docked inside it, on every pointer move, when all that has actually changed
    // is where the divider sits. Deferring the read to layout turns a drag into a re-measure.
    val dragProportion = remember { mutableStateOf<Float?>(null) }
    // Separate from the value above so the renderer's "is dragging" flag flips twice per drag
    // rather than once per frame; it is read in composition, which is what makes that matter.
    var dividerDragging by remember { mutableStateOf(false) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val horizontal = node.orientation == SplitOrientation.Horizontal
    val renderer = LocalDockingRenderer.current

    val dividerDrag = Modifier
        .pointerInput(node.id) {
            detectDragGestures(
                onDragStart = {
                    dragProportion.value = currentNode.proportion
                    dividerDragging = true
                },
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
                        dragProportion.value =
                            ((dragProportion.value ?: currentNode.proportion) + delta / total)
                                .coerceIn(0.05f, 0.95f)
                    }
                },
                onDragEnd = {
                    dragProportion.value?.let { state.setSplitProportion(currentNode.id, it) }
                    dragProportion.value = null
                    dividerDragging = false
                },
                onDragCancel = {
                    dragProportion.value = null
                    dividerDragging = false
                },
            )
        }
        .pointerInput(node.id) {
            detectTapGestures(onDoubleTap = { state.resetSplitProportion(currentNode.id) })
        }
        .pointerHoverIcon(PointerIcon.Hand)

    // An empty area gives its share back to its neighbour and keeps only a strip - or
    // nothing at all, when it stays hidden until a drag. The split's proportion is left
    // alone either way, so filling the area restores the old geometry.
    val stripThickness = emptyAreaThickness()
    val firstEmpty = isEmptyArea(node.first)
    val secondEmpty = isEmptyArea(node.second)
    // Both sides empty means this split is itself an empty area, and its own parent has
    // already collapsed it. Pinning a side here would hand everything the strip did not
    // use to the other one - precisely the oversized empty pane collapsing exists to get
    // rid of - so what is left is divided by proportion, which keeps both sides, and both
    // drop targets, inside the strip. At the root there is no parent to collapse anything,
    // and a layout with nothing docked in it correctly reads as empty.
    val bothEmpty = firstEmpty && secondEmpty
    val firstThickness = stripThickness.takeIf { firstEmpty && !bothEmpty }
    val secondThickness = stripThickness.takeIf { secondEmpty && !bothEmpty }
    val pinned = firstThickness ?: secondThickness
    // Strips carry on down: the sides of a split that was itself collapsed are drawn
    // collapsed too, along the axis of the strip they are inside rather than their own.
    val inheritedCollapse = collapsedIn.takeIf { bothEmpty }
    // A hidden area must not leave a divider behind as an unexplained gap - neither the one
    // beside it, nor the ones inside it once a whole subtree has collapsed away to nothing.
    val hidden = bothEmpty && collapsedIn != null && stripThickness == 0.dp
    val dividerVisible = !hidden && (pinned == null || pinned > 0.dp)
    // Resizing a pinned side would move a divider against a fixed-size strip, so the
    // divider goes inert until there is something there to resize.
    val dividerModifier = if (pinned != null) Modifier else dividerDrag

    Layout(
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .onGloballyPositioned { bounds.updateNode(node.id, it.boundsInRoot()) },
        content = {
            key(node.first.id) {
                RenderNode(
                    node.first,
                    collapsedIn = if (firstThickness != null) node.orientation else inheritedCollapse,
                )
            }
            renderer.SplitDivider(
                DividerModel(node.orientation, dividerDragging, dividerModifier),
                Modifier,
            )
            key(node.second.id) {
                RenderNode(
                    node.second,
                    collapsedIn = if (secondThickness != null) node.orientation else inheritedCollapse,
                )
            }
        },
    ) { measurables, constraints ->
        require(measurables.size == 3) { "split expects exactly [first, divider, second]" }
        // Read here, not in composition: this is the whole point of the drag being cheap.
        val proportion = (dragProportion.value ?: node.proportion).coerceIn(0.05f, 0.95f)
        val thickness = if (dividerVisible) DividerThickness.roundToPx() else 0
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        // Never let a strip crowd out its neighbour on a small window: half the axis is
        // the most an empty area may take, whatever thickness was configured.
        fun strip(axis: Int) = (pinned ?: 0.dp).roundToPx().coerceIn(0, axis / 2)
        if (horizontal) {
            val available = (width - thickness).coerceAtLeast(0)
            val firstWidth = when {
                firstThickness != null -> strip(available)
                secondThickness != null -> available - strip(available)
                else -> (available * proportion).toInt()
            }
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
            val firstHeight = when {
                firstThickness != null -> strip(available)
                secondThickness != null -> available - strip(available)
                else -> (available * proportion).toInt()
            }
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
