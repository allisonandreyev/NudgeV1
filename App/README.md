# Nudge Android Application

This directory contains the source code for the Nudge mobile application, a native Android platform built with Jetpack Compose for real-time EMG biofeedback and clinical management.

## Key Features

- Real-Time Visualization: High-speed neon EMG graphing for three independent channels.
- Guided Therapy: Interactive movement cycles with biofeedback and session recording.
- Physician Portal: Dedicated dashboard for clinicians to manage patient connections and analyze performance data.
- Production Security: Full-disk database encryption (SQLCipher) and secure credential hashing (BCrypt).
- Account Personalization: User-specific data storage and high-score tracking.

## Technical Stack

- Language: Kotlin
- UI Framework: Jetpack Compose
- Dependency Injection: Hilt
- Database: Room with SQLCipher Encryption
- Communication: Bluetooth Low Energy (BLE) using custom TLV packets

## Security and Privacy

- Encryption: 128-bit hardware-backed keys managed via Android Keystore.
- Compliance: Designed with professional data privacy and account deletion protocols.
- Persistence: All data points, therapy sessions, and physician links are stored securely in a local relational database.
