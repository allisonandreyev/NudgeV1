package com.nudge.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "physician_connections")
data class PhysicianConnection(
    @PrimaryKey val physicianEmail: String,
    val physicianName: String,
    val connectionDate: Long = System.currentTimeMillis()
)
