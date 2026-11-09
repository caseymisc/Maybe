package com.caseymisc.fitdash.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caseymisc.fitdash.data.garmin.BodyBatteryPoint
import com.caseymisc.fitdash.data.garmin.StressPoint
import com.caseymisc.fitdash.data.garmin.splitAtGaps
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max

private const val DAY_SECONDS = 86_400f

/** Samples further apart than this are drawn as separate segments (watch off, no data). */
private val MAX_GAP: Duration = Duration.ofMinutes(15)

private val BatteryHigh = Color(0xFF2FBF71)
private val BatteryMid = Color(0xFF2FA4D8)
private val BatteryLow = Color(0xFFE8833A)
private val RestStress = Color(0xFF4F9FDE)
private val Stress = Color(0xFFE8833A)
private val SleepShade = Color(0xFF7C6FD8)

/**
 * A full day of Body Battery (midnight to midnight) at the resolution Garmin records it,
 * with stress beneath and the sleep window shaded. Tap or drag horizontally to read values.
 */
@Composable
fun BodyBatteryChart(
    battery: List<BodyBatteryPoint>,
    stress: List<StressPoint>,
    dayStart: Instant,
    sleepStart: Instant?,
    sleepEnd: Instant?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val is24h = remember { DateFormat.is24HourFormat(context) }
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val cursorColor = MaterialTheme.colorScheme.onSurface
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)

    var selected by remember(battery) { mutableStateOf<BodyBatteryPoint?>(null) }

    val leftPad = 28.dp
    val batteryBand = 150.dp
    val bandGap = 10.dp
    val stressBand = 36.dp
    val axisBand = 18.dp

    fun secondsIntoDay(t: Instant) = Duration.between(dayStart, t).seconds.toFloat().coerceIn(0f, DAY_SECONDS)

    Column(modifier.fillMaxWidth()) {
        // Readout: the selected sample, or the latest one.
        val shown = selected ?: battery.lastOrNull()
        val stressAt = shown?.let { p -> nearestStress(stress, p.time) }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                shown?.level?.toString() ?: "–",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    when {
                        shown == null -> "No data yet"
                        selected == null -> "Latest · ${formatTime(shown.time)}"
                        else -> formatTime(shown.time)
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                if (stressAt != null) {
                    Text(
                        "Stress ${stressAt.level}",
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(batteryBand + bandGap + stressBand + axisBand)
                .pointerInput(battery, dayStart) {
                    detectTapGestures { offset ->
                        val hit = pickPoint(battery, dayStart, offset.x, leftPad.toPx(), size.width.toFloat())
                        selected = if (hit == null || hit == selected) null else hit
                    }
                }
                .pointerInput(battery, dayStart) {
                    fun pick(x: Float) = pickPoint(battery, dayStart, x, leftPad.toPx(), size.width.toFloat())
                    detectHorizontalDragGestures(
                        onDragStart = { selected = pick(it.x) ?: selected },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            selected = pick(change.position.x) ?: selected
                        },
                    )
                },
        ) {
            val left = leftPad.toPx()
            val plotW = size.width - left
            val bbTop = 0f
            val bbBottom = batteryBand.toPx()
            val stTop = bbBottom + bandGap.toPx()
            val stBottom = stTop + stressBand.toPx()
            fun x(t: Instant) = left + secondsIntoDay(t) / DAY_SECONDS * plotW
            fun yBattery(level: Int) = bbBottom - (level.coerceIn(0, 100) / 100f) * (bbBottom - bbTop)

            // Sleep window.
            if (sleepStart != null && sleepEnd != null && sleepEnd.isAfter(dayStart)) {
                val x0 = x(sleepStart)
                val x1 = x(sleepEnd)
                if (x1 > x0) {
                    drawRect(SleepShade.copy(alpha = 0.12f), Offset(x0, bbTop), Size(x1 - x0, stBottom - bbTop))
                }
            }

            // Horizontal grid + level labels.
            listOf(0, 25, 50, 75, 100).forEach { level ->
                val y = yBattery(level)
                drawLine(gridColor, Offset(left, y), Offset(size.width, y), strokeWidth = 1f)
                if (level % 50 == 0) {
                    val layout = textMeasurer.measure(level.toString(), labelStyle)
                    drawText(layout, topLeft = Offset(left - layout.size.width - 6f, (y - layout.size.height / 2f).coerceAtLeast(0f)))
                }
            }
            drawLine(gridColor, Offset(left, stBottom), Offset(size.width, stBottom), strokeWidth = 1f)

            // Vertical hour grid + time labels.
            for (hour in 0..24 step 6) {
                val gx = left + hour / 24f * plotW
                if (hour in 1..23) {
                    drawLine(gridColor.copy(alpha = 0.6f), Offset(gx, bbTop), Offset(gx, stBottom), strokeWidth = 1f)
                }
                val label = hourLabel(hour, is24h)
                val layout = textMeasurer.measure(label, labelStyle)
                val lx = (gx - layout.size.width / 2f).coerceIn(left, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(lx, stBottom + 4f))
            }

            drawBattery(battery, ::x, ::yBattery, bbTop, bbBottom)
            drawStress(stress, ::x, plotW, stTop, stBottom)

            // Cursor.
            selected?.let { p ->
                val cx = x(p.time)
                drawLine(cursorColor.copy(alpha = 0.6f), Offset(cx, bbTop), Offset(cx, stBottom), strokeWidth = 2f)
                drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(cx, yBattery(p.level)))
                drawCircle(batteryColor(p.level), radius = 4.dp.toPx(), center = Offset(cx, yBattery(p.level)))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            LegendSwatch("Stress", Stress)
            LegendSwatch("Rest", RestStress)
            if (sleepStart != null) LegendSwatch("Sleep", SleepShade.copy(alpha = 0.35f))
            Spacer(Modifier.weight(1f))
            Text("Tap or drag to inspect", style = MaterialTheme.typography.labelSmall, color = labelColor)
        }
    }
}

private fun DrawScope.drawBattery(
    points: List<BodyBatteryPoint>,
    x: (Instant) -> Float,
    y: (Int) -> Float,
    top: Float,
    bottom: Float,
) {
    if (points.isEmpty()) return
    val stroke = Brush.verticalGradient(
        0f to BatteryHigh, 0.5f to BatteryMid, 1f to BatteryLow,
        startY = top, endY = bottom,
    )
    val fill = Brush.verticalGradient(
        0f to BatteryHigh.copy(alpha = 0.30f), 1f to BatteryMid.copy(alpha = 0.03f),
        startY = top, endY = bottom,
    )
    segments(points).forEach { segment ->
        if (segment.size == 1) {
            val p = segment.single()
            drawCircle(batteryColor(p.level), radius = 2.5.dp.toPx(), center = Offset(x(p.time), y(p.level)))
            return@forEach
        }
        val line = Path()
        segment.forEachIndexed { i, p ->
            if (i == 0) line.moveTo(x(p.time), y(p.level)) else line.lineTo(x(p.time), y(p.level))
        }
        val area = Path().apply {
            addPath(line)
            lineTo(x(segment.last().time), bottom)
            lineTo(x(segment.first().time), bottom)
            close()
        }
        drawPath(area, fill)
        drawPath(line, stroke, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun DrawScope.drawStress(
    points: List<StressPoint>,
    x: (Instant) -> Float,
    plotW: Float,
    top: Float,
    bottom: Float,
) {
    // Garmin samples stress every ~3 minutes; give each bar that slice of the day.
    val barW = max(1f, plotW * 180f / DAY_SECONDS)
    points.forEach { p ->
        val h = (p.level.coerceIn(1, 100) / 100f) * (bottom - top)
        val color = when {
            p.level <= 25 -> RestStress.copy(alpha = 0.7f)
            p.level <= 50 -> Stress.copy(alpha = 0.45f)
            p.level <= 75 -> Stress.copy(alpha = 0.7f)
            else -> Stress
        }
        drawRect(color, Offset(x(p.time) - barW / 2, bottom - h), Size(barW, h))
    }
}

private fun segments(points: List<BodyBatteryPoint>) = splitAtGaps(points, MAX_GAP) { it.time }

/** The sample nearest to horizontal position [x], if one lies within 30 minutes of it. */
private fun pickPoint(
    battery: List<BodyBatteryPoint>,
    dayStart: Instant,
    x: Float,
    leftPx: Float,
    widthPx: Float,
): BodyBatteryPoint? {
    val plotW = widthPx - leftPx
    if (plotW <= 0f || battery.isEmpty()) return null
    val t = dayStart.plusSeconds((((x - leftPx) / plotW).coerceIn(0f, 1f) * DAY_SECONDS).toLong())
    return battery.minBy { abs(Duration.between(it.time, t).seconds) }
        .takeIf { abs(Duration.between(it.time, t).toMinutes()) <= 30 }
}

private fun nearestStress(stress: List<StressPoint>, t: Instant): StressPoint? =
    stress.minByOrNull { abs(Duration.between(it.time, t).seconds) }
        ?.takeIf { abs(Duration.between(it.time, t).toMinutes()) <= 10 }

private fun batteryColor(level: Int): Color = when {
    level >= 50 -> BatteryHigh
    level >= 25 -> BatteryMid
    else -> BatteryLow
}

private fun hourLabel(hour: Int, is24h: Boolean): String = when {
    is24h -> "%02d".format(hour % 24)
    hour % 24 == 0 -> "12a"
    hour == 12 -> "12p"
    hour < 12 -> "${hour}a"
    else -> "${hour - 12}p"
}

@Composable
private fun LegendSwatch(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = color, shape = RoundedCornerShape(2.dp), modifier = Modifier.size(8.dp)) {}
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
