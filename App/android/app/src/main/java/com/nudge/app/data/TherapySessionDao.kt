package com.nudge.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TherapySessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: TherapySession): Long

    @Update
    suspend fun update(session: TherapySession)

    @Query("SELECT * FROM therapy_sessions WHERE sessionId = :id")
    suspend fun getSessionById(id: Long): TherapySession?

    @Query("SELECT * FROM therapy_sessions WHERE username = :username ORDER BY startTime DESC")
    fun getSessionsForUser(username: String): Flow<List<TherapySession>>

    @Query("DELETE FROM therapy_sessions WHERE sessionId = :id")
    suspend fun deleteSession(id: Long)

    @Query("DELETE FROM therapy_sessions WHERE username = :username")
    suspend fun deleteSessionsForUser(username: String)
}
