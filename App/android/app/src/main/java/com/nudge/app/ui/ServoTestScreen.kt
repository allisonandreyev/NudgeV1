package com.nudge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BackHand
import androidx.compose.material.icons.filled.FrontHand
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nudge.app.bluetooth.BluetoothViewModel
import com.nudge.app.bluetooth.DeviceStatus
import com.nudge.app.ui.components.*

// Motor numbers match the firmware: 0 is the clutch, 3 opens the hand, the rest close it
private val SERVO_NAMES = listOf("Clutch", "Grip 1", "Grip 2", "Opener", "Grip 3", "Grip 4")

/** Manual control of the wearable's motors, for setup and troubleshooting. */
@Composable
fun ServoTestScreen(
    bluetooth: BluetoothViewModel,
    onBack: () -> Unit,
    onConnect: () -> Unit
) {
    val status by bluetooth.deviceStatus.collectAsState()
    val online = status == DeviceStatus.Connected || status == DeviceStatus.Demo
    var speed by remember { mutableFloatStateOf(100f) }
    val angles = remember { mutableStateListOf(0f, 0f, 0f, 0f, 0f, 0f) }
    var showAdvanced by remember { mutableStateOf(false) }

    NudgeScreen(
        title = "Hand controls",
        onBack = onBack,
        bottomBar = {
            if (online) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    PrimaryButton(
                        "Stop all motors",
                        destructive = true,
                        onClick = { bluetooth.sendCommand("stop") },
                        modifier = Modifier.navigationBarsPadding().padding(16.dp)
                    )
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            if (!online) {
                NotConnectedBanner(onConnect)
                Spacer(Modifier.height(20.dp))
            }

            SectionLabel("Whole hand")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(
                    "Close",
                    icon = Icons.Default.FrontHand,
                    enabled = online,
                    onClick = { bluetooth.sendCommand("grasp 255 ${speed.toInt()}") },
                    modifier = Modifier.weight(1f)
                )
                PrimaryButton(
                    "Open",
                    icon = Icons.Default.BackHand,
                    enabled = online,
                    onClick = { bluetooth.sendCommand("retract 255 ${speed.toInt()}") },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(
                    "Engage clutch",
                    icon = Icons.Default.Link,
                    enabled = online,
                    onClick = { bluetooth.sendCommand("engage") },
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    "Release",
                    icon = Icons.Default.LinkOff,
                    enabled = online,
                    onClick = { bluetooth.sendCommand("disengage") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("Speed")
            NudgeCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Movement speed", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("${speed.toInt()}°/s", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Slider(value = speed, onValueChange = { speed = it }, valueRange = 10f..300f)
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) "Hide individual motors" else "Show individual motors")
            }
            if (showAdvanced) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    angles.forEachIndexed { index, angle ->
                        NudgeCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(SERVO_NAMES[index], style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Text("${angle.toInt()}°", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = angle,
                                enabled = online,
                                valueRange = 0f..255f,
                                onValueChange = { angles[index] = it },
                                // Send once when the finger lifts, not on every pixel of the drag
                                onValueChangeFinished = {
                                    bluetooth.sendCommand("Servo $index ${angles[index].toInt()} ${speed.toInt()}")
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
