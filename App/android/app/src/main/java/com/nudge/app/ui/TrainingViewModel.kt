package com.nudge.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nudge.app.ai.GestureTrainer
import com.nudge.app.ai.ModelRepository
import com.nudge.app.ai.Recording
import com.nudge.app.ai.TrainingResult
import com.nudge.app.bluetooth.EmgSample
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TrainingPhase { INTRO, COUNTDOWN, PREPARE, HOLD, BUILDING, RESULT, FAILED }

/** A reading recorded while the user held a prompted gesture. */
data class LabeledSample(val round: Int, val label: String, val values: FloatArray)

/**
 * Walks the user through each gesture a few times, records the readings taken while they
 * hold it (not the switch-over time), then trains and saves their gesture model.
 */
@HiltViewModel
class TrainingViewModel @Inject constructor(
    private val models: ModelRepository
) : ViewModel() {

    val gestures = listOf("REST", "OPEN", "CLOSE", "PINCH")
    val rounds = 5

    private val _phase = MutableStateFlow(TrainingPhase.INTRO)
    val phase = _phase.asStateFlow()

    private val _gesture = MutableStateFlow(gestures.first())
    val gesture = _gesture.asStateFlow()

    private val _round = MutableStateFlow(1)
    val round = _round.asStateFlow()

    /** 0 → 1 across the whole recording */
    private val _progress = MutableStateFlow(0f)
    val progress = _progress.asStateFlow()

    /** 1 → 0 across the current hold */
    private val _holdRemaining = MutableStateFlow(1f)
    val holdRemaining = _holdRemaining.asStateFlow()

    private val _countdown = MutableStateFlow(3)
    val countdown = _countdown.asStateFlow()

    private val _result = MutableStateFlow<TrainingResult?>(null)
    val result = _result.asStateFlow()

    /** True once the new model replaced the previous one. */
    private val _saved = MutableStateFlow(false)
    val saved = _saved.asStateFlow()

    private var username = ""
    private val recorded = mutableListOf<LabeledSample>()
    val samples: List<LabeledSample> get() = synchronized(recorded) { recorded.toList() }

    private var job: Job? = null
    private var collector: Job? = null
    @Volatile private var recordingLabel: String? = null

    val totalSeconds: Int get() = ((PREPARE_MS + HOLD_MS) * gestures.size * rounds / 1000).toInt()

    fun start(username: String, readings: Flow<EmgSample>, onPrompt: (String?) -> Unit) {
        this.username = username
        _result.value = null
        _saved.value = false
        job?.cancel()
        collector?.cancel()
        synchronized(recorded) { recorded.clear() }
        collector = viewModelScope.launch {
            readings.collect { sample ->
                val label = recordingLabel ?: return@collect
                synchronized(recorded) { recorded += LabeledSample(_round.value, label, sample.values) }
            }
        }
        job = viewModelScope.launch {
            _phase.value = TrainingPhase.COUNTDOWN
            for (i in 3 downTo 1) {
                _countdown.value = i
                delay(1000)
            }
            val steps = gestures.size * rounds
            var step = 0
            for (r in 1..rounds) {
                _round.value = r
                for (g in gestures) {
                    _gesture.value = g
                    onPrompt(g)

                    _phase.value = TrainingPhase.PREPARE
                    delay(PREPARE_MS)

                    _phase.value = TrainingPhase.HOLD
                    recordingLabel = g
                    val ticks = (HOLD_MS / TICK_MS).toInt()
                    for (t in ticks downTo 1) {
                        _holdRemaining.value = t / ticks.toFloat()
                        _progress.value = (step + 1f - t / ticks.toFloat()) / steps
                        delay(TICK_MS)
                    }
                    recordingLabel = null
                    step++
                }
            }
            onPrompt(null)
            collector?.cancel()
            _progress.value = 1f
            buildModel()
        }
    }

    private suspend fun buildModel() {
        _phase.value = TrainingPhase.BUILDING
        val started = System.currentTimeMillis()
        val result = try {
            withContext(Dispatchers.Default) { GestureTrainer.train(recordings()) }
        } catch (e: Exception) {
            null
        }
        // Let the "building" state register even though training takes a moment
        delay((MIN_BUILD_MS - (System.currentTimeMillis() - started)).coerceAtLeast(0))
        if (result == null) {
            _phase.value = TrainingPhase.FAILED
            return
        }
        _result.value = result
        // Don't let a poor recording replace a working model without asking
        if ((result.accuracy ?: 1f) >= GOOD_ACCURACY) keepModel()
        _phase.value = TrainingPhase.RESULT
    }

    fun keepModel() {
        val result = _result.value ?: return
        models.save(username, result)
        _saved.value = true
    }

    /** Groups the recorded readings back into one recording per gesture hold. */
    private fun recordings(): List<Recording> {
        val out = mutableListOf<Recording>()
        var current: MutableList<FloatArray>? = null
        var key: Pair<Int, String>? = null
        for (s in samples) {
            if (key != s.round to s.label) {
                current = mutableListOf()
                key = s.round to s.label
                out += Recording(s.round, s.label, current)
            }
            current!!.add(s.values)
        }
        return out
    }

    fun reset() {
        _phase.value = TrainingPhase.INTRO
        _progress.value = 0f
    }

    fun cancel(onPrompt: (String?) -> Unit) {
        job?.cancel()
        collector?.cancel()
        recordingLabel = null
        onPrompt(null)
        _phase.value = TrainingPhase.INTRO
        _progress.value = 0f
    }

    override fun onCleared() {
        recordingLabel = null
        super.onCleared()
    }

    companion object {
        const val PREPARE_MS = 1500L
        const val HOLD_MS = 3000L
        private const val TICK_MS = 50L
        private const val MIN_BUILD_MS = 1200L
        const val GOOD_ACCURACY = 0.7f
    }
}
