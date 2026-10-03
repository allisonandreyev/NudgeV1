# Gesture model cross-check

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
