# Firmware checks on a computer

## Frame decoding and EMG filter

```
clang++ -std=c++17 -I .. dsp_test.cpp -o dsp_test && ./dsp_test
```

Checks ADS1298 frame decoding (24-bit sign extension, status word) and that the filter
removes drift and mains hum while passing the EMG band, at 250 and 500 SPS.

## Motor control

```
clang++ -std=c++17 -I .. motor_test.cpp -o motor_test && ./motor_test
```

Runs `MotorAxis` against a simulated motor with end stops: homing, learning the travel,
moving to a position, stopping on a grip signal or a stall, and timed moves without an encoder.

## Gesture model cross-check

Checks that the firmware's `GestureModel.cpp` installs a model sent by the app and
classifies exactly like the app's `GestureModel.kt`. Runs on a computer; no board needed.

1. Generate the fixture from the app's unit tests (in `App/android`):

   ```
   ./gradlew testDebugUnitTest
   ```

   This writes `App/android/app/build/firmware-fixture/fixture.txt`.

2. Build and run the check (in this folder):

   ```
   clang++ -std=c++17 -I shim -I .. model_test.cpp ../GestureModel.cpp -o model_test
   ./model_test ../../../App/android/app/build/firmware-fixture/fixture.txt
   ```

   Expect `0 mismatches`. The `shim` folder stands in for the Arduino and flash APIs.
