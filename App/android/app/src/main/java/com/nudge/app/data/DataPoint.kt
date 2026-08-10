package com.nudge.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "data_points")
data class DataPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val timestamp: Long,
    val value: Float,
    val type: String,
    val sensorId: Int = 0, // 0 for D0, 1 for D1, 2 for D2
    val sessionId: Long? = null, // Linked therapy session
    val label: String? = null,   // ML Label: REST, OPEN, PINCH, CLOSE
    val isSynced: Boolean = false
)
