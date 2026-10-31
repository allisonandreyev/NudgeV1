# Nudge Software and Firmware

This directory centralizes the codebase for the Nudge ecosystem, including device firmware and auxiliary controller logic.

## Directory Structure

- Nudge_Firmware: The Arduino firmware for the Nudge v2 rev2 board (`Hardware/KiCad/NudgeV2Rev2`: XIAO ESP32-S3, one MyoWare 2.0, one DC motor with encoder on a DRV8833 winding the finger line, battery, status LED). It turns the MyoWare's envelope into 50 Hz frames, runs the gesture model trained in the app (squeeze closes the hand, relaxing opens it), drives the motor (stopping on a stall or after 2 s, and on a low battery), and talks to the app over a custom Binary TLV BLE protocol. All pins are in `BoardConfig.h`; wiring and bring-up are in `Nudge_Firmware/WIRING.md`. Firmware for the 16-pad ADS1298 strip is on the `16-pad-emg-strip` branch.
- MacController: Python tool that connects to the wearable from a Mac: live plot, gesture readout, motor buttons, battery, and the same Train AI and Quick check as the app (no Android phone needed). Run with `uv run nudge_mac.py`, or `--simulate` without hardware. Tests: `uv run --with numpy --with pytest --with matplotlib --with bleak pytest -q`.
- NudgeController, ServoController: earlier servo (PCA9685) prototypes, kept for reference. The servo version of the main firmware is in git history (commit 21a1c1b, folder `XIAO_C6_Firmware`).

## Firmware Requirements

- Arduino IDE or `arduino-cli`, board **XIAO_ESP32S3** (`esp32:esp32:XIAO_ESP32S3`)
- ESP32 Board Manager (v3.0.0+)
- No extra libraries (Edge Impulse and the Adafruit servo libraries are no longer used)

## Gesture Model

Gestures are learned on the phone, not on a website. The app's **Train AI** screen records each gesture a few times, trains a small linear discriminant model in about a second, and sends it to the wearable over Bluetooth. The wearable saves it to flash (`GestureModel.cpp`), so it survives power cycles. The app keeps each user's model and re-sends it automatically if the wearable has a different one.

- Features: for each channel, the level, spread and rate of change of the envelope over the last 200 ms (10 frames). With one MyoWare (rev2) the model learns two gestures, squeeze and relax, because a single sensor can't reliably tell an open hand or a pinch apart; with several sensors it learns close, open, pinch and relax. The app and Mac controller handle any channel count, and ask the user to retrain if the model was made for a different sensor setup.
- Quick check: instead of retraining after re-fitting the wearable, the app runs a 15-second relax/fist/open check and updates a per-sensor gain and offset that the wearable applies before classifying. The trained model itself is unchanged.
- The wearable reports the installed model's id in every packet, so the app knows what it has.
- `GestureModel.cpp` must compute exactly what `App/.../ai/GestureModel.kt` computes. `XIAO_C6_Firmware/test` checks this on a computer.
