package com.nudge.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nudge.app.data.RestPosition
import com.nudge.app.data.TherapySession
import com.nudge.app.data.TherapySessionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TherapyState {
    IDLE, SETUP, ACTIVE_REST, ACTIVE_CONTRACT, STOPPED
}

@HiltViewModel
class TherapyViewModel @Inject constructor(
    private val therapySessionDao: TherapySessionDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(TherapyState.IDLE)
    val uiState = _uiState.asStateFlow()

    private val _currentSessionId = MutableStateFlow<Long?>(null)
    val currentSessionId = _currentSessionId.asStateFlow()

    private val _timerSeconds = MutableStateFlow(5)
    val timerSeconds = _timerSeconds.asStateFlow()

    private var timerJob: Job? = null

    fun startSetup() {
        _uiState.value = TherapyState.SETUP
    }

    fun startSession(username: String, restPosition: RestPosition) {
        viewModelScope.launch {
            val sessionId = therapySessionDao.insert(
                TherapySession(
                    username = username,
                    restPosition = restPosition
                )
            )
            _currentSessionId.value = sessionId
            _uiState.value = TherapyState.ACTIVE_REST
            startTimer()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_uiState.value == TherapyState.ACTIVE_REST || _uiState.value == TherapyState.ACTIVE_CONTRACT) {
                for (i in 5 downTo 1) {
                    _timerSeconds.value = i
                    delay(1000)
                }
                // Toggle state
                _uiState.value = if (_uiState.value == TherapyState.ACTIVE_REST) {
                    TherapyState.ACTIVE_CONTRACT
                } else {
                    TherapyState.ACTIVE_REST
                }
            }
        }
    }

    fun stopSession() {
        val sessionId = _currentSessionId.value ?: return
        viewModelScope.launch {
            timerJob?.cancel()
            val session = therapySessionDao.getSessionById(sessionId)
            if (session != null) {
                therapySessionDao.update(session.copy(endTime = System.currentTimeMillis()))
            }
            _uiState.value = TherapyState.STOPPED
        }
    }

    fun uploadSession() {
        val sessionId = _currentSessionId.value ?: return
        viewModelScope.launch {
            val session = therapySessionDao.getSessionById(sessionId)
            if (session != null) {
                // In a real app, this would trigger a network upload.
                // Here we just mark it as uploaded locally.
                therapySessionDao.update(session.copy(isUploaded = true))
            }
        }
    }

    fun reset() {
        _uiState.value = TherapyState.IDLE
        _currentSessionId.value = null
        _timerSeconds.value = 5
        timerJob?.cancel()
    }
}
