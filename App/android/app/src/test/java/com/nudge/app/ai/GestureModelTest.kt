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
        val sim = DemoSimulator.strip(Random(seed))
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
        for (i in 3 until Features.count(3)) assertEquals(0f, f[i], 1e-6f)
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
        val sim = DemoSimulator.strip(Random(99))
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
        assertEquals(GestureModel.parameterCount(8), params.size)
        assertEquals(164, params.size)
        val copy = GestureModel.fromParameters(params)
        val f = Features.extract(simulatedRecordings(1)[0].readings.take(Features.WINDOW))
        assertEquals(Features.count(8), f.size)
        assertArrayEquals(model.scores(f), copy.scores(f), 1e-5f)
    }

    @Test
    fun oneRoundTrainsButCannotEstimateAccuracy() {
        val result = GestureTrainer.train(simulatedRecordings(rounds = 1))
        assertEquals(null, result.accuracy)
    }
}

class ChannelCountTest {
    @Test
    fun legacyThreeSensorModelsStillLoad() {
        // 58 = 3-channel model saved before recalibration existed, 64 = 3-channel with recalibration
        assertEquals(3, GestureModel.fromParameters(FloatArray(58) { 1f }).channels)
        assertEquals(3, GestureModel.fromParameters(FloatArray(64) { 1f }).channels)
        assertEquals(8, GestureModel.fromParameters(FloatArray(164) { 1f }).channels)
    }
}

class ModelProtocolTest {
    @Test
    fun linesFitInOneBluetoothWrite() {
        val sim = DemoSimulator.strip(Random(3))
        val recs = (1..2).flatMap { r -> listOf("REST", "OPEN", "CLOSE", "PINCH").map { g -> Recording(r, g, List(60) { sim.next(g).values }) } }
        val cmds = ModelProtocol.uploadCommands(GestureTrainer.train(recs).model, id = 4242)
        assertTrue(cmds.first().startsWith("mdl_begin 4242 ${GestureModel.parameterCount(8)}"))
        assertTrue(cmds.last().startsWith("mdl_end "))
        cmds.forEach { assertTrue("${it.length} bytes: $it", it.toByteArray().size <= 180) }
    }

    /**
     * Writes a fixture for the firmware cross-check in Software/Nudge_Firmware/test:
     * the upload commands, a stream of readings, and what the app's model predicts for each window.
     * Matches the rev2 firmware: one MyoWare, squeeze and relax.
     */
    @Test
    fun writeFirmwareFixture() {
        val sim = DemoSimulator(Random(7))
        val classes = GestureModel.classesFor(1)
        val recs = (1..4).flatMap { r -> listOf("REST", "CLOSE").map { g -> Recording(r, g, List(120) { sim.next(g).values }) } }
        // Include a recalibration so the firmware's per-sensor correction is exercised too
        val model = GestureTrainer.train(recs, classes).model.withCalibration(floatArrayOf(0.8f), floatArrayOf(15f))
        val out = StringBuilder()
        ModelProtocol.uploadCommands(model, id = 777).forEach { out.append("CMD ").append(it).append('\n') }
        val readings = mutableListOf<FloatArray>()
        // Partial efforts too, so some windows sit near the boundary
        for (g in listOf("REST", "CLOSE", "PINCH", "REST", "OPEN", "CLOSE")) repeat(40) { readings += sim.next(g).values }
        readings.forEachIndexed { i, v ->
            out.append("READ ").append(v.joinToString(" "))
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

    private val trained by lazy { GestureTrainer.train(record(DemoSimulator.strip(Random(1)), rounds = 5)) }

    // Strip re-fitted badly: flexor-side channels read ~30%, extensor-side ~240%, so a fist
    // starts to look like an open hand. Milder shifts don't confuse 8 channels at all.
    private fun shifted(seed: Int) = DemoSimulator(
        Random(seed),
        floatArrayOf(0.3f, 0.3f, 0.35f, 0.3f, 2.5f, 2.2f, 2.6f, 2.4f),
        floatArrayOf(3f, -1f, 4f, 2f, 0f, 3f, -1f, 2f)
    )

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
        assertEquals(SensorState.Stronger, result.sensors[4])
    }

    @Test
    fun samePlacementNeedsNoCorrection() {
        val result = quickCheck(trained.model, trained.reference, DemoSimulator.strip(Random(5)))
        result.model.gain.forEach { assertEquals(1f, it, 0.2f) }
        assertTrue(result.sensors.all { it == SensorState.Normal })
        assertTrue(accuracy(result.model, DemoSimulator.strip(Random(6))) > 0.9f)
    }

    @Test
    fun deadSensorIsReported() {
        val dead = DemoSimulator(Random(7), FloatArray(8) { if (it == 1) 0f else 1f }, FloatArray(8) { if (it == 1) 3f else 0f })
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

/** The rev2 board: one MyoWare sensor, trained on squeeze and relax only. */
class SingleSensorTest {
    private val classes = GestureModel.classesFor(1)

    private fun record(sim: DemoSimulator, rounds: Int): List<Recording> = (1..rounds).flatMap { r ->
        listOf("REST", "CLOSE").map { g ->
            repeat(75) { sim.next(g) }
            Recording(r, g, List(150) { sim.next(g).values })
        }
    }

    private fun accuracy(model: GestureModel, sim: DemoSimulator): Float {
        var correct = 0
        var total = 0
        for (round in 1..3) for (g in listOf("REST", "CLOSE")) {
            repeat(75) { sim.next(g) }
            val readings = List(100) { sim.next(g).values }
            for (i in 0..readings.size - Features.WINDOW step 5) {
                total++
                if (model.classes[model.predictWindow(readings.subList(i, i + Features.WINDOW)).first] == g) correct++
            }
        }
        return correct.toFloat() / total
    }

    private val trained by lazy { GestureTrainer.train(record(DemoSimulator(Random(1)), rounds = 5), classes) }

    @Test
    fun learnsSqueezeAndRelax() {
        assertEquals(listOf("CLOSE", "REST"), classes)
        assertTrue("accuracy was ${trained.accuracy}", trained.accuracy!! > 0.95f)
        assertEquals(16, trained.model.toParameters().size)
        assertEquals(GestureModel.parameterCount(1, 2), trained.model.toParameters().size)
        val copy = GestureModel.fromParameters(trained.model.toParameters(), classes)
        assertEquals(1, copy.channels)
    }

    @Test
    fun relaxingOpensTheHand() {
        // The firmware opens the hand for index 1 and 3, closes it for 0 and 2
        assertEquals(1, classes.indexOf("REST"))
        assertEquals("REST", com.nudge.app.bluetooth.gestureFromIndex(1, classes))
        assertEquals("UNKNOWN", com.nudge.app.bluetooth.gestureFromIndex(2, classes))
    }

    @Test
    fun quickCheckWithoutOpenRestoresAWeakerSensor() {
        // Sensor re-fitted with poorer contact: 40% of the signal
        fun weaker(seed: Int) = DemoSimulator(Random(seed), floatArrayOf(0.4f), floatArrayOf(10f))
        val sim = weaker(11)
        repeat(50) { sim.next("REST") }
        val rest = List(200) { sim.next("REST").values }
        repeat(50) { sim.next("CLOSE") }
        val fist = List(150) { sim.next("CLOSE").values }
        val result = Calibration.compute(trained.model, trained.reference, rest, fist, emptyList())
        assertEquals(setOf("REST", "CLOSE"), result.recognised.keys)
        assertTrue(result.allRecognised)
        assertEquals(SensorState.Weaker, result.sensors[0])
        assertTrue(accuracy(result.model, weaker(12)) > 0.9f)
    }
}
