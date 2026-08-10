# Nudge Hardware

This directory contains the physical design files and electrical specifications for the Nudge wearable device.

## Contents

- CAD Models: 3D design files for the wearable chassis, electrode housings, and mechanical components.
- Electrical Diagrams: Schematic and wiring diagrams for the XIAO ESP32-C6, Myoware 2.0 sensors, and the PCA9685 servo driver.

## Component Specifications

- Microcontroller: XIAO ESP32-C6
- EMG Sensors: Myoware 2.0 (Three channels: D0, D1, D2)
- Servo Driver: PCA9685 (I2C Address: 0x40)
- Communication: Bluetooth Low Energy (BLE)
- Power: External power supply required for servo high-current requirements.
