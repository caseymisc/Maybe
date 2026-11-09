package com.caseymisc.fitdash.data.hevy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Models for the official Hevy public API (https://api.hevyapp.com/docs). */

@Serializable
data class HevyWorkoutsPage(
    val page: Int = 1,
    @SerialName("page_count") val pageCount: Int = 1,
    val workouts: List<HevyWorkout> = emptyList(),
)

@Serializable
data class HevyWorkout(
    val id: String,
    val title: String = "",
    val description: String? = null,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String? = null,
    val exercises: List<HevyExercise> = emptyList(),
)

@Serializable
data class HevyExercise(
    val index: Int = 0,
    val title: String = "",
    val notes: String? = null,
    @SerialName("exercise_template_id") val templateId: String? = null,
    val sets: List<HevySet> = emptyList(),
)

@Serializable
data class HevySet(
    val index: Int = 0,
    /** "normal", "warmup", "dropset" or "failure". */
    val type: String = "normal",
    @SerialName("weight_kg") val weightKg: Double? = null,
    val reps: Int? = null,
    @SerialName("distance_meters") val distanceMeters: Double? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    val rpe: Double? = null,
)

@Serializable
data class HevyWorkoutCount(
    @SerialName("workout_count") val workoutCount: Int,
)
