package com.nudge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Psychology
import com.nudge.app.bluetooth.ModelSync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nudge.app.bluetooth.BluetoothViewModel
import com.nudge.app.bluetooth.DeviceStatus
import com.nudge.app.ui.components.*

@Composable
fun LiveSignalsScreen(
    bluetooth: BluetoothViewModel,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    onTrain: () -> Unit
) {
    val status by bluetooth.deviceStatus.collectAsState()
    val channels by bluetooth.channels.collectAsState()
    val gesture by bluetooth.lastGesture.collectAsState()
    val sync by bluetooth.modelSync.collectAsState()
    var aiDrives by remember { mutableStateOf(false) }
    val online = status == DeviceStatus.Connected || status == DeviceStatus.Demo

    // Never leave the hand under AI control after leaving this screen
    DisposableEffect(Unit) {
        onDispose { bluetooth.sendCommand("ai_stop") }
    }

    NudgeScreen(title = "Live signals", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            if (!online) {
                NotConnectedBanner(onConnect)
                Spacer(Modifier.height(16.dp))
            }

            NudgeCard(Modifier.fillMaxWidth().height(260.dp)) {
                EmgChart(channels, Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(12.dp))
            GestureReadout(if (online) gesture else "UNKNOWN")
            if (sync == ModelSync.NoModel) {
                Spacer(Modifier.height(12.dp))
                ActionRow(
                    icon = Icons.Default.Psychology,
                    title = "Train the AI first",
                    subtitle = "It needs to learn your gestures before it can recognise them",
                    onClick = onTrain
                )
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("Assist")
            NudgeCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Default.PanTool)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Let AI move my hand", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "The wearable opens and closes when it detects the gesture.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = aiDrives,
                        enabled = online,
                        onCheckedChange = {
                            aiDrives = it
                            bluetooth.sendCommand(if (it) "ai_start" else "ai_stop")
                        }
                    )
                }
            }

            if (online) {
                Spacer(Modifier.height(16.dp))
                SecondaryButton(
                    "Stop all motors",
                    destructive = true,
                    onClick = {
                        aiDrives = false
                        bluetooth.sendCommand("ai_stop")
                        bluetooth.sendCommand("stop")
                    }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
