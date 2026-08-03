package com.nudge.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DataPoint::class, PhysicianConnection::class, UserStats::class, User::class], version = 4, exportSchema = false)
abstract class NudgeDatabase : RoomDatabase() {
    abstract fun dataPointDao(): DataPointDao
    abstract fun physicianConnectionDao(): PhysicianConnectionDao
    abstract fun userStatsDao(): UserStatsDao
    abstract fun userDao(): UserDao
}
