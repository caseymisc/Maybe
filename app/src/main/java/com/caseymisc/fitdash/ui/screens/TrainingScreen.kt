package com.caseymisc.fitdash.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caseymisc.fitdash.DashboardViewModel
import com.caseymisc.fitdash.HealthState
import com.caseymisc.fitdash.HevyDashboard
import com.caseymisc.fitdash.HevyState
import com.caseymisc.fitdash.data.health.ActivitySummary
import com.caseymisc.fitdash.data.hevy.HevyWorkout
import com.caseymisc.fitdash.data.hevy.duration
import com.caseymisc.fitdash.data.hevy.personalBests
import com.caseymisc.fitdash.data.hevy.start
import com.caseymisc.fitdash.data.hevy.volumeKg
import com.caseymisc.fitdash.data.hevy.workingSetCount
import com.caseymisc.fitdash.data.settings.WeightUnit
import com.caseymisc.fitdash.ui.components.BarChart
import com.caseymisc.fitdash.ui.components.MessageCard
import com.caseymisc.fitdash.ui.components.SectionCard
import com.caseymisc.fitdash.ui.components.StatTile
import com.caseymisc.fitdash.ui.components.TileRow
import com.caseymisc.fitdash.ui.components.formatCount
import com.caseymisc.fitdash.ui.components.formatDay
import com.caseymisc.fitdash.ui.components.formatDayTime
import com.caseymisc.fitdash.ui.components.formatDistance
import com.caseymisc.fitdash.ui.components.formatDuration
import com.caseymisc.fitdash.ui.components.formatMinutes
import com.caseymisc.fitdash.ui.components.formatWeight
import java.time.format.DateTimeFormatter

private val weekLabel = DateTimeFormatter.ofPattern("d/M")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(
    vm: DashboardViewModel,
    onOpenWorkout: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val hevy by vm.hevy.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val unit = settings.weightUnit

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { Text("Training", style = MaterialTheme.typography.headlineMedium) }

            when (val state = hevy) {
                HevyState.Loading -> item { CenteredSpinner() }
                HevyState.NotConfigured -> item {
                    MessageCard(
                        title = "Connect Hevy",
                        body = "Add your Hevy API key (Hevy Pro → hevy.com/settings?developer) to see " +
                            "your strength training here.",
                        actionLabel = "Open settings",
                        onAction = onOpenSettings,
                    )
                }
                is HevyState.Error -> item {
                    MessageCard(
                        title = "Couldn't load Hevy",
                        body = state.message,
                        actionLabel = "Retry",
                        onAction = vm::refresh,
                        isError = true,
                    )
                }
                is HevyState.Ready -> {
                    val data = state.data
                    item { HevySummary(data, unit) }
                    item { VolumeCard(data, unit) }
                    val bests = personalBests(data.workouts).entries
                        .sortedByDescending { it.value.weightKg }
                        .take(5)
                    if (bests.isNotEmpty()) item {
                        SectionCard("Heaviest sets", trailing = "8 weeks") {
                            bests.forEach { (exercise, set) ->
                                Row(Modifier.fillMaxWidth()) {
                                    Text(
                                        exercise,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${formatWeight(set.weightKg ?: 0.0, unit, decimals = true)} × ${set.reps ?: 0}",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                    item { Text("Recent workouts", style = MaterialTheme.typography.titleMedium) }
                    if (data.workouts.isEmpty()) item {
                        Text("No workouts in the last 8 weeks.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(data.workouts.take(15), key = { it.id }) { workout ->
                        WorkoutRow(workout, unit, onClick = { onOpenWorkout(workout.id) })
                    }
                }
            }

            // Garmin (and other) activities from Health Connect.
            val activities = (health as? HealthState.Ready)?.snapshot?.activities.orEmpty()
                .filter { it.source != "Hevy" } // Hevy workouts are already listed above.
            if (activities.isNotEmpty()) {
                item {
                    Text(
                        "Activities · last 14 days",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(activities, key = { "activity-${it.start.toEpochMilli()}-${it.source}-${it.type}" }) { activity ->
                    ActivityRow(activity)
                }
            }
        }
    }
}

@Composable
private fun HevySummary(data: HevyDashboard, unit: WeightUnit) {
    val thisWeek = data.weeks.last()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TileRow { m ->
            StatTile("Workouts this week", thisWeek.workouts.toString(), Icons.Filled.CalendarMonth, m)
            StatTile(
                "Volume this week", formatWeight(thisWeek.volumeKg, unit), Icons.Filled.FitnessCenter, m,
                tint = MaterialTheme.colorScheme.tertiary,
            )
        }
        TileRow { m ->
            StatTile(
                "Time this week", formatMinutes(thisWeek.minutes), Icons.Filled.Timer, m,
                tint = MaterialTheme.colorScheme.secondary,
            )
            StatTile(
                "All-time workouts", data.totalWorkouts?.let(::formatCount) ?: "–", Icons.Filled.TrendingUp, m,
            )
        }
    }
}

@Composable
private fun VolumeCard(data: HevyDashboard, unit: WeightUnit) {
    val avg = data.weeks.dropLast(1).map { it.volumeKg }.average()
    SectionCard(
        "Weekly volume",
        trailing = if (avg.isNaN()) null else "avg ${formatWeight(avg, unit)}",
    ) {
        BarChart(
            values = data.weeks.map { it.volumeKg },
            labels = data.weeks.map { it.weekStart.format(weekLabel) },
            barColor = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun WorkoutRow(workout: HevyWorkout, unit: WeightUnit, onClick: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    workout.title.ifBlank { "Workout" },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatDay(workout.start),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val parts = listOfNotNull(
                workout.duration?.let(::formatDuration),
                formatWeight(workout.volumeKg, unit),
                "${workout.exercises.size} exercises",
                "${workout.workingSetCount} sets",
            )
            Text(
                parts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ActivityRow(activity: ActivitySummary) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    activity.title?.takeIf { it.isNotBlank() } ?: activity.type,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    activity.source,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            val parts = listOfNotNull(
                formatDayTime(activity.start),
                formatDuration(activity.duration),
                activity.distanceMeters?.let(::formatDistance),
                activity.activeKcal?.let { "${formatCount(it)} kcal" },
            )
            Text(
                parts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
