package com.nudge.app.ui

import android.bluetooth.BluetoothProfile
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nudge.app.bluetooth.BluetoothViewModel
import com.nudge.app.ui.theme.MedicalGradient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServoTestScreen(
    viewModel: BluetoothViewModel,
    onBack: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected = connectionState == BluetoothProfile.STATE_CONNECTED

    var globalSpeed by remember { mutableFloatStateOf(100f) }
    val servoAngles = remember { mutableStateListOf(0f, 0f, 0f, 0f, 0f, 0f) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = MedicalGradient)
            .safeDrawingPadding(),
        topBar = {
            TopAppBar(
                title = { Text("Hardware Calibration", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (isConnected) {
                        IconButton(onClick = { viewModel.sendCommand("stop") }) {
                            Icon(Icons.Default.Dangerous, contentDescription = "EMERGENCY STOP", tint = Color.Red)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isConnected) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Wearable Disconnected. Commands will not be sent.", color = Color.White, fontSize = 14.sp)
                    }
                }
            }

            // Global Speed Controller
            CalibrationCard(title = "Global Movement Speed") {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Speed: ${globalSpeed.toInt()} deg/s", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                    Slider(
                        value = globalSpeed,
                        onValueChange = { globalSpeed = it },
                        valueRange = 10f..300f,
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // High Level Commands
            CalibrationCard(title = "System Batch Commands") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.sendCommand("engage") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Engage PTO", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { viewModel.sendCommand("disengage") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))
                        ) {
                            Text("Disengage PTO", fontSize = 12.sp)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.sendCommand("grasp 255 ${globalSpeed.toInt()}") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                        ) {
                            Text("Full Grasp", fontSize = 12.sp, color = Color.Black)
                        }
                        Button(
                            onClick = { viewModel.sendCommand("retract 255 ${globalSpeed.toInt()}") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9100))
                        ) {
                            Text("Full Retract", fontSize = 12.sp, color = Color.Black)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "INDIVIDUAL SERVO OVERRIDE",
                modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            // Individual Sliders
            servoAngles.forEachIndexed { index, angle ->
                val label = when(index) {
                    0 -> "PTO (ID 0)"
                    5 -> "Retract (ID 5)"
                    else -> "Grasp Finger ${index} (ID $index)"
                }
                
                IndividualServoControl(
                    label = label,
                    angle = angle,
                    onAngleChange = { newAngle -> 
                        servoAngles[index] = newAngle
                        viewModel.sendCommand("Servo $index ${newAngle.toInt()} ${globalSpeed.toInt()}")
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun CalibrationCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun IndividualServoControl(label: String, angle: Float, onAngleChange: (Float) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.2f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("${angle.toInt()}°", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
            }
            Slider(
                value = angle,
                onValueChange = onAngleChange,
                valueRange = 0f..255f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}
