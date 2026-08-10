package com.nudge.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [DataPoint::class, PhysicianConnection::class, UserStats::class, User::class, TherapySession::class],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class NudgeDatabase : RoomDatabase() {
    abstract fun dataPointDao(): DataPointDao
    abstract fun physicianConnectionDao(): PhysicianConnectionDao
    abstract fun userStatsDao(): UserStatsDao
    abstract fun userDao(): UserDao
    abstract fun therapySessionDao(): TherapySessionDao
}
