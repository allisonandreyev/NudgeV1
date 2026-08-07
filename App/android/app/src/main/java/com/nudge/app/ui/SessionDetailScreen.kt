package com.nudge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nudge.app.data.TherapySession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: Long,
    onBack: () -> Unit,
    viewModel: PhysicianViewModel = hiltViewModel()
) {
    var session by remember { mutableStateOf<TherapySession?>(null) }
    val points by viewModel.getPointsForSession(sessionId).collectAsState(initial = emptyList())
    
    LaunchedEffect(sessionId) {
        session = viewModel.getSessionById(sessionId)
    }

    val dateFormatter = SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault())
    
    val maxVal = if (points.isNotEmpty()) points.maxOf { it.value }.toInt() else 0
    val avgVal = if (points.isNotEmpty()) points.map { it.value }.average().toInt() else 0
    val durationMs = if (session?.endTime != null) session!!.endTime!! - session!!.startTime else 0L
    val durationSec = durationMs / 1000

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session Detail #$sessionId", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = session?.let { dateFormatter.format(Date(it.startTime)) } ?: "Loading...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Resting Position: ${session?.restPosition?.name ?: "N/A"}",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Large Graph
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("FULL SESSION TIMELINE", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (points.isNotEmpty()) {
                        LineGraph(
                            data = points.map { it.value },
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No data points captured.", color = Color.Gray)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricItem(
                    label = "MAX SIGNAL",
                    value = maxVal.toString(),
                    icon = Icons.Default.TrendingUp,
                    modifier = Modifier.weight(1f)
                )
                MetricItem(
                    label = "AVG SIGNAL",
                    value = avgVal.toString(),
                    icon = Icons.Default.Speed,
                    modifier = Modifier.weight(1f)
                )
                MetricItem(
                    label = "DURATION",
                    value = "${durationSec}s",
                    icon = Icons.Default.Timer,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { /* Clinical notes placeholder */ },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Add Clinical Notes")
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        }
    }
}
