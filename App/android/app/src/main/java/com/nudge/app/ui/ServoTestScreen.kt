package com.nudge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BackHand
import androidx.compose.material.icons.filled.FrontHand
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nudge.app.bluetooth.BluetoothViewModel
import com.nudge.app.bluetooth.DeviceStatus
import com.nudge.app.ui.components.*
import com.nudge.app.ui.theme.NudgeTheme

/** Firmware stops closing when either finger sensor reads above this (GRIP_LIMIT in MotorController.cpp). */
private const val GRIP_LIMIT = 2500

/**
 * Manual control of the main board's two motors, for setup and troubleshooting,
 * with the live EMG channels, grip pressure and battery alongside.
 */
@Composable
fun ServoTestScreen(
    bluetooth: BluetoothViewModel,
    onBack: () -> Unit,
    onConnect: () -> Unit
) {
    val status by bluetooth.deviceStatus.collectAsState()
    val channels by bluetooth.channels.collectAsState()
    val grip by bluetooth.grip.collectAsState()
    val battery by bluetooth.battery.collectAsState()
    val online = status == DeviceStatus.Connected || status == DeviceStatus.Demo
    var speed by remember { mutableFloatStateOf(150f) }
    val positions = remember { mutableStateListOf(0f, 0f) }
    var showAdvanced by remember { mutableStateOf(false) }

    NudgeScreen(
        title = "Hand controls",
        onBack = onBack,
        bottomBar = {
            if (online) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    PrimaryButton(
                        "Stop motors",
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

            if (online && channels.isNotEmpty()) {
                SectionLabel("Live signals")
                NudgeCard(Modifier.fillMaxWidth().height(if (channels.size > 3) 240.dp else 140.dp)) {
                    EmgChart(channels, Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(20.dp))
            }

            SectionLabel("Whole hand")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(
                    "Close",
                    icon = Icons.Default.FrontHand,
                    enabled = online,
                    onClick = {
                        positions.indices.forEach { positions[it] = 100f }
                        bluetooth.sendCommand("grasp 255 ${speed.toInt()}")
                    },
                    modifier = Modifier.weight(1f)
                )
                PrimaryButton(
                    "Open",
                    icon = Icons.Default.BackHand,
                    enabled = online,
                    onClick = {
                        positions.indices.forEach { positions[it] = 0f }
                        bluetooth.sendCommand("retract 255 ${speed.toInt()}")
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            grip?.let { (thumb, index) ->
                Spacer(Modifier.height(24.dp))
                SectionLabel("Grip pressure")
                NudgeCard(Modifier.fillMaxWidth()) {
                    PressureBar("Thumb", thumb)
                    Spacer(Modifier.height(12.dp))
                    PressureBar("Index finger", index)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Closing stops when either reaches the line, so the hand doesn't squeeze too hard.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("Speed")
            NudgeCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Movement speed", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("${(speed / 3).toInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                // The firmware reads 300 as full power
                Slider(value = speed, onValueChange = { speed = it }, valueRange = 30f..300f)
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) "Hide motor settings" else "Show motor settings")
            }
            if (showAdvanced) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    positions.forEachIndexed { index, position ->
                        NudgeCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Motor ${index + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Text("${position.toInt()}% closed", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = position,
                                enabled = online,
                                valueRange = 0f..100f,
                                onValueChange = { positions[index] = it },
                                // Send once when the finger lifts, not on every pixel of the drag
                                onValueChangeFinished = {
                                    bluetooth.sendCommand("motor ${index + 1} ${positions[index].toInt()} ${speed.toInt()}")
                                }
                            )
                        }
                    }
                    ActionRow(
                        icon = Icons.Default.Straighten,
                        title = "Calibrate motors",
                        subtitle = "Opens fully, closes until each motor stops, then opens again to learn its range",
                        onClick = { bluetooth.sendCommand("calibrate") }
                    )
                }
            }

            battery?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Battery $it%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (it < 15) NudgeTheme.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PressureBar(label: String, raw: Int) {
    val fraction = (raw / 4095f).coerceIn(0f, 1f)
    val atLimit = raw >= GRIP_LIMIT
    val marker = MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(110.dp))
        Box(Modifier.weight(1f).height(10.dp)) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                color = if (atLimit) NudgeTheme.colors.warning else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            // Line where the firmware stops closing
            Box(Modifier.fillMaxHeight().fillMaxWidth(GRIP_LIMIT / 4095f)) {
                Box(Modifier.align(Alignment.CenterEnd).width(2.dp).fillMaxHeight().background(marker))
            }
        }
    }
}
