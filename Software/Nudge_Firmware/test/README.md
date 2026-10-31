# Firmware checks on a computer

## Motor control

```
clang++ -std=c++17 -I .. motor_test.cpp -o motor_test && ./motor_test
```

Runs `MotorAxis` against a simulated motor: homing against an end stop, learning the travel,
moving to a position, stopping on a grip signal or a stall, timed moves without an encoder,
and the rev2 spool (no end stop when opening: homing doesn't move, opening counts back to 0).

## Gesture model cross-check

Checks that the firmware's `GestureModel.cpp` installs a model sent by the app and
classifies exactly like the app's `GestureModel.kt`. Runs on a computer; no board needed.

1. Generate the fixture from the app's unit tests (in `App/android`):

   ```
   ./gradlew testDebugUnitTest
   ```

   This writes `App/android/app/build/firmware-fixture/fixture.txt`: a one-sensor
   (MyoWare) squeeze/relax model, like the rev2 firmware uses.

2. Build and run the check (in this folder):

   ```
   clang++ -std=c++17 -I shim -I .. model_test.cpp ../GestureModel.cpp -o model_test
   ./model_test ../../../App/android/app/build/firmware-fixture/fixture.txt
   ```

   Expect `0 mismatches`. The `shim` folder stands in for the Arduino and flash APIs.
