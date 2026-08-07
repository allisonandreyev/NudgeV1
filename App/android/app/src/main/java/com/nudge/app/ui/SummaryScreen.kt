package com.nudge.app.ui

import android.bluetooth.BluetoothProfile
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nudge.app.bluetooth.BluetoothViewModel
import com.nudge.app.data.UserRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    viewModel: BluetoothViewModel,
    userRole: UserRole,
    username: String,
    onConnectWithPhysician: () -> Unit = {},
    onStartTherapy: () -> Unit = {},
    onStartMinigame: () -> Unit = {},
    onViewPhysicianDashboard: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val emgDataD0 by viewModel.emgDataD0.collectAsState()
    val emgDataD1 by viewModel.emgDataD1.collectAsState()
    val emgDataD2 by viewModel.emgDataD2.collectAsState()
    
    val receiveRate by viewModel.receiveFrequency.collectAsState()
    val rawLogs by viewModel.rawLogs.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    var commandText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(rawLogs.size) {
        if (rawLogs.isNotEmpty()) {
            listState.animateScrollToItem(rawLogs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            text = if (userRole == UserRole.PATIENT) "ESP32 Debug Console" else "Clinical Hub",
                            fontWeight = FontWeight.Bold, 
                            fontSize = 18.sp
                        )
                        if (userRole == UserRole.PATIENT) {
                            Text(
                                text = when(connectionState) {
                                    BluetoothProfile.STATE_CONNECTED -> "Connected"
                                    BluetoothProfile.STATE_CONNECTING -> "Connecting..."
                                    else -> "Disconnected"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if(connectionState == BluetoothProfile.STATE_CONNECTED) Color.Green else Color.Red
                            )
                        }
                    }
                },
                actions = {
                    if (userRole == UserRole.PATIENT) {
                        Text(
                            "%.1f Hz".format(receiveRate),
                            modifier = Modifier.padding(end = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (userRole == UserRole.PATIENT) {
                // Multi-Sensor EMG Graph Cards
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        "LIVE EMG STREAMS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // Sensor D0
                    SensorGraphCard(label = "Sensor D0", data = emgDataD0, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Sensor D1
                    SensorGraphCard(label = "Sensor D1", data = emgDataD1, color = Color(0xFF4CAF50))
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Sensor D2
                    SensorGraphCard(label = "Sensor D2", data = emgDataD2, color = Color(0xFFFF9800))
                }

                // Command Input
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = commandText,
                        onValueChange = { commandText = it },
                        label = { Text("Send Command") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    IconButton(
                        onClick = {
                            viewModel.sendCommand(commandText)
                            commandText = ""
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Text(
                    "RAW INCOMING DATA",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Raw Logs Console
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        if (rawLogs.isEmpty()) {
                            item {
                                Text(
                                    "No data received yet. Waiting for packets...",
                                    color = Color.Gray,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                        items(rawLogs) { log ->
                            Text(
                                text = "> $log",
                                color = Color.Green,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            } else {
                // Physician Landing View
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(100.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Welcome, Dr. $username",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Select 'View Patient Dashboard' below to manage your connections.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            modifier = Modifier.padding(16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // Navigation Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (userRole == UserRole.PATIENT) {
                    Button(
                        onClick = onStartTherapy,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Therapy", fontSize = 12.sp)
                    }
                    Button(
                        onClick = onStartMinigame,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Game", fontSize = 12.sp)
                    }
                    Button(
                        onClick = onConnectWithPhysician,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Physician", fontSize = 12.sp)
                    }
                } else {
                    // Physician View
                    Button(
                        onClick = onViewPhysicianDashboard,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("View Patient Dashboard", fontSize = 16.sp)
                    }
                }
            }
            
            Button(
                onClick = { viewModel.disconnect() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Disconnect")
            }
        }
    }
}

@Composable
fun SensorGraphCard(label: String, data: List<Float>, color: Color) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = "${data.lastOrNull()?.toInt() ?: 0}",
                    style = MaterialTheme.typography.labelSmall,
                    color = color
                )
            }
            LineGraph(
                data = data,
                modifier = Modifier.fillMaxSize(),
                color = color
            )
        }
    }
}
