package com.nudge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nudge.app.ui.theme.NudgeTheme

@Composable
fun ConnectScreen(onNavigateToSummary: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Connect to Device", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { /* TODO: Implement connection logic */ }) {
            Text("Scan for Devices")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToSummary) {
            Text("Go to Summary (Demo)")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ConnectScreenPreview() {
    NudgeTheme {
        ConnectScreen(onNavigateToSummary = {})
    }
}
