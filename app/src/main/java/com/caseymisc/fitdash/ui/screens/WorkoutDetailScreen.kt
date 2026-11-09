package com.caseymisc.fitdash.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caseymisc.fitdash.DashboardViewModel
import com.caseymisc.fitdash.HevyState
import com.caseymisc.fitdash.data.hevy.HevyExercise
import com.caseymisc.fitdash.data.hevy.HevySet
import com.caseymisc.fitdash.data.hevy.duration
import com.caseymisc.fitdash.data.hevy.start
import com.caseymisc.fitdash.data.hevy.volumeKg
import com.caseymisc.fitdash.data.settings.WeightUnit
import com.caseymisc.fitdash.ui.components.SectionCard
import com.caseymisc.fitdash.ui.components.formatDayTime
import com.caseymisc.fitdash.ui.components.formatDistance
import com.caseymisc.fitdash.ui.components.formatDuration
import com.caseymisc.fitdash.ui.components.formatWeight
import java.time.Duration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(vm: DashboardViewModel, workoutId: String, onBack: () -> Unit) {
    val hevy by vm.hevy.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val workout = (hevy as? HevyState.Ready)?.data?.workouts?.firstOrNull { it.id == workoutId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(workout?.title?.ifBlank { null } ?: "Workout") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (workout == null) {
            Text("Workout not found.", modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val parts = listOfNotNull(
                    formatDayTime(workout.start),
                    workout.duration?.let(::formatDuration),
                    formatWeight(workout.volumeKg, settings.weightUnit),
                )
                Text(parts.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                workout.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, modifier = Modifier.padding(top = 8.dp))
                }
            }
            items(workout.exercises.sortedBy { it.index }) { exercise ->
                ExerciseCard(exercise, settings.weightUnit)
            }
        }
    }
}

@Composable
private fun ExerciseCard(exercise: HevyExercise, unit: WeightUnit) {
    val volume = exercise.volumeKg
    SectionCard(
        exercise.title,
        trailing = if (volume > 0) formatWeight(volume, unit) else null,
    ) {
        exercise.notes?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            var working = 0
            exercise.sets.sortedBy { it.index }.forEach { set ->
                val label = when (set.type) {
                    "warmup" -> "W"
                    "dropset" -> "D"
                    "failure" -> "F"
                    else -> (++working).toString()
                }
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        label,
                        modifier = Modifier.width(32.dp),
                        fontWeight = FontWeight.SemiBold,
                        color = if (set.type == "normal") MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.tertiary,
                    )
                    Text(describeSet(set, unit), modifier = Modifier.weight(1f))
                    set.rpe?.let {
                        Text("RPE ${it}", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun describeSet(set: HevySet, unit: WeightUnit): String {
    val parts = mutableListOf<String>()
    if (set.weightKg != null && set.reps != null) {
        parts += "${formatWeight(set.weightKg, unit, decimals = true)} × ${set.reps}"
    } else {
        set.weightKg?.let { parts += formatWeight(it, unit, decimals = true) }
        set.reps?.let { parts += "$it reps" }
    }
    set.distanceMeters?.let { parts += formatDistance(it) }
    set.durationSeconds?.let { seconds ->
        parts += if (seconds < 60) "${seconds}s" else formatDuration(Duration.ofSeconds(seconds.toLong()))
    }
    return parts.joinToString(" · ").ifEmpty { "–" }
}
