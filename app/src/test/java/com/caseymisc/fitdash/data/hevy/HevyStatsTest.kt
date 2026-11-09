package com.caseymisc.fitdash.data.hevy

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset

class HevyStatsTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    private val samplePage = """
        {
          "page": 1,
          "page_count": 3,
          "workouts": [
            {
              "id": "w1",
              "title": "Push Day",
              "description": null,
              "start_time": "2026-11-09T08:00:00Z",
              "end_time": "2026-11-09T09:05:00+00:00",
              "updated_at": "2026-11-09T09:06:00Z",
              "created_at": "2026-11-09T09:06:00Z",
              "exercises": [
                {
                  "index": 0,
                  "title": "Bench Press (Barbell)",
                  "notes": "",
                  "exercise_template_id": "79D0BB3A",
                  "superset_id": null,
                  "sets": [
                    { "index": 0, "type": "warmup", "weight_kg": 40, "reps": 10, "distance_meters": null, "duration_seconds": null, "rpe": null },
                    { "index": 1, "type": "normal", "weight_kg": 80, "reps": 8, "rpe": 8.5 },
                    { "index": 2, "type": "failure", "weight_kg": 80, "reps": 6 }
                  ]
                },
                {
                  "index": 1,
                  "title": "Plank",
                  "sets": [ { "index": 0, "type": "normal", "duration_seconds": 60 } ]
                }
              ]
            }
          ]
        }
    """.trimIndent()

    private fun workout(id: String, start: String, vararg sets: Pair<Double, Int>) = HevyWorkout(
        id = id,
        startTime = start,
        endTime = null,
        exercises = listOf(HevyExercise(title = "Squat", sets = sets.map { HevySet(weightKg = it.first, reps = it.second) })),
    )

    @Test
    fun parsesWorkoutsPage() {
        val page = json.decodeFromString(HevyWorkoutsPage.serializer(), samplePage)
        assertEquals(3, page.pageCount)
        val w = page.workouts.single()
        assertEquals("Push Day", w.title)
        assertEquals(2, w.exercises.size)
        assertEquals(80.0, w.exercises[0].sets[1].weightKg!!, 0.0)
        assertEquals(8.5, w.exercises[0].sets[1].rpe!!, 0.0)
        assertNull(w.exercises[1].sets[0].weightKg)
        assertEquals(Duration.ofMinutes(65), w.duration)
    }

    @Test
    fun volumeExcludesWarmups() {
        val w = json.decodeFromString(HevyWorkoutsPage.serializer(), samplePage).workouts.single()
        assertEquals(80.0 * 8 + 80.0 * 6, w.volumeKg, 0.001)
        assertEquals(3, w.workingSetCount)
    }

    @Test
    fun weeklyBucketsAreMondayStartAndIncludeEmptyWeeks() {
        val today = LocalDate.of(2026, 11, 11) // Wednesday
        val workouts = listOf(
            workout("a", "2026-11-09T10:00:00Z", 100.0 to 5), // this week (Mon)
            workout("b", "2026-11-08T10:00:00Z", 50.0 to 10), // last week (Sun)
            workout("c", "2026-10-26T10:00:00Z", 10.0 to 1), // 3 weeks ago
        )
        val buckets = weeklyBuckets(workouts, weeks = 4, today = today, zone = ZoneOffset.UTC)

        assertEquals(
            listOf(
                LocalDate.of(2026, 10, 19),
                LocalDate.of(2026, 10, 26),
                LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 9),
            ),
            buckets.map { it.weekStart },
        )
        assertEquals(listOf(0, 1, 1, 1), buckets.map { it.workouts })
        assertEquals(listOf(0.0, 10.0, 500.0, 500.0), buckets.map { it.volumeKg })
    }

    @Test
    fun personalBestPicksHeaviestThenMostReps() {
        val bests = personalBests(
            listOf(
                workout("a", "2026-11-09T10:00:00Z", 100.0 to 5, 100.0 to 6),
                workout("b", "2026-11-02T10:00:00Z", 90.0 to 10),
            )
        )
        val best = bests.getValue("Squat")
        assertEquals(100.0, best.weightKg!!, 0.0)
        assertEquals(6, best.reps)
    }
}
