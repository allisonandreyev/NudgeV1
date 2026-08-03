package com.nudge.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_stats")
data class UserStats(
    @PrimaryKey val username: String,
    val highScore: Int = 0,
    val isSynced: Boolean = false
)
