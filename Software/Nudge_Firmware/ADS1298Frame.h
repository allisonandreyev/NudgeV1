#pragma once
#include <stdint.h>

/**
 * Decoding of one ADS1298 continuous-read frame. No Arduino dependencies, so it can be
 * tested on a computer (see test/).
 *
 * Frame: 24-bit status word, then 8 channels of 24-bit two's-complement samples, MSB first.
 * The status word always starts with binary 1100.
 */
namespace ADS1298Frame
{
    constexpr int CHANNELS = 8;
    constexpr int BYTES = 3 + CHANNELS * 3;  // 27

    struct Frame
    {
        uint32_t status;
        int32_t channel[CHANNELS];
    };

    inline int32_t SignExtend24(uint32_t v)
    {
        return (v & 0x800000) ? (int32_t)(v | 0xFF000000) : (int32_t)v;
    }

    /** Returns false if the status word doesn't have the expected 1100 prefix (bad framing). */
    inline bool Decode(const uint8_t raw[BYTES], Frame& out)
    {
        out.status = ((uint32_t)raw[0] << 16) | ((uint32_t)raw[1] << 8) | raw[2];
        for (int c = 0; c < CHANNELS; c++)
        {
            const uint8_t* p = raw + 3 + c * 3;
            out.channel[c] = SignExtend24(((uint32_t)p[0] << 16) | ((uint32_t)p[1] << 8) | p[2]);
        }
        return (raw[0] & 0xF0) == 0xC0;
    }

    /** Converts a sample to microvolts at the electrodes: LSB = 2 * VREF / gain / 2^24. */
    inline float ToMicrovolts(int32_t code, float vref, int gain)
    {
        return (float)code * (2.0f * vref / (float)gain / 16777216.0f) * 1e6f;
    }
}
