package com.nudge.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BackHand
import androidx.compose.material.icons.filled.FrontHand
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nudge.app.bluetooth.BluetoothViewModel
import com.nudge.app.data.RestPosition

@Composable
fun TherapySessionScreen(
    username: String,
    onSessionEnd: () -> Unit,
    therapyViewModel: TherapyViewModel = hiltViewModel(),
    bluetoothViewModel: BluetoothViewModel = hiltViewModel()
) {
    val uiState by therapyViewModel.uiState.collectAsState()
    val timerSeconds by therapyViewModel.timerSeconds.collectAsState()
    val currentSessionId by therapyViewModel.currentSessionId.collectAsState()
    
    var selectedRestPosition by remember { mutableStateOf(RestPosition.OPENED) }
    var isUploaded by remember { mutableStateOf(false) }

    // Sync session ID with BluetoothViewModel for data tagging
    LaunchedEffect(currentSessionId) {
        bluetoothViewModel.setActiveSession(currentSessionId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Therapy Session",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        when (uiState) {
            TherapyState.IDLE, TherapyState.SETUP -> {
                SetupView(
                    selectedRestPosition = selectedRestPosition,
                    onRestPositionChange = { selectedRestPosition = it },
                    onStart = { therapyViewModel.startSession(username, selectedRestPosition) }
                )
            }
            TherapyState.ACTIVE_REST, TherapyState.ACTIVE_CONTRACT -> {
                ActiveSessionView(
                    uiState = uiState,
                    timerSeconds = timerSeconds,
                    restPosition = selectedRestPosition,
                    onStop = { therapyViewModel.stopSession() }
                )
            }
            TherapyState.STOPPED -> {
                SummaryView(
                    isUploaded = isUploaded,
                    onUpload = {
                        therapyViewModel.uploadSession()
                        isUploaded = true
                    },
                    onFinish = {
                        therapyViewModel.reset()
                        onSessionEnd()
                    }
                )
            }
        }
    }
}

@Composable
fun ColumnScope.SetupView(
    selectedRestPosition: RestPosition,
    onRestPositionChange: (RestPosition) -> Unit,
    onStart: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("1. Select Resting Position", fontWeight = FontWeight.Bold)
            Text(
                "Choose the position your hand naturally sits in due to injury or comfort.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            
            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedRestPosition == RestPosition.OPENED,
                    onClick = { onRestPositionChange(RestPosition.OPENED) },
                    label = { Text("Opened Palm") }
                )
                FilterChip(
                    selected = selectedRestPosition == RestPosition.CLOSED,
                    onClick = { onRestPositionChange(RestPosition.CLOSED) },
                    label = { Text("Closed Fist") }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(32.dp))

    Text(
        text = "When you start, follow the instructions to move your hand every 5 seconds.",
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium
    )

    Spacer(modifier = Modifier.weight(1f))

    Button(
        onClick = onStart,
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Start Reading Data")
    }
}

@Composable
fun ColumnScope.ActiveSessionView(
    uiState: TherapyState,
    timerSeconds: Int,
    restPosition: RestPosition,
    onStop: () -> Unit
) {
    val isResting = uiState == TherapyState.ACTIVE_REST
    val instruction = if (isResting) "REST" else "CONTRACT"
    val action = if (isResting) {
        if (restPosition == RestPosition.OPENED) "Keep hand OPEN" else "Keep hand CLOSED"
    } else {
        if (restPosition == RestPosition.OPENED) "CLOSE your fist" else "OPEN your palm"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().weight(1f)
    ) {
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = timerSeconds / 5f,
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 8.dp,
                color = if (isResting) MaterialTheme.colorScheme.primary else Color(0xFF4CAF50)
            )
            Text(
                text = timerSeconds.toString(),
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = instruction,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = if (isResting) MaterialTheme.colorScheme.primary else Color(0xFF4CAF50)
        )
        Text(
            text = action,
            fontSize = 24.sp,
            color = Color.Gray
        )
    }

    Button(
        onClick = onStop,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
    ) {
        Icon(Icons.Default.Pause, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Stop Session")
    }
}

@Composable
fun ColumnScope.SummaryView(
    isUploaded: Boolean,
    onUpload: () -> Unit,
    onFinish: () -> Unit
) {
    Icon(
        imageVector = Icons.Default.FrontHand,
        contentDescription = null,
        modifier = Modifier.size(120.dp),
        tint = MaterialTheme.colorScheme.primary
    )
    
    Spacer(modifier = Modifier.height(24.dp))
    
    Text("Session Complete!", fontSize = 24.sp, fontWeight = FontWeight.Bold)
    Text("Your EMG data has been saved locally.", color = Color.Gray)

    Spacer(modifier = Modifier.weight(1f))

    if (!isUploaded) {
        Button(
            onClick = onUpload,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
        ) {
            Text("Save for Physician")
        }
        Spacer(modifier = Modifier.height(16.dp))
    } else {
        Text(
            "✓ Saved for Physician",
            color = Color(0xFF4CAF50),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }

    OutlinedButton(
        onClick = onFinish,
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Text("Back to Dashboard")
    }
}
