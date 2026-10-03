# Nudge Android Application

This directory contains the source code for the Nudge mobile application, a native Android platform built with Jetpack Compose for real-time EMG biofeedback and clinical management.

## Key Features

- Live signals: the three EMG channels on one chart, plus the gesture the wearable detects.
- Guided therapy: timed relax/move repetitions, recorded and optionally shared with a clinician.
- Train AI: teaches the wearable the user's gestures in about 90 seconds, no external tools (see `Software/README.md`).
- Play: a flappy-bird game controlled by opening the hand.
- Care team: patients link to clinician accounts; clinicians see shared sessions and add notes.
- Demo device: "Try it without a device" on the welcome screen simulates a wearable, so every screen works in the emulator.

## Building

Open `App/android` in Android Studio (not the repo root), or from the command line in `App/android`:

```
./gradlew assembleDebug        # app at app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # gesture model tests
```

Needs JDK 17 and the Android SDK (API 34). A real phone is needed to connect to the wearable; the emulator has no Bluetooth.

## Technical Stack

- Language: Kotlin
- UI Framework: Jetpack Compose (Material 3, light and dark themes)
- Dependency Injection: Hilt
- Database: Room with SQLCipher Encryption
- Communication: Bluetooth Low Energy (BLE) using custom TLV packets

## Security and Privacy

- All accounts and data live on the phone in an encrypted database; there is no server.
- Passwords are hashed with BCrypt.
- Clinicians only see sessions the patient chose to share.
