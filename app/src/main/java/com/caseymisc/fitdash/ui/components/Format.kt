package com.caseymisc.fitdash.ui.components

import com.caseymisc.fitdash.data.settings.WeightUnit
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToLong

private const val KG_TO_LB = 2.2046226218

fun formatCount(value: Number): String = NumberFormat.getIntegerInstance().format(value.toDouble().roundToLong())

fun formatWeight(kg: Double, unit: WeightUnit, decimals: Boolean = false): String {
    val value = if (unit == WeightUnit.LB) kg * KG_TO_LB else kg
    val suffix = if (unit == WeightUnit.LB) "lb" else "kg"
    val number = if (decimals) String.format(Locale.getDefault(), "%.1f", value) else formatCount(value)
    return "$number $suffix"
}

fun formatDistance(meters: Double): String =
    if (meters >= 1000) String.format(Locale.getDefault(), "%.2f km", meters / 1000) else "${meters.roundToLong()} m"

fun formatMinutes(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

fun formatDuration(duration: Duration): String = formatMinutes(duration.toMinutes())

private val dayFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

fun formatDay(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val date = instant.atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(dayFormatter)
    }
}

fun formatDayTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    "${formatDay(instant, zone)}, ${instant.atZone(zone).toLocalTime().format(timeFormatter)}"

fun formatTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    instant.atZone(zone).toLocalTime().format(timeFormatter)

/** One-letter weekday label (M, T, W...) for chart axes. */
fun weekdayLabel(date: LocalDate): String =
    date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault())
