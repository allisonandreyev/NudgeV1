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
    val isSynced: Boolean = false
)
