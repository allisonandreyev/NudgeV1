# Nudge Hardware

This directory contains the physical design files and electrical specifications for the Nudge wearable device.

## Contents

- CAD Models: 3D design files for the wearable chassis, electrode housings, and mechanical components.
- Electrical Diagrams: an early EMG test schematic.
- `KiCad/NudgeV2Rev2`: **the board in use**, Nudge v2 rev2 (schematic, netlist, PDF, firmware
  notes). XIAO ESP32-S3, one MyoWare 2.0 on J2, one DC motor with encoder on a DRV8833, USB-C
  charging and a push-button power switch. No PCB layout file was included.
- `KiCad/NudgePCB`: the 16-pad EMG strip designs, from `Nudge Fall Internship.zip`
  (firmware for these is on the `16-pad-emg-strip` branch):
  - `Nudge`: main board. J2 is now the 12-pin EMG strip header (was the MyoWare connector).
    The PCB is an unrouted placement draft.
  - `NudgeEMGStrip`: the 16-pad EMG strip. C7 replaced by a 0 Ω jumper (R2) so VCAP3 gets 1 µF.
  The git history shows each change against the original files.

## Component Specifications

Rev2 board:

- Microcontroller: Seeed XIAO ESP32-S3
- EMG: one MyoWare 2.0 muscle sensor (analog envelope output)
- Motor: one N20 DC gear motor with a single-channel hall encoder, winding a line on a spool;
  elastic cords open the hand. DRV8833 driver with about 1 A current limit, from a 9 V boost
- Power: 1S LiPo, MCP73831 USB-C charger, MAX16054 push-button on/off
- Sensors: battery voltage (force-sensor connectors are fitted but unused)
- Communication: Bluetooth Low Energy (BLE)

Pin-by-pin wiring, the strip's pad map, and known schematic issues: `Software/Nudge_Firmware/WIRING.md`.
