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

    private val dailyStressFeed = """
        {"calendarDate":"2026-11-09","maxStressLevel":88,"avgStressLevel":31,
         "stressValueDescriptorsDTOList":[{"key":"timestamp","index":0},{"key":"stressLevel","index":1}],
         "stressValuesArray":[[1762646400000,22],[1762646580000,-1],[1762646760000,-2],[1762646940000,47]],
         "bodyBatteryValueDescriptorsDTOList":[
           {"bodyBatteryValueDescriptorIndex":0,"bodyBatteryValueDescriptorKey":"timestamp"},
           {"bodyBatteryValueDescriptorIndex":1,"bodyBatteryValueDescriptorKey":"bodyBatteryStatus"},
           {"bodyBatteryValueDescriptorIndex":2,"bodyBatteryValueDescriptorKey":"bodyBatteryLevel"},
           {"bodyBatteryValueDescriptorIndex":3,"bodyBatteryValueDescriptorKey":"bodyBatteryVersion"}],
         "bodyBatteryValuesArray":[[1762646400000,"MEASURED",71,2.0],[1762646580000,"MEASURED",70,2.0],
                                   [1762646760000,"MEASURED",70,2.0]]}
    """.trimIndent()

    @Test
    fun detailedBodyBatteryFromDailyStressFeed() {
        val curve = parseBodyBatteryCurve(j(dailyStressFeed))
        assertEquals(listOf(71, 70, 70), curve.map { it.level })
        assertEquals(Instant.ofEpochMilli(1762646580000), curve[1].time)
    }

    @Test
    fun stressDropsUnmeasuredSamples() {
        val stress = parseStressCurve(j(dailyStressFeed))
        assertEquals(listOf(22, 47), stress.map { it.level })
    }

    @Test
    fun fourColumnRowsWithoutDescriptorsUseLevelColumn() {
        val curve = parseBodyBatteryCurve(j("""{"bodyBatteryValuesArray":[[1000,"MEASURED",55,2.0]]}"""))
        assertEquals(listOf(55), curve.map { it.level })
    }

    @Test
    fun splitsSeriesAtGaps() {
        val base = Instant.parse("2026-11-09T00:00:00Z")
        val times = listOf(0L, 3, 6, 40, 43, 120).map { base.plusSeconds(it * 60) }
        val segments = splitAtGaps(times, java.time.Duration.ofMinutes(15)) { it }
        assertEquals(listOf(3, 2, 1), segments.map { it.size })
    }
}
