#pragma once
#include <Arduino.h>

/**
 * The hand's DC motor (DRV8833 on the rev2 board), battery and status LED, plus force
 * sensors on boards that have them. Motor count and pins: BoardConfig.h.
 *
 * Battery: below BATTERY_LOW_V the LED flashes; below BATTERY_EMPTY_V the motor stops,
 * the LED blinks slowly and the hand may open but not close until it's charged.
 *
 * Text commands (from the app over BLE, or the Serial Monitor):
 *   grasp [amount 0-255] [speed]      close the hand (amount 255 = fully)
 *   retract [amount] [speed]          open the hand
 *   motor <n> <percent closed> [speed]   move one motor
 *   stop                              stop and put the driver to sleep
 *   calibrate                         re-learn each motor's travel (close until it stalls, then open)
 *   status                            print positions, sensors and battery
 *   ai_start / ai_stop                let detected gestures move the hand, or not
 * Speeds are in the app's units (10-300); 300 = full power.
 */
namespace MotorController
{
    /** Sets up pins, loads the saved travel, homes the motors (see OPEN_END_STOP). */
    void Init();

    bool ParseCommand(const String& cmd);

    void MoveGrasp(float amount = 255, float speed = 300);
    void MoveRetract(float amount = 255, float speed = 300);
    void StopAll();

    void EnableAIActuation(bool enable);
    bool IsAIActuationEnabled();

    /** 0-100, estimated from the battery voltage. */
    uint8_t BatteryPercent();

    /** True if the board has force sensors (rev2 doesn't use them). */
    bool HasForceSensors();

    /** Raw force-sensor readings 0-4095 (thumb, index); 0 if not fitted. */
    uint16_t ForceThumb();
    uint16_t ForceIndex();
}
