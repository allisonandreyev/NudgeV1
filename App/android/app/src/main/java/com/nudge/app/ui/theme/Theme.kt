package com.nudge.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Medical Dark Blue Palette
val MedicalDarkBlue = Color(0xFF0A192F)
val MedicalDeepBlue = Color(0xFF112240)
val MedicalLightBlue = Color(0xFF233554)
val MedicalCyan = Color(0xFF64FFDA)
val PureWhite = Color(0xFFFFFFFF)

private val MedicalDarkColorScheme = darkColorScheme(
    primary = MedicalCyan,
    onPrimary = MedicalDarkBlue,
    primaryContainer = MedicalLightBlue,
    onPrimaryContainer = PureWhite,
    secondary = Color(0xFF4CAF50),
    onSecondary = PureWhite,
    background = MedicalDarkBlue,
    onBackground = PureWhite,
    surface = MedicalDeepBlue,
    onSurface = PureWhite,
    outline = MedicalLightBlue,
    error = Color(0xFFFF4D4D)
)

val MedicalGradient = Brush.verticalGradient(
    colors = listOf(MedicalDarkBlue, MedicalDeepBlue, MedicalLightBlue)
)

@Composable
fun NudgeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MedicalDarkColorScheme,
        content = content
    )
}
