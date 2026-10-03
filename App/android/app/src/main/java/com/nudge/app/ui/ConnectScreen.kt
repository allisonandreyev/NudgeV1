package com.nudge.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nudge.app.bluetooth.BluetoothViewModel
import com.nudge.app.bluetooth.DeviceStatus
import com.nudge.app.ui.components.*
import com.nudge.app.ui.theme.NudgeTheme

private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun hasPermissions(context: Context) = requiredPermissions().all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

private fun bluetoothOn(context: Context): Boolean =
    (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true

@SuppressLint("MissingPermission")
@Composable
fun ConnectScreen(
    bluetooth: BluetoothViewModel,
    onBack: () -> Unit,
    onConnected: () -> Unit
) {
    val context = LocalContext.current
    val devices by bluetooth.discoveredDevices.collectAsState()
    val scanning by bluetooth.isScanning.collectAsState()
    val status by bluetooth.deviceStatus.collectAsState()
    val connectedName by bluetooth.connectedName.collectAsState()

    var permitted by remember { mutableStateOf(hasPermissions(context)) }
    var enabled by remember { mutableStateOf(bluetoothOn(context)) }
    var connectRequested by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permitted = hasPermissions(context)
    }
    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        enabled = bluetoothOn(context)
    }

    LaunchedEffect(Unit) {
        if (!permitted) permissionLauncher.launch(requiredPermissions())
    }
    LaunchedEffect(permitted, enabled) {
        if (permitted && enabled && status == DeviceStatus.Disconnected) bluetooth.startScanning()
    }
    // Leave once a connection the user asked for comes up
    LaunchedEffect(status) {
        if (connectRequested && status == DeviceStatus.Connected) onConnected()
    }
    DisposableEffect(Unit) { onDispose { bluetooth.stopScanning() } }

    NudgeScreen(title = "Your wearable", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            when {
                status == DeviceStatus.Connected || status == DeviceStatus.Demo -> ConnectedCard(
                    name = if (status == DeviceStatus.Demo) "Demo device" else connectedName ?: "Nudge",
                    isDemo = status == DeviceStatus.Demo,
                    onDisconnect = { bluetooth.disconnect() }
                )
                !permitted -> InfoState(
                    icon = Icons.Default.Bluetooth,
                    title = "Allow Bluetooth access",
                    message = "Nudge uses Bluetooth to talk to your wearable.",
                    action = "Allow",
                    onAction = { permissionLauncher.launch(requiredPermissions()) }
                )
                !enabled -> InfoState(
                    icon = Icons.Default.BluetoothDisabled,
                    title = "Bluetooth is off",
                    message = "Turn on Bluetooth to find your wearable.",
                    action = "Turn on Bluetooth",
                    onAction = { enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                )
                else -> {
                    ScanHeader(scanning = scanning || status == DeviceStatus.Connecting, connecting = status == DeviceStatus.Connecting)
                    Spacer(Modifier.height(24.dp))
                    if (devices.isNotEmpty()) SectionLabel("Nearby")
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        devices.forEach { device ->
                            ActionRow(
                                icon = Icons.Default.Bluetooth,
                                title = device.name ?: "Nudge",
                                subtitle = device.address,
                                onClick = {
                                    connectRequested = true
                                    bluetooth.connectToDevice(device)
                                },
                                trailing = { Text("Connect", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }
                            )
                        }
                    }
                    if (!scanning && devices.isEmpty() && status != DeviceStatus.Connecting) {
                        Text(
                            "No wearable found. Make sure it's powered on and close by.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    if (!scanning && status != DeviceStatus.Connecting) {
                        SecondaryButton("Search again", onClick = { bluetooth.startScanning() })
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            if (status != DeviceStatus.Demo && status != DeviceStatus.Connected) {
                TextButton(
                    onClick = {
                        bluetooth.startDemo()
                        onConnected()
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp)
                ) {
                    Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Use a demo device instead")
                }
            }
        }
    }
}

@Composable
private fun ScanHeader(scanning: Boolean, connecting: Boolean) {
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "scale"
    )
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(96.dp)
                .scale(if (scanning) pulse.value else 1f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Bluetooth, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            when {
                connecting -> "Connecting…"
                scanning -> "Looking for your wearable…"
                else -> "Choose your wearable"
            },
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun ConnectedCard(name: String, isDemo: Boolean, onDisconnect: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (isDemo) NudgeTheme.colors.warning else NudgeTheme.colors.success,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(name, style = MaterialTheme.typography.titleLarge)
        Text(
            if (isDemo) "Simulated signals, no hardware needed." else "Connected and streaming.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        SecondaryButton("Disconnect", onClick = onDisconnect)
    }
}

@Composable
private fun InfoState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, message: String, action: String, onAction: () -> Unit) {
    EmptyState(icon = icon, title = title, message = message)
    PrimaryButton(action, onClick = onAction)
}
