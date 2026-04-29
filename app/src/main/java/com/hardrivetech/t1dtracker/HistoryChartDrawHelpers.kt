package com.hardrivetech.t1dtracker

import android.graphics.Paint as AndroidPaint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun buildSmoothedPath(pointCords: List<Offset>): Path {
    val path = Path()
    if (pointCords.isEmpty()) return path

    path.moveTo(pointCords[0].x, pointCords[0].y)
    if (pointCords.size > 1) {
        var previousPoint = pointCords[0]
        for (i in 1 until pointCords.size) {
            val current = pointCords[i]
            val midPoint = Offset(
                (previousPoint.x + current.x) / 2f,
                (previousPoint.y + current.y) / 2f
            )
            path.quadraticBezierTo(
                previousPoint.x,
                previousPoint.y,
                midPoint.x,
                midPoint.y
            )
            previousPoint = current
        }
        path.lineTo(pointCords.last().x, pointCords.last().y)
    }
    return path
}

internal fun DrawScope.drawYAxisLabels(
    yLabelCount: Int,
    yr: YRange,
    area: ChartArea,
    yPos: (Double) -> Float,
    labelPaint: AndroidPaint
) {
    if (yLabelCount <= 0) return
    val labelSpacing = (yr.yMaxAdj - yr.yMinAdj) / (yLabelCount - 1).coerceAtLeast(1)
    for (i in 0 until yLabelCount) {
        val value = yr.yMinAdj + i * labelSpacing
        val y = yPos(value)
        drawContext.canvas.nativeCanvas.apply {
            labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
            drawText(
                String.format(Locale.getDefault(), "%.0f", value),
                area.leftPaddingPx - 8f,
                y + (labelPaint.textSize / 2f),
                labelPaint
            )
        }
    }
}

internal fun DrawScope.drawXTicks(
    ticks: List<Long>,
    xPos: (Long) -> Float,
    canvasHeight: Float,
    labelPaint: AndroidPaint
) {
    val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
    for (t in ticks) {
        val x = xPos(t)
        drawContext.canvas.nativeCanvas.apply {
            labelPaint.textAlign = android.graphics.Paint.Align.CENTER
            drawText(sdf.format(Date(t)), x, canvasHeight - 6f, labelPaint)
        }
    }
}

internal fun DrawScope.drawGridAndYAxis(
    cfg: ChartRenderConfig,
    xPos: (Long) -> Float,
    yPos: (Double) -> Float
) {
    // draw grid lines
    val paintColor = Color.LightGray
    for (i in 0 until cfg.yLabelCount) {
        val denom = (cfg.yLabelCount - 1).coerceAtLeast(1)
        val t = cfg.yr.yMinAdj + i * (cfg.yr.yMaxAdj - cfg.yr.yMinAdj) / denom
        val y = yPos(t)
        drawLine(
            color = paintColor,
            start = Offset(cfg.area.leftPaddingPx, y),
            end = Offset(
                cfg.area.leftPaddingPx + cfg.area.chartWidth,
                y
            ),
            strokeWidth = 1f
        )
    }
    drawYAxisLabels(cfg.yLabelCount, cfg.yr, cfg.area, yPos, cfg.labelPaint)
    val xTicks = listOf(
        cfg.vr.visibleMin,
        cfg.vr.visibleMin + (cfg.vr.visibleMax - cfg.vr.visibleMin) / 2,
        cfg.vr.visibleMax
    )
    drawXTicks(xTicks, xPos, size.height, cfg.labelPaint)
}

internal fun DrawScope.drawPathAndPoints(
    cfg: ChartRenderConfig,
    xPos: (Long) -> Float,
    yPos: (Double) -> Float,
    points: List<Pair<Long, Double>>
) {
    val pointCords = points.map { Offset(xPos(it.first), yPos(it.second)) }
    val path = buildSmoothedPath(pointCords)
    drawPath(
        path = path,
        color = Color(0xFF3B82F6),
        style = Stroke(width = 3f)
    )
    // draw points
    points.forEachIndexed { idx, p ->
        drawCircle(
            color = if (cfg.selectedIndex == idx) Color.Yellow else Color.White,
            radius = if (cfg.selectedIndex == idx) 6f else 3f,
            center = Offset(xPos(p.first), yPos(p.second))
        )
    }
}

internal fun DrawScope.drawChartBorder(area: ChartArea) {
    drawRect(
        color = Color.LightGray,
        topLeft = Offset(area.leftPaddingPx, area.topPaddingPx),
        size = Size(area.chartWidth, area.chartHeight),
        style = Stroke(width = 1f)
    )
}

internal fun DrawScope.drawChartContent(points: List<Pair<Long, Double>>, cfg: ChartRenderConfig) {
    val (xPos, yPos) = makeCoordinateFunctions(cfg.vr, cfg.area, cfg.yr)
    drawGridAndYAxis(cfg, xPos, yPos)
    drawPathAndPoints(cfg, xPos, yPos, points)
    drawChartBorder(cfg.area)
}

internal fun makeCoordinateFunctions(
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
