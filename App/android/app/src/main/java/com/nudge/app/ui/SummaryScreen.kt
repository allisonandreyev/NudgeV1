package com.nudge.app.ui

import android.bluetooth.BluetoothProfile
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    viewModel: BluetoothViewModel,
    onConnectWithPhysician: () -> Unit = {},
    onStartTherapy: () -> Unit = {},
    onStartMinigame: () -> Unit = {}
) {
    val emgData by viewModel.emgDataHistory.collectAsState()
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
                        Text("ESP32 Debug Console", fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
                },
                actions = {
                    Text(
                        "%.1f Hz".format(receiveRate),
                        modifier = Modifier.padding(end = 16.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
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
            // Live EMG Graph Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Live EMG Stream", style = MaterialTheme.typography.labelMedium)
                    LineGraph(
                        data = emgData,
                        modifier = Modifier.fillMaxSize()
                    )
                }
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
                    .weight(1f),
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
