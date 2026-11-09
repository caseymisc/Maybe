package com.caseymisc.fitdash.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caseymisc.fitdash.DashboardViewModel
import com.caseymisc.fitdash.HealthState
import com.caseymisc.fitdash.data.health.HealthConnectAvailability
import com.caseymisc.fitdash.data.health.HealthConnectRepository
import com.caseymisc.fitdash.data.health.HealthSnapshot
import com.caseymisc.fitdash.data.health.SleepSummary
import com.caseymisc.fitdash.ui.components.BarChart
import com.caseymisc.fitdash.ui.components.MessageCard
import com.caseymisc.fitdash.ui.components.SectionCard
import com.caseymisc.fitdash.ui.components.StackedBar
import com.caseymisc.fitdash.ui.components.StatTile
import com.caseymisc.fitdash.ui.components.TileRow
import com.caseymisc.fitdash.ui.components.formatCount
import com.caseymisc.fitdash.ui.components.formatDistance
import com.caseymisc.fitdash.ui.components.formatMinutes
import com.caseymisc.fitdash.ui.components.formatTime
import com.caseymisc.fitdash.ui.components.weekdayLabel

private const val STEP_GOAL = 10_000.0
private const val SLEEP_GOAL_MINUTES = 8 * 60.0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(vm: DashboardViewModel) {
    val health by vm.health.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { vm.refreshHealth() }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Text("Today", style = MaterialTheme.typography.headlineMedium)
            }
            when (val state = health) {
                HealthState.Loading -> item { CenteredSpinner() }
                is HealthState.Unavailable -> item {
                    val updating = state.availability == HealthConnectAvailability.UPDATE_REQUIRED
                    MessageCard(
                        title = if (updating) "Update Health Connect" else "Health Connect not available",
                        body = "FitDash reads your Garmin data through Health Connect. " +
                            if (updating) "Please update it from the Play Store." else "Install it from the Play Store.",
                        actionLabel = "Open Play Store",
                        onAction = {
                            val uri = Uri.parse(
                                "market://details?id=${HealthConnectRepository.HEALTH_CONNECT_PACKAGE}" +
                                    "&url=healthconnect%3A%2F%2Fonboarding"
                            )
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending"))
                        },
                    )
                }
                HealthState.NeedsPermissions -> item {
                    MessageCard(
                        title = "Connect your Garmin data",
                        body = "Grant FitDash read access in Health Connect. Make sure Garmin Connect " +
                            "is also allowed to write to Health Connect (Garmin Connect app → " +
                            "Connected Apps → Health Connect).",
                        actionLabel = "Grant access",
                        onAction = { permissionLauncher.launch(vm.healthPermissions) },
                    )
                }
                is HealthState.Error -> item {
                    MessageCard(
                        title = "Couldn't read Health Connect",
                        body = state.message,
                        actionLabel = "Retry",
                        onAction = vm::refreshHealth,
                        isError = true,
                    )
                }
                is HealthState.Ready -> {
                    val snapshot = state.snapshot
                    if (snapshot.missingPermissions.isNotEmpty()) item {
                        MessageCard(
                            title = "Some data is hidden",
                            body = "${snapshot.missingPermissions.size} Health Connect permission(s) not granted.",
                            actionLabel = "Review permissions",
                            onAction = { permissionLauncher.launch(vm.healthPermissions) },
                        )
                    }
                    item { TodayTiles(snapshot) }
                    item { SleepCard(snapshot) }
                    item { StepsCard(snapshot) }
                    if (snapshot.dailyRestingHr.size >= 2) item { RestingHrCard(snapshot) }
                }
            }
        }
    }
}

@Composable
private fun TodayTiles(snapshot: HealthSnapshot) {
    val t = snapshot.today
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TileRow { m ->
            StatTile(
                "Steps", t.steps?.let(::formatCount) ?: "–", Icons.Filled.DirectionsWalk, m,
                detail = t.steps?.let { "${(it * 100 / STEP_GOAL).toInt()}% of ${formatCount(STEP_GOAL)}" },
            )
            StatTile(
                "Active energy", t.activeKcal?.let { "${formatCount(it)} kcal" } ?: "–",
                Icons.Filled.LocalFireDepartment, m, tint = MaterialTheme.colorScheme.tertiary,
            )
        }
        TileRow { m ->
            StatTile(
                "Resting HR", t.restingHr?.let { "$it bpm" } ?: "–", Icons.Filled.Favorite, m,
                tint = Color(0xFFE05666),
            )
            StatTile(
                "Heart rate", t.avgHr?.let { "$it avg" } ?: "–", Icons.Filled.MonitorHeart, m,
                detail = if (t.minHr != null && t.maxHr != null) "${t.minHr}–${t.maxHr} bpm" else null,
                tint = Color(0xFFE05666),
            )
        }
        TileRow { m ->
            StatTile(
                "Distance", t.distanceMeters?.let(::formatDistance) ?: "–", Icons.Filled.Straighten, m,
                tint = MaterialTheme.colorScheme.secondary,
            )
            StatTile(
                "Last sleep", snapshot.lastSleep?.let { formatMinutes(it.asleepMinutes) } ?: "–",
                Icons.Filled.Bedtime, m, tint = Color(0xFF7C6FD8),
            )
        }
    }
}

private val DeepColor = Color(0xFF3F3BA8)
private val LightColor = Color(0xFF7C9CF0)
private val RemColor = Color(0xFFB57EDC)
private val AwakeColor = Color(0xFFF2A65A)

@Composable
private fun SleepCard(snapshot: HealthSnapshot) {
    val avg = snapshot.dailySleepMinutes.filter { it.value > 0 }.map { it.value }.average()
    SectionCard(
        "Sleep",
        trailing = if (avg.isNaN()) null else "7-day avg ${formatMinutes(avg.toLong())}",
    ) {
        snapshot.lastSleep?.let { LastSleep(it) }
        BarChart(
            values = snapshot.dailySleepMinutes.map { it.value },
            labels = snapshot.dailySleepMinutes.map { weekdayLabel(it.date) },
            barColor = Color(0xFF7C6FD8),
            goal = SLEEP_GOAL_MINUTES,
        )
    }
}

@Composable
private fun LastSleep(sleep: SleepSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "${formatMinutes(sleep.asleepMinutes)} asleep · ${formatTime(sleep.start)} – ${formatTime(sleep.end)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        val hasStages = sleep.deepMinutes + sleep.remMinutes + sleep.lightMinutes > 0
        if (hasStages) {
            StackedBar(
                listOf(
                    sleep.deepMinutes.toDouble() to DeepColor,
                    sleep.lightMinutes.toDouble() to LightColor,
                    sleep.remMinutes.toDouble() to RemColor,
                    sleep.awakeMinutes.toDouble() to AwakeColor,
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Legend("Deep", sleep.deepMinutes, DeepColor)
                Legend("Light", sleep.lightMinutes, LightColor)
                Legend("REM", sleep.remMinutes, RemColor)
                Legend("Awake", sleep.awakeMinutes, AwakeColor)
            }
        }
    }
}

@Composable
private fun Legend(label: String, minutes: Long, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = color, shape = CircleShape, modifier = Modifier.size(8.dp)) {}
        Spacer(Modifier.width(4.dp))
        Text("$label ${formatMinutes(minutes)}", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun StepsCard(snapshot: HealthSnapshot) {
    val avg = snapshot.dailySteps.map { it.value }.average()
    SectionCard("Steps", trailing = "7-day avg ${formatCount(if (avg.isNaN()) 0.0 else avg)}") {
        BarChart(
            values = snapshot.dailySteps.map { it.value },
            labels = snapshot.dailySteps.map { weekdayLabel(it.date) },
            goal = STEP_GOAL,
        )
    }
}

@Composable
private fun RestingHrCard(snapshot: HealthSnapshot) {
    val values = snapshot.dailyRestingHr
    SectionCard("Resting heart rate", trailing = "latest ${values.last().value.toInt()} bpm") {
        // Plot relative to a floor so day-to-day changes are visible.
        val floor = (values.minOf { it.value } - 5).coerceAtLeast(0.0)
        BarChart(
            values = values.map { it.value - floor },
            labels = values.map { weekdayLabel(it.date) },
            barColor = Color(0xFFE05666),
            height = 80.dp,
        )
    }
}

@Composable
internal fun CenteredSpinner() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
