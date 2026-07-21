package com.nudge.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nudge.app.ui.theme.NudgeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSelectScreen(onDeviceSelected: (String) -> Unit) {
    val devices = remember {
        mutableStateListOf(
            "Nudge-ESP32C6-A1",
            "Nudge-ESP32C6-B4",
            "Nudge-Physio-01"
        )
    }
    var isScanning by remember { mutableStateOf(true) }

    // Pulsing animation for the Bluetooth icon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Device Selection", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { /* Reset scan */ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Animated Scanner Header
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            ) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer(scaleX = scale, scaleY = scale),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (isScanning) "Scanning for Nudge Wearables..." else "Select your device",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .padding(top = 16.dp, start = 48.dp, end = 48.dp)
                        .fillMaxWidth()
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "AVAILABLE DEVICES",
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(start = 24.dp, bottom = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            ) {
                LazyColumn {
                    items(devices) { deviceName ->
                        ListItem(
                            headlineContent = { Text(deviceName, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("ESP32-C6 Chipset") },
                            leadingContent = {
                                Icon(
                                    Icons.Default.Bluetooth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingContent = {
                                Text("Connect", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            },
                            modifier = Modifier.clickable { onDeviceSelected(deviceName) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        if (deviceName != devices.last()) HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                text = "Ensure your Nudge device is powered on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DeviceSelectScreenPreview() {
    NudgeTheme {
        DeviceSelectScreen(onDeviceSelected = {})
    }
}
