package com.caseymisc.fitdash.data.garmin

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonElement
import java.time.LocalDate
import java.time.ZoneId

data class GarminDashboard(
    val displayName: String,
    /** Oldest first; the last entry is today. */
    val days: List<GarminDay>,
    val readiness: GarminReadiness?,
    val hrv: GarminHrv?,
    val trainingStatus: String?,
    val bodyBatteryToday: List<BodyBatteryPoint>,
    val stressToday: List<StressPoint> = emptyList(),
) {
    val today: GarminDay get() = days.last()

    /** Last night's sleep is filed under today's date by Garmin. */
    val lastSleep: GarminSleep? get() = days.lastOrNull { it.sleep != null }?.sleep
}

class GarminRepository(private val client: GarminClient) {

    suspend fun isSignedIn() = client.isSignedIn()

    suspend fun signOut() = client.signOut()

    suspend fun load(days: Int = 7, zone: ZoneId = ZoneId.systemDefault()): GarminDashboard = coroutineScope {
        val name = client.displayName()
        val today = LocalDate.now(zone)
        val dates = (days - 1 downTo 0).map { today.minusDays(it.toLong()) }
        val limit = Semaphore(4)

        // Auth errors propagate; any other single-endpoint failure just leaves that metric empty.
        suspend fun fetch(path: String, params: Map<String, String> = emptyMap()): JsonElement? =
            limit.withPermit {
                try {
                    client.get(path, params)
                } catch (e: GarminAuthException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }

        val dayJobs = dates.map { date ->
            async {
                val d = date.toString()
                val summary = async {
                    fetch("/usersummary-service/usersummary/daily/$name", mapOf("calendarDate" to d))
                }
                val sleep = async {
                    fetch(
                        "/wellness-service/wellness/dailySleepData/$name",
                        mapOf("date" to d, "nonSleepBufferMinutes" to "60"),
                    )
                }
                parseDailySummary(date, summary.await()).copy(sleep = parseSleep(sleep.await()))
            }
        }
        val t = today.toString()
        val readiness = async { parseTrainingReadiness(fetch("/metrics-service/metrics/trainingreadiness/$t")) }
        val hrv = async { parseHrv(fetch("/hrv-service/hrv/$t")) }
        val status = async { parseTrainingStatus(fetch("/metrics-service/metrics/trainingstatus/daily/$t")) }
        // The daily stress feed carries Body Battery every few minutes; the Body Battery report
        // is coarser, so it's only used when the stress feed has no Body Battery samples.
        val stressFeed = async { fetch("/wellness-service/wellness/dailyStress/$t") }
        val curve = async {
            parseBodyBatteryCurve(stressFeed.await()).ifEmpty {
                parseBodyBatteryCurve(
                    fetch("/wellness-service/wellness/bodyBattery/reports/daily", mapOf("startDate" to t, "endDate" to t))
                )
            }
        }

        GarminDashboard(
            displayName = name,
            days = dayJobs.awaitAll(),
            readiness = readiness.await(),
            hrv = hrv.await(),
            trainingStatus = status.await(),
            bodyBatteryToday = curve.await(),
            stressToday = parseStressCurve(stressFeed.await()),
        )
    }
}
