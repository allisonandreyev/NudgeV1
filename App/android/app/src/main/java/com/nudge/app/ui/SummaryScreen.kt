package com.nudge.app.ui

import android.bluetooth.BluetoothProfile
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
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
import com.nudge.app.ui.theme.MedicalGradient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    viewModel: BluetoothViewModel,
    userRole: UserRole,
    username: String,
    onConnectWithPhysician: () -> Unit = {},
    onStartTherapy: () -> Unit = {},
    onStartMinigame: () -> Unit = {},
    onStartTraining: () -> Unit = {},
    onStartServoTest: () -> Unit = {},
    onStartAITest: () -> Unit = {},
    onViewPhysicianDashboard: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val emgDataD0 by viewModel.emgDataD0.collectAsState()
    val emgDataD1 by viewModel.emgDataD1.collectAsState()
    val emgDataD2 by viewModel.emgDataD2.collectAsState()
    
    val receiveRate by viewModel.receiveFrequency.collectAsState()
    val rawLogs by viewModel.rawLogs.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val listState = rememberLazyListState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = MedicalGradient)
            .safeDrawingPadding(),
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            text = if (userRole == UserRole.PATIENT) "Nudge Dashboard" else "Clinical Hub",
                            fontWeight = FontWeight.Bold, 
                            fontSize = 18.sp,
                            color = Color.White
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
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                    if (userRole == UserRole.PATIENT && connectionState == BluetoothProfile.STATE_CONNECTED) {
                        IconButton(
                            onClick = { viewModel.sendCommand("stop") },
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Icon(Icons.Default.Dangerous, contentDescription = "Stop All", tint = Color.Red)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            if (userRole == UserRole.PATIENT) {
                // Multi-Sensor EMG Graph Cards
                item {
                    Text(
                        "LIVE EMG STREAMS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                item { SensorGraphCard(label = "Sensor D0", data = emgDataD0, color = MaterialTheme.colorScheme.primary) }
                item { SensorGraphCard(label = "Sensor D1", data = emgDataD1, color = Color(0xFF00E676)) }
                item { SensorGraphCard(label = "Sensor D2", data = emgDataD2, color = Color(0xFFFF9100)) }

                // Test User Special Section
                if (username == "test_user") {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "ENGINEERING TOOLS",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onStartTraining,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Train AI", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = onStartServoTest,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Calibrate", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Button(
                                onClick = onStartAITest,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            ) {
                                Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("OPEN REAL-TIME AI TEST", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "PATIENT ACTIONS",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onStartTherapy,
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))
                            ) {
                                Text("Therapy", fontSize = 12.sp, color = Color.White)
                            }
                            Button(
                                onClick = onStartMinigame,
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))
                            ) {
                                Text("Game", fontSize = 12.sp, color = Color.White)
                            }
                            Button(
                                onClick = onConnectWithPhysician,
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))
                            ) {
                                Text("Physician", fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                }
            } else {
                // Physician View
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(100.dp),
                                tint = Color.White.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Welcome, Dr. $username",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = onViewPhysicianDashboard,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("View Patient Dashboard", fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { viewModel.disconnect() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.1f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f))
                ) {
                    Text("Disconnect Wearable", color = Color.White)
                }
            }
            
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun SensorGraphCard(label: String, data: List<Float>, color: Color) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
                Text(
                    text = "${data.lastOrNull()?.toInt() ?: 0}",
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.ExtraBold
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
