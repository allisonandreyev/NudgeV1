#include "MyoWare.h"

namespace MyoWare
{
    namespace
    {
        constexpr int SAMPLES_PER_FRAME = SAMPLE_HZ / FRAME_HZ;

        struct Frame
        {
            float values[CHANNELS];
        };

        QueueHandle_t frames = nullptr;
        bool diagnostics = false;
        uint32_t lastReport = 0;

        // Diagnostics since the last report, written by the sampling task
        portMUX_TYPE statsLock = portMUX_INITIALIZER_UNLOCKED;
        uint32_t frameCount = 0;
        uint32_t dropped = 0;
        float sum = 0, low = 1e9f, high = 0;

        void SampleTask(void*)
        {
            TickType_t wake = xTaskGetTickCount();
            float acc = 0;
            int n = 0;
            for (;;)
            {
                vTaskDelayUntil(&wake, pdMS_TO_TICKS(1000 / SAMPLE_HZ));
                acc += analogReadMilliVolts(Board::PIN_EMG);  // calibrated, unlike analogRead
                if (++n < SAMPLES_PER_FRAME) continue;

                Frame f;
                f.values[0] = acc / n;
                acc = 0;
                n = 0;
                bool sent = xQueueSend(frames, &f, 0) == pdTRUE;

                portENTER_CRITICAL(&statsLock);
                frameCount++;
                if (!sent) dropped++;
                sum += f.values[0];
                low = fminf(low, f.values[0]);
                high = fmaxf(high, f.values[0]);
                portEXIT_CRITICAL(&statsLock);
            }
        }
    }

    bool Begin()
    {
        pinMode(Board::PIN_EMG, INPUT);
        analogSetPinAttenuation(Board::PIN_EMG, ADC_11db);  // full 0-3.3 V range
        frames = xQueueCreate(10, sizeof(Frame));
        xTaskCreate(SampleTask, "emg", 3072, nullptr, 4, nullptr);
        Serial.println(">> EMG: MyoWare on D0, sampling");
        return true;
    }

    bool NextEnvelope(float out[CHANNELS], uint32_t timeoutMs)
    {
        Frame f;
        if (frames == nullptr || xQueueReceive(frames, &f, pdMS_TO_TICKS(timeoutMs)) != pdTRUE) return false;
        memcpy(out, f.values, sizeof(f.values));
        return true;
    }

    bool HandleCommand(const String& cmd)
    {
        if (!cmd.startsWith("emg")) return false;
        if (cmd == "emg diag on") diagnostics = true;
        else if (cmd == "emg diag off") diagnostics = false;
        else Serial.println("emg diag on|off");
        return true;
    }

    void PrintDiagnostics()
    {
        uint32_t now = millis();
        if (!diagnostics || now - lastReport < 1000) return;
        lastReport = now;

        portENTER_CRITICAL(&statsLock);
        uint32_t count = frameCount, lost = dropped;
        float mean = count ? sum / count : 0, lo = low, hi = high;
        frameCount = dropped = 0;
        sum = 0; low = 1e9f; high = 0;
        portEXIT_CRITICAL(&statsLock);

        Serial.printf("EMG %u frames/s (dropped %u): mean %.0f mV, min %.0f, max %.0f\n", count, lost, mean, count ? lo : 0, hi);
    }
}
