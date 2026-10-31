#pragma once
#include <Arduino.h>
#include "BoardConfig.h"

/**
 * MyoWare 2.0 muscle sensor on J2. Its ENV output is already rectified and smoothed:
 * 0 V when relaxed, rising as the muscle works. Gain is set with the sensor's own trimmer.
 *
 * After Begin(), a background task samples ENV at 1 kHz and averages every 20 ms into
 * 50 Hz frames (millivolts) for the gesture model.
 */
namespace MyoWare
{
    constexpr int CHANNELS = Board::EMG_CHANNELS;
    constexpr int SAMPLE_HZ = 1000;
    constexpr int FRAME_HZ = 50;

    /** Starts sampling. */
    bool Begin();

    /** Waits up to [timeoutMs] for the next 50 Hz frame (millivolts per channel). */
    bool NextEnvelope(float out[CHANNELS], uint32_t timeoutMs);

    /**
     * Handles "emg ..." text commands (from BLE or Serial). Returns false if [cmd] isn't one.
     *   emg diag on|off     print the signal level to Serial every second
     */
    bool HandleCommand(const String& cmd);

    /** Prints the signal level to Serial once a second if diagnostics are on. Call from loop(). */
    void PrintDiagnostics();
}
