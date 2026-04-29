package com.hardrivetech.t1dtracker

import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Locale

internal data class ChartArea(
    val leftPaddingPx: Float,
    val chartWidth: Float,
    val topPaddingPx: Float,
    val chartHeight: Float
)

internal data class VisibleRange(
    val fullMin: Long,
    val fullMax: Long,
    val visibleMin: Long,
    val visibleMax: Long
)

internal data class YRange(val yMinAdj: Double, val yMaxAdj: Double)

internal data class ChartRenderConfig(
    val area: ChartArea,
    val vr: VisibleRange,
    val yr: YRange,
    val yLabelCount: Int,
    val labelPaint: AndroidPaint,
    val selectedIndex: Int?
)

private data class ChartGestureContext(
    val canvasSize: IntSize,
    val leftPaddingPx: Float,
    val rightPaddingPx: Float,
    val topPaddingPx: Float,
    val fullXMin: Long,
    val fullXMax: Long,
    val visibleXMin: Long,
    val visibleXMax: Long
)

private data class ChartHitTestContext(
    val canvasSize: IntSize,
    val leftPaddingPx: Float,
    val rightPaddingPx: Float,
    val topPaddingPx: Float,
    val bottomPaddingPx: Float,
    val fullXMin: Long,
    val fullXMax: Long,
    val visibleXMin: Long,
    val visibleXMax: Long,
    val yMinAll: Double,
    val yMaxAll: Double
)

@Composable
fun LineChart(
    points: List<Pair<Long, Double>>,
    modifier: Modifier = Modifier,
    yLabelCount: Int = 5,
    onPointSelected: ((Int) -> Unit)? = null
) {
    val density = LocalDensity.current
    if (points.isEmpty()) {
        Box(modifier = modifier) {
            Text("No data", style = MaterialTheme.typography.body2)
        }
        return
    }

    val fullXMin = points.minOf { it.first }
    val fullXMax = points.maxOf { it.first }

    val state = rememberChartState(fullXMin, fullXMax)

    val leftPaddingPx = with(density) { 40.dp.toPx() }
    val rightPaddingPx = with(density) { 8.dp.toPx() }
    val topPaddingPx = with(density) { 8.dp.toPx() }
    val bottomPaddingPx = with(density) { 24.dp.toPx() }

    Box(modifier = modifier) {
        ChartCanvas(
            points = points,
            state = state,
            params = ChartParams(
                leftPaddingPx = leftPaddingPx,
                rightPaddingPx = rightPaddingPx,
                topPaddingPx = topPaddingPx,
                bottomPaddingPx = bottomPaddingPx,
                yLabelCount = yLabelCount
            ),
            onPointSelected = onPointSelected
        )

        ChartTooltip(
            selectedIndex = state.selectedIndex.value,
            selectedOffset = state.selectedOffset.value,
            points = points
        )
    }
}

private class ChartState(
    val canvasSize: MutableState<IntSize>,
    val selectedIndex: MutableState<Int?>,
    val selectedOffset: MutableState<Offset>,
    val visibleRange: MutableState<LongRange>,
    val fullXMin: Long,
    val fullXMax: Long
)

@Composable
private fun rememberChartState(fullXMin: Long, fullXMax: Long): ChartState {
    return ChartState(
        canvasSize = remember { mutableStateOf(IntSize.Zero) },
        selectedIndex = remember { mutableStateOf<Int?>(null) },
        selectedOffset = remember { mutableStateOf(Offset.Zero) },
        visibleRange = remember { mutableStateOf(fullXMin..fullXMax) },
        fullXMin = fullXMin,
        fullXMax = fullXMax
    )
}

private data class ChartParams(
    val leftPaddingPx: Float,
    val rightPaddingPx: Float,
    val topPaddingPx: Float,
    val bottomPaddingPx: Float,
    val yLabelCount: Int
)

@Composable
private fun ChartCanvas(
    points: List<Pair<Long, Double>>,
    state: ChartState,
    params: ChartParams,
    onPointSelected: ((Int) -> Unit)?
) {
    val density = LocalDensity.current
    Canvas(
        modifier = Modifier
            .size(300.dp)
            .pointerInput(points) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val gestureCtx = ChartGestureContext(
                        canvasSize = state.canvasSize.value,
                        leftPaddingPx = params.leftPaddingPx,
                        rightPaddingPx = params.rightPaddingPx,
                        topPaddingPx = params.topPaddingPx,
                        fullXMin = state.fullXMin,
                        fullXMax = state.fullXMax,
                        visibleXMin = state.visibleRange.value.start,
                        visibleXMax = state.visibleRange.value.endInclusive
                    )
                    handleTransformGesture(centroid.x, pan.x, zoom, gestureCtx) { nm, nx ->
                        state.visibleRange.value = nm..nx
                    }
                }
            }
            .pointerInput(points) {
                detectTapGestures { tap ->
                    val hitCtx = ChartHitTestContext(
                        canvasSize = state.canvasSize.value,
                        leftPaddingPx = params.leftPaddingPx,
                        rightPaddingPx = params.rightPaddingPx,
                        topPaddingPx = params.topPaddingPx,
                        bottomPaddingPx = params.bottomPaddingPx,
                        fullXMin = state.fullXMin,
                        fullXMax = state.fullXMax,
                        visibleXMin = state.visibleRange.value.start,
                        visibleXMax = state.visibleRange.value.endInclusive,
                        yMinAll = points.minOf { it.second },
                        yMaxAll = points.maxOf { it.second }
                    )
                    val (chosen, off) = handleTapGesture(tap, hitCtx, points)
                    if (chosen != null) {
                        state.selectedIndex.value = chosen
                        state.selectedOffset.value = off
                        onPointSelected?.invoke(chosen)
                    }
                }
            }
    ) {
        state.canvasSize.value = IntSize(size.width.toInt(), size.height.toInt())
        val cfg = buildChartRenderConfig(points, state, params, density)
        drawChartContent(points, cfg)
    }
}

private fun DrawScope.buildChartRenderConfig(
    points: List<Pair<Long, Double>>,
    state: ChartState,
    params: ChartParams,
    density: androidx.compose.ui.unit.Density
): ChartRenderConfig {
    val w = size.width
    val h = size.height
    val chartWidth = w - params.leftPaddingPx - params.rightPaddingPx
    val chartHeight = h - params.topPaddingPx - params.bottomPaddingPx

    val yMinAll = points.minOf { it.second }
    val yMaxAll = points.maxOf { it.second }
    val rawYRange = if (yMaxAll - yMinAll == 0.0) 1.0 else yMaxAll - yMinAll
    val yMinAdj = yMinAll - 0.1 * rawYRange
    val yMaxAdj = yMaxAll + 0.1 * rawYRange

    val area = ChartArea(
        leftPaddingPx = params.leftPaddingPx,
        chartWidth = chartWidth,
        topPaddingPx = params.topPaddingPx,
        chartHeight = chartHeight
    )
    val vr = VisibleRange(
        state.fullXMin,
        state.fullXMax,
        state.visibleRange.value.start,
        state.visibleRange.value.endInclusive
    )
    val yr = YRange(yMinAdj, yMaxAdj)

    val labelPaint = AndroidPaint().apply {
        color = android.graphics.Color.BLACK
        textSize = with(density) { 12.sp.toPx() }
        isAntiAlias = true
    }

    return ChartRenderConfig(
        area = area,
        vr = vr,
        yr = yr,
        yLabelCount = params.yLabelCount,
        labelPaint = labelPaint,
        selectedIndex = state.selectedIndex.value
    )
}

private fun handleTransformGesture(
    centroidX: Float,
    panX: Float,
    zoom: Float,
    ctx: ChartGestureContext,
    onUpdate: (Long, Long) -> Unit
) {
    if (ctx.canvasSize.width == 0 || ctx.canvasSize.height == 0) return
    val w = ctx.canvasSize.width.toFloat()
    val chartWidth = w - ctx.leftPaddingPx - ctx.rightPaddingPx
    if (chartWidth <= 0f) return

    val area = ChartArea(
        leftPaddingPx = ctx.leftPaddingPx,
        chartWidth = chartWidth,
        topPaddingPx = ctx.topPaddingPx,
        chartHeight = 0f
    )
    val vr = VisibleRange(ctx.fullXMin, ctx.fullXMax, ctx.visibleXMin, ctx.visibleXMax)
    val (newMin, newMax) = computeNewVisibleRange(centroidX, panX, zoom, area, vr)
    onUpdate(newMin, newMax)
}

private fun handleTapGesture(
    tap: Offset,
    ctx: ChartHitTestContext,
    points: List<Pair<Long, Double>>
): Pair<Int?, Offset> {
    var result: Pair<Int?, Offset> = Pair(null, Offset.Zero)
    if (!(ctx.canvasSize.width == 0 || ctx.canvasSize.height == 0)) {
        val w = ctx.canvasSize.width.toFloat()
        val h = ctx.canvasSize.height.toFloat()
        val chartWidth = w - ctx.leftPaddingPx - ctx.rightPaddingPx
        val chartHeight = h - ctx.topPaddingPx - ctx.bottomPaddingPx

        val rawYRange = if (ctx.yMaxAll - ctx.yMinAll == 0.0) 1.0 else ctx.yMaxAll - ctx.yMinAll
        val yMinAdj = ctx.yMinAll - 0.1 * rawYRange
        val yMaxAdj = ctx.yMaxAll + 0.1 * rawYRange

        val area = ChartArea(
            leftPaddingPx = ctx.leftPaddingPx,
            chartWidth = chartWidth,
            topPaddingPx = ctx.topPaddingPx,
            chartHeight = chartHeight
        )
        val vr = VisibleRange(ctx.fullXMin, ctx.fullXMax, ctx.visibleXMin, ctx.visibleXMax)
        val yr = YRange(yMinAdj, yMaxAdj)
        val (xPos, yPos) = makeCoordinateFunctions(vr, area, yr)

        val chosen = findNearestIndex(points, tap, xPos, yPos, area)
        if (chosen != null) {
            val off = Offset(xPos(points[chosen].first), yPos(points[chosen].second))
            result = Pair(chosen, off)
        }
    }
    return result
}

@Composable
private fun ChartTooltip(selectedIndex: Int?, selectedOffset: Offset, points: List<Pair<Long, Double>>) {
    if (selectedIndex == null) return
    val density = LocalDensity.current
    val idx = selectedIndex
    val p = points.getOrNull(idx) ?: return
    val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(java.util.Date(p.first))
    val text = "$date: ${p.second} mg/dL"
    val tooltipDpX = with(density) { selectedOffset.x.toDp() }
    val tooltipDpY = with(density) { (selectedOffset.y - 36f).toDp() }
    Card(modifier = Modifier.offset(x = tooltipDpX, y = tooltipDpY)) {
        Text(text = text, style = MaterialTheme.typography.body2)
    }
}

private fun computeNewVisibleRange(
    centroidX: Float,
    panX: Float,
    zoom: Float,
    area: ChartArea,
    vr: VisibleRange
): Pair<Long, Long> {
    val oldMin = vr.visibleMin.toDouble()
    val oldMax = vr.visibleMax.toDouble()
    val oldRange = oldMax - oldMin
    if (oldRange <= 0) return Pair(vr.visibleMin, vr.visibleMax)

    val centerX = centroidX.coerceIn(area.leftPaddingPx, area.leftPaddingPx + area.chartWidth)
    val centerFrac = ((centerX - area.leftPaddingPx) / area.chartWidth).coerceIn(0f, 1f)
    val centerTime = oldMin + centerFrac * oldRange

    val newRange = (oldRange / zoom).coerceAtLeast(1.0)
    var newMin = centerTime - (centerTime - oldMin) / zoom
    var newMax = centerTime + (oldMax - centerTime) / zoom

    val timeDelta = (panX / area.chartWidth.toDouble()) * newRange
    newMin -= timeDelta
    newMax -= timeDelta

    val fullMin = vr.fullMin.toDouble()
    val fullMax = vr.fullMax.toDouble()
    val visibleRange = newMax - newMin
    if (visibleRange > (fullMax - fullMin)) {
        newMin = fullMin
        newMax = fullMax
    } else {
        if (newMin < fullMin) {
            val shift = fullMin - newMin
            newMin += shift
            newMax += shift
        }
        if (newMax > fullMax) {
            val shift = newMax - fullMax
            newMin -= shift
            newMax -= shift
        }
    }

    val nm = newMin.toLong().coerceAtLeast(vr.fullMin)
    val nx = newMax.toLong().coerceAtMost(vr.fullMax)
    return Pair(nm, nx)
}

private fun findNearestIndex(
    points: List<Pair<Long, Double>>,
    tap: Offset,
    xPos: (Long) -> Float,
    yPos: (Double) -> Float,
    area: ChartArea
): Int? {
    var nearestIdx: Int? = null
    var nearestDist = Float.MAX_VALUE
    points.forEachIndexed { idx, p ->
        val px = xPos(p.first)
        if (px < area.leftPaddingPx || px > area.leftPaddingPx + area.chartWidth) return@forEachIndexed
        val py = yPos(p.second)
        val d = (px - tap.x) * (px - tap.x) + (py - tap.y) * (py - tap.y)
        if (d < nearestDist) {
            nearestDist = d
            nearestIdx = idx
        }
    }
    return nearestIdx
}
