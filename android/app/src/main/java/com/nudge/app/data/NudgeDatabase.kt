package com.nudge.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DataPoint::class], version = 1, exportSchema = false)
abstract class NudgeDatabase : RoomDatabase() {
    abstract fun dataPointDao(): DataPointDao
}
