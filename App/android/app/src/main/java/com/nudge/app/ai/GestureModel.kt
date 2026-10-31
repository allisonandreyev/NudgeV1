package com.nudge.app.ai

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Gesture classifier small enough to train on the phone in a moment and run on the wearable.
 *
 * Every reading is turned into a feature vector describing the last [WINDOW] samples of each
 * sensor (level, variability, and how fast it changes). A linear discriminant (LDA) then scores
 * each gesture. The firmware implements exactly the same features and scoring, so any change to
 * [Features] or [GestureModel.scores] must be mirrored in GestureModel.h.
 */
object Features {
    const val WINDOW = 10 // 200 ms at 50 Hz

    /** Feature count for a wearable with [channels] sensors: level, spread and change for each. */
    fun count(channels: Int) = channels * 3

    /**
     * [window] holds WINDOW readings, oldest first, one value per channel. Works for any
     * channel count: 1 for the MyoWare on the rev2 board, 8 for the 16-pad ADS1298 strip.
     */
    fun extract(window: List<FloatArray>): FloatArray {
        require(window.size == WINDOW)
        val channels = window[0].size
        val out = FloatArray(count(channels))
        for (c in 0 until channels) {
            var sum = 0.0
            for (s in window) sum += s[c]
            val mean = sum / WINDOW
            var sq = 0.0
            var wl = 0.0
            for (i in window.indices) {
                val d = window[i][c] - mean
                sq += d * d
                if (i > 0) wl += abs(window[i][c] - window[i - 1][c])
            }
            // log(1 + x) evens out the very different signal sizes between people and sensors
            out[c] = ln(1.0 + mean).toFloat()
            out[channels + c] = ln(1.0 + sqrt(sq / WINDOW)).toFloat()
            out[2 * channels + c] = ln(1.0 + wl / (WINDOW - 1)).toFloat()
        }
        return out
    }

    /** All full windows inside one continuous recording, stepping [step] readings at a time. */
    fun slide(recording: List<FloatArray>, step: Int = 2): List<FloatArray> {
        if (recording.size < WINDOW) return emptyList()
        return (0..recording.size - WINDOW step step).map { extract(recording.subList(it, it + WINDOW)) }
    }
}

class GestureModel(
    /** Gesture names in the order the firmware reports them. */
    val classes: List<String>,
    val mean: FloatArray,
    val scale: FloatArray,
    val weights: Array<FloatArray>,
    val bias: FloatArray,
    /**
     * Per-sensor correction applied to raw readings before anything else: gain * x + offset.
     * Identity when trained; set by a quick recalibration so the model keeps working when
     * a sensor reads stronger or weaker than on training day.
     */
    val gain: FloatArray = FloatArray(mean.size / 3) { 1f },
    val offset: FloatArray = FloatArray(mean.size / 3)
) {
    /** Number of sensor channels this model was trained for. */
    val channels: Int get() = gain.size

    /** Raw reading corrected for today's sensor placement. Never negative, like the raw signal. */
    fun adjust(raw: FloatArray): FloatArray = FloatArray(raw.size) { (gain[it] * raw[it] + offset[it]).coerceAtLeast(0f) }

    /** Classifies WINDOW raw readings, oldest first. */
    fun predictWindow(raw: List<FloatArray>): Pair<Int, Float> = predict(Features.extract(raw.map(::adjust)))

    fun withCalibration(gain: FloatArray, offset: FloatArray) = GestureModel(classes, mean, scale, weights, bias, gain, offset)

    fun scores(features: FloatArray): FloatArray {
        val z = FloatArray(features.size) { (features[it] - mean[it]) / scale[it] }
        return FloatArray(classes.size) { k ->
            var s = bias[k]
            for (i in z.indices) s += weights[k][i] * z[i]
            s
        }
    }

    /** Returns the most likely gesture index and its probability. */
    fun predict(features: FloatArray): Pair<Int, Float> {
        val s = scores(features)
        val max = s.max()
        val exps = s.map { exp((it - max).toDouble()) }
        val total = exps.sum()
        val best = s.indices.maxBy { s[it] }
        return best to (exps[best] / total).toFloat()
    }

    /** Flat parameter list in the order the firmware expects: gain, offset, mean, scale, weights by class, bias. */
    fun toParameters(): FloatArray =
        gain + offset + mean + scale + weights.fold(FloatArray(0)) { acc, row -> acc + row } + bias

    companion object {
        /** Gesture order used by the firmware's actuation logic: 0/2 close the hand, 1/3 open it. */
        val FIRMWARE_CLASSES = listOf("CLOSE", "OPEN", "PINCH", "REST")

        /**
         * Gestures for a single sensor, which can only tell a squeeze from relaxing.
         * Relaxing opens the hand (index 1), so the firmware's actuation rule still holds.
         */
        val SQUEEZE_CLASSES = listOf("CLOSE", "REST")

        /** Gestures a wearable with [channels] sensors is trained on, in firmware order. */
        fun classesFor(channels: Int) = if (channels == 1) SQUEEZE_CLASSES else FIRMWARE_CLASSES
        private fun coreCount(channels: Int, classes: Int) = Features.count(channels) * 2 + classes * (Features.count(channels) + 1)

        /** Parameters sent to the wearable: 16 for one MyoWare (2 gestures), 164 for the 8-channel strip. */
        fun parameterCount(channels: Int, classes: Int = FIRMWARE_CLASSES.size) = channels * 2 + coreCount(channels, classes)

        fun fromParameters(params: FloatArray, classes: List<String> = FIRMWARE_CLASSES): GestureModel {
            val k = classes.size
            // Work out the channel count from the size; models saved before recalibration
            // existed have no gain/offset, so treat them as uncorrected
            val withGain = (1..16).firstOrNull { parameterCount(it, k) == params.size }
            val legacy = (1..16).firstOrNull { coreCount(it, k) == params.size }
            val ch = withGain ?: legacy ?: throw IllegalArgumentException("Unexpected parameter count ${params.size}")
            val n = Features.count(ch)
            val p = if (withGain == null) FloatArray(ch) { 1f } + FloatArray(ch) + params else params
            val base = ch * 2
            val weights = Array(k) { c -> p.copyOfRange(base + 2 * n + c * n, base + 2 * n + (c + 1) * n) }
            return GestureModel(
                classes,
                mean = p.copyOfRange(base, base + n),
                scale = p.copyOfRange(base + n, base + 2 * n),
                weights = weights,
                bias = p.copyOfRange(p.size - classes.size, p.size),
                gain = p.copyOfRange(0, ch),
                offset = p.copyOfRange(ch, base)
            )
        }
    }
}

/** One continuous hold of a gesture during training. */
data class Recording(val round: Int, val label: String, val readings: List<FloatArray>)

/**
 * How strong each sensor read on training day: typical level when relaxed, and the
 * 90th percentile while making a fist or opening the hand. Recalibration maps today's
 * readings back onto these.
 */
data class ChannelReference(val rest: FloatArray, val active: FloatArray)

data class TrainingResult(
    val model: GestureModel,
    val reference: ChannelReference,
    /** Accuracy on rounds the model didn't see, or null if there weren't enough rounds to check. */
    val accuracy: Float?,
    val perGesture: Map<String, Float>,
    val windows: Int
)

object GestureTrainer {
    private const val SHRINKAGE = 0.1

    fun train(recordings: List<Recording>, classes: List<String> = GestureModel.FIRMWARE_CLASSES): TrainingResult {
        val model = fit(recordings, classes)
        val windows = recordings.sumOf { Features.slide(it.readings).size }
        val reference = reference(recordings)

        // Check on each round in turn after training on the others
        val rounds = recordings.map { it.round }.distinct()
        if (rounds.size < 2) return TrainingResult(model, reference, null, emptyMap(), windows)
        val correct = mutableMapOf<String, Int>()
        val total = mutableMapOf<String, Int>()
        for (held in rounds) {
            val trainSet = recordings.filter { it.round != held }
            if (classes.any { c -> trainSet.none { it.label == c } }) continue
            val m = fit(trainSet, classes)
            for (r in recordings.filter { it.round == held }) {
                for (f in Features.slide(r.readings)) {
                    total[r.label] = (total[r.label] ?: 0) + 1
                    if (classes[m.predict(f).first] == r.label) correct[r.label] = (correct[r.label] ?: 0) + 1
                }
            }
        }
        val all = total.values.sum()
        val accuracy = if (all == 0) null else correct.values.sum().toFloat() / all
        val perGesture = total.mapValues { (g, n) -> (correct[g] ?: 0).toFloat() / n }
        return TrainingResult(model, reference, accuracy, perGesture, windows)
    }

    fun reference(recordings: List<Recording>): ChannelReference {
        val rest = recordings.filter { it.label == "REST" }.flatMap { it.readings }
        val active = recordings.filter { it.label == "CLOSE" || it.label == "OPEN" }.flatMap { it.readings }
        val channels = recordings.first().readings.first().size
        return ChannelReference(
            rest = FloatArray(channels) { c -> percentile(rest.map { it[c] }, 0.5f) },
            active = FloatArray(channels) { c -> percentile(active.map { it[c] }, 0.9f) }
        )
    }

    fun percentile(values: List<Float>, p: Float): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        return sorted[((sorted.size - 1) * p).toInt()]
    }

    fun fit(recordings: List<Recording>, classes: List<String>): GestureModel {
        val byClass = classes.map { c -> recordings.filter { it.label == c }.flatMap { Features.slide(it.readings) } }
        require(byClass.all { it.size >= 2 }) { "Every gesture needs recorded data" }
        val d = byClass[0][0].size
        val all = byClass.flatten()

        // Standardise each feature so no single sensor dominates
        val mean = FloatArray(d) { i -> all.map { it[i].toDouble() }.average().toFloat() }
        val scale = FloatArray(d) { i ->
            val v = all.map { (it[i] - mean[i]).toDouble().let { x -> x * x } }.average()
            sqrt(v).toFloat().coerceAtLeast(1e-3f)
        }
        val z = byClass.map { rows -> rows.map { f -> DoubleArray(d) { ((f[it] - mean[it]) / scale[it]).toDouble() } } }

        // Class centres and the spread shared by all classes
        val centres = z.map { rows -> DoubleArray(d) { i -> rows.sumOf { it[i] } / rows.size } }
        val cov = Array(d) { DoubleArray(d) }
        var n = 0
        z.forEachIndexed { k, rows ->
            for (r in rows) {
                for (i in 0 until d) for (j in 0 until d) cov[i][j] += (r[i] - centres[k][i]) * (r[j] - centres[k][j])
            }
            n += rows.size
        }
        val dof = (n - classes.size).coerceAtLeast(1).toDouble()
        for (i in 0 until d) for (j in 0 until d) cov[i][j] /= dof

        // Shrink toward a diagonal so short recordings still give a stable inverse
        val avgVar = (0 until d).sumOf { cov[it][it] } / d
        for (i in 0 until d) for (j in 0 until d) {
            cov[i][j] = (1 - SHRINKAGE) * cov[i][j] + if (i == j) SHRINKAGE * avgVar else 0.0
        }
        val inv = invert(cov)

        val prior = ln(1.0 / classes.size)
        val weights = Array(classes.size) { k ->
            FloatArray(d) { i -> (0 until d).sumOf { j -> inv[i][j] * centres[k][j] }.toFloat() }
        }
        val bias = FloatArray(classes.size) { k ->
            (-0.5 * (0 until d).sumOf { i -> weights[k][i] * centres[k][i] } + prior).toFloat()
        }
        return GestureModel(classes, mean, scale, weights, bias)
    }

    /** Gauss-Jordan inverse with partial pivoting. */
    internal fun invert(m: Array<DoubleArray>): Array<DoubleArray> {
        val n = m.size
        val a = Array(n) { i -> DoubleArray(2 * n) { j -> if (j < n) m[i][j] else if (j - n == i) 1.0 else 0.0 } }
        for (col in 0 until n) {
            val pivot = (col until n).maxBy { abs(a[it][col]) }
            val tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp
            val p = a[col][col]
            require(abs(p) > 1e-12) { "Matrix is singular" }
            for (j in 0 until 2 * n) a[col][j] /= p
            for (r in 0 until n) if (r != col) {
                val f = a[r][col]
                if (f != 0.0) for (j in 0 until 2 * n) a[r][j] -= f * a[col][j]
            }
        }
        return Array(n) { i -> a[i].copyOfRange(n, 2 * n) }
    }
}
