package com.nudge.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nudge.app.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Links between patients and clinicians, and the clinician's view of patient data. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PhysicianViewModel @Inject constructor(
    private val physicianConnectionDao: PhysicianConnectionDao,
    private val userDao: UserDao,
    private val userStatsDao: UserStatsDao,
    private val therapySessionDao: TherapySessionDao,
    private val dataPointDao: DataPointDao
) : ViewModel() {

    private val _physician = MutableStateFlow<String?>(null)
    private val _patient = MutableStateFlow<String?>(null)

    val physicianConnections = _physician.flatMapLatest { name ->
        if (name != null) physicianConnectionDao.getConnectionsForPhysician(name) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val patientConnections = _patient.flatMapLatest { name ->
        if (name != null) physicianConnectionDao.getConnectionsForPatient(name) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setPhysicianContext(username: String) {
        _physician.value = username
    }

    fun setPatientContext(username: String) {
        _patient.value = username
    }

    fun acceptConnection(connection: PhysicianConnection) {
        viewModelScope.launch { physicianConnectionDao.updateConnection(connection.copy(status = ConnectionStatus.ACCEPTED)) }
    }

    fun rejectConnection(connection: PhysicianConnection) {
        viewModelScope.launch { physicianConnectionDao.updateConnection(connection.copy(status = ConnectionStatus.REJECTED)) }
    }

    /** Sends a request to a clinician account. Returns an error message, or null on success. */
    suspend fun requestClinician(clinicianUsername: String, patientUsername: String): String? {
        val clinician = userDao.getUserByUsername(clinicianUsername)
        if (clinician == null || clinician.role != UserRole.PHYSICIAN) {
            return "No clinician account called \"$clinicianUsername\"."
        }
        physicianConnectionDao.insert(
            PhysicianConnection(
                physicianEmail = clinician.username,
                patientUsername = patientUsername,
                physicianName = clinician.username,
                status = ConnectionStatus.PENDING
            )
        )
        return null
    }

    fun removeConnection(clinicianUsername: String, patientUsername: String) {
        viewModelScope.launch { physicianConnectionDao.deleteConnection(clinicianUsername, patientUsername) }
    }

    fun getPatientStats(username: String): Flow<UserStats?> = userStatsDao.getUserStats(username)

    fun getPatientSessions(username: String): Flow<List<TherapySession>> = therapySessionDao.getSessionsForUser(username)

    fun getPointsForSession(sessionId: Long): Flow<List<DataPoint>> = dataPointDao.getPointsForSession(sessionId)

    suspend fun getSessionById(sessionId: Long): TherapySession? = therapySessionDao.getSessionById(sessionId)

    fun updateSessionNotes(sessionId: Long, notes: String) {
        viewModelScope.launch {
            therapySessionDao.getSessionById(sessionId)?.let { therapySessionDao.update(it.copy(notes = notes)) }
        }
    }
}
