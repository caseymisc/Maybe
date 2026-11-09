package com.caseymisc.fitdash.data.hevy

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Hevy timestamps are ISO-8601 with either a `Z` or a numeric offset. */
fun parseHevyInstant(value: String): Instant = OffsetDateTime.parse(value).toInstant()

val HevyWorkout.start: Instant get() = parseHevyInstant(startTime)

val HevyWorkout.duration: Duration?
    get() = endTime?.let { Duration.between(start, parseHevyInstant(it)) }

val HevySet.isWorkingSet: Boolean get() = type != "warmup"

/** Volume = weight x reps over working sets (warm-ups excluded). */
val HevyExercise.volumeKg: Double
    get() = sets.filter { it.isWorkingSet }.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }

val HevyWorkout.volumeKg: Double get() = exercises.sumOf { it.volumeKg }

val HevyWorkout.workingSetCount: Int get() = exercises.sumOf { ex -> ex.sets.count { it.isWorkingSet } }

data class WeekBucket(
    val weekStart: LocalDate,
    val workouts: Int,
    val volumeKg: Double,
    val minutes: Long,
)

fun LocalDate.startOfWeek(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/**
 * Groups workouts into Monday-start weeks, returning exactly [weeks] buckets ending with
 * the week that contains [today] (oldest first). Empty weeks are included as zeros.
 */
fun weeklyBuckets(
    workouts: List<HevyWorkout>,
    weeks: Int,
    today: LocalDate,
    zone: ZoneId,
): List<WeekBucket> {
    val currentWeek = today.startOfWeek()
    val byWeek = workouts.groupBy { it.start.atZone(zone).toLocalDate().startOfWeek() }
    return (weeks - 1 downTo 0).map { offset ->
        val weekStart = currentWeek.minusWeeks(offset.toLong())
        val inWeek = byWeek[weekStart].orEmpty()
        WeekBucket(
            weekStart = weekStart,
            workouts = inWeek.size,
            volumeKg = inWeek.sumOf { it.volumeKg },
            minutes = inWeek.sumOf { it.duration?.toMinutes() ?: 0L },
        )
    }
}

/** Best set (highest weight, then reps) per exercise title across the given workouts. */
fun personalBests(workouts: List<HevyWorkout>): Map<String, HevySet> =
    workouts.flatMap { it.exercises }
        .groupBy { it.title }
        .mapValues { (_, exercises) ->
            exercises.flatMap { it.sets }
                .filter { it.isWorkingSet && it.weightKg != null }
                .maxWithOrNull(compareBy<HevySet>({ it.weightKg }, { it.reps ?: 0 }))
        }
        .filterValues { it != null }
        .mapValues { it.value!! }
