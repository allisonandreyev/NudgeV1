package com.nudge.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val username: String,
    val passwordHash: String, // In a real app, we'd hash this. For the prototype, we'll store it as is for simplicity.
    val role: UserRole
)
