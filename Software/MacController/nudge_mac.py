# /// script
# requires-python = ">=3.10"
# dependencies = ["bleak", "matplotlib"]
# ///
"""
Nudge Mac Controller

Stands in for the Android app on a Mac: connects to the wearable over BLE,
plots the three EMG channels live, shows the detected gesture, and sends
servo commands.

    uv run nudge_mac.py              # connect to the wearable
    uv run nudge_mac.py --simulate   # fake data, no hardware needed
"""

import argparse
import asyncio
import math
import random
import struct
from collections import deque

import matplotlib.pyplot as plt
from matplotlib.widgets import Button

# UUIDs - must match XIAO_C6_Firmware.ino
SERVICE_UUID = "000b1e53-d47a-cede-de57-000000008488"
CHAR_TX_UUID = "00008488-d47a-cede-0000-466178454d47"  # device -> us (notify)
CHAR_RX_UUID = "00008288-d47a-cede-0000-526563436d64"  # us -> device (write)

TYPE_FLOAT = 0x1130
TYPE_INT16 = 0x1112
HEADER_SIZE = 6

# Order comes from the Edge Impulse model, same mapping as BluetoothViewModel.kt
GESTURES = {0: "CLOSE", 1: "OPEN", 2: "PINCH", 3: "REST"}
GESTURE_COLORS = {"CLOSE": "#ff5252", "OPEN": "#00e676", "PINCH": "#ff9100", "REST": "#9e9e9e"}

HISTORY = 250  # samples on screen, 5 seconds at 50Hz
SERVO_SPEED = 150


def parse_packet(data: bytes):
    """Returns ([emg0, emg1, emg2], classification) from one TLV packet."""
    emg, gesture = [], -1
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
        offset += length
    return emg, gesture


def build_packet(message_id: int, emg, gesture: int) -> bytes:
    """Same layout the firmware sends. Only used by --simulate."""
    packet = bytes([0x01, 0x00, message_id & 0xFF, 0x10, 0x00, 30])
    for value in emg:
        packet += struct.pack(">HH", TYPE_FLOAT, 4) + struct.pack("<f", value)
    packet += struct.pack(">HH", TYPE_INT16, 2) + struct.pack("<h", gesture)
    return packet


class Controller:
    def __init__(self, simulate: bool):
        self.simulate = simulate
        self.client = None
        self.running = True
        self.status = "Starting..."
        self.gesture = "UNKNOWN"
        self.channels = [deque([0.0] * HISTORY, maxlen=HISTORY) for _ in range(3)]
        self.tasks = set()

    def on_packet(self, _sender, data: bytearray):
        emg, gesture = parse_packet(bytes(data))
        if len(emg) != 3:
            return
        for channel, value in zip(self.channels, emg):
            channel.append(value)
        self.gesture = GESTURES.get(gesture, "UNKNOWN")

    async def send(self, command: str):
        print(f">> {command}")
        if self.simulate:
            return
        if self.client is None or not self.client.is_connected:
            print("   not connected, command dropped")
            return
        try:
            await self.client.write_gatt_char(CHAR_RX_UUID, command.encode(), response=True)
        except Exception as e:
            print(f"   send failed: {e}")

    def send_soon(self, command: str):
        task = asyncio.get_running_loop().create_task(self.send(command))
        self.tasks.add(task)
        task.add_done_callback(self.tasks.discard)

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
                    while self.running and client.is_connected:
                        await asyncio.sleep(0.2)
            except Exception as e:
                print(f"Connection error: {e}")
            self.client = None
            self.status = "Disconnected"
            await asyncio.sleep(1.0)

    async def run_simulated(self):
        self.status = "Simulated data (no hardware)"
        message_id, t = 0, 0.0
        while self.running:
            # Cycle through the gestures every 3 seconds with matching fake activity
            gesture = int(t / 3) % 4
            level = {0: 1800, 1: 900, 2: 1300, 3: 150}[gesture]
            emg = [max(0.0, level * (0.6 + 0.4 * math.sin(t * 9 + i)) + random.gauss(0, 60)) for i in range(3)]
            self.on_packet(None, bytearray(build_packet(message_id, emg, gesture)))
            message_id += 1
            t += 0.02
            await asyncio.sleep(0.02)

    async def run_ui(self):
        plt.ion()
        fig, ax = plt.subplots(figsize=(10, 6))
        fig.canvas.manager.set_window_title("Nudge Mac Controller")
        fig.subplots_adjust(bottom=0.22, top=0.86)

        lines = [ax.plot(range(HISTORY), list(ch), label=f"EMG D{i}")[0] for i, ch in enumerate(self.channels)]
        ax.set_ylim(0, 4095)  # 12-bit ADC
        ax.set_xlim(0, HISTORY - 1)
        ax.set_xticks([])
        ax.set_ylabel("Raw ADC")
        ax.legend(loc="upper left")
        status_text = fig.text(0.02, 0.95, "", fontsize=10)
        gesture_text = fig.text(0.98, 0.93, "", fontsize=22, fontweight="bold", ha="right")

        # Kept in a list so the widgets are not garbage collected
        buttons = []
        commands = [
            ("AI on", "ai_start"),
            ("AI off", "ai_stop"),
            ("Grasp", f"grasp 255 {SERVO_SPEED}"),
            ("Retract", f"retract 255 {SERVO_SPEED}"),
            ("Engage", "engage"),
            ("Disengage", "disengage"),
            ("STOP", "stop"),
        ]
        width = 0.9 / len(commands)
        for i, (label, command) in enumerate(commands):
            button_ax = fig.add_axes([0.05 + i * width, 0.05, width - 0.01, 0.08])
            button = Button(button_ax, label, color="#ffcdd2" if command == "stop" else "0.9")
            button.on_clicked(lambda _event, c=command: self.send_soon(c))
            buttons.append(button)

        fig.canvas.mpl_connect("close_event", lambda _event: setattr(self, "running", False))
        fig.show()

        while self.running:
            for line, channel in zip(lines, self.channels):
                line.set_ydata(list(channel))
            status_text.set_text(self.status)
            gesture_text.set_text(self.gesture)
            gesture_text.set_color(GESTURE_COLORS.get(self.gesture, "#616161"))
            fig.canvas.draw_idle()
            fig.canvas.flush_events()
            await asyncio.sleep(0.04)

    async def run(self):
        source = asyncio.create_task(self.run_simulated() if self.simulate else self.run_ble())
        try:
            await self.run_ui()
        finally:
            self.running = False
            # Leave the device safe: stop AI actuation before we go
            await self.send("ai_stop")
            source.cancel()
            await asyncio.gather(source, return_exceptions=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Nudge wearable controller for Mac")
    parser.add_argument("--simulate", action="store_true", help="use fake data instead of the wearable")
    args = parser.parse_args()
    asyncio.run(Controller(args.simulate).run())
