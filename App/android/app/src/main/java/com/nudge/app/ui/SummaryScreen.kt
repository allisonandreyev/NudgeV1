package com.nudge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nudge.app.ui.theme.NudgeTheme

@Composable
fun SummaryScreen(onConnectWithPhysician: () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text(text = "Summarized Data", style = MaterialTheme.typography.headlineMedium)
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(
            onClick = onConnectWithPhysician,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Link a Physician/Therapist")
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Status: Connected", style = MaterialTheme.typography.bodyLarge)
                Text(text = "Last Sync: Just now", style = MaterialTheme.typography.bodyMedium)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(text = "Recent Readings", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        
        // Mock data for now
        LazyColumn {
            items(5) { index ->
                ListItem(
                    headlineContent = { Text("Reading #${index + 1}") },
                    supportingContent = { Text("Value: ${70 + index} bpm") }
                )
                HorizontalDivider()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SummaryScreenPreview() {
    NudgeTheme {
        SummaryScreen()
    }
}
