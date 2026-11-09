package com.caseymisc.fitdash.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.caseymisc.fitdash.DashboardViewModel
import com.caseymisc.fitdash.GarminLoginActivity
import com.caseymisc.fitdash.GarminState
import com.caseymisc.fitdash.data.garmin.GarminDashboard
import com.caseymisc.fitdash.data.garmin.GarminSleep
import com.caseymisc.fitdash.ui.components.BarChart
import com.caseymisc.fitdash.ui.components.LineChart
import com.caseymisc.fitdash.ui.components.MessageCard
import com.caseymisc.fitdash.ui.components.SectionCard
import com.caseymisc.fitdash.ui.components.StackedBar
import com.caseymisc.fitdash.ui.components.StatTile
import com.caseymisc.fitdash.ui.components.TileRow
import com.caseymisc.fitdash.ui.components.formatMinutes
import com.caseymisc.fitdash.ui.components.formatTime
import com.caseymisc.fitdash.ui.components.weekdayLabel

internal val BatteryColor = Color(0xFF2FA4D8)
internal val StressColor = Color(0xFFE8833A)
internal val SleepColor = Color(0xFF7C6FD8)
internal val HrvColor = Color(0xFF3FB68B)

/** Returns a callback that opens Garmin sign-in and reloads Garmin data afterwards. */
@Composable
fun rememberGarminSignIn(vm: DashboardViewModel): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.refreshGarmin()
    }
    return { launcher.launch(Intent(context, GarminLoginActivity::class.java)) }
}

@Composable
fun GarminOverview(state: GarminState, onSignIn: () -> Unit, onRetry: () -> Unit) {
    when (state) {
        GarminState.Loading -> CenteredSpinner()
        is GarminState.SignedOut -> MessageCard(
            title = "Connect Garmin",
            body = (state.reason?.let { "$it\n\n" } ?: "") +
                "Sign in to Garmin Connect to see Body Battery, sleep score, Training Readiness, " +
                "HRV status and stress. You sign in on Garmin's own page; FitDash never sees your password.",
            actionLabel = "Sign in to Garmin",
            onAction = onSignIn,
        )
        is GarminState.Error -> MessageCard(
            title = "Couldn't load Garmin",
            body = state.message,
            actionLabel = "Retry",
            onAction = onRetry,
            isError = true,
        )
        is GarminState.Ready -> GarminReady(state.data)
    }
}

@Composable
private fun GarminReady(data: GarminDashboard) {
    val today = data.today
    val sleep = data.lastSleep
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TileRow { m ->
            StatTile(
                "Body Battery",
                (today.bodyBatteryLatest ?: today.bodyBatteryHigh)?.toString() ?: "–",
                Icons.Filled.BatteryChargingFull, m,
                detail = if (today.bodyBatteryHigh != null && today.bodyBatteryLow != null)
                    "High ${today.bodyBatteryHigh} · Low ${today.bodyBatteryLow}" else null,
                tint = BatteryColor,
            )
            StatTile(
                "Sleep score", sleep?.score?.toString() ?: "–", Icons.Filled.Bedtime, m,
                detail = listOfNotNull(sleep?.quality, sleep?.totalSeconds?.let { formatMinutes(it / 60) })
                    .joinToString(" · ").ifEmpty { null },
                tint = SleepColor,
            )
        }
        TileRow { m ->
            StatTile(
                "Training Readiness", data.readiness?.score?.toString() ?: "–", Icons.Filled.Bolt, m,
                detail = data.readiness?.level,
                tint = MaterialTheme.colorScheme.tertiary,
            )
            StatTile(
                "HRV", data.hrv?.lastNightAvg?.let { "$it ms" } ?: "–", Icons.Filled.MonitorHeart, m,
                detail = listOfNotNull(data.hrv?.status, data.hrv?.weeklyAvg?.let { "7d $it ms" })
                    .joinToString(" · ").ifEmpty { null },
                tint = HrvColor,
            )
        }
        TileRow { m ->
            StatTile(
                "Stress", today.avgStress?.toString() ?: "–", Icons.Filled.Psychology, m,
                detail = today.maxStress?.let { "avg · max $it" },
                tint = StressColor,
            )
            StatTile(
                "Training status", data.trainingStatus ?: "–", Icons.Filled.ShowChart, m,
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
        if (data.bodyBatteryToday.size >= 2) BodyBatteryCard(data)
        sleep?.let { GarminSleepCard(it) }
        TrendsCard(data)
    }
}

@Composable
private fun BodyBatteryCard(data: GarminDashboard) {
    val t = data.today
    val trailing = listOfNotNull(
        t.bodyBatteryCharged?.let { "+$it" },
        t.bodyBatteryDrained?.let { "−$it" },
    ).joinToString(" / ").ifEmpty { null }
    SectionCard("Body Battery today", trailing = trailing) {
        val points = data.bodyBatteryToday
        LineChart(
            points = points.map { it.time.epochSecond.toDouble() to it.level.toDouble() },
            lineColor = BatteryColor,
        )
        Row {
            Text(formatTime(points.first().time), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = androidx.compose.ui.Modifier.weight(1f))
            Text(formatTime(points.last().time), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GarminSleepCard(sleep: GarminSleep) {
    SectionCard("Last night's sleep", trailing = sleep.score?.let { "Score $it" }) {
        val window = if (sleep.start != null && sleep.end != null)
            " · ${formatTime(sleep.start)} – ${formatTime(sleep.end)}" else ""
        Text(
            "${sleep.totalSeconds?.let { formatMinutes(it / 60) } ?: "–"} asleep$window",
            style = MaterialTheme.typography.bodyMedium,
        )
        val deep = sleep.deepSeconds ?: 0
        val light = sleep.lightSeconds ?: 0
        val rem = sleep.remSeconds ?: 0
        val awake = sleep.awakeSeconds ?: 0
        if (deep + light + rem > 0) {
            StackedBar(
                listOf(
                    deep.toDouble() to DeepColor,
                    light.toDouble() to LightColor,
                    rem.toDouble() to RemColor,
                    awake.toDouble() to AwakeColor,
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Legend("Deep", deep / 60, DeepColor)
                Legend("Light", light / 60, LightColor)
                Legend("REM", rem / 60, RemColor)
                Legend("Awake", awake / 60, AwakeColor)
            }
        }
    }
}

@Composable
private fun TrendsCard(data: GarminDashboard) {
    val labels = data.days.map { weekdayLabel(it.date) }
    SectionCard("Last 7 days") {
        TrendLabel("Sleep score")
        BarChart(data.days.map { (it.sleep?.score ?: 0).toDouble() }, labels, height = 70.dp, barColor = SleepColor)
        TrendLabel("Body Battery peak")
        BarChart(data.days.map { (it.bodyBatteryHigh ?: 0).toDouble() }, labels, height = 70.dp, barColor = BatteryColor)
        TrendLabel("Average stress")
        BarChart(data.days.map { (it.avgStress ?: 0).toDouble() }, labels, height = 70.dp, barColor = StressColor)
    }
}

@Composable
private fun TrendLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
