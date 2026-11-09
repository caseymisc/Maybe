package com.caseymisc.fitdash.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Minimal bar chart: one bar per value, the last bar highlighted, an optional dashed
 * goal line, and a label under each bar.
 */
@Composable
fun BarChart(
    values: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    barColor: Color = MaterialTheme.colorScheme.primary,
    goal: Double? = null,
) {
    val muted = barColor.copy(alpha = 0.45f)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val goalColor = MaterialTheme.colorScheme.outline
    val maxValue = (values + listOfNotNull(goal)).maxOrNull()?.takeIf { it > 0 } ?: 1.0

    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (values.isEmpty()) return@Canvas
            val slot = size.width / values.size
            val barWidth = slot * 0.6f
            val radius = CornerRadius(barWidth / 4, barWidth / 4)
            values.forEachIndexed { i, v ->
                val left = i * slot + (slot - barWidth) / 2
                drawRoundRect(track, Offset(left, 0f), Size(barWidth, size.height), radius)
                val barHeight = (v / maxValue).toFloat() * size.height
                if (barHeight > 0f) {
                    drawRoundRect(
                        color = if (i == values.lastIndex) barColor else muted,
                        topLeft = Offset(left, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = radius,
                    )
                }
            }
            if (goal != null) {
                val y = size.height - (goal / maxValue).toFloat() * size.height
                drawLine(
                    color = goalColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            labels.forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Horizontal stacked bar, e.g. for sleep stages. */
@Composable
fun StackedBar(
    parts: List<Pair<Double, Color>>,
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
) {
    val total = parts.sumOf { it.first }.takeIf { it > 0 } ?: return
    Canvas(modifier.fillMaxWidth().height(height)) {
        var x = 0f
        parts.forEach { (value, color) ->
            val w = (value / total).toFloat() * size.width
            drawRect(color, Offset(x, 0f), Size(w, size.height))
            x += w
        }
    }
}

/**
 * Simple line chart over a fixed y-range, with a soft fill. [points] are (x, y) where x is
 * any increasing value (e.g. epoch seconds).
 */
@Composable
fun LineChart(
    points: List<Pair<Double, Double>>,
    modifier: Modifier = Modifier,
    yMin: Double = 0.0,
    yMax: Double = 100.0,
    height: Dp = 120.dp,
    lineColor: Color = MaterialTheme.colorScheme.primary,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(height)) {
        listOf(0.25f, 0.5f, 0.75f).forEach { f ->
            drawLine(grid, Offset(0f, size.height * f), Offset(size.width, size.height * f), strokeWidth = 1f)
        }
        if (points.size < 2) return@Canvas
        val xMin = points.first().first
        val xSpan = (points.last().first - xMin).takeIf { it > 0 } ?: 1.0
        fun toOffset(p: Pair<Double, Double>) = Offset(
            ((p.first - xMin) / xSpan).toFloat() * size.width,
            size.height - ((p.second - yMin) / (yMax - yMin)).toFloat().coerceIn(0f, 1f) * size.height,
        )
        val line = androidx.compose.ui.graphics.Path()
        points.forEachIndexed { i, p ->
            val o = toOffset(p)
            if (i == 0) line.moveTo(o.x, o.y) else line.lineTo(o.x, o.y)
        }
        val fill = androidx.compose.ui.graphics.Path().apply {
            addPath(line)
            lineTo(toOffset(points.last()).x, size.height)
            lineTo(toOffset(points.first()).x, size.height)
            close()
        }
        drawPath(fill, lineColor.copy(alpha = 0.15f))
        drawPath(
            line,
            lineColor,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 3.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}
