package com.nudge.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nudge.app.ui.theme.NudgeTheme

@Composable
fun SummaryScreen(
    onConnectWithPhysician: () -> Unit = {},
    onStartTherapy: () -> Unit = {},
    onStartMinigame: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "User Data",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp),
            color = MaterialTheme.colorScheme.onSurface
        )

        // Bar Chart Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().height(250.dp).padding(bottom = 24.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Recovery Progress (Bar Chart)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(16.dp))
                BarChart()
            }
        }

        // Pie Chart Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().height(300.dp).padding(bottom = 32.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Activity Distribution (Pie Chart)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(16.dp))
                PieChart()
            }
        }

        // Quick Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onStartTherapy,
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                Text("Start Therapy")
            }
            Button(
                onClick = onStartMinigame,
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                Text("Play Minigame")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onConnectWithPhysician,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Link a Physician/Therapist")
        }
    }
}

@Composable
fun BarChart() {
    val barColors = listOf(Color(0xFF4285F4), Color(0xFF9B72F3), Color(0xFFF4B400), Color(0xFFFBBC04))
    val values = listOf(0.7f, 0.5f, 0.9f, 0.3f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val spacing = 40.dp.toPx()
        val barWidth = (size.width - (spacing * (values.size + 1))) / values.size
        
        values.forEachIndexed { index, value ->
            val left = spacing + (index * (barWidth + spacing))
            val top = size.height * (1 - value)
            
            drawRect(
                color = barColors[index % barColors.size],
                topLeft = Offset(left, top),
                size = Size(barWidth, size.height - top)
            )
        }
    }
}

@Composable
fun PieChart() {
    val colors = listOf(Color(0xFF4285F4), Color(0xFF9B72F3), Color(0xFFF4B400), Color(0xFFFBBC04))
    val angles = listOf(180f, 90f, 60f, 30f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        var startAngle = 0f
        val canvasSize = size.minDimension * 0.8f
        val topLeft = Offset((size.width - canvasSize) / 2, (size.height - canvasSize) / 2)

        angles.forEachIndexed { index, angle ->
            drawArc(
                color = colors[index % colors.size],
                startAngle = startAngle,
                sweepAngle = angle,
                useCenter = true,
                topLeft = topLeft,
                size = Size(canvasSize, canvasSize)
            )
            startAngle += angle
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SummaryScreenPreview() {
    NudgeTheme {
        SummaryScreen()
    }
}
