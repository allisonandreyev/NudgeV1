package com.nudge.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun LineGraph(
    data: List<Float>,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(200.dp),
    color: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas

        val maxVal = data.maxOrNull() ?: 1f
        val minVal = data.minOrNull() ?: 0f
        val range = (maxVal - minVal).coerceAtLeast(1f)

        val width = size.width
        val height = size.height
        val xStep = width / (data.size - 1)

        val path = Path().apply {
            data.forEachIndexed { index, value ->
                val x = index * xStep
                // Invert Y because (0,0) is top-left
                val y = height - ((value - minVal) / range * height)
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 3.dp.toPx())
        )
        
        // Optional: Draw a base line
        drawLine(
            color = Color.Gray.copy(alpha = 0.5f),
            start = Offset(0f, height),
            end = Offset(width, height),
            strokeWidth = 1.dp.toPx()
        )
    }
}
