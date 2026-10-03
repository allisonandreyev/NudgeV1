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

enum class TherapyState { SETUP, ACTIVE_REST, ACTIVE_CONTRACT, FINISHED }

@HiltViewModel
class TherapyViewModel @Inject constructor(
    private val therapySessionDao: TherapySessionDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(TherapyState.SETUP)
    val uiState = _uiState.asStateFlow()

    private val _currentSessionId = MutableStateFlow<Long?>(null)
    val currentSessionId = _currentSessionId.asStateFlow()

    /** Fraction of the current phase remaining, 1 → 0 */
    private val _phaseRemaining = MutableStateFlow(1f)
    val phaseRemaining = _phaseRemaining.asStateFlow()

    private val _rep = MutableStateFlow(0)
    val rep = _rep.asStateFlow()

    private val _totalReps = MutableStateFlow(10)
    val totalReps = _totalReps.asStateFlow()

    private val _durationSeconds = MutableStateFlow(0)
    val durationSeconds = _durationSeconds.asStateFlow()

    private val _restPosition = MutableStateFlow(RestPosition.OPENED)
    val restPosition = _restPosition.asStateFlow()

    private val _shared = MutableStateFlow(false)
    val shared = _shared.asStateFlow()

    private var timerJob: Job? = null
    private var startedAt = 0L

    fun startSession(username: String, restPosition: RestPosition, reps: Int) {
        _totalReps.value = reps
        _restPosition.value = restPosition
        viewModelScope.launch {
            _currentSessionId.value = therapySessionDao.insert(TherapySession(username = username, restPosition = restPosition))
            startedAt = System.currentTimeMillis()
            runTimer(reps)
        }
    }

    private fun runTimer(reps: Int) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            for (r in 1..reps) {
                _rep.value = r
                for (phase in listOf(TherapyState.ACTIVE_REST, TherapyState.ACTIVE_CONTRACT)) {
                    _uiState.value = phase
                    val steps = (PHASE_MS / TICK_MS).toInt()
                    for (i in steps downTo 1) {
                        _phaseRemaining.value = i / steps.toFloat()
                        delay(TICK_MS)
                    }
                }
            }
            finish()
        }
    }

    fun stopSession() {
        timerJob?.cancel()
        viewModelScope.launch { finish() }
    }

    private suspend fun finish() {
        val id = _currentSessionId.value ?: return
        val end = System.currentTimeMillis()
        _durationSeconds.value = ((end - startedAt) / 1000).toInt()
        therapySessionDao.getSessionById(id)?.let { therapySessionDao.update(it.copy(endTime = end)) }
        _uiState.value = TherapyState.FINISHED
    }

    fun setShared(shared: Boolean) {
        val id = _currentSessionId.value ?: return
        _shared.value = shared
        viewModelScope.launch {
            therapySessionDao.getSessionById(id)?.let { therapySessionDao.update(it.copy(isUploaded = shared)) }
        }
    }

    companion object {
        const val PHASE_MS = 5000L
        private const val TICK_MS = 50L
    }
}
