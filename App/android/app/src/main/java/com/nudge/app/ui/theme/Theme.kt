package com.nudge.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MonochromeColorScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color.White,
    onPrimaryContainer = Color.Black,
    secondary = Color.Black,
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    outline = Color.Black,
    error = Color.Red // Keeping error as red for visibility
)

@Composable
fun NudgeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MonochromeColorScheme,
        content = content
    )
}
