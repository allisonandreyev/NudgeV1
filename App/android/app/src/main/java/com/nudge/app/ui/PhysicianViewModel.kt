package com.nudge.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nudge.app.data.ConnectionStatus
import com.nudge.app.data.PhysicianConnection
import com.nudge.app.data.PhysicianConnectionDao
import com.nudge.app.data.UserStats
import com.nudge.app.data.UserStatsDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PhysicianViewModel @Inject constructor(
    private val physicianConnectionDao: PhysicianConnectionDao,
    private val userStatsDao: UserStatsDao
) : ViewModel() {

    private val _currentEmail = MutableStateFlow<String?>(null)

    val connections = _currentEmail.flatMapLatest { email ->
        if (email != null) {
            physicianConnectionDao.getConnectionsForPhysician(email)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setUsername(email: String) {
        _currentEmail.value = email
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

    fun getPatientStats(username: String): Flow<UserStats?> {
        return userStatsDao.getUserStats(username)
    }
}
