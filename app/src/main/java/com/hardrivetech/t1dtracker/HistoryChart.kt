package com.hardrivetech.t1dtracker

import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Locale

private data class ChartArea(
    val leftPaddingPx: Float,
    val chartWidth: Float,
    val topPaddingPx: Float,
    val chartHeight: Float
)

private data class VisibleRange(
    val fullMin: Long,
    val fullMax: Long,
    val visibleMin: Long,
    val visibleMax: Long
)

private data class YRange(val yMinAdj: Double, val yMaxAdj: Double)

private data class ChartRenderConfig(
    val area: ChartArea,
    val vr: VisibleRange,
    val yr: YRange,
    val yLabelCount: Int,
    val labelPaint: AndroidPaint,
    val selectedIndex: Int?
)

@Composable
fun LineChart(
    points: List<Pair<Long, Double>>,
    modifier: Modifier = Modifier,
    yLabelCount: Int = 4,
    onPointSelected: ((Int) -> Unit)? = null
) {
    if (points.isEmpty()) {
        Text("No data to chart")
        return
    }

    val xs = points.map { it.first }
    val ys = points.map { it.second }
    val fullXMin = xs.minOrNull() ?: 0L
    val fullXMax = xs.maxOrNull() ?: (fullXMin + 1L)
    val yMinAll = ys.minOrNull() ?: 0.0
    val yMaxAll = ys.maxOrNull() ?: 1.0

    val density = LocalDensity.current
    val leftPaddingPx = with(density) { 40.sp.toDp().toPx() }
    val rightPaddingPx = with(density) { 12.sp.toDp().toPx() }
    val topPaddingPx = with(density) { 12.sp.toDp().toPx() }
    val bottomPaddingPx = with(density) { 24.sp.toDp().toPx() }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var selectedOffset by remember { mutableStateOf(Offset.Zero) }

    var visibleXMin by remember { mutableStateOf(fullXMin) }
    var visibleXMax by remember { mutableStateOf(fullXMax) }

    Box(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(points) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    if (canvasSize.width == 0 || canvasSize.height == 0) return@detectTransformGestures
                    val w = canvasSize.width.toFloat()
                    val chartWidth = w - leftPaddingPx - rightPaddingPx
                    if (chartWidth <= 0f) return@detectTransformGestures

                    val area = ChartArea(
                        leftPaddingPx = leftPaddingPx,
                        chartWidth = chartWidth,
                        topPaddingPx = topPaddingPx,
                        chartHeight = 0f
                    )
                    val vr = VisibleRange(fullXMin, fullXMax, visibleXMin, visibleXMax)
                    val (newMin, newMax) = computeNewVisibleRange(centroid.x, pan.x, zoom, area, vr)
                    visibleXMin = newMin
                    visibleXMax = newMax
                }
            }
    ) {
        Canvas(
            modifier = Modifier.matchParentSize().pointerInput(points) {
                detectTapGestures { tap ->
                    if (canvasSize.width == 0 || canvasSize.height == 0) return@detectTapGestures
                    val w = canvasSize.width.toFloat()
                    val h = canvasSize.height.toFloat()
                    val chartWidth = w - leftPaddingPx - rightPaddingPx
                    val chartHeight = h - topPaddingPx - bottomPaddingPx

                    val rawYRange = if (yMaxAll - yMinAll == 0.0) 1.0 else yMaxAll - yMinAll
                    val yMinAdj = yMinAll - 0.1 * rawYRange
                    val yMaxAdj = yMaxAll + 0.1 * rawYRange

                    val area = ChartArea(
                        leftPaddingPx = leftPaddingPx,
                        chartWidth = chartWidth,
                        topPaddingPx = topPaddingPx,
                        chartHeight = chartHeight
                    )
                    val vr = VisibleRange(fullXMin, fullXMax, visibleXMin, visibleXMax)
                    val yr = YRange(yMinAdj, yMaxAdj)
                    val (xPos, yPos) = makeCoordinateFunctions(vr, area, yr)

                    val chosen = findNearestIndex(points, tap, xPos, yPos, area)
                    if (chosen != null) {
                        selectedIndex = chosen
                        selectedOffset = Offset(xPos(points[chosen].first), yPos(points[chosen].second))
                        onPointSelected?.invoke(chosen)
                    }
                }
            }
        ) {
            val w = size.width
            val h = size.height
            val chartWidth = w - leftPaddingPx - rightPaddingPx
            val chartHeight = h - topPaddingPx - bottomPaddingPx

            val rawYRange = if (yMaxAll - yMinAll == 0.0) 1.0 else yMaxAll - yMinAll
            val yMinAdj = yMinAll - 0.1 * rawYRange
            val yMaxAdj = yMaxAll + 0.1 * rawYRange

            val area = ChartArea(
                leftPaddingPx = leftPaddingPx,
                chartWidth = chartWidth,
                topPaddingPx = topPaddingPx,
                chartHeight = chartHeight
            )
            val vr = VisibleRange(fullXMin, fullXMax, visibleXMin, visibleXMax)
            val yr = YRange(yMinAdj, yMaxAdj)

            val labelPaint = AndroidPaint().apply {
                color = android.graphics.Color.BLACK
                textSize = with(density) { 12.sp.toPx() }
                isAntiAlias = true
            }

            val cfg = ChartRenderConfig(
                area = area,
                vr = vr,
                yr = yr,
                yLabelCount = yLabelCount,
                labelPaint = labelPaint,
                selectedIndex = selectedIndex
            )

            drawChartContent(points, cfg)
        }

        if (selectedIndex != null) {
            val idx = selectedIndex!!
            val p = points[idx]
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(java.util.Date(p.first))
            val text = "$date: ${p.second} mg/dL"
            val tooltipDpX = with(density) { selectedOffset.x.toDp() }
            val tooltipDpY = with(density) { (selectedOffset.y - 36f).toDp() }
            Card(
                modifier = Modifier.offset(x = tooltipDpX, y = tooltipDpY)
            ) {
                Text(
                    text = text,
                    modifier = Modifier,
                    style = MaterialTheme.typography.body2
                )
            }
        }
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

private fun makeCoordinateFunctions(
    vr: VisibleRange,
    area: ChartArea,
    yr: YRange
): Pair<(Long) -> Float, (Double) -> Float> {
    fun computeX(x: Long): Float {
        if (vr.visibleMax == vr.visibleMin) return area.leftPaddingPx + area.chartWidth / 2f
        val denom = (vr.visibleMax - vr.visibleMin).toFloat()
        val frac = (x - vr.visibleMin).toFloat() / denom
        return area.leftPaddingPx + frac * area.chartWidth
    }

    val yPos: (Double) -> Float = { y ->
        val frac = ((y - yr.yMinAdj) / (yr.yMaxAdj - yr.yMinAdj))
            .toFloat()
            .coerceIn(0f, 1f)
        area.topPaddingPx + (area.chartHeight * (1f - frac))
    }

    return Pair(::computeX, yPos)
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

private fun buildSmoothedPath(pointCords: List<Offset>): Path {
    val path = Path()
    if (pointCords.isNotEmpty()) {
        path.moveTo(pointCords[0].x, pointCords[0].y)
        for (i in 1 until pointCords.size) {
            val prev = pointCords[i - 1]
            val curr = pointCords[i]
            val midX = (prev.x + curr.x) / 2f
            val midY = (prev.y + curr.y) / 2f
            path.quadraticBezierTo(prev.x, prev.y, midX, midY)
        }
        val last = pointCords.last()
        path.lineTo(last.x, last.y)
    }
    return path
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawYAxisLabels(
    yLabelCount: Int,
    yr: YRange,
    area: ChartArea,
    yPos: (Double) -> Float,
    labelPaint: AndroidPaint
) {
    val gridColor = Color.Gray.copy(alpha = 0.25f)
    for (i in 0 until yLabelCount) {
        val frac = if (yLabelCount == 1) 0.5f else i.toFloat() / (yLabelCount - 1)
        val yValue = yr.yMaxAdj - frac * (yr.yMaxAdj - yr.yMinAdj)
        val y = yPos(yValue)
        drawLine(
            color = gridColor,
            start = Offset(area.leftPaddingPx, y),
            end = Offset(area.leftPaddingPx + area.chartWidth, y)
        )
        val labelText = if (yValue % 1.0 == 0.0) "%.0f".format(yValue) else "%.1f".format(yValue)
        drawContext.canvas.nativeCanvas.drawText(
            labelText,
            4f,
            y + labelPaint.textSize / 2f,
            labelPaint
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawXTicks(
    xTicks: List<Long>,
    xPos: (Long) -> Float,
    h: Float,
    labelPaint: AndroidPaint
) {
    val sdf = SimpleDateFormat("MM-dd", Locale.getDefault())
    for (xt in xTicks) {
        val label = sdf.format(java.util.Date(xt))
        val x = xPos(xt)
        drawContext.canvas.nativeCanvas.drawText(
            label,
            x - labelPaint.measureText(label) / 2f,
            h - 4f,
            labelPaint
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChartContent(
    points: List<Pair<Long, Double>>,
    cfg: ChartRenderConfig
) {
    val (xPos, yPos) = makeCoordinateFunctions(cfg.vr, cfg.area, cfg.yr)

    drawGridAndYAxis(cfg, xPos, yPos)

    val pointCords = points
        .asSequence()
        .filter { it.first in cfg.vr.visibleMin..cfg.vr.visibleMax }
        .map { Offset(xPos(it.first), yPos(it.second)) }
        .toList()

    drawPathAndPoints(cfg, points, xPos, yPos, pointCords)
    drawChartBorder(cfg)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGridAndYAxis(
    cfg: ChartRenderConfig,
    xPos: (Long) -> Float,
    yPos: (Double) -> Float
) {
    drawYAxisLabels(cfg.yLabelCount, cfg.yr, cfg.area, yPos, cfg.labelPaint)
    val xTicks = listOf(
        cfg.vr.visibleMin,
        cfg.vr.visibleMin + (cfg.vr.visibleMax - cfg.vr.visibleMin) / 2,
        cfg.vr.visibleMax
    )
    drawXTicks(xTicks, xPos, size.height, cfg.labelPaint)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPathAndPoints(
    cfg: ChartRenderConfig,
    points: List<Pair<Long, Double>>,
    xPos: (Long) -> Float,
    yPos: (Double) -> Float,
    pointCords: List<Offset>
) {
    val path = buildSmoothedPath(pointCords)
    drawPath(
        path = path,
        color = Color(0xFF0077CC),
        style = Stroke(width = 3f, cap = StrokeCap.Round)
    )

    points.forEachIndexed { idx, p ->
        if (p.first !in cfg.vr.visibleMin..cfg.vr.visibleMax) return@forEachIndexed
        val pt = Offset(xPos(p.first), yPos(p.second))
        drawCircle(color = Color.Red, radius = 4f, center = pt)
        if (cfg.selectedIndex == idx) {
            drawCircle(color = Color.Yellow, radius = 6f, center = pt)
            drawLine(
                color = Color.Gray,
                start = Offset(pt.x, cfg.area.topPaddingPx),
                end = Offset(pt.x, cfg.area.topPaddingPx + cfg.area.chartHeight)
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChartBorder(cfg: ChartRenderConfig) {
    val chartSize = Size(cfg.area.chartWidth, cfg.area.chartHeight)
    drawRect(
        color = Color.LightGray,
        topLeft = Offset(
            cfg.area.leftPaddingPx,
            cfg.area.topPaddingPx
        ),
        size = chartSize,
        style = Stroke(width = 1f)
    )
}
