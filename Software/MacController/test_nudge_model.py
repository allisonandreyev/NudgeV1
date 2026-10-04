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
                correct += nm.CLASSES[model.predict_window(readings[i:i + nm.WINDOW])[0]] == g
    return correct / total


@pytest.fixture(scope="module")
def trained():
    return nm.train(record(nm.Simulator(random.Random(1)), rounds=5))


@pytest.mark.skipif(not APP_FIXTURE.exists(), reason="run the app's unit tests first")
def test_matches_the_app_and_firmware():
    """Installs the app's model through the upload commands and compares every prediction."""
    device = nm.SimulatedWearable()
    window, checked = [], 0
    for line in APP_FIXTURE.read_text().splitlines():
        kind, _, rest = line.partition(" ")
        if kind == "CMD":
            device.command(rest)
        elif kind == "READ":
            parts = rest.split()
            window = (window + [[float(v) for v in parts[:3]]])[-nm.WINDOW:]
            if "EXPECT" in parts:
                k, p = device.model.predict_window(window)
                assert k == int(parts[4])
                assert p == pytest.approx(float(parts[5]), abs=1e-3)
                checked += 1
    assert device.model_id == 777
    assert checked > 100


def test_learns_distinct_gestures(trained):
    assert trained.accuracy > 0.95
    assert set(trained.per_gesture) == set(GESTURES)


def test_upload_installs_the_same_model(trained):
    device = nm.SimulatedWearable()
    for cmd in nm.upload_commands(trained.model, 4242):
        assert len(cmd.encode()) <= 180
        device.command(cmd)
    assert device.model_id == 4242
    np.testing.assert_allclose(device.model.parameters(), trained.model.parameters(), rtol=1e-5)


def test_quick_check_recovers_from_moved_sensors(trained):
    def shifted(seed):
        return nm.Simulator(random.Random(seed), (0.45, 1.6, 0.8), (60, -20, 90))

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
    assert result.sensors[0] == "weaker" and result.sensors[1] == "stronger"


def test_save_and_load_round_trip(tmp_path, trained):
    saved = nm.SavedModel(123, trained.model, trained.accuracy, trained.per_gesture, nm.now_ms(), trained.reference)
    path = tmp_path / "model.json"
    nm.save(path, saved)
    loaded = nm.load(path)
    assert loaded.id == 123
    np.testing.assert_allclose(loaded.model.parameters(), trained.model.parameters(), rtol=1e-6)
    np.testing.assert_allclose(loaded.reference.active, trained.reference.active)


def test_writes_fixture_for_firmware_check(trained):
    """Python-made upload + predictions, for Software/XIAO_C6_Firmware/test/model_test."""
    model = trained.model.with_calibration([0.9, 1.2, 1.05], [5, -10, 15])
    sim = nm.Simulator(random.Random(7))
    readings = [sim.next(g) for g in ["REST", "OPEN", "CLOSE", "PINCH"] for _ in range(40)]
    lines = [f"CMD {c}" for c in nm.upload_commands(model, 777)]
    for i, r in enumerate(readings):
        line = f"READ {r[0]} {r[1]} {r[2]}"
        if i >= nm.WINDOW - 1:
            k, p = model.predict_window(readings[i - nm.WINDOW + 1:i + 1])
            line += f" EXPECT {k} {p}"
        lines.append(line)
    out = Path(__file__).parent / ".fixture-python.txt"
    out.write_text("\n".join(lines) + "\n")
