"""
Gesture model for the Mac controller: the same LDA classifier, quick-check correction and
upload format as the Android app (App/.../ai/*.kt), so a model trained here installs on the
wearable exactly like one trained on a phone. Keep the two in step; test_nudge_model.py
checks this one against fixtures produced by the app's tests.
"""

import json
import math
import random
import time
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np

CHANNELS = 3
WINDOW = 10  # 200 ms at 50 Hz
FEATURES = CHANNELS * 3
# Order the firmware reports gestures in: 0/2 close the hand, 1/3 open it
CLASSES = ["CLOSE", "OPEN", "PINCH", "REST"]
PARAMETER_COUNT = CHANNELS * 2 + FEATURES * 2 + len(CLASSES) * (FEATURES + 1)
SHRINKAGE = 0.1
GOOD_ACCURACY = 0.7


def extract(window) -> np.ndarray:
    """Features of WINDOW readings (oldest first): level, spread and rate of change per sensor."""
    w = np.asarray(window, dtype=np.float64)
    mean = w.mean(axis=0)
    std = np.sqrt(((w - mean) ** 2).mean(axis=0))
    wl = np.abs(np.diff(w, axis=0)).sum(axis=0) / (WINDOW - 1)
    return np.log1p(np.concatenate([mean, std, wl])).astype(np.float32)


def slide(readings, step=2) -> list:
    """All full windows in one continuous recording."""
    return [extract(readings[i:i + WINDOW]) for i in range(0, len(readings) - WINDOW + 1, step)]


def percentile(values, p: float) -> float:
    """Same rule as the app: the value at index (n - 1) * p of the sorted list."""
    if len(values) == 0:
        return 0.0
    s = sorted(values)
    return float(s[int((len(s) - 1) * p)])


@dataclass
class Model:
    mean: np.ndarray
    scale: np.ndarray
    weights: np.ndarray  # classes x features
    bias: np.ndarray
    gain: np.ndarray = field(default_factory=lambda: np.ones(CHANNELS, dtype=np.float32))
    offset: np.ndarray = field(default_factory=lambda: np.zeros(CHANNELS, dtype=np.float32))

    def adjust(self, reading) -> np.ndarray:
        return np.maximum(self.gain * np.asarray(reading, dtype=np.float32) + self.offset, 0)

    def predict_features(self, f):
        z = (np.asarray(f, dtype=np.float32) - self.mean) / self.scale
        scores = self.weights @ z + self.bias
        e = np.exp(scores - scores.max())
        best = int(np.argmax(scores))
        return best, float(e[best] / e.sum())

    def predict_window(self, raw_window):
        return self.predict_features(extract([self.adjust(r) for r in raw_window]))

    def with_calibration(self, gain, offset) -> "Model":
        return Model(self.mean, self.scale, self.weights, self.bias,
                     np.asarray(gain, dtype=np.float32), np.asarray(offset, dtype=np.float32))

    def parameters(self) -> np.ndarray:
        return np.concatenate([self.gain, self.offset, self.mean, self.scale, self.weights.ravel(), self.bias]).astype(np.float32)

    @staticmethod
    def from_parameters(p) -> "Model":
        p = np.asarray(p, dtype=np.float32)
        if len(p) == PARAMETER_COUNT - CHANNELS * 2:  # saved before recalibration existed
            p = np.concatenate([np.ones(CHANNELS), np.zeros(CHANNELS), p]).astype(np.float32)
        assert len(p) == PARAMETER_COUNT
        b = CHANNELS * 2
        n, k = FEATURES, len(CLASSES)
        return Model(
            mean=p[b:b + n], scale=p[b + n:b + 2 * n],
            weights=p[b + 2 * n:b + 2 * n + k * n].reshape(k, n), bias=p[-k:],
            gain=p[:CHANNELS], offset=p[CHANNELS:b],
        )


@dataclass
class Recording:
    round: int
    label: str
    readings: list


def fit(recordings) -> Model:
    by_class = [np.array([f for r in recordings if r.label == c for f in slide(r.readings)], dtype=np.float64) for c in CLASSES]
    if any(len(x) < 2 for x in by_class):
        raise ValueError("Every gesture needs recorded data")
    allf = np.vstack(by_class)
    mean = allf.mean(axis=0)
    scale = np.maximum(np.sqrt(((allf - mean) ** 2).mean(axis=0)), 1e-3)
    z = [(x - mean) / scale for x in by_class]
    centres = np.array([x.mean(axis=0) for x in z])
    cov = sum((x - c).T @ (x - c) for x, c in zip(z, centres)) / max(len(allf) - len(CLASSES), 1)
    # Shrink toward a diagonal so short recordings still give a stable inverse
    cov = (1 - SHRINKAGE) * cov + SHRINKAGE * np.trace(cov) / FEATURES * np.eye(FEATURES)
    weights = centres @ np.linalg.inv(cov)
    bias = -0.5 * np.einsum("kd,kd->k", weights, centres) + math.log(1 / len(CLASSES))
    return Model(mean.astype(np.float32), scale.astype(np.float32), weights.astype(np.float32), bias.astype(np.float32))


@dataclass
class Reference:
    """Training-day signal levels per sensor, used by the quick check."""
    rest: np.ndarray
    active: np.ndarray


def reference(recordings) -> Reference:
    rest = [r for rec in recordings if rec.label == "REST" for r in rec.readings]
    active = [r for rec in recordings if rec.label in ("CLOSE", "OPEN") for r in rec.readings]
    return Reference(
        rest=np.array([percentile([x[c] for x in rest], 0.5) for c in range(CHANNELS)], dtype=np.float32),
        active=np.array([percentile([x[c] for x in active], 0.9) for c in range(CHANNELS)], dtype=np.float32),
    )


@dataclass
class TrainingResult:
    model: Model
    reference: Reference
    accuracy: float | None
    per_gesture: dict


def train(recordings) -> TrainingResult:
    model = fit(recordings)
    rounds = sorted({r.round for r in recordings})
    correct, total = {}, {}
    if len(rounds) >= 2:
        # Check on each round in turn after training on the others
        for held in rounds:
            train_set = [r for r in recordings if r.round != held]
            if any(not any(r.label == c for r in train_set) for c in CLASSES):
                continue
            m = fit(train_set)
            for r in recordings:
                if r.round != held:
                    continue
                for f in slide(r.readings):
                    total[r.label] = total.get(r.label, 0) + 1
                    if CLASSES[m.predict_features(f)[0]] == r.label:
                        correct[r.label] = correct.get(r.label, 0) + 1
    n = sum(total.values())
    accuracy = sum(correct.values()) / n if n else None
    per = {g: correct.get(g, 0) / t for g, t in total.items()}
    return TrainingResult(model, reference(recordings), accuracy, per)


# --- Quick check ---------------------------------------------------------------

MIN_GAIN, MAX_GAIN = 0.25, 4.0


@dataclass
class CalibrationResult:
    model: Model
    sensors: list  # "normal" | "weaker" | "stronger" | "not responding"
    strength: list  # 1.0 = same as training day
    recognised: dict

    @property
    def all_recognised(self):
        return all(self.recognised.values())


def calibrate(base: Model, ref: Reference, rest, fist, open_) -> CalibrationResult:
    """Corrects each sensor so today's relaxed and active levels match training day."""
    if min(len(rest), len(fist), len(open_)) < WINDOW:
        raise ValueError("Not enough readings")
    active = list(fist) + list(open_)
    gain, offset, states, strength = [], [], [], []
    for c in range(CHANNELS):
        today_rest = percentile([r[c] for r in rest], 0.5)
        today_active = percentile([r[c] for r in active], 0.9)
        ref_range = max(float(ref.active[c] - ref.rest[c]), 1.0)
        today_range = today_active - today_rest
        raw = MAX_GAIN if today_range <= 0 else ref_range / today_range
        g = min(max(raw, MIN_GAIN), MAX_GAIN)
        gain.append(g)
        offset.append(float(ref.rest[c]) - g * today_rest)
        s = 1 / max(raw, 1e-3)
        strength.append(s)
        states.append("not responding" if raw >= MAX_GAIN else "normal" if abs(1 - s) < 0.2 else "weaker" if s < 1 else "stronger")
    model = base.with_calibration(gain, offset)

    def mostly(gesture, readings):
        windows = [readings[i:i + WINDOW] for i in range(0, len(readings) - WINDOW + 1, 2)]
        hits = sum(CLASSES[model.predict_window(w)[0]] == gesture for w in windows)
        return hits * 2 > len(windows)

    recognised = {"REST": mostly("REST", rest), "CLOSE": mostly("CLOSE", fist), "OPEN": mostly("OPEN", open_)}
    return CalibrationResult(model, states, strength, recognised)


# --- Upload protocol (must match GestureModel.cpp) -------------------------------

def upload_commands(model: Model, model_id: int) -> list:
    assert 1 <= model_id <= 0xFFFF
    values = [f"{v:.7g}" for v in model.parameters()]
    checksum = sum(float(v) for v in values)
    lines = [f"mdl {i} {' '.join(values[i:i + 8])}" for i in range(0, len(values), 8)]
    return [f"mdl_begin {model_id} {len(values)}"] + lines + [f"mdl_end {checksum:.7g}"]


# --- Saved model (same JSON as the app's ModelRepository) -----------------------

@dataclass
class SavedModel:
    id: int
    model: Model
    accuracy: float | None
    per_gesture: dict
    trained_at: int
    reference: Reference | None
    calibrated_at: int | None = None


def new_id(avoid=0) -> int:
    while True:
        i = random.randint(1, 0xFFFF)
        if i != avoid:
            return i


def save(path: Path, saved: SavedModel):
    data = {
        "id": saved.id,
        "classes": CLASSES,
        "parameters": [float(v) for v in saved.model.parameters()],
        "accuracy": saved.accuracy,
        "perGesture": saved.per_gesture,
        "trainedAt": saved.trained_at,
        "calibratedAt": saved.calibrated_at,
    }
    if saved.reference is not None:
        data["referenceRest"] = [float(v) for v in saved.reference.rest]
        data["referenceActive"] = [float(v) for v in saved.reference.active]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=1))


def load(path: Path) -> SavedModel | None:
    if not path.exists():
        return None
    d = json.loads(path.read_text())
    ref = None
    if "referenceRest" in d and "referenceActive" in d:
        ref = Reference(np.array(d["referenceRest"], dtype=np.float32), np.array(d["referenceActive"], dtype=np.float32))
    return SavedModel(d["id"], Model.from_parameters(d["parameters"]), d.get("accuracy"), d.get("perGesture", {}),
                      d["trainedAt"], ref, d.get("calibratedAt"))


def now_ms() -> int:
    return int(time.time() * 1000)


# --- Firmware behaviour, for --simulate --------------------------------------------

class SimulatedWearable:
    """Acts like the firmware: installs uploaded models and classifies with them."""

    def __init__(self):
        self.model_id = 0
        self.model = None
        self._staging = {}
        self._staging_id = 0
        self._window = []
        self._last, self._count, self.confirmed = -1, 0, -1

    def command(self, cmd: str):
        parts = cmd.split()
        if not parts or not parts[0].startswith("mdl"):
            return
        if parts[0] == "mdl_begin":
            self._staging_id, self._staging = int(parts[1]), {}
        elif parts[0] == "mdl":
            start = int(parts[1])
            for i, v in enumerate(parts[2:]):
                self._staging[start + i] = float(v)
        elif parts[0] == "mdl_end":
            if len(self._staging) == PARAMETER_COUNT and abs(sum(self._staging.values()) - float(parts[1])) <= 1e-3 * (1 + abs(float(parts[1]))):
                self.model = Model.from_parameters([self._staging[i] for i in range(PARAMETER_COUNT)])
                self.model_id = self._staging_id
        elif parts[0] == "mdl_clear":
            self.model, self.model_id = None, 0

    def classify(self, reading) -> int:
        self._window = (self._window + [list(reading)])[-WINDOW:]
        if self.model is None or len(self._window) < WINDOW:
            return -1
        k, p = self.model.predict_window(self._window)
        raw = k if p >= 0.8 else -1
        if raw != self._last:
            self._last, self._count = raw, 1
        else:
            self._count += 1
            if self._count >= 3:
                self.confirmed = raw
        return self.confirmed


class Simulator:
    """Believable EMG envelopes per gesture; mirrors the app's DemoSimulator."""
    PATTERNS = {"REST": (160, 140, 170), "OPEN": (850, 420, 1500), "CLOSE": (1650, 1250, 520), "PINCH": (700, 1550, 620)}
    CYCLE = ["REST", "OPEN", "REST", "CLOSE", "REST", "PINCH"]

    def __init__(self, rng=None, gain=(1, 1, 1), offset=(0, 0, 0)):
        self.rng = rng or random.Random()
        self.gain, self.offset = gain, offset
        self.levels = [150.0] * 3
        self.tick = 0

    @classmethod
    def random_placement(cls, rng=None):
        rng = rng or random.Random()
        return cls(rng, tuple(0.6 + rng.random() * 0.9 for _ in range(3)), tuple(rng.random() * 120 - 40 for _ in range(3)))

    def next(self, target=None):
        self.tick += 1
        gesture = target or self.CYCLE[int(self.tick / 125) % len(self.CYCLE)]
        out = []
        for i, p in enumerate(self.PATTERNS[gesture]):
            self.levels[i] += (p - self.levels[i]) * 0.15
            tremor = 1 + 0.08 * math.sin(self.tick * 0.6 + i * 2)
            noise = self.rng.random() * 0.2 + 0.9
            out.append(min(max(self.gain[i] * self.levels[i] * tremor * noise + self.offset[i], 0), 4095))
        return out
