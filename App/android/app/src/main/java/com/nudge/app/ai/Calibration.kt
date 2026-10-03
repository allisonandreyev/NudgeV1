package com.nudge.app.ai

import kotlin.math.abs

/** How one sensor compares with training day. */
enum class SensorState { Normal, Weaker, Stronger, NotResponding }

data class CalibrationResult(
    /** The saved model with today's correction applied. */
    val model: GestureModel,
    val sensors: List<SensorState>,
    /** How much each sensor's signal changed: 1.0 = same as training day, 0.5 = half as strong. */
    val strength: FloatArray,
    /** Whether the corrected model recognised each gesture made during the check. */
    val recognised: Map<String, Boolean>
) {
    val allRecognised get() = recognised.values.all { it }
}

/**
 * Quick recalibration: the user relaxes, makes a fist and opens their hand. Each sensor's
 * relaxed level and active level are compared with training day and a straight-line correction
 * maps today's readings back onto training-day readings. The trained model itself is unchanged.
 *
 * This fixes sensors that read stronger or weaker than before (contact, sweat, a small shift).
 * It can't fix a sensor that moved onto a different muscle; that needs a retrain.
 */
object Calibration {
    private const val MIN_GAIN = 0.25f
    private const val MAX_GAIN = 4f

    fun compute(
        base: GestureModel,
        reference: ChannelReference,
        rest: List<FloatArray>,
        fist: List<FloatArray>,
        open: List<FloatArray>
    ): CalibrationResult {
        require(rest.size >= Features.WINDOW && fist.size >= Features.WINDOW && open.size >= Features.WINDOW) {
            "Not enough readings"
        }
        val active = fist + open
        val gain = FloatArray(Features.CHANNELS)
        val offset = FloatArray(Features.CHANNELS)
        val states = mutableListOf<SensorState>()
        val strength = FloatArray(Features.CHANNELS)

        for (c in 0 until Features.CHANNELS) {
            val todayRest = GestureTrainer.percentile(rest.map { it[c] }, 0.5f)
            val todayActive = GestureTrainer.percentile(active.map { it[c] }, 0.9f)
            val refRange = (reference.active[c] - reference.rest[c]).coerceAtLeast(1f)
            val todayRange = todayActive - todayRest

            val raw = if (todayRange <= 0f) MAX_GAIN else refRange / todayRange
            gain[c] = raw.coerceIn(MIN_GAIN, MAX_GAIN)
            offset[c] = reference.rest[c] - gain[c] * todayRest
            strength[c] = 1f / raw.coerceAtLeast(1e-3f)

            states += when {
                raw >= MAX_GAIN -> SensorState.NotResponding
                abs(1f - strength[c]) < 0.2f -> SensorState.Normal
                strength[c] < 1f -> SensorState.Weaker
                else -> SensorState.Stronger
            }
        }

        val model = base.withCalibration(gain, offset)
        val recognised = mapOf("REST" to rest, "CLOSE" to fist, "OPEN" to open).mapValues { (gesture, readings) ->
            recognisedMostly(model, gesture, readings)
        }
        return CalibrationResult(model, states, strength, recognised)
    }

    /** True if most windows of [readings] are classified as [gesture]. */
    private fun recognisedMostly(model: GestureModel, gesture: String, readings: List<FloatArray>): Boolean {
        val windows = (0..readings.size - Features.WINDOW step 2).map { readings.subList(it, it + Features.WINDOW) }
        val hits = windows.count { model.classes[model.predictWindow(it).first] == gesture }
        return hits * 2 > windows.size
    }
}
