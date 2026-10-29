// Checks ADS1298 frame decoding and the EMG filter on a computer.
// Build and run: see README in this folder.
#include "../ADS1298Frame.h"
#include "../EmgFilter.h"
#include <cmath>
#include <cstdio>
#include <initializer_list>

static int failures = 0;
#define CHECK(cond, ...) do { if (!(cond)) { failures++; std::printf("FAIL: " __VA_ARGS__); std::printf("\n"); } } while (0)

/** Steady-state gain of one channel filter for a sine at [hz]. */
static float Gain(float hz, float fs, float mains)
{
    EmgFilter::ChannelFilter f;
    f.Init(fs, mains);
    float peak = 0;
    int n = (int)(fs * 4);
    for (int i = 0; i < n; i++)
    {
        float y = f.Process(std::sin(2 * EmgFilter::PI_F * hz * i / fs));
        if (i > n / 2) peak = std::fmax(peak, std::fabs(y));  // skip the settling half
    }
    return peak;
}

int main()
{
    // --- Frame decoding ---
    uint8_t raw[ADS1298Frame::BYTES] = {0xC0, 0x00, 0x00};
    const int32_t expected[8] = {0, 1, -1, 8388607, -8388608, 123456, -654321, 256};
    for (int c = 0; c < 8; c++)
    {
        uint32_t v = (uint32_t)expected[c] & 0xFFFFFF;
        raw[3 + c * 3] = v >> 16;
        raw[4 + c * 3] = v >> 8;
        raw[5 + c * 3] = v;
    }
    ADS1298Frame::Frame frame;
    CHECK(ADS1298Frame::Decode(raw, frame), "valid status rejected");
    for (int c = 0; c < 8; c++) CHECK(frame.channel[c] == expected[c], "channel %d: %d != %d", c, frame.channel[c], expected[c]);
    raw[0] = 0x00;
    CHECK(!ADS1298Frame::Decode(raw, frame), "bad status accepted");

    // Full scale at gain 6 is +/- VREF / 6 = 400 mV
    float fs_uv = ADS1298Frame::ToMicrovolts(8388607, 2.4f, 6);
    CHECK(std::fabs(fs_uv - 400000) < 1, "full scale %.1f uV", fs_uv);

    // --- Filter response at 250 and 500 SPS ---
    for (float fs : {250.0f, 500.0f})
    {
        float dc = Gain(0.001f, fs, 60), hum = Gain(60, fs, 60), low = Gain(5, fs, 60);
        float g40 = Gain(40, fs, 60), g85 = Gain(85, fs, 60);
        std::printf("fs %.0f: 0 Hz %.3f, 5 Hz %.3f, 40 Hz %.3f, 60 Hz %.3f, 85 Hz %.3f\n", fs, dc, low, g40, hum, g85);
        CHECK(dc < 0.01f, "DC passes at %.0f SPS", fs);
        CHECK(low < 0.05f, "5 Hz motion passes at %.0f SPS", fs);
        CHECK(hum < 0.05f, "60 Hz hum passes at %.0f SPS", fs);
        CHECK(g40 > 0.7f && g85 > 0.6f, "EMG band attenuated at %.0f SPS", fs);
    }
    float hum50 = Gain(50, 250, 50);
    CHECK(hum50 < 0.05f, "50 Hz notch for Europe");

    // --- Envelope: 50 Hz frames, mean absolute value ~ 2A/pi for a sine of amplitude A ---
    EmgFilter::Envelope<8> env;
    env.Init(250, 60);
    float in[8], out[8], last[8] = {0};
    int frames = 0;
    for (int i = 0; i < 250 * 3; i++)
    {
        for (int c = 0; c < 8; c++) in[c] = 100.0f * (c + 1) * std::sin(2 * EmgFilter::PI_F * 70 * i / 250.0f) + 5000;  // DC offset like a dry electrode
        if (env.Push(in, out))
        {
            frames++;
            for (int c = 0; c < 8; c++) last[c] = out[c];
        }
    }
    CHECK(frames == 150, "expected 50 frames/s, got %d in 3 s", frames);
    for (int c = 0; c < 8; c++)
    {
        float want = 2 / EmgFilter::PI_F * 100.0f * (c + 1);
        CHECK(std::fabs(last[c] - want) / want < 0.2f, "envelope ch%d %.1f vs %.1f", c, last[c], want);
    }

    std::printf("%s (%d failures)\n", failures ? "FAILED" : "all DSP checks passed", failures);
    return failures ? 1 : 0;
}
