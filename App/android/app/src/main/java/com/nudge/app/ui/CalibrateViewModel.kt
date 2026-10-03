package com.nudge.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nudge.app.ai.Calibration
import com.nudge.app.ai.CalibrationResult
import com.nudge.app.ai.ModelRepository
import com.nudge.app.ai.SavedModel
import com.nudge.app.bluetooth.EmgSample
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class CalibratePhase { INTRO, PREPARE, HOLD, RESULT, FAILED }

/** One step of the quick check: a gesture and how long to hold it. */
data class CalibrateStep(val gesture: String, val holdMs: Long)

/**
 * Runs the 15-second check (relax, fist, open), works out how each sensor shifted since
 * training, and saves the corrected model, which then goes to the wearable automatically.
 */
@HiltViewModel
class CalibrateViewModel @Inject constructor(
    private val models: ModelRepository
) : ViewModel() {

    val steps = listOf(CalibrateStep("REST", 4000), CalibrateStep("CLOSE", 3000), CalibrateStep("OPEN", 3000))

    private val _phase = MutableStateFlow(CalibratePhase.INTRO)
    val phase = _phase.asStateFlow()

    private val _step = MutableStateFlow(0)
    val step = _step.asStateFlow()

    private val _holdRemaining = MutableStateFlow(1f)
    val holdRemaining = _holdRemaining.asStateFlow()

    private val _result = MutableStateFlow<CalibrationResult?>(null)
    val result = _result.asStateFlow()

    private val _applied = MutableStateFlow(false)
    val applied = _applied.asStateFlow()

    private var job: Job? = null
    private var username = ""
    private var base: SavedModel? = null

    val totalSeconds: Int get() = ((steps.sumOf { it.holdMs } + steps.size * PREPARE_MS) / 1000).toInt()

    fun start(username: String, saved: SavedModel, readings: Flow<EmgSample>, onPrompt: (String?) -> Unit) {
        this.username = username
        base = saved
        _result.value = null
        _applied.value = false
        job?.cancel()
        job = viewModelScope.launch {
            val recorded = steps.associate { it.gesture to mutableListOf<FloatArray>() }
            var recordingInto: MutableList<FloatArray>? = null
            val collector = launch { readings.collect { s -> recordingInto?.add(s.values) } }

            for ((i, step) in steps.withIndex()) {
                _step.value = i
                onPrompt(step.gesture)
                _phase.value = CalibratePhase.PREPARE
                delay(PREPARE_MS)
                _phase.value = CalibratePhase.HOLD
                recordingInto = recorded.getValue(step.gesture)
                val ticks = (step.holdMs / TICK_MS).toInt()
                for (t in ticks downTo 1) {
                    _holdRemaining.value = t / ticks.toFloat()
                    delay(TICK_MS)
                }
                recordingInto = null
            }
            onPrompt(null)
            collector.cancel()

            val reference = saved.reference
            val result = if (reference == null) null else try {
                withContext(Dispatchers.Default) {
                    Calibration.compute(saved.model, reference, recorded.getValue("REST"), recorded.getValue("CLOSE"), recorded.getValue("OPEN"))
                }
            } catch (e: Exception) {
                null
            }
            if (result == null) {
                _phase.value = CalibratePhase.FAILED
                return@launch
            }
            _result.value = result
            // Apply straight away when the check went well; otherwise let the user decide
            if (result.allRecognised) apply()
            _phase.value = CalibratePhase.RESULT
        }
    }

    fun apply() {
        val r = _result.value ?: return
        val b = base ?: return
        models.saveCalibration(username, b, r.model)
        _applied.value = true
    }

    fun cancel(onPrompt: (String?) -> Unit) {
        job?.cancel()
        onPrompt(null)
        _phase.value = CalibratePhase.INTRO
    }

    companion object {
        const val PREPARE_MS = 1500L
        private const val TICK_MS = 50L
    }
}
