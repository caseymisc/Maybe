package com.caseymisc.fitdash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.caseymisc.fitdash.ui.theme.FitDashTheme

/** Privacy explanation that Health Connect links to from its permission screen. */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FitDashTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(24.dp)) {
                        Text("How FitDash uses your health data", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "FitDash only reads steps, distance, calories, heart rate, sleep and " +
                                "exercise sessions from Health Connect to show them on your personal " +
                                "dashboard. Data stays on this device. It is never uploaded, shared or " +
                                "written back. The only network service FitDash talks to is the Hevy API, " +
                                "using the API key you provide.",
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        Button(onClick = { finish() }, modifier = Modifier.padding(top = 24.dp)) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}
