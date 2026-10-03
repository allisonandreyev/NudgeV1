package com.nudge.app.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.nudge.app.data.DataPoint
import java.io.File

object CSVExporter {

    fun exportTrainingData(context: Context, username: String, dataPoints: List<DataPoint>) =
        export(context, "nudge_training_${username}_${System.currentTimeMillis()}.csv", dataPoints, "Export training data")

    fun exportSession(context: Context, sessionId: Long, dataPoints: List<DataPoint>) =
        export(context, "nudge_session_${sessionId}.csv", dataPoints, "Export session")

    /** Writes one row per reading (all three sensors share a timestamp) and opens the share sheet. */
    private fun export(context: Context, fileName: String, dataPoints: List<DataPoint>, title: String) {
        if (dataPoints.isEmpty()) {
            Toast.makeText(context, "Nothing to export yet", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val file = File(context.cacheDir, fileName)
            val startTime = dataPoints.minOf { it.timestamp }
            file.bufferedWriter().use { out ->
                out.write("time_ms,sensor_1,sensor_2,sensor_3,label\n")
                dataPoints.groupBy { it.timestamp }.toSortedMap().forEach { (time, points) ->
                    val value = { sensor: Int -> points.find { it.sensorId == sensor }?.value ?: "" }
                    val label = points.firstNotNullOfOrNull { it.label } ?: ""
                    out.write("${time - startTime},${value(0)},${value(1)},${value(2)},$label\n")
                }
            }
            share(context, file, title)
        } catch (e: Exception) {
            Log.e("CSVExporter", "Export failed", e)
            Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun share(context: Context, file: File, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
