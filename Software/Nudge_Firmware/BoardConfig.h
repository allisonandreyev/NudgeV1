#pragma once
#include <Arduino.h>

/**
 * Wiring of the Nudge v2 rev2 board (Hardware/KiCad/NudgeV2Rev2) with a Seeed XIAO ESP32-S3
 * and a MyoWare 2.0 muscle sensor. Everything board-specific lives here.
 * Build with board "XIAO_ESP32S3" (esp32:esp32:XIAO_ESP32S3).
 */
namespace Board
{
    // --- Motor: DRV8833 (U2) motor A, powered from the 9 V boost (U3) ----------------------
    // Motor B (D8/D9, J6) is on the board but not fitted.
    constexpr int MOTOR_COUNT = 1;
    constexpr int PIN_MOTOR_EN = D6;                   // DRV8833 SLEEP; R7 pulls it low (asleep)
    constexpr int PIN_MOTOR_IN1[MOTOR_COUNT] = {D4};   // AIN1
    constexpr int PIN_MOTOR_IN2[MOTOR_COUNT] = {D5};   // AIN2

    // Encoder channel A only (J5 pin 5, 10k pull-up R5), so direction comes from the drive
    // command. -1 = no encoder: moves are timed instead.
    constexpr int PIN_ENCODER[MOTOR_COUNT] = {D7};

    // Which way the motor turns to wind the line in (close the hand): +1 or -1.
    constexpr int CLOSE_DIRECTION[MOTOR_COUNT] = {+1};

    // The motor winds a line on a spool and elastic cords open the hand, so there's no end
    // stop when opening: the hand is assumed open at power-up and "open" means back to count 0.
    // Closing ends at the target or when the motor stalls (hand closed or holding something).
    constexpr bool OPEN_END_STOP = false;
    constexpr uint32_t MOVE_TIMEOUT_MS = 2000;  // stop any single move after this long
    constexpr uint32_t STALL_MS = 150;          // no encoder pulses for this long while driving = stalled

    // --- EMG: MyoWare 2.0 on J2 (pin 1 3V3, 2 GND, 3 ENV) ----------------------------------
    constexpr int EMG_CHANNELS = 1;
    constexpr int PIN_EMG = D0;          // ENV output, 0 V relaxed, rising with effort (ADC1)

    // --- Sensors ------------------------------------------------------------------------
    constexpr int PIN_BATTERY = D3;      // VBAT through a 100k/100k divider (R3/R4)
    constexpr float BATTERY_DIVIDER = 2.0f;
    constexpr float BATTERY_EMPTY_V = 3.3f;   // below this the motor is stopped
    constexpr float BATTERY_LOW_V = 3.5f;     // below this the LED warns
    constexpr float BATTERY_FULL_V = 4.2f;

    // Force sensors J3/J4 (D1/D2) are on the board but not used on rev2. -1 = not fitted.
    constexpr int PIN_FSR_THUMB = -1;
    constexpr int PIN_FSR_INDEX = -1;

    // --- Status LED: the XIAO's user LED, on when LOW ----------------------------------------
    constexpr int PIN_LED = 21;
}
