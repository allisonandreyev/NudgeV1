package com.nudge.app.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.nudge.app.data.DataPoint
import java.io.File
import java.io.FileOutputStream

object CSVExporter {

    fun exportTrainingData(context: Context, username: String, dataPoints: List<DataPoint>) {
        if (dataPoints.isEmpty()) return

        val fileName = "nudge_training_${username}_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)
        
        val fos = FileOutputStream(file)
        fos.use { stream ->
            // Write Header
            stream.write("timestamp,sensor_d0,sensor_d1,sensor_d2,label\n".toByteArray())
            
            // Group by timestamp (since our C6 sends 3 points with same/similar timestamps)
            // Or just write them as they are if we want raw data.
            // Professional ML tools usually want synchronized sensor readings.
            // Let's group them by approximate timestamp (within 10ms)
            val grouped = dataPoints.groupBy { it.timestamp / 10 * 10 }
            
            grouped.forEach { (_, points) ->
                val label = points.firstOrNull { it.label != null }?.label ?: "UNKNOWN"
                val d0 = points.find { it.sensorId == 0 }?.value ?: 0f
                val d1 = points.find { it.sensorId == 1 }?.value ?: 0f
                val d2 = points.find { it.sensorId == 2 }?.value ?: 0f
                
                val line = "${points.first().timestamp},$d0,$d1,$d2,$label\n"
                stream.write(line.toByteArray())
            }
        }

        shareFile(context, file)
    }

    private fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(intent, "Export Training Data"))
    }
}
