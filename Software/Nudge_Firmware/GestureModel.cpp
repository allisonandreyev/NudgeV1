#include "GestureModel.h"
#include <Preferences.h>
#include <math.h>

namespace GestureModel
{
    namespace
    {
        // Parameter layout: see PARAMS in GestureModel.h
        struct Model
        {
            uint16_t id;
            float p[PARAMS];
        };

        Model active = {0, {0}};
        Model staging = {0, {0}};
        bool received[PARAMS];
        SemaphoreHandle_t lock = nullptr;

        float window[WINDOW][CHANNELS];
        int head = 0;
        int filled = 0;

        Preferences prefs;

        void Save()
        {
            prefs.begin("nudge", false);
            prefs.putBytes("model", &active, sizeof(active));
            prefs.end();
        }

        void Features(float out[FEATURES])
        {
            for (int c = 0; c < CHANNELS; c++)
            {
                // Oldest reading first, same order as the app
                float sum = 0;
                for (int i = 0; i < WINDOW; i++) sum += window[(head + i) % WINDOW][c];
                float mean = sum / WINDOW;

                float sq = 0, wl = 0, prev = 0;
                for (int i = 0; i < WINDOW; i++)
                {
                    float v = window[(head + i) % WINDOW][c];
                    sq += (v - mean) * (v - mean);
                    if (i > 0) wl += fabsf(v - prev);
                    prev = v;
                }
                out[c] = logf(1.0f + mean);
                out[CHANNELS + c] = logf(1.0f + sqrtf(sq / WINDOW));
                out[2 * CHANNELS + c] = logf(1.0f + wl / (WINDOW - 1));
            }
        }
    }

    void Init()
    {
        lock = xSemaphoreCreateMutex();
        prefs.begin("nudge", true);
        if (prefs.getBytesLength("model") == sizeof(Model))
        {
            prefs.getBytes("model", &active, sizeof(active));
            Serial.printf(">> Gesture model %u loaded\n", active.id);
        }
        else
        {
            Serial.println(">> No gesture model yet. Train one in the app.");
        }
        prefs.end();
    }

    bool HandleCommand(const String& cmd)
    {
        if (!cmd.startsWith("mdl")) return false;
        const char* s = cmd.c_str();

        if (cmd.startsWith("mdl_begin"))
        {
            unsigned id = 0;
            int count = 0;
            if (sscanf(s, "mdl_begin %u %d", &id, &count) != 2 || count != PARAMS || id == 0)
            {
                Serial.printf("Model upload rejected: expected %d parameters\n", PARAMS);
                return true;
            }
            staging.id = (uint16_t)id;
            memset(received, 0, sizeof(received));
            return true;
        }

        if (cmd.startsWith("mdl_end"))
        {
            float checksum = strtof(s + 7, nullptr);
            float sum = 0;
            for (int i = 0; i < PARAMS; i++)
            {
                if (!received[i])
                {
                    Serial.printf("Model upload incomplete: missing %d\n", i);
                    return true;
                }
                sum += staging.p[i];
            }
            if (fabsf(sum - checksum) > 1e-3f * (1.0f + fabsf(checksum)))
            {
                Serial.printf("Model upload corrupted: checksum %f vs %f\n", sum, checksum);
                return true;
            }
            xSemaphoreTake(lock, portMAX_DELAY);
            active = staging;
            xSemaphoreGive(lock);
            Save();
            Serial.printf(">> Gesture model %u installed\n", active.id);
            return true;
        }

        if (cmd.startsWith("mdl_clear"))
        {
            xSemaphoreTake(lock, portMAX_DELAY);
            active.id = 0;
            xSemaphoreGive(lock);
            Save();
            Serial.println(">> Gesture model cleared");
            return true;
        }

        // mdl <offset> <values...>
        char* end;
        long offset = strtol(s + 3, &end, 10);
        for (int i = offset; i < PARAMS; i++)
        {
            char* next;
            float v = strtof(end, &next);
            if (next == end) break;
            staging.p[i] = v;
            received[i] = true;
            end = next;
        }
        return true;
    }

    void Push(const float values[CHANNELS])
    {
        float gain[CHANNELS], offset[CHANNELS];
        xSemaphoreTake(lock, portMAX_DELAY);
        bool hasModel = active.id != 0;
        memcpy(gain, active.p, sizeof(gain));
        memcpy(offset, active.p + CHANNELS, sizeof(offset));
        xSemaphoreGive(lock);

        for (int c = 0; c < CHANNELS; c++)
        {
            float v = hasModel ? gain[c] * values[c] + offset[c] : values[c];
            window[head][c] = v < 0 ? 0 : v;
        }
        head = (head + 1) % WINDOW;
        if (filled < WINDOW) filled++;
    }

    bool Predict(int& gesture, float& probability)
    {
        if (filled < WINDOW || lock == nullptr) return false;

        Model m;
        xSemaphoreTake(lock, portMAX_DELAY);
        m = active;
        xSemaphoreGive(lock);
        if (m.id == 0) return false;

        float f[FEATURES];
        Features(f);

        const float* mean = m.p + 2 * CHANNELS;
        const float* scale = mean + FEATURES;
        const float* weights = scale + FEATURES;
        const float* bias = weights + CLASSES * FEATURES;

        float scores[CLASSES];
        int best = 0;
        for (int k = 0; k < CLASSES; k++)
        {
            float s = bias[k];
            for (int i = 0; i < FEATURES; i++) s += weights[k * FEATURES + i] * (f[i] - mean[i]) / scale[i];
            scores[k] = s;
            if (s > scores[best]) best = k;
        }
        float total = 0;
        for (int k = 0; k < CLASSES; k++) total += expf(scores[k] - scores[best]);

        gesture = best;
        probability = 1.0f / total;
        return true;
    }

    uint16_t ModelId()
    {
        return active.id;
    }
}
