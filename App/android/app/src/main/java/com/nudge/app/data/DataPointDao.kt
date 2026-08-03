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

    @Query("SELECT * FROM data_points WHERE username = :username ORDER BY timestamp DESC")
    fun getAllDataPoints(username: String): Flow<List<DataPoint>>

    @Query("SELECT * FROM data_points WHERE username = :username AND type = :type ORDER BY timestamp DESC")
    fun getDataPointsByType(username: String, type: String): Flow<List<DataPoint>>
}
