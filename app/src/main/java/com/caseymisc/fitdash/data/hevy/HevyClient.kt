package com.caseymisc.fitdash.data.hevy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

class HevyApiException(val code: Int, message: String) : IOException(message)

/**
 * Thin client for the Hevy public API. Requires a Hevy Pro API key from
 * https://hevy.com/settings?developer
 */
class HevyClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = "https://api.hevyapp.com",
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    suspend fun workoutsPage(apiKey: String, page: Int, pageSize: Int = 10): HevyWorkoutsPage =
        get(apiKey, "/v1/workouts?page=$page&pageSize=$pageSize") {
            json.decodeFromString(HevyWorkoutsPage.serializer(), it)
        }

    suspend fun workoutCount(apiKey: String): Int =
        get(apiKey, "/v1/workouts/count") {
            json.decodeFromString(HevyWorkoutCount.serializer(), it).workoutCount
        }

    /**
     * Pages through workouts (newest first) until one starts before [since], the last page
     * is reached, or [maxPages] pages have been read.
     */
    suspend fun workoutsSince(apiKey: String, since: Instant, maxPages: Int = 25): List<HevyWorkout> {
        val result = mutableListOf<HevyWorkout>()
        var page = 1
        while (page <= maxPages) {
            val response = try {
                workoutsPage(apiKey, page)
            } catch (e: HevyApiException) {
                // Hevy answers 404 for a page past the end.
                if (e.code == 404 && page > 1) break else throw e
            }
            val inRange = response.workouts.filter { !it.start.isBefore(since) }
            result += inRange
            val reachedOlder = inRange.size < response.workouts.size
            if (reachedOlder || response.workouts.isEmpty() || page >= response.pageCount) break
            page++
        }
        return result.sortedByDescending { it.start }
    }

    private suspend fun <T> get(apiKey: String, path: String, parse: (String) -> T): T =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(baseUrl + path)
                .header("api-key", apiKey)
                .header("accept", "application/json")
                .build()
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val reason = when (response.code) {
                        401, 403 -> "Hevy rejected the API key (HTTP ${response.code}). Check it in Settings."
                        else -> "Hevy API error HTTP ${response.code}: ${body.take(200)}"
                    }
                    throw HevyApiException(response.code, reason)
                }
                parse(body)
            }
        }
}
