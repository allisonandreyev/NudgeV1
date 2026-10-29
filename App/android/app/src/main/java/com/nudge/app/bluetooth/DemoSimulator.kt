package com.nudge.app.bluetooth

import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Produces believable EMG envelopes so the app can be tried without hardware. It mimics the
 * 16-pad ADS1298 strip: 8 channels around the wrist, values in microvolts, 50 frames a second.
 * Each gesture activates the ring of channels in a different pattern, with smooth transitions,
 * noise and a slow tremor.
 */
class DemoSimulator(
    private val random: Random = Random.Default,
    /**
     * How the strip sits today compared with an ideal placement: each channel's signal is
     * scaled by [placementGain] and shifted by [placementOffset], like after taking the
     * wearable off and putting it back on.
     */
    private val placementGain: FloatArray = FloatArray(CHANNELS) { 1f },
    private val placementOffset: FloatArray = FloatArray(CHANNELS)
) {

    private val levels = FloatArray(CHANNELS) { 4f }
    private var tick = 0L

    /**
     * @param target gesture to act out, or null to cycle through gestures on its own
     */
    fun next(target: String?): EmgSample {
        tick++
        val gesture = target ?: autoGesture()
        val pattern = PATTERNS.getValue(gesture)

        val values = FloatArray(CHANNELS) { i ->
            // Move ~15% of the way to the target each sample: settles in about a third of a second
            levels[i] += (pattern[i] - levels[i]) * 0.15f
            val tremor = 1f + 0.08f * sin(tick * 0.6f + i * 2f)
            val noise = random.nextFloat() * 0.2f + 0.9f
            (placementGain[i] * levels[i] * tremor * noise + placementOffset[i]).coerceAtLeast(0f)
        }
        val settled = values.indices.all { abs(levels[it] - pattern[it]) < pattern[it] * 0.2f }
        return EmgSample(System.currentTimeMillis(), values, if (settled) gesture else "UNKNOWN")
    }

    private fun autoGesture(): String {
        val step = (tick / (RATE_HZ * 2.5)).toInt()
        return CYCLE[step % CYCLE.size]
    }

    companion object {
        const val RATE_HZ = 50
        const val CHANNELS = 8

        /** A random but plausible re-fit of the wearable, different each time. */
        fun randomPlacement(random: Random = Random.Default) = DemoSimulator(
            random,
            placementGain = FloatArray(CHANNELS) { 0.6f + random.nextFloat() * 0.9f },
            placementOffset = FloatArray(CHANNELS) { random.nextFloat() * 4f - 1f }
        )

        private val CYCLE = listOf("REST", "OPEN", "REST", "CLOSE", "REST", "PINCH")

        // Envelope in microvolts per channel. Channels 1-4 roughly over the flexors, 5-8 the extensors.
        private val PATTERNS = mapOf(
            "REST" to floatArrayOf(4f, 4f, 5f, 4f, 4f, 5f, 4f, 4f),
            "OPEN" to floatArrayOf(8f, 10f, 12f, 9f, 60f, 110f, 90f, 40f),
            "CLOSE" to floatArrayOf(90f, 120f, 100f, 60f, 20f, 30f, 25f, 15f),
            "PINCH" to floatArrayOf(30f, 60f, 25f, 10f, 10f, 40f, 70f, 20f)
        )
    }
}
