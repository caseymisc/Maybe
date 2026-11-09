package com.caseymisc.fitdash.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Teal = Color(0xFF2C9C92)
private val Blue = Color(0xFF3B82C4)
private val Amber = Color(0xFFDD8A2E)

private val LightColors = lightColorScheme(primary = Teal, secondary = Blue, tertiary = Amber)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FD1C5),
    secondary = Color(0xFF63B3ED),
    tertiary = Color(0xFFF6AD55),
)

@Composable
fun FitDashTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
