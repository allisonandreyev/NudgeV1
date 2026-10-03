# Nudge Software and Firmware

This directory centralizes the codebase for the Nudge ecosystem, including device firmware and auxiliary controller logic.

## Directory Structure

- XIAO_C6_Firmware: The primary Arduino firmware for the XIAO ESP32-C6 wearable. It streams the three EMG sensors (50Hz), runs the gesture model trained in the app, and controls the servos, all over a custom Binary TLV BLE protocol.
- NudgeController: Alternative development environment for PlatformIO/VS Code users.
- ServoController: Modular logic for high-precision servo movement and PCA9685 integration.
- MacController: Python tool that connects to the wearable from a Mac (live plot, gesture readout, servo buttons). Run with `uv run nudge_mac.py`, or `--simulate` without hardware.

## Firmware Requirements

- Arduino IDE or PlatformIO
- ESP32 Board Manager (v3.0.0+)
- Adafruit PWM Servo Driver Library
- Adafruit BusIO Library

The Edge Impulse library is no longer needed.

## Gesture Model

Gestures are learned on the phone, not on a website. The app's **Train AI** screen records each gesture a few times, trains a small linear discriminant model in about a second, and sends it to the wearable over Bluetooth. The wearable saves it to flash (`GestureModel.cpp`), so it survives power cycles. The app keeps each user's model and re-sends it automatically if the wearable has a different one.

- Features: for each sensor, the level, spread and rate of change over the last 200 ms (10 readings).
- Quick check: instead of retraining after re-fitting the wearable, the app runs a 15-second relax/fist/open check and updates a per-sensor gain and offset that the wearable applies before classifying. The trained model itself is unchanged.
- The wearable reports the installed model's id in every packet, so the app knows what it has.
- `GestureModel.cpp` must compute exactly what `App/.../ai/GestureModel.kt` computes. `XIAO_C6_Firmware/test` checks this on a computer.
