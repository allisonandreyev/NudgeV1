# /// script
# requires-python = ">=3.10"
# dependencies = ["bleak", "matplotlib", "numpy"]
# ///
"""
Nudge Mac Controller

Stands in for the Android app on a Mac: connects to the wearable over BLE,
plots the three EMG channels live, shows the detected gesture, sends servo
commands, and trains and recalibrates the gesture model.

    uv run nudge_mac.py              # connect to the wearable
    uv run nudge_mac.py --simulate   # fake wearable, no hardware needed

The trained model is saved to ~/.nudge/model.json (change with --model) and is
sent to the wearable automatically whenever it doesn't have it.
"""

import argparse
import asyncio
import struct
from collections import deque
from pathlib import Path

import matplotlib.pyplot as plt
from matplotlib.widgets import Button

import nudge_model as nm

# UUIDs - must match XIAO_C6_Firmware.ino
SERVICE_UUID = "000b1e53-d47a-cede-de57-000000008488"
CHAR_TX_UUID = "00008488-d47a-cede-0000-466178454d47"  # device -> us (notify)
CHAR_RX_UUID = "00008288-d47a-cede-0000-526563436d64"  # us -> device (write)

TYPE_FLOAT = 0x1130
TYPE_INT16 = 0x1112
TYPE_UINT16 = 0x1116
HEADER_SIZE = 6

GESTURES = {0: "CLOSE", 1: "OPEN", 2: "PINCH", 3: "REST"}
GESTURE_COLORS = {"CLOSE": "#dc2626", "OPEN": "#16a34a", "PINCH": "#d97706", "REST": "#64748b"}
GESTURE_NAMES = {"REST": "Relax", "OPEN": "Open hand", "CLOSE": "Close fist", "PINCH": "Pinch", "UNKNOWN": "—"}
GESTURE_HINTS = {
    "REST": "Let your hand go loose",
    "OPEN": "Spread your fingers wide",
    "CLOSE": "Make a firm fist",
    "PINCH": "Press thumb and index finger together",
}

HISTORY = 250  # samples on screen, 5 seconds at 50Hz
SERVO_SPEED = 150

TRAIN_ROUNDS = 5
TRAIN_ORDER = ["REST", "OPEN", "CLOSE", "PINCH"]
PREPARE_S = 1.5
HOLD_S = 3.0
CHECK_STEPS = [("REST", 4.0), ("CLOSE", 3.0), ("OPEN", 3.0)]
UPLOAD_ATTEMPTS = 3
UPLOAD_TIMEOUT_S = 6.0


def parse_packet(data: bytes):
    """Returns ([emg0, emg1, emg2], classification, model_id) from one TLV packet.
    model_id is -1 if the firmware doesn't report one (older firmware)."""
    emg, gesture, model_id = [], -1, -1
    offset = HEADER_SIZE
    while offset + 4 <= len(data):
        tlv_type, length = struct.unpack_from(">HH", data, offset)
        offset += 4
        if offset + length > len(data):
            break
        if tlv_type == TYPE_FLOAT and length == 4:
            emg.append(struct.unpack_from("<f", data, offset)[0])
        elif tlv_type == TYPE_INT16 and length == 2:
            gesture = struct.unpack_from("<h", data, offset)[0]
        elif tlv_type == TYPE_UINT16 and length == 2:
            model_id = struct.unpack_from("<H", data, offset)[0]
        offset += length
    return emg, gesture, model_id


def build_packet(message_id: int, emg, gesture: int, model_id: int) -> bytes:
    """Same layout the firmware sends. Only used by --simulate."""
    packet = bytes([0x01, 0x00, message_id & 0xFF, 0x10, 0x00, 36])
    for value in emg:
        packet += struct.pack(">HH", TYPE_FLOAT, 4) + struct.pack("<f", value)
    packet += struct.pack(">HH", TYPE_INT16, 2) + struct.pack("<h", gesture)
    packet += struct.pack(">HH", TYPE_UINT16, 2) + struct.pack("<H", model_id)
    return packet


class Controller:
    def __init__(self, simulate: bool, model_path: Path):
        self.simulate = simulate
        self.model_path = model_path
        self.saved = nm.load(model_path)
        self.client = None
        self.connected = False
        self.running = True
        self.status = "Starting..."
        self.gesture = "UNKNOWN"
        self.channels = [deque([0.0] * HISTORY, maxlen=HISTORY) for _ in range(3)]
        self.tasks = set()

        # Model on the wearable: None until it reports, -1 if its firmware can't hold one
        self.device_model_id = None
        self.sync_note = ""
        self.upload_attempts = {}

        # Guided training / quick check
        self.flow = None
        self.prompted = None  # gesture the user is asked to make; the simulator acts it out
        self.recording = None  # list readings are appended to while set
        self.prompt = None  # (title, subtitle, color)
        self.progress = ""
        self.info = "Press Train AI to teach Nudge your gestures." if self.saved is None else self.model_summary()

        if simulate:
            # Each run is like putting the wearable back on: sensors sit a little differently
            self.wearable = nm.SimulatedWearable()
            self.simulator = nm.Simulator.random_placement()

    # --- Data in -------------------------------------------------------------

    def on_packet(self, _sender, data: bytearray):
        emg, gesture, model_id = parse_packet(bytes(data))
        if len(emg) != 3:
            return
        for channel, value in zip(self.channels, emg):
            channel.append(value)
        self.gesture = GESTURES.get(gesture, "UNKNOWN")
        self.device_model_id = model_id
        if self.recording is not None:
            self.recording.append(list(emg))

    # --- Commands out --------------------------------------------------------

    async def send(self, command: str, quiet=False):
        if not quiet:
            print(f">> {command}")
        if self.simulate:
            self.wearable.command(command)
            return
        if self.client is None or not self.client.is_connected:
            print("   not connected, command dropped")
            return
        try:
            # Waits for the wearable to acknowledge, so commands never overlap
            await self.client.write_gatt_char(CHAR_RX_UUID, command.encode(), response=True)
        except Exception as e:
            print(f"   send failed: {e}")

    def start_task(self, coro):
        task = asyncio.get_running_loop().create_task(coro)
        self.tasks.add(task)
        task.add_done_callback(self.tasks.discard)
        return task

    # --- Model sync ----------------------------------------------------------

    def model_summary(self):
        s = self.saved
        acc = f"{s.accuracy * 100:.0f}% accurate" if s.accuracy is not None else "Trained"
        checked = " · quick-checked" if s.calibrated_at else ""
        return f"Model: {acc}{checked}"

    def sync_state(self):
        if self.saved is None:
            return "No model yet"
        if not self.connected:
            return "Saved; sent when the wearable connects"
        if self.device_model_id == self.saved.id:
            return "On your wearable"
        if self.device_model_id == -1:
            return "Wearable needs the new firmware for this"
        return self.sync_note or "Sending to wearable…"

    async def sync_loop(self):
        """Sends the saved model whenever the connected wearable has a different one."""
        while self.running:
            await asyncio.sleep(0.5)
            s = self.saved
            dev = self.device_model_id
            if s is None or not self.connected or dev is None or dev < 0 or dev == s.id:
                continue
            tries = self.upload_attempts.get(s.id, 0)
            if tries >= UPLOAD_ATTEMPTS:
                self.sync_note = "Couldn't send to the wearable. Reconnect it to retry."
                continue
            self.upload_attempts[s.id] = tries + 1
            self.sync_note = "Sending to wearable…"
            print(f"Sending model {s.id} to the wearable (attempt {tries + 1})")
            for cmd in nm.upload_commands(s.model, s.id):
                await self.send(cmd, quiet=True)
            for _ in range(int(UPLOAD_TIMEOUT_S / 0.1)):
                if self.device_model_id == s.id:
                    print("Model installed on the wearable")
                    break
                await asyncio.sleep(0.1)

    # --- Guided flows --------------------------------------------------------

    def start_flow(self, kind):
        if self.flow is not None:
            return
        if not self.connected:
            self.info = "Connect the wearable first."
            return
        self.flow = self.start_task(self.train_flow() if kind == "train" else self.check_flow())
        self.flow.add_done_callback(lambda _t: self.end_flow())

    def cancel_flow(self):
        if self.flow is not None:
            self.flow.cancel()
            self.info = "Cancelled."

    def end_flow(self):
        self.flow = None
        self.prompted = None
        self.recording = None
        self.prompt = None
        self.progress = ""

    async def hold(self, gesture, seconds, into, caption):
        """Shows 'Next', then records [into] while the user holds [gesture]."""
        name = GESTURE_NAMES[gesture]
        self.prompted = gesture
        self.prompt = (f"Next: {name}", GESTURE_HINTS[gesture], "#475569")
        await asyncio.sleep(PREPARE_S)
        self.recording = into
        steps = int(seconds * 10)
        for i in range(steps, 0, -1):
            self.prompt = (f"Hold: {name}", f"{caption}   ·   {i / 10:.1f} s", GESTURE_COLORS[gesture])
            await asyncio.sleep(0.1)
        self.recording = None

    async def train_flow(self):
        for i in (3, 2, 1):
            self.prompt = ("Get ready", f"Starting in {i}", "#0f766e")
            await asyncio.sleep(1)
        recordings = []
        for r in range(1, TRAIN_ROUNDS + 1):
            for g in TRAIN_ORDER:
                readings = []
                self.progress = f"Round {r} of {TRAIN_ROUNDS}"
                await self.hold(g, HOLD_S, readings, f"Round {r} of {TRAIN_ROUNDS}")
                recordings.append(nm.Recording(r, g, readings))
        self.prompted = None
        self.prompt = ("Learning your gestures…", "", "#0f766e")
        try:
            result = await asyncio.to_thread(nm.train, recordings)
        except ValueError:
            self.info = "Not enough data. Make sure the wearable stays connected, then train again."
            return
        per = "   ".join(f"{GESTURE_NAMES[g]} {result.per_gesture.get(g, 0) * 100:.0f}%" for g in TRAIN_ORDER)
        acc = result.accuracy or 0
        if acc < nm.GOOD_ACCURACY:
            self.info = (f"Accuracy {acc * 100:.0f}% is low, so it wasn't saved; your previous model stays. "
                         f"Check the sensors sit firmly, then train again.\n{per}")
            return
        self.saved = nm.SavedModel(nm.new_id(self.saved.id if self.saved else 0), result.model, result.accuracy,
                                   result.per_gesture, nm.now_ms(), result.reference)
        nm.save(self.model_path, self.saved)
        self.info = f"Trained: {acc * 100:.0f}% estimated accuracy. Saved; the status above shows when the wearable has it.\n{per}"
        print(self.info)

    async def check_flow(self):
        if self.saved is None or self.saved.reference is None:
            self.info = "Train the AI first (or retrain once if it was trained before quick checks existed)."
            return
        recorded = {}
        for i, (g, seconds) in enumerate(CHECK_STEPS):
            recorded[g] = []
            self.progress = f"Quick check: step {i + 1} of {len(CHECK_STEPS)}"
            await self.hold(g, seconds, recorded[g], f"Step {i + 1} of {len(CHECK_STEPS)}")
        self.prompted = None
        try:
            result = nm.calibrate(self.saved.model, self.saved.reference, recorded["REST"], recorded["CLOSE"], recorded["OPEN"])
        except ValueError:
            self.info = "Not enough signal. Make sure the wearable stays connected for the whole check."
            return
        sensors = "   ".join(
            f"Sensor {c + 1}: " + ("about the same" if st == "normal" else
                                   "barely responding, check skin contact" if st == "not responding" else
                                   f"{st} ({result.strength[c] * 100:.0f}%), adjusted")
            for c, st in enumerate(result.sensors))
        if not result.all_recognised:
            missed = ", ".join(GESTURE_NAMES[g] for g, ok in result.recognised.items() if not ok)
            self.info = f"Not recognised: {missed}. A sensor may have moved; retrain (Train AI).\n{sensors}"
            return
        base = self.saved
        self.saved = nm.SavedModel(nm.new_id(base.id), result.model, base.accuracy, base.per_gesture,
                                   base.trained_at, base.reference, nm.now_ms())
        nm.save(self.model_path, self.saved)
        self.info = f"Tuned to today's fit. Saved; the status above shows when the wearable has it.\n{sensors}"
        print(self.info)

    # --- Connections ---------------------------------------------------------

    async def run_ble(self):
        from bleak import BleakClient, BleakScanner

        def is_nudge(device, adv):
            name = device.name or adv.local_name or ""
            return name.startswith("Nudge") or SERVICE_UUID in [u.lower() for u in adv.service_uuids]

        while self.running:
            self.status = "Scanning for Nudge..."
            device = await BleakScanner.find_device_by_filter(is_nudge, timeout=10.0)
            if device is None:
                continue

            self.status = f"Connecting to {device.name}..."
            try:
                async with BleakClient(device) as client:
                    self.client = client
                    await client.start_notify(CHAR_TX_UUID, self.on_packet)
                    self.status = f"Connected: {device.name}"
                    self.connected = True
                    self.upload_attempts.clear()
                    while self.running and client.is_connected:
                        await asyncio.sleep(0.2)
            except Exception as e:
                print(f"Connection error: {e}")
            self.client = None
            self.connected = False
            self.device_model_id = None
            self.cancel_flow()
            self.status = "Disconnected"
            await asyncio.sleep(1.0)

    async def run_simulated(self):
        self.status = "Simulated wearable (no hardware)"
        self.connected = True
        message_id = 0
        while self.running:
            reading = self.simulator.next(self.prompted)
            gesture = self.wearable.classify(reading)
            self.on_packet(None, bytearray(build_packet(message_id, reading, gesture, self.wearable.model_id)))
            message_id += 1
            await asyncio.sleep(0.02)

    # --- Window --------------------------------------------------------------

    async def run_ui(self):
        plt.ion()
        fig = plt.figure(figsize=(11, 7.5))
        fig.canvas.manager.set_window_title("Nudge Mac Controller")
        ax = fig.add_axes([0.06, 0.36, 0.9, 0.5])

        colors = ["#0d9488", "#6366f1", "#f59e0b"]
        lines = [ax.plot(range(HISTORY), list(ch), color=colors[i], lw=1.6, label=f"Sensor {i + 1}")[0]
                 for i, ch in enumerate(self.channels)]
        ax.set_ylim(0, 4095)  # 12-bit ADC
        ax.set_xlim(0, HISTORY - 1)
        ax.set_xticks([])
        ax.set_ylabel("Raw signal")
        ax.legend(loc="upper left", frameon=False)
        for side in ("top", "right"):
            ax.spines[side].set_visible(False)

        status_text = fig.text(0.06, 0.95, "", fontsize=10)
        model_text = fig.text(0.06, 0.915, "", fontsize=10, color="#475569")
        gesture_text = fig.text(0.96, 0.92, "", fontsize=24, fontweight="bold", ha="right")
        prompt_title = ax.text(0.5, 0.6, "", transform=ax.transAxes, ha="center", va="center", fontsize=30,
                               fontweight="bold", bbox=dict(boxstyle="round,pad=0.6", fc="white", ec="#e2e8f0", alpha=0.92))
        prompt_sub = ax.text(0.5, 0.36, "", transform=ax.transAxes, ha="center", va="center", fontsize=13, color="#475569",
                             bbox=dict(boxstyle="round,pad=0.4", fc="white", ec="none", alpha=0.85))
        info_text = fig.text(0.06, 0.29, "", fontsize=10.5, va="top", wrap=True)

        # Kept in a list so the widgets are not garbage collected
        buttons = []

        def add_row(y, items):
            width = 0.9 / len(items)
            for i, (label, action, color) in enumerate(items):
                button = Button(fig.add_axes([0.06 + i * width, y, width - 0.01, 0.065]), label, color=color, hovercolor="0.85")
                button.on_clicked(lambda _e, a=action: a())
                buttons.append(button)

        cmd = lambda c: (lambda: self.start_task(self.send(c)))
        add_row(0.11, [
            ("AI on", cmd("ai_start"), "0.93"),
            ("AI off", cmd("ai_stop"), "0.93"),
            ("Close hand", cmd(f"grasp 255 {SERVO_SPEED}"), "0.93"),
            ("Open hand", cmd(f"retract 255 {SERVO_SPEED}"), "0.93"),
            ("Engage", cmd("engage"), "0.93"),
            ("Disengage", cmd("disengage"), "0.93"),
            ("STOP", cmd("stop"), "#fecaca"),
        ])
        add_row(0.03, [
            ("Train AI (90 s)", lambda: self.start_flow("train"), "#ccfbf1"),
            ("Quick check (15 s)", lambda: self.start_flow("check"), "#ccfbf1"),
            ("Cancel", self.cancel_flow, "0.93"),
        ])

        fig.canvas.mpl_connect("close_event", lambda _event: setattr(self, "running", False))
        fig.show()

        while self.running:
            for line, channel in zip(lines, self.channels):
                line.set_ydata(list(channel))
            status_text.set_text(self.status)
            model_text.set_text((self.model_summary() + " · " if self.saved else "") + self.sync_state())
            gesture_text.set_text(GESTURE_NAMES.get(self.gesture, "—"))
            gesture_text.set_color(GESTURE_COLORS.get(self.gesture, "#94a3b8"))
            if self.prompt:
                title, sub, color = self.prompt
                prompt_title.set_text(title)
                prompt_title.set_color(color)
                prompt_title.set_visible(True)
                prompt_sub.set_text(sub)
                prompt_sub.set_visible(bool(sub))
            else:
                prompt_title.set_visible(False)
                prompt_sub.set_visible(False)
            info_text.set_text(self.progress if self.flow else self.info)
            fig.canvas.draw_idle()
            fig.canvas.flush_events()
            await asyncio.sleep(0.04)

    async def run(self):
        source = asyncio.create_task(self.run_simulated() if self.simulate else self.run_ble())
        sync = asyncio.create_task(self.sync_loop())
        try:
            await self.run_ui()
        finally:
            self.running = False
            # Leave the device safe: stop AI actuation before we go
            await self.send("ai_stop")
            for t in [source, sync, *self.tasks]:
                t.cancel()
            await asyncio.gather(source, sync, *self.tasks, return_exceptions=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Nudge wearable controller for Mac")
    parser.add_argument("--simulate", action="store_true", help="use a fake wearable instead of the real one")
    parser.add_argument("--model", type=Path, default=Path.home() / ".nudge" / "model.json",
                        help="where the trained model is saved (default: ~/.nudge/model.json)")
    args = parser.parse_args()
    if args.simulate and args.model == parser.get_default("model"):
        # Don't let simulated training overwrite a real model
        args.model = Path.home() / ".nudge" / "model-simulated.json"
    asyncio.run(Controller(args.simulate, args.model).run())
