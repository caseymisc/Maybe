package com.caseymisc.fitdash.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

enum class HealthConnectAvailability { AVAILABLE, NOT_INSTALLED, UPDATE_REQUIRED }

data class DayValue(val date: LocalDate, val value: Double)

data class TodayStats(
    val steps: Long?,
    val activeKcal: Double?,
    val distanceMeters: Double?,
    val avgHr: Long?,
    val minHr: Long?,
    val maxHr: Long?,
    val restingHr: Long?,
)

data class SleepSummary(
    val start: Instant,
    val end: Instant,
    val asleepMinutes: Long,
    val deepMinutes: Long,
    val remMinutes: Long,
    val lightMinutes: Long,
    val awakeMinutes: Long,
)

data class ActivitySummary(
    val start: Instant,
    val end: Instant,
    val type: String,
    val title: String?,
    val source: String,
    val distanceMeters: Double?,
    val activeKcal: Double?,
) {
    val duration: Duration get() = Duration.between(start, end)
}

data class HealthSnapshot(
    val today: TodayStats,
    val dailySteps: List<DayValue>,
    val dailySleepMinutes: List<DayValue>,
    val dailyRestingHr: List<DayValue>,
    val lastSleep: SleepSummary?,
    val activities: List<ActivitySummary>,
    val missingPermissions: Set<String>,
)

/**
 * Reads health data from Android Health Connect. Garmin Connect (and Hevy) write into
 * Health Connect, so this is how the app sees Garmin steps, sleep, heart rate and activities.
 */
class HealthConnectRepository(private val context: Context) {

    private val readTypes: List<KClass<out Record>> = listOf(
        StepsRecord::class,
        DistanceRecord::class,
        ActiveCaloriesBurnedRecord::class,
        HeartRateRecord::class,
        RestingHeartRateRecord::class,
        SleepSessionRecord::class,
        ExerciseSessionRecord::class,
    )

    val requiredPermissions: Set<String> =
        readTypes.map { HealthPermission.getReadPermission(it) }.toSet()

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    fun availability(): HealthConnectAvailability =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectAvailability.UPDATE_REQUIRED
            else -> HealthConnectAvailability.NOT_INSTALLED
        }

    suspend fun grantedPermissions(): Set<String> =
        client.permissionController.getGrantedPermissions()

    suspend fun loadSnapshot(
        granted: Set<String>,
        days: Int = 7,
        activityDays: Long = 14,
        zone: ZoneId = ZoneId.systemDefault(),
    ): HealthSnapshot {
        fun has(type: KClass<out Record>) = HealthPermission.getReadPermission(type) in granted

        val now = Instant.now()
        val today = LocalDate.now(zone)
        val firstDay = today.minusDays(days - 1L)
        val dayRange = (0 until days).map { firstDay.plusDays(it.toLong()) }

        // --- Today -------------------------------------------------------------------------
        val todayMetrics = buildSet<AggregateMetric<*>> {
            if (has(StepsRecord::class)) add(StepsRecord.COUNT_TOTAL)
            if (has(ActiveCaloriesBurnedRecord::class)) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            if (has(DistanceRecord::class)) add(DistanceRecord.DISTANCE_TOTAL)
            if (has(HeartRateRecord::class)) {
                add(HeartRateRecord.BPM_AVG)
                add(HeartRateRecord.BPM_MIN)
                add(HeartRateRecord.BPM_MAX)
            }
        }
        val todayResult = if (todayMetrics.isEmpty()) null else client.aggregate(
            AggregateRequest(
                metrics = todayMetrics,
                timeRangeFilter = TimeRangeFilter.between(today.atStartOfDay(zone).toInstant(), now),
            )
        )

        // --- Per-day steps & resting HR ----------------------------------------------------
        val dailyMetrics = buildSet<AggregateMetric<*>> {
            if (has(StepsRecord::class)) add(StepsRecord.COUNT_TOTAL)
            if (has(RestingHeartRateRecord::class)) add(RestingHeartRateRecord.BPM_AVG)
        }
        val daily = if (dailyMetrics.isEmpty()) emptyList() else client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = dailyMetrics,
                timeRangeFilter = TimeRangeFilter.between(
                    firstDay.atStartOfDay(),
                    today.plusDays(1).atStartOfDay(),
                ),
                timeRangeSlicer = Period.ofDays(1),
            )
        )
        val stepsByDay = daily.associate {
            it.startTime.toLocalDate() to (it.result[StepsRecord.COUNT_TOTAL] ?: 0L).toDouble()
        }
        val rhrByDay = daily.mapNotNull { group ->
            group.result[RestingHeartRateRecord.BPM_AVG]?.let { group.startTime.toLocalDate() to it.toDouble() }
        }.toMap()

        // --- Sleep -------------------------------------------------------------------------
        val sleepSessions = if (!has(SleepSessionRecord::class)) emptyList() else client.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    firstDay.minusDays(1).atStartOfDay(zone).toInstant(),
                    now,
                ),
            )
        ).records
        val sleepSummaries = sleepSessions.map { it.toSummary() }
        val sleepByDay = sleepSummaries
            .groupBy { it.end.atZone(zone).toLocalDate() }
            .mapValues { (_, list) -> list.sumOf { it.asleepMinutes }.toDouble() }

        // --- Activities --------------------------------------------------------------------
        val sessions = if (!has(ExerciseSessionRecord::class)) emptyList() else client.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(now.minus(Duration.ofDays(activityDays)), now),
                ascendingOrder = false,
            )
        ).records
        val activities = sessions.map { session ->
            val totals = sessionTotals(session, has(DistanceRecord::class), has(ActiveCaloriesBurnedRecord::class))
            ActivitySummary(
                start = session.startTime,
                end = session.endTime,
                type = exerciseTypeName(session.exerciseType),
                title = session.title,
                source = sourceName(session.metadata.dataOrigin.packageName),
                distanceMeters = totals.first,
                activeKcal = totals.second,
            )
        }

        return HealthSnapshot(
            today = TodayStats(
                steps = todayResult?.get(StepsRecord.COUNT_TOTAL),
                activeKcal = todayResult?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories,
                distanceMeters = todayResult?.get(DistanceRecord.DISTANCE_TOTAL)?.inMeters,
                avgHr = todayResult?.get(HeartRateRecord.BPM_AVG),
                minHr = todayResult?.get(HeartRateRecord.BPM_MIN),
                maxHr = todayResult?.get(HeartRateRecord.BPM_MAX),
                restingHr = rhrByDay[today]?.toLong(),
            ),
            dailySteps = dayRange.map { DayValue(it, stepsByDay[it] ?: 0.0) },
            dailySleepMinutes = dayRange.map { DayValue(it, sleepByDay[it] ?: 0.0) },
            dailyRestingHr = dayRange.mapNotNull { day -> rhrByDay[day]?.let { DayValue(day, it) } },
            lastSleep = sleepSummaries.maxByOrNull { it.end },
            activities = activities,
            missingPermissions = requiredPermissions - granted,
        )
    }

    private suspend fun sessionTotals(
        session: ExerciseSessionRecord,
        canReadDistance: Boolean,
        canReadCalories: Boolean,
    ): Pair<Double?, Double?> {
        val metrics = buildSet<AggregateMetric<*>> {
            if (canReadDistance) add(DistanceRecord.DISTANCE_TOTAL)
            if (canReadCalories) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
        }
        if (metrics.isEmpty()) return null to null
        return runCatching {
            val result = client.aggregate(
                AggregateRequest(
                    metrics = metrics,
                    timeRangeFilter = TimeRangeFilter.between(session.startTime, session.endTime),
                )
            )
            result[DistanceRecord.DISTANCE_TOTAL]?.inMeters?.takeIf { it > 0 } to
                result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.takeIf { it > 0 }
        }.getOrDefault(null to null)
    }

    private fun SleepSessionRecord.toSummary(): SleepSummary {
        fun minutesOf(stage: Int) = stages.filter { it.stage == stage }
            .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }

        val total = Duration.between(startTime, endTime).toMinutes()
        val awake = minutesOf(SleepSessionRecord.STAGE_TYPE_AWAKE) +
            minutesOf(SleepSessionRecord.STAGE_TYPE_OUT_OF_BED)
        return SleepSummary(
            start = startTime,
            end = endTime,
            asleepMinutes = (total - awake).coerceAtLeast(0),
            deepMinutes = minutesOf(SleepSessionRecord.STAGE_TYPE_DEEP),
            remMinutes = minutesOf(SleepSessionRecord.STAGE_TYPE_REM),
            lightMinutes = minutesOf(SleepSessionRecord.STAGE_TYPE_LIGHT),
            awakeMinutes = awake,
        )
    }

    companion object {
        const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

        fun sourceName(packageName: String): String = when {
            packageName.startsWith("com.garmin") -> "Garmin"
            packageName == "com.hevy" -> "Hevy"
            packageName == "com.google.android.apps.fitness" -> "Google Fit"
            packageName == "com.sec.android.app.shealth" -> "Samsung Health"
            else -> packageName
        }

        fun exerciseTypeName(type: Int): String = when (type) {
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Run"
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Treadmill run"
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walk"
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hike"
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Ride"
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Indoor ride"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Pool swim"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Open water swim"
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Strength"
            ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Weightlifting"
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "Indoor row"
            ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Elliptical"
            ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
            else -> "Workout"
        }
    }
}
