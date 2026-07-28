# BLE Integration Plan

The goal is to move from the mock UI in `NudgeV1` to a functional BLE implementation by porting logic from `BluetoothTestingApp` and adapting it to the advanced `NimBLE` firmware.

## Proposed Changes

### [Android App]

#### [NEW] [Packet.kt](file:///C:/Users/Aditya/AndroidStudioProjects/NudgeV1/App/android/app/src/main/java/com/nudge/app/bluetooth/Packet.kt)
- Define `TypeCode` enum matching the C++ firmware.
- Create `Packet` data class to hold header and payload.
- Implement `PacketDeserializer` class:
    - Handle segment reassembly using a buffer for each `messageID`.
    - Decode TLV (Type-Length-Value) structures from the payload.
    - Support primitives (Int, UInt, Float, Bool, etc.) and Strings.

#### [BluetoothLeService.kt](file:///C:/Users/Aditya/AndroidStudioProjects/NudgeV1/App/android/app/src/main/java/com/nudge/app/bluetooth/BluetoothLeService.kt)
- Integrate `PacketDeserializer`.
- Emit decoded `Packet` objects through a SharedFlow.

#### [BluetoothViewModel.kt](file:///NEW)
- Create a new ViewModel to bridge the `BluetoothLeService` and the UI.
- Handle frequency calculations and data history for the EMG graph.

#### [DeviceSelectScreen.kt](file:///C:/Users/Aditya/AndroidStudioProjects/NudgeV1/App/android/app/src/main/java/com/nudge/app/ui/DeviceSelectScreen.kt)
- Connect the UI to the `BluetoothViewModel`.
- Replace the hardcoded list with real discovered devices.
- Trigger connection when a device is tapped.

#### [SummaryScreen.kt](file:///C:/Users/Aditya/AndroidStudioProjects/NudgeV1/App/android/app/src/main/java/com/nudge/app/ui/SummaryScreen.kt)
- Update the graph to show live data received from the BLE service.

---

### [Firmware Alignment]
- Ensure the Android app uses the UUIDs defined in `BLESystem.ino`:
    - Service TX: `000B1E53-D47A-CEDE-DE57-000000008488`
    - Characteristic SendEMGData: `00008488-D47A-CEDE-0000-466178454d47`

## Verification Plan

### Manual Verification
1. **Scanning**: Launch the app, navigate to the Device Selection screen, and verify that the ESP32 (running `BLESystem.ino`) appears in the list.
2. **Connection**: Tap the device and verify the connection state changes.
3. **Data Stream**: Navigate to the Summary screen and verify the graph updates with live values.
4. **Stability**: Monitor logcat for any GATT errors or packet parsing failures.
