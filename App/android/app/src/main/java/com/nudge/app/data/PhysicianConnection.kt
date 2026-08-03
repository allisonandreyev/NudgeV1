package com.nudge.app.data

import androidx.room.Entity

@Entity(
    tableName = "physician_connections",
    primaryKeys = ["physicianEmail", "patientUsername"]
)
data class PhysicianConnection(
    val physicianEmail: String,
    val patientUsername: String,
    val physicianName: String,
    val connectionDate: Long = System.currentTimeMillis(),
    val status: ConnectionStatus = ConnectionStatus.PENDING,
    val isSynced: Boolean = false
)

enum class ConnectionStatus {
    PENDING, ACCEPTED, REJECTED
}
