#pragma once
#include <Arduino.h>
#include "ADS1298Frame.h"
#include "BoardConfig.h"

/**
 * Driver for the TI ADS1298 on the 16-pad EMG strip (8 differential channels).
 * Wiring, pad map and bring-up steps: WIRING.md.
 *
 * After Begin(), a background task reads every conversion on DRDY, filters it and
 * produces 50 Hz envelope frames (microvolts per channel) for the gesture model.
 */
namespace ADS1298
{
    constexpr int CHANNELS = ADS1298Frame::CHANNELS;

    // --- Pins: set in BoardConfig.h (-1 = tied on the strip's header instead of driven) ---
    constexpr int PIN_SCLK = Board::PIN_STRIP_SCLK;
    constexpr int PIN_DIN = Board::PIN_STRIP_DIN;
    constexpr int PIN_CS = Board::PIN_STRIP_CS;
    constexpr int PIN_DOUT = Board::PIN_STRIP_DOUT;
    constexpr int PIN_START = Board::PIN_STRIP_START;
    constexpr int PIN_DRDY = Board::PIN_STRIP_DRDY;
    constexpr int PIN_PWDN = Board::PIN_STRIP_PWDN;
    constexpr int PIN_RESET = Board::PIN_STRIP_RESET;
    constexpr int PIN_CLKSEL = Board::PIN_STRIP_CLKSEL;

    // --- Signal settings --------------------------------------------------------------
    constexpr int SAMPLE_RATE = 250;  // 250 (low-power mode) or 500 (high-resolution mode)
    constexpr int GAIN = 6;           // PGA gain: 1, 2, 3, 4, 6, 8 or 12. 6 is the chip default.
    constexpr float VREF = 2.4f;      // internal reference; 4 V needs a 5 V supply
    constexpr float MAINS_HZ = 60;    // 50 in Europe, 0 to turn the notch off
    constexpr uint32_t SPI_HZ = 4000000;

    /** What the channel inputs are connected to. Everything but Electrodes is for bring-up. */
    enum class InputMode : uint8_t
    {
        Electrodes = 0x0,   // normal
        Shorted = 0x1,      // inputs shorted together: shows the noise floor, should be near 0 uV
        Supply = 0x3,       // measures the supply (MVDD): a large, steady value
        Temperature = 0x4,  // on-chip temperature sensor: steady, about 145 mV at 25 C
        TestSignal = 0x5    // internal ~1 Hz square wave (see WIRING.md caveat)
    };

    struct Stats
    {
        uint32_t frames;     // conversions read
        uint32_t badFrames;  // status word didn't start with 1100: SPI or wiring problem
        uint32_t dropped;    // envelope frames lost because nothing consumed them
        float rateHz;        // measured conversions per second over the last report
        float peakToPeakUv[CHANNELS];  // raw signal swing over the last report
        float rmsUv[CHANNELS];         // filtered signal level over the last report
    };

    /** Powers up, checks the chip ID, configures and starts streaming. Prints progress to Serial. */
    bool Begin();

    /** Device ID register; 0x92 for ADS1298, 0xD2 for ADS1298R. */
    uint8_t DeviceId();

    bool SetInputMode(InputMode mode);
    InputMode CurrentMode();

    /** Waits up to [timeoutMs] for the next 50 Hz envelope frame (microvolts per channel). */
    bool NextEnvelope(float out[CHANNELS], uint32_t timeoutMs);

    /** Statistics since the previous call. */
    Stats TakeStats();

    /**
     * Handles "ads ..." text commands (from BLE or Serial). Returns false if [cmd] isn't one.
     *   ads mode electrodes|shorted|supply|temp|test
     *   ads diag on|off     print signal stats to Serial every second
     */
    bool HandleCommand(const String& cmd);

    /** Prints stats to Serial once a second if diagnostics are on. Call from loop(). */
    void PrintDiagnostics();
}
