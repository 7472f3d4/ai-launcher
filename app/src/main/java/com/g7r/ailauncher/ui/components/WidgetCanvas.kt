package com.g7r.ailauncher.ui.components

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import android.util.SizeF
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.g7r.ailauncher.data.LauncherWidgetHostView
import com.g7r.ailauncher.data.WidgetEntry
import com.g7r.ailauncher.data.WidgetGrid
import com.g7r.ailauncher.data.WidgetHostManager
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val WidgetCorner = RoundedCornerShape(24.dp)
private val CellPadding = 3.dp

private data class CanvasMetrics(
    val cellWPx: Float,
    val cellHPx: Float,
    val rows: Int
)

private fun shownRow(entry: WidgetEntry, rows: Int): Int =
    if (entry.row + entry.spanH > rows) max(0, rows - entry.spanH) else entry.row

/**
 * Pixel-style free widget grid: long-press lifts a widget and drags it, dropping
 * shows a resize frame with edge handles (width AND height), tap elsewhere
 * dismisses. Placement snaps to a [WidgetGrid.COLUMNS]-column grid.
 */
@Composable
fun WidgetCanvas(
    widgets: List<WidgetEntry>,
    host: WidgetHostManager,
    selectedId: Int?,
    onSelect: (Int?) -> Unit,
    onMove: (id: Int, col: Int, row: Int) -> Unit,
    onResize: (id: Int, col: Int, row: Int, spanW: Int, spanH: Int) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { onSelect(null) })
        }
    ) {
        val density = LocalDensity.current
        val view = LocalView.current
        val scope = rememberCoroutineScope()

        val rows = max(1, (maxHeight / WidgetGrid.TARGET_CELL_HEIGHT_DP.dp).toInt())
        val cellW: Dp = maxWidth / WidgetGrid.COLUMNS
        val cellH: Dp = maxHeight / rows
        val cellWPx = with(density) { cellW.toPx() }
        val cellHPx = with(density) { cellH.toPx() }

        val metricsState = rememberUpdatedState(CanvasMetrics(cellWPx, cellHPx, rows))
        val widgetsState = rememberUpdatedState(widgets)
        val onSelectState = rememberUpdatedState(onSelect)
        val onMoveState = rememberUpdatedState(onMove)

        // One drag at a time.
        var dragId by remember { mutableStateOf<Int?>(null) }
        var dragBase by remember { mutableStateOf(Offset.Zero) }
        var dragStartCell by remember { mutableStateOf(0 to 0) }
        var dropTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        val dragOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

        // Drop-target preview, drawn behind the widgets.
        val previewEntry = widgets.firstOrNull { it.id == dragId }
        val previewCell = dropTarget
        if (previewEntry != null && previewCell != null) {
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (previewCell.first * cellWPx).roundToInt(),
                            (previewCell.second * cellHPx).roundToInt()
                        )
                    }
                    .size(cellW * previewEntry.spanW, cellH * previewEntry.spanH)
                    .padding(CellPadding)
                    .clip(WidgetCorner)
                    .background(Color.White.copy(alpha = 0.16f))
                    .border(2.dp, Color.White.copy(alpha = 0.55f), WidgetCorner)
            )
        }

        widgets.forEach { entry ->
            key(entry.id) {
                val entryState = rememberUpdatedState(entry)
                val isDragging = dragId == entry.id
                val isSelected = selectedId == entry.id

                val dragListener = remember {
                    object : LauncherWidgetHostView.DragListener {
                        override fun onDragStart() {
                            val e = entryState.value
                            val m = metricsState.value
                            val startRow = shownRow(e, m.rows)
                            dragStartCell = e.col to startRow
                            dragBase = Offset(e.col * m.cellWPx, startRow * m.cellHPx)
                            dropTarget = e.col to startRow
                            onSelectState.value(null)
                            dragId = e.id
                            scope.launch { dragOffset.snapTo(Offset.Zero) }
                        }

                        override fun onDragMove(dxPx: Float, dyPx: Float) {
                            if (dragId != entryState.value.id) return
                            val e = entryState.value
                            val m = metricsState.value
                            scope.launch { dragOffset.snapTo(Offset(dxPx, dyPx)) }
                            val pos = dragBase + Offset(dxPx, dyPx)
                            val col = (pos.x / m.cellWPx).roundToInt()
                                .coerceIn(0, WidgetGrid.COLUMNS - e.spanW)
                            val row = (pos.y / m.cellHPx).roundToInt()
                                .coerceIn(0, max(0, m.rows - e.spanH))
                            val ok = WidgetGrid.fits(
                                widgetsState.value, e.id, col, row, e.spanW, e.spanH, m.rows
                            )
                            // Keep the last valid cell as the drop target (Pixel behavior).
                            if (ok && dropTarget != col to row) {
                                dropTarget = col to row
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }

                        override fun onDragEnd(cancelled: Boolean) {
                            if (dragId != entryState.value.id) return
                            val e = entryState.value
                            val m = metricsState.value
                            val target = (if (cancelled) null else dropTarget) ?: dragStartCell
                            scope.launch {
                                val destPx = Offset(
                                    target.first * m.cellWPx,
                                    target.second * m.cellHPx
                                )
                                dragOffset.animateTo(
                                    destPx - dragBase,
                                    spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
                                )
                                if (target != dragStartCell) {
                                    onMoveState.value(e.id, target.first, target.second)
                                }
                                dragId = null
                                dropTarget = null
                                dragOffset.snapTo(Offset.Zero)
                                onSelectState.value(e.id)
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            }
                        }
                    }
                }

                val scale by animateFloatAsState(
                    targetValue = if (isDragging) 1.06f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "widgetLift"
                )
                val elevation by animateDpAsState(
                    targetValue = if (isDragging) 14.dp else 0.dp,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "widgetShadow"
                )

                Box(
                    Modifier
                        .offset {
                            if (dragId == entry.id) {
                                IntOffset(
                                    (dragBase.x + dragOffset.value.x).roundToInt(),
                                    (dragBase.y + dragOffset.value.y).roundToInt()
                                )
                            } else {
                                IntOffset(
                                    (entry.col * cellWPx).roundToInt(),
                                    (shownRow(entry, rows) * cellHPx).roundToInt()
                                )
                            }
                        }
                        .size(cellW * entry.spanW, cellH * min(entry.spanH, rows))
                        .zIndex(if (isDragging || isSelected) 1f else 0f)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .padding(CellPadding)
                        .shadow(elevation, WidgetCorner, clip = false)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            host.createView(ctx, entry.id)
                                ?: android.widget.TextView(ctx).apply {
                                    text = "ウィジェットを読み込めません"
                                }
                        },
                        update = { v ->
                            if (v is LauncherWidgetHostView) v.dragListener = dragListener
                            if (v is AppWidgetHostView) {
                                val wDp = (cellW * entry.spanW - CellPadding * 2).value
                                val hDp = (cellH * entry.spanH - CellPadding * 2).value
                                runCatching {
                                    v.updateAppWidgetSize(Bundle(), listOf(SizeF(wDp, hDp)))
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(WidgetCorner)
                    )
                }
            }
        }

        // Pixel-style resize frame for the selected widget.
        val selected = widgets.firstOrNull { it.id == selectedId }
        if (selected != null && dragId == null) {
            key(selected.id) {
                ResizeOverlay(
                    entry = selected,
                    info = remember(selected.id) { host.info(selected.id) },
                    widgetsState = widgetsState,
                    metricsState = metricsState,
                    cellW = cellW,
                    cellH = cellH,
                    onResize = onResize,
                    onRemove = onRemove
                )
            }
        }
    }
}

/** Continuous per-edge drag offsets (px) of the resize frame within one gesture. */
private class FrameDrag {
    var l by mutableFloatStateOf(0f)
    var t by mutableFloatStateOf(0f)
    var r by mutableFloatStateOf(0f)
    var b by mutableFloatStateOf(0f)

    fun reset() {
        l = 0f; t = 0f; r = 0f; b = 0f
    }
}

private enum class Edge { LEFT, TOP, RIGHT, BOTTOM }

@Composable
private fun ResizeOverlay(
    entry: WidgetEntry,
    info: AppWidgetProviderInfo?,
    widgetsState: State<List<WidgetEntry>>,
    metricsState: State<CanvasMetrics>,
    cellW: Dp,
    cellH: Dp,
    onResize: (id: Int, col: Int, row: Int, spanW: Int, spanH: Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val metrics = metricsState.value
    val entryState = rememberUpdatedState(entry)
    val onResizeState = rememberUpdatedState(onResize)
    val drag = remember(entry.id) { FrameDrag() }

    val canResizeW = info == null ||
        (info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0
    val canResizeH = info == null ||
        (info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL) != 0

    // Span limits from the provider (px values compared against px cell sizes).
    // Computed at gesture start: pointerInput keeps its first lambda alive while
    // keys are unchanged, so composition-time values would go stale mid-gesture.
    fun widthLimits(g: WidgetEntry, m: CanvasMetrics): Pair<Int, Int> {
        val pMin = (info?.takeIf { it.minResizeWidth > 0 }
            ?.let { ceil(it.minResizeWidth / m.cellWPx).toInt() } ?: 1)
            .coerceIn(1, WidgetGrid.COLUMNS)
        val lo = min(g.spanW, pMin)
        val pMax = (info?.takeIf { it.maxResizeWidth > 0 }
            ?.let { (it.maxResizeWidth / m.cellWPx).roundToInt() } ?: WidgetGrid.COLUMNS)
            .coerceIn(lo, WidgetGrid.COLUMNS)
        return lo to max(g.spanW, pMax)
    }

    fun heightLimits(g: WidgetEntry, m: CanvasMetrics): Pair<Int, Int> {
        val pMin = (info?.takeIf { it.minResizeHeight > 0 }
            ?.let { ceil(it.minResizeHeight / m.cellHPx).toInt() } ?: 1)
            .coerceIn(1, m.rows)
        val lo = min(g.spanH, pMin)
        val pMax = (info?.takeIf { it.maxResizeHeight > 0 }
            ?.let { (it.maxResizeHeight / m.cellHPx).roundToInt() } ?: m.rows)
            .coerceIn(lo, m.rows)
        return lo to max(g.spanH, pMax)
    }

    val row = shownRow(entry, metrics.rows)
    val baseL = entry.col * metrics.cellWPx
    val baseT = row * metrics.cellHPx
    val baseW = entry.spanW * metrics.cellWPx
    val baseH = entry.spanH * metrics.cellHPx

    /**
     * Applies one continuous edge-drag step: the frame follows the finger, and the
     * widget itself snaps (and commits) whenever the frame crosses a cell boundary.
     * Returns the entry as committed so far within this gesture.
     */
    fun applyEdgeDrag(
        edge: Edge,
        g: WidgetEntry,
        amount: Float,
        limW: Pair<Int, Int>,
        limH: Pair<Int, Int>
    ): WidgetEntry {
        val m = metricsState.value
        val list = widgetsState.value
        val commit = onResizeState.value
        val (minW, maxW) = limW
        val (minH, maxH) = limH
        when (edge) {
            Edge.RIGHT -> {
                drag.r += amount
                val proposed = ((g.spanW * m.cellWPx + drag.r) / m.cellWPx).roundToInt()
                    .coerceIn(minW, maxW)
                    .coerceAtMost(WidgetGrid.COLUMNS - g.col)
                if (proposed != g.spanW &&
                    WidgetGrid.fits(list, g.id, g.col, g.row, proposed, g.spanH, m.rows)
                ) {
                    commit(g.id, g.col, g.row, proposed, g.spanH)
                    drag.r -= (proposed - g.spanW) * m.cellWPx
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    return g.copy(spanW = proposed)
                }
            }
            Edge.LEFT -> {
                drag.l += amount
                val rightCol = g.col + g.spanW
                val newCol = ((g.col * m.cellWPx + drag.l) / m.cellWPx).roundToInt()
                    .coerceIn(rightCol - maxW, rightCol - minW)
                    .coerceAtLeast(0)
                val newSpan = rightCol - newCol
                if (newCol != g.col &&
                    WidgetGrid.fits(list, g.id, newCol, g.row, newSpan, g.spanH, m.rows)
                ) {
                    commit(g.id, newCol, g.row, newSpan, g.spanH)
                    drag.l -= (newCol - g.col) * m.cellWPx
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    return g.copy(col = newCol, spanW = newSpan)
                }
            }
            Edge.BOTTOM -> {
                drag.b += amount
                val proposed = ((g.spanH * m.cellHPx + drag.b) / m.cellHPx).roundToInt()
                    .coerceIn(minH, maxH)
                    .coerceAtMost(m.rows - g.row)
                if (proposed != g.spanH && proposed >= 1 &&
                    WidgetGrid.fits(list, g.id, g.col, g.row, g.spanW, proposed, m.rows)
                ) {
                    commit(g.id, g.col, g.row, g.spanW, proposed)
                    drag.b -= (proposed - g.spanH) * m.cellHPx
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    return g.copy(spanH = proposed)
                }
            }
            Edge.TOP -> {
                drag.t += amount
                val bottomRow = g.row + g.spanH
                val newRow = ((g.row * m.cellHPx + drag.t) / m.cellHPx).roundToInt()
                    .coerceIn(bottomRow - maxH, bottomRow - minH)
                    .coerceAtLeast(0)
                val newSpan = bottomRow - newRow
                if (newRow != g.row &&
                    WidgetGrid.fits(list, g.id, g.col, newRow, g.spanW, newSpan, m.rows)
                ) {
                    commit(g.id, g.col, newRow, g.spanW, newSpan)
                    drag.t -= (newRow - g.row) * m.cellHPx
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    return g.copy(row = newRow, spanH = newSpan)
                }
            }
        }
        return g
    }

    fun Modifier.edgeDrag(edge: Edge): Modifier = pointerInput(entry.id, edge) {
        var g = entryState.value
        var limW = 1 to WidgetGrid.COLUMNS
        var limH = 1 to Int.MAX_VALUE
        detectDragGestures(
            onDragStart = {
                g = entryState.value
                limW = widthLimits(g, metricsState.value)
                limH = heightLimits(g, metricsState.value)
            },
            onDrag = { change, amt ->
                change.consume()
                val delta = if (edge == Edge.LEFT || edge == Edge.RIGHT) amt.x else amt.y
                g = applyEdgeDrag(edge, g, delta, limW, limH)
            },
            onDragEnd = { drag.reset() },
            onDragCancel = { drag.reset() }
        )
    }

    // The frame: follows the finger continuously, the widget snaps underneath.
    Box(
        Modifier
            .offset {
                IntOffset(
                    (baseL + drag.l).roundToInt(),
                    (baseT + drag.t).roundToInt()
                )
            }
            .size(
                with(density) { (baseW - drag.l + drag.r).toDp() },
                with(density) { (baseH - drag.t + drag.b).toDp() }
            )
            .zIndex(2f)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(1.dp)
                .border(2.dp, Color.White.copy(alpha = 0.9f), WidgetCorner)
        )
        if (canResizeW) {
            ResizeHandle(Modifier.align(Alignment.CenterStart).edgeDrag(Edge.LEFT))
            ResizeHandle(Modifier.align(Alignment.CenterEnd).edgeDrag(Edge.RIGHT))
        }
        if (canResizeH) {
            ResizeHandle(Modifier.align(Alignment.TopCenter).edgeDrag(Edge.TOP))
            ResizeHandle(Modifier.align(Alignment.BottomCenter).edgeDrag(Edge.BOTTOM))
        }
    }

    // Remove pill floating above the widget (anchored to the committed rect).
    Box(
        Modifier
            .offset {
                IntOffset(
                    baseL.roundToInt(),
                    (baseT - with(density) { 46.dp.toPx() }).roundToInt()
                )
            }
            .width(with(density) { baseW.toDp() })
            .height(40.dp)
            .zIndex(2f),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 4.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onRemove(entry.id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "削除",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ResizeHandle(modifier: Modifier) {
    Box(modifier.size(36.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(14.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape)
                .border(1.dp, Color.Black.copy(alpha = 0.12f), CircleShape)
        )
    }
}
