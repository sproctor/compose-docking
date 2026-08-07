package com.seanproctor.docking.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.seanproctor.docking.model.AutoHideSide
import com.seanproctor.docking.model.DockWindow
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.SplitOrientation
import com.seanproctor.docking.spi.AutoHideButtonModel
import com.seanproctor.docking.spi.DividerModel
import com.seanproctor.docking.spi.LocalDockingRenderer
import com.seanproctor.docking.spi.LocalDockingTheme

private const val SLIDE_ANIMATION_MS = 180

@Composable
internal fun DockAreaScope.AutoHideToolbarStrip(window: DockWindow, side: AutoHideSide) {
    val entries = window.autoHide[side]
    if (entries.isEmpty()) return
    val renderer = LocalDockingRenderer.current
    val theme = LocalDockingTheme.current
    val buttons: @Composable () -> Unit = {
        for (entry in entries) {
            val spec = state.registry[entry.dockableId]
            renderer.AutoHideButton(
                AutoHideButtonModel(
                    id = entry.dockableId,
                    title = spec?.title?.invoke() ?: entry.dockableId.value,
                    icon = spec?.icon?.invoke(),
                    side = side,
                    isPanelOpen = state.expandedAutoHide(windowId) == entry.dockableId,
                    onClick = {
                        val isOpen = state.expandedAutoHide(windowId) == entry.dockableId
                        state.expandAutoHide(windowId, if (isOpen) null else entry.dockableId)
                    },
                ),
                Modifier,
            )
        }
    }
    val stripModifier = Modifier
        .background(theme.toolbarBackground)
        .onGloballyPositioned { autoHideStripBounds[side] = it.boundsInRoot() }
    when (side) {
        AutoHideSide.South -> Row(stripModifier.fillMaxWidth()) { buttons() }
        AutoHideSide.West -> Box(stripModifier.fillMaxHeight()) {
            Row(Modifier.rotateVertically(clockwise = false)) { buttons() }
        }
        AutoHideSide.East -> Box(stripModifier.fillMaxHeight()) {
            Row(Modifier.rotateVertically(clockwise = true)) { buttons() }
        }
    }
}

@Composable
internal fun DockAreaScope.AutoHideSlideOutLayer(window: DockWindow, modifier: Modifier) {
    val expandedId = state.expandedAutoHide(windowId)
    var lastShown by remember { mutableStateOf<DockableId?>(null) }
    LaunchedEffect(expandedId) {
        if (expandedId != null) lastShown = expandedId
    }
    val shownId = expandedId ?: lastShown ?: return
    val side = window.autoHide.sideOf(shownId) ?: return
    val visible = expandedId != null

    Box(modifier) {
        val (alignment, enter, exit) = when (side) {
            AutoHideSide.West -> Triple(
                Alignment.CenterStart,
                slideInHorizontally(tween(SLIDE_ANIMATION_MS)) { -it },
                slideOutHorizontally(tween(SLIDE_ANIMATION_MS)) { -it },
            )
            AutoHideSide.East -> Triple(
                Alignment.CenterEnd,
                slideInHorizontally(tween(SLIDE_ANIMATION_MS)) { it },
                slideOutHorizontally(tween(SLIDE_ANIMATION_MS)) { it },
            )
            AutoHideSide.South -> Triple(
                Alignment.BottomCenter,
                slideInVertically(tween(SLIDE_ANIMATION_MS)) { it },
                slideOutVertically(tween(SLIDE_ANIMATION_MS)) { it },
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = enter,
            exit = exit,
            modifier = Modifier.align(alignment),
        ) {
            SlideOutPanel(window, shownId, side)
        }
    }
}

@Composable
private fun DockAreaScope.SlideOutPanel(
    window: DockWindow,
    id: DockableId,
    side: AutoHideSide,
) {
    val entry = window.autoHide[side].firstOrNull { it.dockableId == id } ?: return
    val renderer = LocalDockingRenderer.current
    val theme = LocalDockingTheme.current
    var dragProportion by remember(id) { mutableStateOf<Float?>(null) }
    val proportion = (dragProportion ?: entry.slideProportion).coerceIn(0.1f, 0.9f)

    val resizeModifier = Modifier.pointerInput(id, side) {
        detectDragGestures(
            onDrag = { change, amount ->
                change.consume()
                val total = when (side) {
                    AutoHideSide.West, AutoHideSide.East -> bounds.rootBounds.width
                    AutoHideSide.South -> bounds.rootBounds.height
                }
                if (total > 0f) {
                    val delta = when (side) {
                        AutoHideSide.West -> amount.x
                        AutoHideSide.East -> -amount.x
                        AutoHideSide.South -> -amount.y
                    }
                    dragProportion = ((dragProportion ?: entry.slideProportion) + delta / total)
                        .coerceIn(0.1f, 0.9f)
                }
            },
            onDragEnd = {
                dragProportion?.let { state.setAutoHideSlide(id, it) }
                dragProportion = null
            },
            onDragCancel = { dragProportion = null },
        )
    }

    val panelContent: @Composable (Modifier) -> Unit = { contentModifier ->
        Column(contentModifier.background(theme.headerBackground)) {
            val spec = state.registry[id]
            if (spec == null) {
                renderer.MissingDockable(id, Modifier.weight(1f).fillMaxWidth())
            } else {
                renderer.DockableHeader(buildHeaderModel(spec), Modifier.fillMaxWidth())
                DockableContentBox(id, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }

    val panelBoundsModifier = Modifier.onGloballyPositioned {
        slideOutPanelBounds = it.boundsInRoot()
    }

    when (side) {
        AutoHideSide.West -> Row(panelBoundsModifier.fillMaxHeight().fillMaxWidth(proportion)) {
            panelContent(Modifier.weight(1f).fillMaxHeight())
            renderer.SplitDivider(
                DividerModel(SplitOrientation.Horizontal, dragProportion != null, resizeModifier),
                Modifier.width(6.dp).fillMaxHeight(),
            )
        }
        AutoHideSide.East -> Row(panelBoundsModifier.fillMaxHeight().fillMaxWidth(proportion)) {
            renderer.SplitDivider(
                DividerModel(SplitOrientation.Horizontal, dragProportion != null, resizeModifier),
                Modifier.width(6.dp).fillMaxHeight(),
            )
            panelContent(Modifier.weight(1f).fillMaxHeight())
        }
        AutoHideSide.South -> Column(panelBoundsModifier.fillMaxWidth().fillMaxHeight(proportion)) {
            renderer.SplitDivider(
                DividerModel(SplitOrientation.Vertical, dragProportion != null, resizeModifier),
                Modifier.height(6.dp).fillMaxWidth(),
            )
            panelContent(Modifier.weight(1f).fillMaxWidth())
        }
    }
}

/**
 * Collapses the open slide-out panel when a press lands outside it (and outside the
 * toolbar strips, whose buttons toggle the panel themselves). Non-consuming, initial
 * pass — the press still reaches whatever was under it.
 */
internal fun Modifier.autoHideDismissListener(scope: DockAreaScope): Modifier =
    pointerInput(scope) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Press) continue
                if (scope.state.expandedAutoHide(scope.windowId) == null) continue
                val local = event.changes.firstOrNull()?.position ?: continue
                val pressInRoot = local + scope.bounds.rootBounds.topLeft
                val insidePanel = scope.slideOutPanelBounds?.contains(pressInRoot) == true
                val insideStrip = scope.autoHideStripBounds.values.any { it.contains(pressInRoot) }
                if (!insidePanel && !insideStrip) {
                    scope.state.expandAutoHide(scope.windowId, null)
                }
            }
        }
    }
