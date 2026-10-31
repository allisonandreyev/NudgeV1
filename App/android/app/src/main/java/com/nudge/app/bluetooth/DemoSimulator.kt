package com.nudge.app.bluetooth

import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Produces believable EMG envelopes so the app can be tried without hardware, 50 frames a second,
 * with smooth transitions, noise and a slow tremor. The channel count is the size of
 * [placementGain]:
 *  - 1 mimics the rev2 board's MyoWare 2.0 on the forearm flexors (millivolts). A squeeze is
 *    clear; opening the hand and pinching show only partly.
 *  - 8 mimics the 16-pad ADS1298 strip (microvolts), where each gesture activates the ring of
 *    channels in a different pattern.
 */
class DemoSimulator(
    private val random: Random = Random.Default,
    /**
     * How the strip sits today compared with an ideal placement: each channel's signal is
     * scaled by [placementGain] and shifted by [placementOffset], like after taking the
     * wearable off and putting it back on.
     */
    private val placementGain: FloatArray = FloatArray(CHANNELS) { 1f },
    private val placementOffset: FloatArray = FloatArray(placementGain.size)
) {
    private val channels = placementGain.size
    private val patterns = when (channels) {
        1 -> MYOWARE_PATTERNS
        STRIP_CHANNELS -> STRIP_PATTERNS
        else -> throw IllegalArgumentException("No demo patterns for $channels channels")
    }
    private val levels = FloatArray(channels) { patterns.getValue("REST")[it] }
    private var tick = 0L

    /**
     * @param target gesture to act out, or null to cycle through gestures on its own
     */
    fun next(target: String?): EmgSample {
        tick++
        val gesture = target ?: autoGesture()
        val pattern = patterns.getValue(gesture)

        val values = FloatArray(channels) { i ->
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
        /** The demo device: the rev2 board's single MyoWare. */
        const val CHANNELS = 1
        const val STRIP_CHANNELS = 8

        /** The 16-pad strip at an ideal placement. */
        fun strip(random: Random = Random.Default) = DemoSimulator(random, FloatArray(STRIP_CHANNELS) { 1f })

        /** A random but plausible re-fit of the wearable, different each time. */
        fun randomPlacement(random: Random = Random.Default, channels: Int = CHANNELS): DemoSimulator {
            // Offsets in the sensor's own units: millivolts for the MyoWare, microvolts for the strip
            val offsetRange = if (channels == 1) 40f else 4f
            return DemoSimulator(
                random,
                placementGain = FloatArray(channels) { 0.6f + random.nextFloat() * 0.9f },
                placementOffset = FloatArray(channels) { (random.nextFloat() - 0.25f) * offsetRange }
            )
        }

        private val CYCLE = listOf("REST", "OPEN", "REST", "CLOSE", "REST", "PINCH")

        // MyoWare ENV output in millivolts, sensor over the forearm flexors
        private val MYOWARE_PATTERNS = mapOf(
            "REST" to floatArrayOf(40f),
            "OPEN" to floatArrayOf(260f),
            "CLOSE" to floatArrayOf(1100f),
            "PINCH" to floatArrayOf(520f)
        )

        // Strip envelope in microvolts per channel. Channels 1-4 roughly over the flexors, 5-8 the extensors.
        private val STRIP_PATTERNS = mapOf(
            "REST" to floatArrayOf(4f, 4f, 5f, 4f, 4f, 5f, 4f, 4f),
            "OPEN" to floatArrayOf(8f, 10f, 12f, 9f, 60f, 110f, 90f, 40f),
            "CLOSE" to floatArrayOf(90f, 120f, 100f, 60f, 20f, 30f, 25f, 15f),
            "PINCH" to floatArrayOf(30f, 60f, 25f, 10f, 10f, 40f, 70f, 20f)
        )
    }
}
