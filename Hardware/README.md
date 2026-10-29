# Nudge Hardware

This directory contains the physical design files and electrical specifications for the Nudge wearable device.

## Contents

- CAD Models: 3D design files for the wearable chassis, electrode housings, and mechanical components.
- Electrical Diagrams: an early EMG test schematic.
- `KiCad/NudgePCB`: the current designs, from `Nudge Fall Internship.zip`:
  - `Nudge`: main board. J2 is now the 12-pin EMG strip header (was the MyoWare connector).
    The PCB is an unrouted placement draft.
  - `NudgeEMGStrip`: the 16-pad EMG strip. C7 replaced by a 0 Ω jumper (R2) so VCAP3 gets 1 µF.
  The git history shows each change against the original files.

## Component Specifications

- Microcontroller: Seeed XIAO ESP32-S3 (main board)
- EMG: 16-pad dry-electrode flex strip with a TI ADS1298 (8 differential channels, SPI) and a
  right-leg-drive reference pad. Replaces the earlier three MyoWare 2.0 sensors.
- Motors: two DC motors with single-channel encoders on an Adafruit DRV8833 driver, from a 9 V boost
- Sensors: thumb and index force sensors, battery voltage
- Communication: Bluetooth Low Energy (BLE)

Pin-by-pin wiring, the strip's pad map, and known schematic issues: `Software/Nudge_Firmware/WIRING.md`.
