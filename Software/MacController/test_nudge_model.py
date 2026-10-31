"""
Run from this folder:  uv run --with numpy --with pytest pytest -q

The parity test needs the fixture written by the app's unit tests
(App/android: ./gradlew testDebugUnitTest) and is skipped without it.
"""

import random
from pathlib import Path

import numpy as np
import pytest

import nudge_model as nm

GESTURES = ["REST", "OPEN", "CLOSE", "PINCH"]
APP_FIXTURE = Path(__file__).parents[2] / "App/android/app/build/firmware-fixture/fixture.txt"


def record(sim, rounds):
    out = []
    for r in range(1, rounds + 1):
        for g in GESTURES:
            for _ in range(75):
                sim.next(g)
            out.append(nm.Recording(r, g, [sim.next(g) for _ in range(150)]))
    return out


def accuracy(model, sim):
    correct = total = 0
    for _ in range(3):
        for g in GESTURES:
            for _ in range(75):
                sim.next(g)
            readings = [sim.next(g) for _ in range(100)]
            for i in range(0, len(readings) - nm.WINDOW + 1, 5):
                total += 1
                correct += model.classes[model.predict_window(readings[i:i + nm.WINDOW])[0]] == g
    return correct / total


@pytest.fixture(scope="module")
def trained():
    """Four gestures on the 16-pad strip."""
    return nm.train(record(nm.Simulator.strip(random.Random(1)), rounds=5))


@pytest.mark.skipif(not APP_FIXTURE.exists(), reason="run the app's unit tests first")
def test_matches_the_app_and_firmware():
    """Installs the app's model (one MyoWare, like the rev2 firmware) through the upload commands
    and compares every prediction."""
    device = nm.SimulatedWearable(channels=1)
    window, checked = [], 0
    for line in APP_FIXTURE.read_text().splitlines():
        kind, _, rest = line.partition(" ")
        if kind == "CMD":
            device.command(rest)
        elif kind == "READ":
            parts = rest.split()
            n = parts.index("EXPECT") if "EXPECT" in parts else len(parts)
            window = (window + [[float(v) for v in parts[:n]]])[-nm.WINDOW:]
            if n < len(parts):
                k, p = device.model.predict_window(window)
                assert k == int(parts[n + 1])
                assert p == pytest.approx(float(parts[n + 2]), abs=1e-3)
                checked += 1
    assert device.model_id == 777
    assert checked > 100


def test_learns_distinct_gestures(trained):
    assert trained.accuracy > 0.95
    assert set(trained.per_gesture) == set(GESTURES)


def test_channel_counts():
    assert nm.parameter_count(8) == 164 and nm.parameter_count(3) == 64 and nm.parameter_count(1) == 16
    assert nm.Model.from_parameters(np.ones(164)).channels == 8
    assert nm.Model.from_parameters(np.ones(58)).channels == 3  # 3-sensor model from before recalibration


def test_upload_installs_the_same_model(trained):
    device = nm.SimulatedWearable(channels=nm.STRIP_CHANNELS)
    for cmd in nm.upload_commands(trained.model, 4242):
        assert len(cmd.encode()) <= 180
        device.command(cmd)
    assert device.model_id == 4242
    np.testing.assert_allclose(device.model.parameters(), trained.model.parameters(), rtol=1e-5)


def test_quick_check_recovers_from_moved_sensors(trained):
    # Strip re-fitted badly: flexor side ~30%, extensor side ~240% (same as the app's test)
    def shifted(seed):
        return nm.Simulator(random.Random(seed), (0.3, 0.3, 0.35, 0.3, 2.5, 2.2, 2.6, 2.4), (3, -1, 4, 2, 0, 3, -1, 2))

    def check(sim):
        for _ in range(50): sim.next("REST")
        rest = [sim.next("REST") for _ in range(200)]
        for _ in range(50): sim.next("CLOSE")
        fist = [sim.next("CLOSE") for _ in range(150)]
        for _ in range(50): sim.next("OPEN")
        open_ = [sim.next("OPEN") for _ in range(150)]
        return nm.calibrate(trained.model, trained.reference, rest, fist, open_)

    before = accuracy(trained.model, shifted(10))
    result = check(shifted(11))
    after = accuracy(result.model, shifted(12))
    assert before < 0.8
    assert after > 0.9
    assert result.all_recognised
    assert result.sensors[0] == "weaker" and result.sensors[4] == "stronger"


def test_save_and_load_round_trip(tmp_path, trained):
    saved = nm.SavedModel(123, trained.model, trained.accuracy, trained.per_gesture, nm.now_ms(), trained.reference)
    path = tmp_path / "model.json"
    nm.save(path, saved)
    loaded = nm.load(path)
    assert loaded.id == 123
    np.testing.assert_allclose(loaded.model.parameters(), trained.model.parameters(), rtol=1e-6)
    np.testing.assert_allclose(loaded.reference.active, trained.reference.active)


def test_writes_fixture_for_firmware_check(single):
    """Python-made upload + predictions, for Software/Nudge_Firmware/test/model_test (one MyoWare)."""
    model = single.model.with_calibration([0.9], [12])
    sim = nm.Simulator(random.Random(7))
    readings = [sim.next(g) for g in ["REST", "CLOSE", "PINCH", "REST", "OPEN"] for _ in range(40)]
    lines = [f"CMD {c}" for c in nm.upload_commands(model, 777)]
    for i, r in enumerate(readings):
        line = "READ " + " ".join(str(v) for v in r)
        if i >= nm.WINDOW - 1:
            k, p = model.predict_window(readings[i - nm.WINDOW + 1:i + 1])
            line += f" EXPECT {k} {p}"
        lines.append(line)
    out = Path(__file__).parent / ".fixture-python.txt"
    out.write_text("\n".join(lines) + "\n")


def test_main_board_packet_round_trip():
    """Packet layout must match Nudge_Firmware.ino: floats, gesture, model id, battery, forces if fitted."""
    import nudge_mac
    emg = [float(i) for i in range(8)]
    packet = nudge_mac.build_packet(5, emg, 2, 4242, battery=63, grip=(1000, 2600))
    got_emg, gesture, model_id, battery, grip = nudge_mac.parse_packet(packet)
    assert got_emg == emg and gesture == 2 and model_id == 4242
    assert battery == 63 and grip == (1000, 2600)

    # The rev2 board has no force sensors, so it leaves them out
    got_emg, gesture, model_id, battery, grip = nudge_mac.parse_packet(nudge_mac.build_packet(6, [512.0], 1, 9, battery=40))
    assert got_emg == [512.0] and gesture == 1 and battery == 40 and grip is None


# --- One MyoWare (rev2 board): squeeze and relax -----------------------------------

def record_single(sim, rounds):
    out = []
    for r in range(1, rounds + 1):
        for g in ["REST", "CLOSE"]:
            for _ in range(75):
                sim.next(g)
            out.append(nm.Recording(r, g, [sim.next(g) for _ in range(150)]))
    return out


@pytest.fixture(scope="module")
def single():
    return nm.train(record_single(nm.Simulator(random.Random(1)), rounds=5))


def test_single_sensor_learns_squeeze_and_relax(single):
    assert single.model.classes == ["CLOSE", "REST"]
    assert single.accuracy > 0.95
    assert len(single.model.parameters()) == 16
    # Relaxing is index 1, which the firmware treats as "open the hand"
    assert single.model.classes.index("REST") == 1


def test_single_sensor_upload_and_quick_check(single, tmp_path):
    device = nm.SimulatedWearable(channels=1)
    for cmd in nm.upload_commands(single.model, 31):
        device.command(cmd)
    assert device.model_id == 31 and device.model.classes == ["CLOSE", "REST"]

    sim = nm.Simulator(random.Random(11), (0.4,), (10,))  # poorer contact today: 40% of the signal
    for _ in range(50): sim.next("REST")
    rest = [sim.next("REST") for _ in range(200)]
    for _ in range(50): sim.next("CLOSE")
    fist = [sim.next("CLOSE") for _ in range(150)]
    result = nm.calibrate(single.model, single.reference, rest, fist)
    assert set(result.recognised) == {"REST", "CLOSE"} and result.all_recognised
    assert result.sensors == ["weaker"]

    path = tmp_path / "m.json"
    nm.save(path, nm.SavedModel(5, result.model, single.accuracy, single.per_gesture, nm.now_ms(), single.reference))
    assert nm.load(path).model.classes == ["CLOSE", "REST"]
