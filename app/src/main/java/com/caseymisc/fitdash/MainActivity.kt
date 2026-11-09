package com.caseymisc.fitdash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.caseymisc.fitdash.ui.FitDashNavHost
import com.caseymisc.fitdash.ui.theme.FitDashTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FitDashTheme {
                FitDashNavHost()
            }
        }
    }
}
