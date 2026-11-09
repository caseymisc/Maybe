package com.caseymisc.fitdash.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caseymisc.fitdash.DashboardViewModel
import com.caseymisc.fitdash.HealthState
import com.caseymisc.fitdash.HevyState
import com.caseymisc.fitdash.data.settings.WeightUnit
import com.caseymisc.fitdash.ui.components.SectionCard

@Composable
fun SettingsScreen(vm: DashboardViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val hevy by vm.hevy.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var keyInput by rememberSaveable { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    LaunchedEffect(settings.hevyApiKey) { keyInput = settings.hevyApiKey }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        SectionCard("Hevy", trailing = hevyStatus(hevy)) {
            Text(
                "Requires Hevy Pro. Create an API key at hevy.com/settings?developer and paste it here. " +
                    "The key is stored only on this device.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showKey) "Hide key" else "Show key",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.saveHevyApiKey(keyInput) },
                    enabled = keyInput.trim() != settings.hevyApiKey,
                ) { Text("Save") }
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://hevy.com/settings?developer"))
                    )
                }) { Text("Get a key") }
            }
        }

        SectionCard("Garmin via Health Connect", trailing = healthStatus(health)) {
            Text(
                "Garmin Connect has no public API for personal use. FitDash reads the data that the " +
                    "Garmin Connect app syncs into Health Connect. In Garmin Connect, open " +
                    "Connected Apps → Health Connect and allow it to write steps, sleep, heart rate " +
                    "and activities.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
                }
            }) { Text("Open Health Connect") }
        }

        SectionCard("Units") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeightUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = settings.weightUnit == unit,
                        onClick = { vm.setWeightUnit(unit) },
                        label = { Text(if (unit == WeightUnit.KG) "Kilograms" else "Pounds") },
                    )
                }
            }
        }
    }
}

private fun hevyStatus(state: HevyState): String = when (state) {
    HevyState.Loading -> "Loading…"
    HevyState.NotConfigured -> "Not connected"
    is HevyState.Ready -> "Connected"
    is HevyState.Error -> "Error"
}

private fun healthStatus(state: HealthState): String = when (state) {
    HealthState.Loading -> "Loading…"
    is HealthState.Unavailable -> "Unavailable"
    HealthState.NeedsPermissions -> "Not connected"
    is HealthState.Ready ->
        if (state.snapshot.missingPermissions.isEmpty()) "Connected" else "Partially connected"
    is HealthState.Error -> "Error"
}
