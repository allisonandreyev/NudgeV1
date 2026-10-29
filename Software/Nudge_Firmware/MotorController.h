#pragma once
#include <Arduino.h>

/**
 * The hand's two DC motors (DRV8833 on the main board), force sensors and battery.
 * Replaces the servo controller used on the earlier PCA9685 prototype.
 *
 * Text commands (from the app over BLE, or the Serial Monitor):
 *   grasp [amount 0-255] [speed]      close both motors (amount 255 = fully)
 *   retract [amount] [speed]          open both motors
 *   motor <1|2> <percent closed> [speed]   move one motor
 *   stop                              stop both motors and put the driver to sleep
 *   calibrate                         re-learn each motor's travel (open, then close until it stalls)
 *   status                            print positions, sensors and battery
 *   ai_start / ai_stop                let detected gestures move the hand, or not
 * Speeds are in the app's units (10-300); 300 = full power.
 */
namespace MotorController
{
    /** Sets up pins, loads the saved travel, homes both motors (opens the hand). */
    void Init();

    bool ParseCommand(const String& cmd);

    void MoveGrasp(float amount = 255, float speed = 300);
    void MoveRetract(float amount = 255, float speed = 300);
    void StopAll();

    void EnableAIActuation(bool enable);
    bool IsAIActuationEnabled();

    /** 0-100, estimated from the battery voltage. */
    uint8_t BatteryPercent();

    /** Raw force-sensor readings 0-4095 (thumb, index); 0 if not fitted. */
    uint16_t ForceThumb();
    uint16_t ForceIndex();
}
