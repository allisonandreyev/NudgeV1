#pragma once
#include <math.h>
#include <stdint.h>

/**
 * Turns raw EMG samples into the 50 Hz envelope the gesture model uses.
 * No Arduino dependencies, so it can be tested on a computer (see test/).
 *
 * Per channel: 4th-order high-pass at 20 Hz (removes electrode drift and motion),
 * 2nd-order low-pass at 100 Hz, notches at the mains frequency and its harmonics
 * below Nyquist, then rectify and average over 20 ms (mean absolute value).
 */
namespace EmgFilter
{
    constexpr float PI_F = 3.14159265358979f;

    /** One second-order section (transposed direct form II). Coefficients from the RBJ cookbook. */
    struct Biquad
    {
        float b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0;
        float z1 = 0, z2 = 0;

        float Process(float x)
        {
            float y = b0 * x + z1;
            z1 = b1 * x - a1 * y + z2;
            z2 = b2 * x - a2 * y;
            return y;
        }

        void Reset() { z1 = z2 = 0; }

        static Biquad Make(float b0, float b1, float b2, float a0, float a1, float a2)
        {
            Biquad q;
            q.b0 = b0 / a0; q.b1 = b1 / a0; q.b2 = b2 / a0;
            q.a1 = a1 / a0; q.a2 = a2 / a0;
            return q;
        }

        static Biquad HighPass(float fc, float fs, float Q)
        {
            float w = 2 * PI_F * fc / fs, c = cosf(w), alpha = sinf(w) / (2 * Q);
            return Make((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha);
        }

        static Biquad LowPass(float fc, float fs, float Q)
        {
            float w = 2 * PI_F * fc / fs, c = cosf(w), alpha = sinf(w) / (2 * Q);
            return Make((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha);
        }

        static Biquad Notch(float f0, float fs, float Q)
        {
            float w = 2 * PI_F * f0 / fs, c = cosf(w), alpha = sinf(w) / (2 * Q);
            return Make(1, -2 * c, 1, 1 + alpha, -2 * c, 1 - alpha);
        }
    };

    /** Band-pass plus mains notches for one channel. */
    class ChannelFilter
    {
    public:
        static constexpr int MAX_STAGES = 6;

        void Init(float fs, float mainsHz)
        {
            count = 0;
            // 4th-order Butterworth high-pass as two sections
            stages[count++] = Biquad::HighPass(20, fs, 0.5412f);
            stages[count++] = Biquad::HighPass(20, fs, 1.3066f);
            if (100 < fs * 0.45f) stages[count++] = Biquad::LowPass(100, fs, 0.7071f);
            // Mains hum and harmonics that fall inside the usable band
            for (float f = mainsHz; mainsHz > 0 && f < fs * 0.48f && count < MAX_STAGES; f += mainsHz)
            {
                stages[count++] = Biquad::Notch(f, fs, 10);
            }
        }

        float Process(float x)
        {
            for (int i = 0; i < count; i++) x = stages[i].Process(x);
            return x;
        }

        int Stages() const { return count; }

    private:
        Biquad stages[MAX_STAGES];
        int count = 0;
    };

    /**
     * Filters every channel and emits one envelope frame (mean absolute value per channel)
     * every fs / ENVELOPE_HZ samples.
     */
    template <int CHANNELS>
    class Envelope
    {
    public:
        static constexpr int ENVELOPE_HZ = 50;

        void Init(float fs, float mainsHz)
        {
            for (int c = 0; c < CHANNELS; c++) filters[c].Init(fs, mainsHz);
            block = (int)(fs / ENVELOPE_HZ + 0.5f);
            Clear();
        }

        /** Adds one sample per channel. Returns true when [out] holds a new envelope frame. */
        bool Push(const float in[CHANNELS], float out[CHANNELS])
        {
            for (int c = 0; c < CHANNELS; c++) sum[c] += fabsf(filters[c].Process(in[c]));
            if (++n < block) return false;
            for (int c = 0; c < CHANNELS; c++) out[c] = sum[c] / n;
            Clear();
            return true;
        }

    private:
        void Clear()
        {
            n = 0;
            for (int c = 0; c < CHANNELS; c++) sum[c] = 0;
        }

        ChannelFilter filters[CHANNELS];
        float sum[CHANNELS];
        int block = 5;
        int n = 0;
    };
}
