package com.nudge.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PhysicianConnectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(connection: PhysicianConnection)

    @Update
    suspend fun updateConnection(connection: PhysicianConnection)

    @Query("SELECT * FROM physician_connections WHERE patientUsername = :patientUsername ORDER BY connectionDate DESC")
    fun getConnectionsForPatient(patientUsername: String): Flow<List<PhysicianConnection>>

    @Query("SELECT * FROM physician_connections WHERE physicianEmail = :physicianEmail ORDER BY connectionDate DESC")
    fun getConnectionsForPhysician(physicianEmail: String): Flow<List<PhysicianConnection>>

    @Query("DELETE FROM physician_connections WHERE physicianEmail = :email AND patientUsername = :patientUsername")
    suspend fun deleteConnection(email: String, patientUsername: String)

    @Query("DELETE FROM physician_connections WHERE patientUsername = :username OR physicianEmail = :username")
    suspend fun deleteAllConnectionsForUser(username: String)
}
