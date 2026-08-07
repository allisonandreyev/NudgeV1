package com.nudge.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "therapy_sessions")
data class TherapySession(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val username: String,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val restPosition: RestPosition,
    val isUploaded: Boolean = false,
    val isSynced: Boolean = false
)

enum class RestPosition {
    OPENED, CLOSED
}
