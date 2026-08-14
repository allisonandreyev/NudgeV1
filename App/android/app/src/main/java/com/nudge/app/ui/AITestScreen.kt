package com.nudge.app.ui

import android.bluetooth.BluetoothProfile
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Warning
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
fun AITestScreen(
    viewModel: BluetoothViewModel,
    onBack: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected = connectionState == BluetoothProfile.STATE_CONNECTED
    
    val emgD1 by viewModel.emgDataD1.collectAsState()
    val emgD2 by viewModel.emgDataD2.collectAsState()
    val lastGesture by viewModel.lastGesture.collectAsState()

    val gestureColor by animateColorAsState(
        targetValue = when (lastGesture) {
            "OPEN" -> Color(0xFF00E676)
            "CLOSE" -> Color(0xFFFF5252)
            "PINCH" -> Color(0xFFFF9100)
            "REST" -> Color.White.copy(alpha = 0.5f)
            else -> Color.Gray
        },
        label = "gestureColor"
    )

    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        viewModel.sendCommand("ai_start")
        onDispose {
            viewModel.sendCommand("ai_stop")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = MedicalGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopAppBar(
                title = { Text("AI Inference Test", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            if (!isConnected) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Wearable Disconnected. Connect to see real-time AI results.", color = Color.White, fontSize = 14.sp)
                    }
                }
            }

            // AI Status Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, gestureColor.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = gestureColor
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "DETECTED GESTURE",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Text(
                        lastGesture,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        color = gestureColor
                    )
                }
            }

            Text(
                "ACTIVE SENSOR INPUTS (D1 & D2)",
                modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            // Real-time Graphs
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SensorGraphCard(
                    label = "Channel D1 (Primary Grasp)",
                    data = emgD1,
                    color = Color(0xFF00E676)
                )
                SensorGraphCard(
                    label = "Channel D2 (Antagonistic)",
                    data = emgD2,
                    color = Color(0xFFFF9100)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Back to Dashboard")
            }
        }
    }
}
