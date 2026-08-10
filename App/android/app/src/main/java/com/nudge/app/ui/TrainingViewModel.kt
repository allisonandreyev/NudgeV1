package com.nudge.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nudge.app.data.DataPoint
import com.nudge.app.data.DataPointDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TrainingState {
    IDLE, COUNTDOWN, RECORDING, FINISHED
}

@HiltViewModel
class TrainingViewModel @Inject constructor(
    private val dataPointDao: DataPointDao
) : ViewModel() {

    private val gestures = listOf("REST", "OPEN", "PINCH", "CLOSE")
    private val totalRepetitions = 10
    private val recordingDurationMs = 5000L

    private val _uiState = MutableStateFlow(TrainingState.IDLE)
    val uiState = _uiState.asStateFlow()

    private val _currentGesture = MutableStateFlow("")
    val currentGesture = _currentGesture.asStateFlow()

    private val _currentRepetition = MutableStateFlow(0)
    val currentRepetition = _currentRepetition.asStateFlow()

    private val _timerSeconds = MutableStateFlow(0)
    val timerSeconds = _timerSeconds.asStateFlow()

    private var trainingJob: Job? = null

    fun startTraining(username: String, onLabelChanged: (String?) -> Unit) {
        trainingJob?.cancel()
        trainingJob = viewModelScope.launch {
            // First clear any data from this session attempt if we are restarting
            dataPointDao.clearTrainingData(username)
            
            _uiState.value = TrainingState.COUNTDOWN
            for (i in 3 downTo 1) {
                _timerSeconds.value = i
                delay(1000)
            }

            for (rep in 1..totalRepetitions) {
                _currentRepetition.value = rep
                for (gesture in gestures) {
                    _currentGesture.value = gesture
                    onLabelChanged(gesture)
                    _uiState.value = TrainingState.RECORDING
                    
                    for (i in (recordingDurationMs / 1000).toInt() downTo 1) {
                        _timerSeconds.value = i
                        delay(1000)
                    }
                }
            }

            onLabelChanged(null)
            _uiState.value = TrainingState.FINISHED
        }
    }

    fun stopTraining(onLabelChanged: (String?) -> Unit) {
        trainingJob?.cancel()
        onLabelChanged(null)
        _uiState.value = TrainingState.IDLE
    }

    fun clearData(username: String) {
        viewModelScope.launch {
            dataPointDao.clearTrainingData(username)
        }
    }

    suspend fun getLabeledData(username: String): List<DataPoint> {
        return dataPointDao.getLabeledDataForUser(username).first()
    }
}
