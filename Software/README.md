# Nudge Software and Firmware

This directory centralizes the codebase for the Nudge ecosystem, including device firmware and auxiliary controller logic.

## Directory Structure

- XIAO_C6_Firmware: The primary Arduino firmware for the XIAO ESP32-C6 wearable. It handles triple EMG sensor streaming (50Hz) and remote servo control via a custom Binary TLV BLE protocol.
- NudgeController: Alternative development environment for PlatformIO/VS Code users.
- ServoController: Modular logic for high-precision servo movement and PCA9685 integration.

## Firmware Requirements

- Arduino IDE or PlatformIO
- ESP32 Board Manager (v3.0.0+)
- Adafruit PWM Servo Driver Library
- Adafruit BusIO Library
