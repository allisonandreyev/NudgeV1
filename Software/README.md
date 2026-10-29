# Nudge Software and Firmware

This directory centralizes the codebase for the Nudge ecosystem, including device firmware and auxiliary controller logic.

## Directory Structure

- Nudge_Firmware: The Arduino firmware for the Nudge main board (`Nudge.kicad_sch`: XIAO ESP32-S3, two DC motors on a DRV8833 with encoders, thumb/index force sensors, battery) with the 16-pad EMG strip (TI ADS1298, 8 channels over SPI). It filters the EMG into 50 Hz muscle-activity envelopes, runs the gesture model trained in the app, drives the motors (stopping when the force sensors say the grip is firm), and talks to the app over a custom Binary TLV BLE protocol. All pins are in `BoardConfig.h`; wiring, pad map and bring-up are in `Nudge_Firmware/WIRING.md`.
- MacController: Python tool that connects to the wearable from a Mac: live plot, gesture readout, motor buttons, battery and grip, and the same Train AI and Quick check as the app (no Android phone needed). Run with `uv run nudge_mac.py`, or `--simulate` without hardware. Tests: `uv run --with numpy --with pytest --with matplotlib --with bleak pytest -q`.
- NudgeController, ServoController: earlier servo (PCA9685) prototypes, kept for reference. The servo version of the main firmware is in git history (commit 21a1c1b, folder `XIAO_C6_Firmware`).

## Firmware Requirements

- Arduino IDE or `arduino-cli`, board **XIAO_ESP32S3** (`esp32:esp32:XIAO_ESP32S3`)
- ESP32 Board Manager (v3.0.0+)
- No extra libraries (Edge Impulse and the Adafruit servo libraries are no longer used)

## Gesture Model

Gestures are learned on the phone, not on a website. The app's **Train AI** screen records each gesture a few times, trains a small linear discriminant model in about a second, and sends it to the wearable over Bluetooth. The wearable saves it to flash (`GestureModel.cpp`), so it survives power cycles. The app keeps each user's model and re-sends it automatically if the wearable has a different one.

- Features: for each of the 8 channels, the level, spread and rate of change of the envelope over the last 200 ms (10 frames). The app and Mac controller handle any channel count, so models trained on the old 3-sensor MyoWare setup are recognised and the user is asked to retrain.
- Quick check: instead of retraining after re-fitting the wearable, the app runs a 15-second relax/fist/open check and updates a per-sensor gain and offset that the wearable applies before classifying. The trained model itself is unchanged.
- The wearable reports the installed model's id in every packet, so the app knows what it has.
- `GestureModel.cpp` must compute exactly what `App/.../ai/GestureModel.kt` computes. `XIAO_C6_Firmware/test` checks this on a computer.
