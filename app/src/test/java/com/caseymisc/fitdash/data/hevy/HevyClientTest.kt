package com.caseymisc.fitdash.data.hevy

import com.caseymisc.fitdash.data.TestServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HevyClientTest {
    private fun w(id: String, start: String) = """{"id":"$id","title":"$id","start_time":"$start","exercises":[]}"""

    @Test
    fun pagesUntilWorkoutsAreOlderThanSince() = runBlocking {
        TestServer().use { server ->
            server.route("/v1/workouts") { req ->
                val page = Regex("page=(\\d+)").find(req.query!!)!!.groupValues[1].toInt()
                200 to when (page) {
                    1 -> """{"page":1,"page_count":5,"workouts":[${w("a", "2026-11-08T10:00:00Z")},${w("b", "2026-11-01T10:00:00Z")}]}"""
                    2 -> """{"page":2,"page_count":5,"workouts":[${w("c", "2026-10-20T10:00:00Z")},${w("d", "2026-09-01T10:00:00Z")}]}"""
                    else -> """{"page":$page,"page_count":5,"workouts":[${w("z", "2026-01-01T10:00:00Z")}]}"""
                }
            }
            server.start()
            val result = HevyClient(baseUrl = server.url).workoutsSince("k1", Instant.parse("2026-10-01T00:00:00Z"))
            assertEquals(listOf("a", "b", "c"), result.map { it.id })
            assertEquals(2, server.requests.size) // stopped once older workouts appeared
            assertEquals("k1", server.requests[0].headers["api-key"])
        }
    }

    @Test
    fun badKeyIsReportedClearly() = runBlocking {
        TestServer().use { server ->
            server.route("/v1/workouts/count") { 401 to "" }
            server.start()
            val error = runCatching { HevyClient(baseUrl = server.url).workoutCount("bad") }.exceptionOrNull()
            assertTrue(error is HevyApiException && error.code == 401)
        }
    }
}
