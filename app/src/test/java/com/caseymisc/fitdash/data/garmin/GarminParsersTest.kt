package com.caseymisc.fitdash.data.garmin

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class GarminParsersTest {
    private fun j(s: String) = Json.parseToJsonElement(s)
    private val date = LocalDate.of(2026, 11, 9)

    @Test
    fun dailySummary() {
        val day = parseDailySummary(
            date,
            j(
                """{"totalSteps":8123,"restingHeartRate":48,"averageStressLevel":27,"maxStressLevel":91,
                   "bodyBatteryHighestValue":88,"bodyBatteryLowestValue":21,"bodyBatteryMostRecentValue":64,
                   "bodyBatteryChargedValue":55,"bodyBatteryDrainedValue":42,"unknownField":{"x":1}}"""
            ),
        )
        assertEquals(64, day.bodyBatteryLatest)
        assertEquals(88, day.bodyBatteryHigh)
        assertEquals(21, day.bodyBatteryLow)
        assertEquals(27, day.avgStress)
        assertEquals(48, day.restingHr)
        assertEquals(8123, day.steps)
    }

    @Test
    fun dailySummaryHandlesMissingValuesAndNegativeStress() {
        val day = parseDailySummary(date, j("""{"averageStressLevel":-1,"bodyBatteryHighestValue":null}"""))
        assertNull(day.avgStress)
        assertNull(day.bodyBatteryHigh)
        assertEquals(GarminDay(date), parseDailySummary(date, null))
    }

    @Test
    fun sleep() {
        val sleep = parseSleep(
            j(
                """{"dailySleepDTO":{"sleepTimeSeconds":27000,"deepSleepSeconds":5400,"lightSleepSeconds":14400,
                   "remSleepSeconds":7200,"awakeSleepSeconds":900,"sleepStartTimestampGMT":1762642800000,
                   "sleepEndTimestampGMT":1762670700000,
                   "sleepScores":{"overall":{"value":84,"qualifierKey":"GOOD"},"remPercentage":{"value":27}}},
                   "avgOvernightHrv":52.0}"""
            )
        )!!
        assertEquals(84, sleep.score)
        assertEquals("Good", sleep.quality)
        assertEquals(27000L, sleep.totalSeconds)
        assertEquals(Instant.ofEpochMilli(1762642800000), sleep.start)
        assertEquals(52.0, sleep.avgOvernightHrv!!, 0.0)
    }

    @Test
    fun noSleepRecorded() {
        assertNull(parseSleep(j("""{"dailySleepDTO":{"sleepTimeSeconds":null}}""")))
        assertNull(parseSleep(j("{}")))
    }

    @Test
    fun trainingReadinessPicksLatest() {
        val r = parseTrainingReadiness(
            j(
                """[{"timestampLocal":"2026-11-09T06:10:00.0","score":61,"level":"MODERATE","feedbackShort":"RECOVERED_AND_READY"},
                    {"timestampLocal":"2026-11-09T09:30:00.0","score":58,"level":"MODERATE"}]"""
            )
        )!!
        assertEquals(58, r.score)
        assertEquals("Moderate", r.level)
    }

    @Test
    fun hrv() {
        val h = parseHrv(j("""{"hrvSummary":{"lastNightAvg":49,"weeklyAvg":53,"status":"BALANCED"}}"""))!!
        assertEquals(49, h.lastNightAvg)
        assertEquals("Balanced", h.status)
        assertNull(parseHrv(j("{}")))
    }

    @Test
    fun trainingStatusIsFoundWhereverItIsNested() {
        val s = parseTrainingStatus(
            j("""{"mostRecentTrainingStatus":{"latestTrainingStatusData":{"3412345":{"trainingStatusFeedbackPhrase":"PRODUCTIVE_3"}}}}""")
        )
        assertEquals("Productive", s)
    }

    @Test
    fun bodyBatteryCurveUsesDescriptors() {
        val curve = parseBodyBatteryCurve(
            j(
                """[{"date":"2026-11-09",
                    "bodyBatteryValueDescriptorDTOList":[
                      {"bodyBatteryValueDescriptorIndex":0,"bodyBatteryValueDescriptorKey":"timestamp"},
                      {"bodyBatteryValueDescriptorIndex":1,"bodyBatteryValueDescriptorKey":"bodyBatteryStatus"},
                      {"bodyBatteryValueDescriptorIndex":2,"bodyBatteryValueDescriptorKey":"bodyBatteryLevel"}],
                    "bodyBatteryValuesArray":[[1762675200000,"MEASURED",60],[1762671600000,"MEASURED",55],[1762678800000,"MEASURED",null]]}]"""
            )
        )
        assertEquals(listOf(55, 60), curve.map { it.level })
    }

    @Test
    fun bodyBatteryCurveDefaultsToSecondColumn() {
        val curve = parseBodyBatteryCurve(j("""[{"bodyBatteryValuesArray":[[1000,40],[2000,38.6]]}]"""))
        assertEquals(listOf(40, 39), curve.map { it.level })
    }

    @Test
    fun prettyEnums() {
        assertEquals("Productive", prettyEnum("PRODUCTIVE_3"))
        assertEquals("Recovered and ready", prettyEnum("RECOVERED_AND_READY"))
        assertNull(prettyEnum(""))
    }
}
