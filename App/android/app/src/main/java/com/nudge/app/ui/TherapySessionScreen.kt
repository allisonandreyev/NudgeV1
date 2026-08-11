package com.nudge.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import com.nudge.app.ui.theme.MedicalGradient

@Composable
fun TherapySessionScreen(
    username: String,
    onSessionEnd: () -> Unit,
    therapyViewModel: TherapyViewModel = hiltViewModel(),
    bluetoothViewModel: BluetoothViewModel
) {
    val uiState by therapyViewModel.uiState.collectAsState()
    val timerSeconds by therapyViewModel.timerSeconds.collectAsState()
    val currentSessionId by therapyViewModel.currentSessionId.collectAsState()
    
    val emgDataD0 by bluetoothViewModel.emgDataD0.collectAsState()
    val emgDataD1 by bluetoothViewModel.emgDataD1.collectAsState()
    val emgDataD2 by bluetoothViewModel.emgDataD2.collectAsState()
    
    var selectedRestPosition by remember { mutableStateOf(RestPosition.OPENED) }
    var isUploaded by remember { mutableStateOf(false) }

    // Sync session ID with BluetoothViewModel for data tagging
    LaunchedEffect(currentSessionId) {
        bluetoothViewModel.setActiveSession(currentSessionId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = MedicalGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Guided Therapy",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 32.dp),
                color = Color.White
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
                        emgDataD0 = emgDataD0,
                        emgDataD1 = emgDataD1,
                        emgDataD2 = emgDataD2,
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
}

@Composable
fun ColumnScope.SetupView(
    selectedRestPosition: RestPosition,
    onRestPositionChange: (RestPosition) -> Unit,
    onStart: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("1. Select Resting Position", fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "Choose the position your hand naturally sits in due to injury or comfort.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
            
            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedRestPosition == RestPosition.OPENED,
                    onClick = { onRestPositionChange(RestPosition.OPENED) },
                    label = { Text("Opened Palm") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = Color.White.copy(alpha = 0.1f),
                        labelColor = Color.White
                    )
                )
                FilterChip(
                    selected = selectedRestPosition == RestPosition.CLOSED,
                    onClick = { onRestPositionChange(RestPosition.CLOSED) },
                    label = { Text("Closed Fist") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = Color.White.copy(alpha = 0.1f),
                        labelColor = Color.White
                    )
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(32.dp))

    Text(
        text = "When you start, follow the instructions to move your hand every 5 seconds.",
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.8f)
    )

    Spacer(modifier = Modifier.weight(1f))

    Button(
        onClick = onStart,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Start Training", fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ColumnScope.ActiveSessionView(
    uiState: TherapyState,
    timerSeconds: Int,
    restPosition: RestPosition,
    emgDataD0: List<Float>,
    emgDataD1: List<Float>,
    emgDataD2: List<Float>,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier.size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = timerSeconds / 5f,
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 6.dp,
                    color = if (isResting) MaterialTheme.colorScheme.primary else Color(0xFF00E676)
                )
                Text(
                    text = timerSeconds.toString(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = instruction,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isResting) MaterialTheme.colorScheme.primary else Color(0xFF00E676)
                )
                Text(
                    text = action,
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Live EMG Graphs
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SensorGraphCard(label = "Sensor D0", data = emgDataD0, color = MaterialTheme.colorScheme.primary)
            SensorGraphCard(label = "Sensor D1", data = emgDataD1, color = Color(0xFF00E676))
            SensorGraphCard(label = "Sensor D2", data = emgDataD2, color = Color(0xFFFF9100))
        }
    }

    Button(
        onClick = onStop,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.2f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f))
    ) {
        Icon(Icons.Default.Pause, contentDescription = null, tint = Color.White)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Stop Session", color = Color.White)
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
        tint = Color.White
    )
    
    Spacer(modifier = Modifier.height(24.dp))
    
    Text("Session Complete!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
    Text("Your EMG data has been saved locally.", color = Color.White.copy(alpha = 0.6f))

    Spacer(modifier = Modifier.weight(1f))

    if (!isUploaded) {
        Button(
            onClick = onUpload,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                contentColor = Color.White
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
        ) {
            Text("Save for Physician", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))
    } else {
        Text(
            "✓ Saved for Physician",
            color = Color(0xFF00E676),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }

    OutlinedButton(
        onClick = onFinish,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
    ) {
        Text("Back to Dashboard")
    }
}
