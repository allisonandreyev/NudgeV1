package com.nudge.app.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.nudge.app.data.DataPoint
import java.io.File
import java.io.FileOutputStream

object CSVExporter {

    fun exportTrainingData(context: Context, username: String, dataPoints: List<DataPoint>) {
        Log.d("CSVExporter", "Starting export for user $username. Data points: ${dataPoints.size}")
        
        if (dataPoints.isEmpty()) {
            Log.w("CSVExporter", "Export failed: Data points list is empty.")
            Toast.makeText(context, "No training data found to export", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val fileName = "nudge_training_${username}_${System.currentTimeMillis()}.csv"
            val file = File(context.cacheDir, fileName)
            
            var rowCount = 0
            val fos = FileOutputStream(file)
            fos.use { stream ->
                // Write Header
                stream.write("timestamp,sensor_d0,sensor_d1,sensor_d2,label\n".toByteArray())
                
                // Group by timestamp (10ms windows) to synchronize the 3 sensors
                val grouped = dataPoints.groupBy { it.timestamp / 10 * 10 }
                Log.d("CSVExporter", "Grouped ${dataPoints.size} points into ${grouped.size} synchronized rows")
                rowCount = grouped.size

                // Calculate the start time to normalize timestamps to 0
                val startTime = dataPoints.minOf { it.timestamp }

                grouped.forEach { (_, points) ->
                    val label = points.firstOrNull { it.label != null }?.label ?: "UNKNOWN"
                    val d0 = points.find { it.sensorId == 0 }?.value ?: 0f
                    val d1 = points.find { it.sensorId == 1 }?.value ?: 0f
                    val d2 = points.find { it.sensorId == 2 }?.value ?: 0f
                    
                    // Normalize timestamp so it starts at 0
                    val normalizedTimestamp = points.first().timestamp - startTime
                    val line = "$normalizedTimestamp,$d0,$d1,$d2,$label\n"
                    stream.write(line.toByteArray())
                }
            }

            Log.d("CSVExporter", "File written successfully to ${file.absolutePath}")
            Toast.makeText(context, "Exported $rowCount rows successfully!", Toast.LENGTH_SHORT).show()
            shareFile(context, file)
        } catch (e: Exception) {
            Log.e("CSVExporter", "Critical error during CSV export", e)
            Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            val chooser = Intent.createChooser(intent, "Export Training Data")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            
            Log.d("CSVExporter", "Share intent launched for URI: $uri")
        } catch (e: Exception) {
            Log.e("CSVExporter", "Failed to launch share intent", e)
            Toast.makeText(context, "Share failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
