package com.nudge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nudge.app.ui.theme.NudgeTheme

@Composable
fun MinigameScreen(onGameEnd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Minigame",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp),
            color = MaterialTheme.colorScheme.onSurface
        )

        // Explain how to play
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = "explain how to play",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Minigame graphics
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), MaterialTheme.shapes.large),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "minigame\ngraphics",
                style = MaterialTheme.typography.displaySmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onGameEnd,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Back to Dashboard")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MinigameScreenPreview() {
    NudgeTheme {
        MinigameScreen(onGameEnd = {})
    }
}
