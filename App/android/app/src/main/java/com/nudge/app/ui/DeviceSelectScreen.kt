package com.nudge.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSelectScreen(onDeviceSelected: (String) -> Unit) {
    // Mock data for ESP32-C6 devices
    val devices = remember {
        mutableStateListOf(
            "Nudge-ESP32C6-001",
            "Nudge-ESP32C6-002",
            "Nudge-EMG-Sensor-Alpha"
        )
    }
    var isScanning by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Nudge Device") },
                actions = {
                    IconButton(onClick = { /* TODO: Trigger actual BLE Scan */ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            
            Text(
                text = "Looking for ESP32-C6 devices...",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp)
            )

            LazyColumn {
                items(devices) { deviceName ->
                    ListItem(
                        headlineContent = { Text(deviceName) },
                        supportingContent = { Text("ESP32-C6 (BLE)") },
                        modifier = Modifier.clickable { onDeviceSelected(deviceName) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
