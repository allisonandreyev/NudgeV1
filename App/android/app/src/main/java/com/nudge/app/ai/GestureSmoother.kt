package com.nudge.app.ai

/**
 * Same confidence and stability rules the firmware applies before it trusts a gesture,
 * so the demo device behaves like the real one.
 */
class GestureSmoother(private val confidence: Float = 0.8f, private val stability: Int = 3) {
    private var last = -1
    private var count = 0
    var confirmed = -1
        private set

    fun update(gesture: Int, probability: Float): Int {
        val raw = if (probability < confidence) -1 else gesture
        if (raw != last) {
            last = raw
            count = 1
        } else if (++count >= stability) {
            confirmed = raw
        }
        return confirmed
    }
}
