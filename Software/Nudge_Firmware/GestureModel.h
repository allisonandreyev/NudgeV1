#pragma once
#include <Arduino.h>

/**
 * On-device gesture classifier trained by the Nudge app.
 *
 * The app trains a linear discriminant model and sends its parameters over BLE.
 * They are saved to flash, so the model survives power cycles and only changes
 * when the user retrains or recalibrates. Each sensor reading is first corrected with a
 * per-sensor gain and offset, which a quick recalibration in the app updates so the
 * model keeps working after re-fitting the wearable.
 * Features and scoring must match GestureModel.kt in the app.
 *
 * BLE commands (text, one per write):
 *   mdl_begin <id> <count>      start an upload, count must be ParamCount(2) or ParamCount(4)
 *   mdl <offset> <v> <v> ...    parameter values starting at offset
 *   mdl_end <checksum>          sum of all values; installs and saves if it matches
 *   mdl_clear                   forget the model
 */
namespace GestureModel
{
    constexpr int CHANNELS = 1;  // one MyoWare sensor (Board::EMG_CHANNELS)
    constexpr int WINDOW = 10;   // 200 ms of 50 Hz envelope frames
    constexpr int FEATURES = CHANNELS * 3;
    // Gestures, in the order the app trains them:
    //   4 = CLOSE, OPEN, PINCH, REST (several sensors)
    //   2 = CLOSE, REST (one sensor can only tell a squeeze from relaxing)
    // Index 0 and 2 close the hand, 1 and 3 open it.
    constexpr int MAX_CLASSES = 4;
    // gain[CHANNELS], offset[CHANNELS], mean[FEATURES], scale[FEATURES], weights[classes][FEATURES], bias[classes]
    constexpr int ParamCount(int classes) { return CHANNELS * 2 + FEATURES * 2 + classes * (FEATURES + 1); }
    constexpr int MAX_PARAMS = ParamCount(MAX_CLASSES);

    /** Load a saved model from flash. Call once in setup(). */
    void Init();

    /** Handle an mdl* command. Returns false if the text isn't a model command. */
    bool HandleCommand(const String& cmd);

    /** Add one raw reading (one value per channel); the model's correction is applied here. */
    void Push(const float values[CHANNELS]);

    /**
     * Classify the last WINDOW readings.
     * Returns false if there is no model or not enough readings yet.
     */
    bool Predict(int& gesture, float& probability);

    /** Id of the installed model, 0 if none. Reported to the app in every packet. */
    uint16_t ModelId();
}
