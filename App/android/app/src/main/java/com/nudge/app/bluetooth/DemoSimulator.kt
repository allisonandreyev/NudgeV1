package com.nudge.app.bluetooth

import kotlin.math.sin
import kotlin.random.Random

/**
 * Produces believable EMG envelope readings so the app can be tried without hardware.
 * Each gesture activates the three sensors in a different pattern, with smooth
 * transitions, noise and a slow tremor.
 */
class DemoSimulator(
    private val random: Random = Random.Default,
    /**
     * How the sensors sit today compared with an ideal placement: each sensor's signal is
     * scaled by [placementGain] and shifted by [placementOffset], like after taking the
     * wearable off and putting it back on.
     */
    private val placementGain: FloatArray = floatArrayOf(1f, 1f, 1f),
    private val placementOffset: FloatArray = floatArrayOf(0f, 0f, 0f)
) {

    private val levels = floatArrayOf(150f, 150f, 150f)
    private var tick = 0L

    /**
     * @param target gesture to act out, or null to cycle through gestures on its own
     */
    fun next(target: String?): EmgSample {
        tick++
        val gesture = target ?: autoGesture()
        val pattern = PATTERNS.getValue(gesture)

        val values = FloatArray(3) { i ->
            // Move ~15% of the way to the target each sample: settles in about a third of a second
            levels[i] += (pattern[i] - levels[i]) * 0.15f
            val tremor = 1f + 0.08f * sin(tick * 0.6f + i * 2f)
            val noise = random.nextFloat() * 0.2f + 0.9f
            (placementGain[i] * levels[i] * tremor * noise + placementOffset[i]).coerceIn(0f, 4095f)
        }
        val settled = values.indices.all { kotlin.math.abs(levels[it] - pattern[it]) < pattern[it] * 0.2f }
        return EmgSample(System.currentTimeMillis(), values, if (settled) gesture else "UNKNOWN")
    }

    private fun autoGesture(): String {
        val step = (tick / (RATE_HZ * 2.5)).toInt()
        return CYCLE[step % CYCLE.size]
    }

    companion object {
        const val RATE_HZ = 50

        /** A random but plausible re-fit of the wearable, different each time. */
        fun randomPlacement(random: Random = Random.Default) = DemoSimulator(
            random,
            placementGain = FloatArray(3) { 0.6f + random.nextFloat() * 0.9f },
            placementOffset = FloatArray(3) { random.nextFloat() * 120f - 40f }
        )

        private val CYCLE = listOf("REST", "OPEN", "REST", "CLOSE", "REST", "PINCH")

        private val PATTERNS = mapOf(
            "REST" to floatArrayOf(160f, 140f, 170f),
            "OPEN" to floatArrayOf(850f, 420f, 1500f),
            "CLOSE" to floatArrayOf(1650f, 1250f, 520f),
            "PINCH" to floatArrayOf(700f, 1550f, 620f)
        )
    }
}
