package com.nudge.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nudge.app.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PhysicianViewModel @Inject constructor(
    private val physicianConnectionDao: PhysicianConnectionDao,
    private val userStatsDao: UserStatsDao,
    private val therapySessionDao: TherapySessionDao,
    private val dataPointDao: DataPointDao
) : ViewModel() {

    private val _currentEmail = MutableStateFlow<String?>(null)
    private val _currentPatientUsername = MutableStateFlow<String?>(null)

    // For Physician View: Get connections for this physician
    val physicianConnections = _currentEmail.flatMapLatest { email ->
        if (email != null) {
            physicianConnectionDao.getConnectionsForPhysician(email)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // For Patient View: Get connections for this patient
    val patientConnections = _currentPatientUsername.flatMapLatest { username ->
        if (username != null) {
            physicianConnectionDao.getConnectionsForPatient(username)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Backwards compatibility for UI that uses 'connections'
    val connections = physicianConnections

    fun setPhysicianContext(email: String) {
        _currentEmail.value = email
    }

    fun setPatientContext(username: String) {
        _currentPatientUsername.value = username
    }

    fun acceptConnection(connection: PhysicianConnection) {
        viewModelScope.launch {
            physicianConnectionDao.updateConnection(connection.copy(status = ConnectionStatus.ACCEPTED))
        }
    }

    fun rejectConnection(connection: PhysicianConnection) {
        viewModelScope.launch {
            physicianConnectionDao.updateConnection(connection.copy(status = ConnectionStatus.REJECTED))
        }
    }

    fun connectPhysician(email: String, patientUsername: String, name: String) {
        viewModelScope.launch {
            physicianConnectionDao.insert(
                PhysicianConnection(
                    physicianEmail = email,
                    patientUsername = patientUsername,
                    physicianName = name,
                    status = ConnectionStatus.PENDING
                )
            )
        }
    }

    fun removeConnection(email: String, patientUsername: String) {
        viewModelScope.launch {
            physicianConnectionDao.deleteConnection(email, patientUsername)
        }
    }

    fun getPatientStats(username: String): Flow<UserStats?> {
        return userStatsDao.getUserStats(username)
    }

    fun getPatientSessions(username: String): Flow<List<TherapySession>> {
        return therapySessionDao.getSessionsForUser(username)
    }

    fun getPointsForSession(sessionId: Long): Flow<List<DataPoint>> {
        return dataPointDao.getPointsForSession(sessionId)
    }

    suspend fun getSessionById(sessionId: Long): TherapySession? {
        return therapySessionDao.getSessionById(sessionId)
    }
}
