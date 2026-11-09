package com.caseymisc.fitdash.data.garmin

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Parsers for Garmin Connect responses. Garmin's JSON is undocumented and shifts between
 * versions, so everything here is lenient: missing fields become null instead of errors.
 */

data class GarminDay(
    val date: LocalDate,
    val bodyBatteryLatest: Int? = null,
    val bodyBatteryHigh: Int? = null,
    val bodyBatteryLow: Int? = null,
    val bodyBatteryCharged: Int? = null,
    val bodyBatteryDrained: Int? = null,
    val avgStress: Int? = null,
    val maxStress: Int? = null,
    val restingHr: Int? = null,
    val steps: Int? = null,
    val sleep: GarminSleep? = null,
)

data class GarminSleep(
    val score: Int?,
    val quality: String?,
    val totalSeconds: Long?,
    val deepSeconds: Long?,
    val lightSeconds: Long?,
    val remSeconds: Long?,
    val awakeSeconds: Long?,
    val start: Instant?,
    val end: Instant?,
    val avgOvernightHrv: Double?,
)

data class GarminReadiness(val score: Int?, val level: String?, val feedback: String?)

data class GarminHrv(val lastNightAvg: Int?, val weeklyAvg: Int?, val status: String?)

data class BodyBatteryPoint(val time: Instant, val level: Int)

// --- JSON helpers ------------------------------------------------------------------------

internal fun JsonElement?.prim(): JsonPrimitive? = (this as? JsonPrimitive)?.takeIf { this !is JsonNull }

internal fun JsonObject.num(key: String): Double? = this[key].prim()?.doubleOrNull

internal fun JsonObject.int(key: String): Int? = num(key)?.roundToInt()

internal fun JsonObject.long(key: String): Long? = this[key].prim()?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }

internal fun JsonObject.str(key: String): String? = this[key].prim()?.takeIf { it.isString }?.contentOrNull

internal fun JsonElement?.obj(key: String): JsonObject? = (this as? JsonObject)?.get(key) as? JsonObject

/** Depth-first search for the first non-null value stored under [key]. */
internal fun JsonElement?.findKey(key: String): JsonElement? = when (this) {
    is JsonObject -> this[key]?.takeIf { it !is JsonNull } ?: values.firstNotNullOfOrNull { it.findKey(key) }
    is JsonArray -> firstNotNullOfOrNull { it.findKey(key) }
    else -> null
}

/** "PRODUCTIVE_3" -> "Productive", "MODERATE" -> "Moderate", "FAIR" -> "Fair". */
fun prettyEnum(value: String?): String? {
    if (value.isNullOrBlank()) return null
    val words = value.replace(Regex("_\\d+$"), "").split('_').filter { it.isNotBlank() }
    return words.joinToString(" ") { it.lowercase(Locale.ROOT) }
        .replaceFirstChar { it.titlecase(Locale.ROOT) }
}

// --- Endpoint parsers --------------------------------------------------------------------

/** `/usersummary-service/usersummary/daily/{displayName}?calendarDate=` */
fun parseDailySummary(date: LocalDate, json: JsonElement?): GarminDay {
    val o = json as? JsonObject ?: return GarminDay(date)
    return GarminDay(
        date = date,
        bodyBatteryLatest = o.int("bodyBatteryMostRecentValue"),
        bodyBatteryHigh = o.int("bodyBatteryHighestValue"),
        bodyBatteryLow = o.int("bodyBatteryLowestValue"),
        bodyBatteryCharged = o.int("bodyBatteryChargedValue"),
        bodyBatteryDrained = o.int("bodyBatteryDrainedValue"),
        avgStress = o.int("averageStressLevel")?.takeIf { it >= 0 },
        maxStress = o.int("maxStressLevel")?.takeIf { it >= 0 },
        restingHr = o.int("restingHeartRate"),
        steps = o.int("totalSteps"),
    )
}

/** `/wellness-service/wellness/dailySleepData/{displayName}?date=` */
fun parseSleep(json: JsonElement?): GarminSleep? {
    val dto = json.obj("dailySleepDTO") ?: return null
    val total = dto.long("sleepTimeSeconds")
    val overall = dto.obj("sleepScores").obj("overall")
    val score = overall?.int("value")
    if ((total == null || total == 0L) && score == null) return null
    return GarminSleep(
        score = score,
        quality = prettyEnum(overall?.str("qualifierKey")),
        totalSeconds = total,
        deepSeconds = dto.long("deepSleepSeconds"),
        lightSeconds = dto.long("lightSleepSeconds"),
        remSeconds = dto.long("remSleepSeconds"),
        awakeSeconds = dto.long("awakeSleepSeconds"),
        start = dto.long("sleepStartTimestampGMT")?.let(Instant::ofEpochMilli),
        end = dto.long("sleepEndTimestampGMT")?.let(Instant::ofEpochMilli),
        avgOvernightHrv = (json as? JsonObject)?.num("avgOvernightHrv") ?: dto.num("avgOvernightHrv"),
    )
}

/** `/metrics-service/metrics/trainingreadiness/{date}`: a list, possibly several per day. */
fun parseTrainingReadiness(json: JsonElement?): GarminReadiness? {
    val entries = when (json) {
        is JsonArray -> json.mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(json)
        else -> emptyList()
    }.filter { it.int("score") != null }
    val latest = entries.maxByOrNull { it.str("timestampLocal") ?: it.str("timestamp") ?: "" } ?: return null
    return GarminReadiness(
        score = latest.int("score"),
        level = prettyEnum(latest.str("level")),
        feedback = prettyEnum(latest.str("feedbackShort")),
    )
}

/** `/hrv-service/hrv/{date}` */
fun parseHrv(json: JsonElement?): GarminHrv? {
    val summary = json.obj("hrvSummary") ?: return null
    return GarminHrv(
        lastNightAvg = summary.int("lastNightAvg"),
        weeklyAvg = summary.int("weeklyAvg"),
        status = prettyEnum(summary.str("status")),
    ).takeIf { it.lastNightAvg != null || it.weeklyAvg != null || it.status != null }
}

/** `/metrics-service/metrics/trainingstatus/daily/{date}`: nested per device, so search. */
fun parseTrainingStatus(json: JsonElement?): String? =
    prettyEnum(json.findKey("trainingStatusFeedbackPhrase").prim()?.contentOrNull)

/** `/wellness-service/wellness/bodyBattery/reports/daily?startDate=&endDate=` */
fun parseBodyBatteryCurve(json: JsonElement?): List<BodyBatteryPoint> {
    val day = when (json) {
        is JsonArray -> json.lastOrNull() as? JsonObject
        is JsonObject -> json
        else -> null
    } ?: return emptyList()
    val values = day["bodyBatteryValuesArray"] as? JsonArray ?: return emptyList()

    // Descriptors say which column holds the level; default to Garmin's usual [timestamp, level].
    val descriptors = day.entries
        .firstOrNull { it.key.startsWith("bodyBatteryValueDescriptor") && it.value is JsonArray }
        ?.value as? JsonArray
    val levelIndex = descriptors?.mapNotNull { it as? JsonObject }
        ?.firstOrNull { d -> d.values.any { it.prim()?.contentOrNull == "bodyBatteryLevel" } }
        ?.let { d -> d.entries.firstOrNull { it.key.endsWith("Index") }?.value.prim()?.doubleOrNull?.toInt() }
        ?: 1

    return values.mapNotNull { row ->
        val cols = row as? JsonArray ?: return@mapNotNull null
        val ts = cols.getOrNull(0).prim()?.longOrNull ?: return@mapNotNull null
        val level = cols.getOrNull(levelIndex).prim()?.doubleOrNull ?: return@mapNotNull null
        BodyBatteryPoint(Instant.ofEpochMilli(ts), level.roundToInt())
    }.sortedBy { it.time }
}
