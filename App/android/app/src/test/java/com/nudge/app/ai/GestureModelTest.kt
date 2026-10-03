package com.nudge.app.ai

import com.nudge.app.bluetooth.DemoSimulator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GestureModelTest {

    /** Records [rounds] holds of each gesture from the demo simulator, like the Train AI screen does. */
    private fun simulatedRecordings(rounds: Int, seed: Int = 1): List<Recording> {
        val sim = DemoSimulator(Random(seed))
        val out = mutableListOf<Recording>()
        for (r in 1..rounds) {
            for (g in listOf("REST", "OPEN", "CLOSE", "PINCH")) {
                repeat(75) { sim.next(g) } // 1.5 s switch-over, not recorded
                out += Recording(r, g, List(150) { sim.next(g).values }) // 3 s hold
            }
        }
        return out
    }

    @Test
    fun featuresOfAFlatSignalHaveNoSpreadOrMovement() {
        val f = Features.extract(List(Features.WINDOW) { floatArrayOf(100f, 200f, 300f) })
        assertEquals(kotlin.math.ln(101.0).toFloat(), f[0], 1e-5f)
        for (i in 3 until Features.COUNT) assertEquals(0f, f[i], 1e-6f)
    }

    @Test
    fun inverseTimesOriginalIsIdentity() {
        val m = arrayOf(doubleArrayOf(4.0, 1.0, 0.5), doubleArrayOf(1.0, 3.0, 0.2), doubleArrayOf(0.5, 0.2, 2.0))
        val inv = GestureTrainer.invert(m)
        for (i in 0..2) for (j in 0..2) {
            val v = (0..2).sumOf { m[i][it] * inv[it][j] }
            assertEquals(if (i == j) 1.0 else 0.0, v, 1e-9)
        }
    }

    @Test
    fun learnsDistinctGesturesAccurately() {
        val result = GestureTrainer.train(simulatedRecordings(rounds = 5))
        assertNotNull(result.accuracy)
        assertTrue("accuracy was ${result.accuracy}", result.accuracy!! > 0.95f)
        assertEquals(4, result.perGesture.size)
    }

    @Test
    fun classifiesFreshDataFromTheSameUser() {
        val model = GestureTrainer.train(simulatedRecordings(rounds = 5, seed = 1)).model
        val sim = DemoSimulator(Random(99))
        for (g in listOf("REST", "OPEN", "CLOSE", "PINCH")) {
            repeat(100) { sim.next(g) }
            val window = List(Features.WINDOW) { sim.next(g).values }
            val (index, prob) = model.predict(Features.extract(window))
            assertEquals(g, model.classes[index])
            assertTrue(prob > 0.8f)
        }
    }

    @Test
    fun parametersRoundTripInFirmwareOrder() {
        val model = GestureTrainer.train(simulatedRecordings(rounds = 3)).model
        val params = model.toParameters()
        assertEquals(GestureModel.PARAMETER_COUNT, params.size)
        val copy = GestureModel.fromParameters(params)
        val f = Features.extract(simulatedRecordings(1)[0].readings.take(Features.WINDOW))
        assertArrayEquals(model.scores(f), copy.scores(f), 1e-5f)
    }

    @Test
    fun oneRoundTrainsButCannotEstimateAccuracy() {
        val result = GestureTrainer.train(simulatedRecordings(rounds = 1))
        assertEquals(null, result.accuracy)
    }
}

class ModelProtocolTest {
    @Test
    fun linesFitInOneBluetoothWrite() {
        val sim = DemoSimulator(Random(3))
        val recs = (1..2).flatMap { r -> listOf("REST", "OPEN", "CLOSE", "PINCH").map { g -> Recording(r, g, List(60) { sim.next(g).values }) } }
        val cmds = ModelProtocol.uploadCommands(GestureTrainer.train(recs).model, id = 4242)
        assertTrue(cmds.first().startsWith("mdl_begin 4242 ${GestureModel.PARAMETER_COUNT}"))
        assertTrue(cmds.last().startsWith("mdl_end "))
        cmds.forEach { assertTrue("${it.length} bytes: $it", it.toByteArray().size <= 180) }
    }

    /**
     * Writes a fixture for the firmware cross-check in Software/XIAO_C6_Firmware/test:
     * the upload commands, a stream of readings, and what the app's model predicts for each window.
     */
    @Test
    fun writeFirmwareFixture() {
        val sim = DemoSimulator(Random(7))
        val recs = (1..4).flatMap { r -> listOf("REST", "OPEN", "CLOSE", "PINCH").map { g -> Recording(r, g, List(120) { sim.next(g).values }) } }
        // Include a recalibration so the firmware's per-sensor correction is exercised too
        val model = GestureTrainer.train(recs).model
            .withCalibration(floatArrayOf(0.8f, 1.3f, 1.1f), floatArrayOf(10f, -5f, 20f))
        val out = StringBuilder()
        ModelProtocol.uploadCommands(model, id = 777).forEach { out.append("CMD ").append(it).append('\n') }
        val readings = mutableListOf<FloatArray>()
        for (g in listOf("REST", "OPEN", "CLOSE", "PINCH", "OPEN")) repeat(40) { readings += sim.next(g).values }
        readings.forEachIndexed { i, v ->
            out.append("READ ${v[0]} ${v[1]} ${v[2]}")
            if (i >= Features.WINDOW - 1) {
                val (k, p) = model.predictWindow(readings.subList(i - Features.WINDOW + 1, i + 1))
                out.append(" EXPECT $k $p")
            }
            out.append('\n')
        }
        val dir = java.io.File(System.getProperty("user.dir"), "build/firmware-fixture").apply { mkdirs() }
        java.io.File(dir, "fixture.txt").writeText(out.toString())
    }
}

class CalibrationTest {
    private val gestures = listOf("REST", "OPEN", "CLOSE", "PINCH")

    private fun record(sim: DemoSimulator, rounds: Int): List<Recording> = (1..rounds).flatMap { r ->
        gestures.map { g ->
            repeat(75) { sim.next(g) }
            Recording(r, g, List(150) { sim.next(g).values })
        }
    }

    private fun accuracy(model: GestureModel, sim: DemoSimulator): Float {
        var correct = 0
        var total = 0
        for (round in 1..3) for (g in gestures) {
            repeat(75) { sim.next(g) }
            val readings = List(100) { sim.next(g).values }
            for (i in 0..readings.size - Features.WINDOW step 5) {
                total++
                if (model.classes[model.predictWindow(readings.subList(i, i + Features.WINDOW)).first] == g) correct++
            }
        }
        return correct.toFloat() / total
    }

    /** The quick check the app runs: 4 s relaxed, 3 s fist, 3 s open, skipping the switch-over. */
    private fun quickCheck(model: GestureModel, reference: ChannelReference, sim: DemoSimulator): CalibrationResult {
        repeat(50) { sim.next("REST") }
        val rest = List(200) { sim.next("REST").values }
        repeat(50) { sim.next("CLOSE") }
        val fist = List(150) { sim.next("CLOSE").values }
        repeat(50) { sim.next("OPEN") }
        val open = List(150) { sim.next("OPEN").values }
        return Calibration.compute(model, reference, rest, fist, open)
    }

    private val trained by lazy { GestureTrainer.train(record(DemoSimulator(Random(1)), rounds = 5)) }

    // Sensor 1 much weaker, sensor 2 stronger, sensor 3 noisier baseline
    private fun shifted(seed: Int) = DemoSimulator(Random(seed), floatArrayOf(0.45f, 1.6f, 0.8f), floatArrayOf(60f, -20f, 90f))

    @Test
    fun movedSensorsHurtAccuracyAndRecalibrationRestoresIt() {
        val before = accuracy(trained.model, shifted(10))
        val result = quickCheck(trained.model, trained.reference, shifted(11))
        val after = accuracy(result.model, shifted(12))
        println("accuracy after re-fit: uncorrected ${"%.2f".format(before)}, recalibrated ${"%.2f".format(after)}")
        assertTrue("re-fit should hurt the uncorrected model, got $before", before < 0.8f)
        assertTrue("recalibrated accuracy was $after", after > 0.9f)
        assertTrue(result.allRecognised)
        assertEquals(SensorState.Weaker, result.sensors[0])
        assertEquals(SensorState.Stronger, result.sensors[1])
    }

    @Test
    fun samePlacementNeedsNoCorrection() {
        val result = quickCheck(trained.model, trained.reference, DemoSimulator(Random(5)))
        result.model.gain.forEach { assertEquals(1f, it, 0.2f) }
        assertTrue(result.sensors.all { it == SensorState.Normal })
        assertTrue(accuracy(result.model, DemoSimulator(Random(6))) > 0.9f)
    }

    @Test
    fun deadSensorIsReported() {
        val dead = DemoSimulator(Random(7), floatArrayOf(1f, 0f, 1f), floatArrayOf(0f, 30f, 0f))
        val result = quickCheck(trained.model, trained.reference, dead)
        assertEquals(SensorState.NotResponding, result.sensors[1])
    }

    @Test
    fun calibrationTravelsWithTheModel() {
        val result = quickCheck(trained.model, trained.reference, shifted(11))
        val copy = GestureModel.fromParameters(result.model.toParameters())
        assertArrayEquals(result.model.gain, copy.gain, 0f)
        assertArrayEquals(result.model.offset, copy.offset, 0f)
    }
}
