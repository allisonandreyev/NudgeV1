package com.nudge.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DataPointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dataPoint: DataPoint)

    @Query("SELECT * FROM data_points ORDER BY timestamp DESC")
    fun getAllDataPoints(): Flow<List<DataPoint>>

    @Query("SELECT * FROM data_points WHERE type = :type ORDER BY timestamp DESC")
    fun getDataPointsByType(type: String): Flow<List<DataPoint>>
}
