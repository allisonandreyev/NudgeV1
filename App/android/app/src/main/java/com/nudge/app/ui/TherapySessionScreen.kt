package com.nudge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BackHand
import androidx.compose.material.icons.filled.FrontHand
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nudge.app.ui.theme.NudgeTheme

@Composable
fun TherapySessionScreen(onSessionEnd: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    val instructions = listOf("Open your palm", "Close your fist")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Text(
                text = "Therapy Session",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Graphic Placeholder (Hand from wireframe)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (step == 0) Icons.Default.FrontHand else Icons.Default.BackHand,
                contentDescription = null,
                modifier = Modifier.size(200.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(64.dp))

        Text(
            text = instructions[step],
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Text(
            text = "Prompt user what to do",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                if (step < instructions.size - 1) {
                    step++
                } else {
                    onSessionEnd()
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(if (step < instructions.size - 1) "Next" else "Finish Session")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TherapySessionScreenPreview() {
    NudgeTheme {
        TherapySessionScreen(onSessionEnd = {})
    }
}
