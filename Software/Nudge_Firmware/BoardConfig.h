#pragma once
#include <Arduino.h>

/**
 * Wiring of the Nudge main board (Nudge.kicad_sch, Oct 11) with a Seeed XIAO ESP32-S3,
 * plus the 16-pad EMG strip (NudgeFlexStrip). Everything board-specific lives here.
 * Build with board "XIAO_ESP32S3" (esp32:esp32:XIAO_ESP32S3).
 */
namespace Board
{
    // --- Motors: Adafruit DRV8833 (U2), powered from the 9 V boost (U3) -------------------
    constexpr int MOTOR_COUNT = 2;
    constexpr int PIN_MOTOR_EN = D6;                   // DRV8833 SLP; R7 pulls it low (asleep) by default
    constexpr int PIN_MOTOR_IN1[MOTOR_COUNT] = {D4, D8};  // AIN1, BIN1
    constexpr int PIN_MOTOR_IN2[MOTOR_COUNT] = {D5, D9};  // AIN2, BIN2

    // Encoders: only channel A of each motor is wired (J5/J6 pin 5; pin 6 is unconnected),
    // so the firmware counts pulses and takes direction from the drive command.
    // -1 = no encoder: moves are timed instead.
    // Strip on the bottom pads (default) keeps both encoders; the bench option borrows them.

    // Which way each motor turns to close the hand: +1 or -1. Flip if a motor runs backwards.
    constexpr int CLOSE_DIRECTION[MOTOR_COUNT] = {+1, +1};

    // --- Sensors ------------------------------------------------------------------------
    constexpr int PIN_BATTERY = D3;      // VBAT through a 100k/100k divider (R3/R4)
    constexpr float BATTERY_DIVIDER = 2.0f;

    // Force sensors to 3V3 with 10k pull-downs (R1/R2): more pressure, higher reading

    // --- EMG strip (ADS1298 header J3 on the strip) ---------------------------------------
    // Default: the revised main board (Hardware/KiCad/NudgePCB/Nudge), where J2 "EMG_Strip"
    //   replaces the MyoWare connector and is wired to D0 and the XIAO's four bottom pads.
    //   No features are lost.
    //
    // Bench option (boards built before the revision): borrows the force-sensor and encoder pins, so
    //   unplug both force sensors and encoders; grip limiting is off and motors run on timers.
    //
    // Uncomment to use the bench option:
    // #define STRIP_ON_BENCH_PINS
#ifndef STRIP_ON_BENCH_PINS
    constexpr int PIN_STRIP_SCLK = 39;   // MTCK pad
    constexpr int PIN_STRIP_DIN = 40;    // MTDO pad (MOSI, into the ADS1298)
    constexpr int PIN_STRIP_DOUT = 41;   // MTDI pad (MISO, out of the ADS1298)
    constexpr int PIN_STRIP_CS = 42;     // MTMS pad
    constexpr int PIN_STRIP_DRDY = D0;   // was the MyoWare EMG input
    constexpr int PIN_ENCODER[MOTOR_COUNT] = {D7, D10};
    constexpr int PIN_FSR_THUMB = D1;
    constexpr int PIN_FSR_INDEX = D2;
#else
    constexpr int PIN_STRIP_SCLK = D1;   // FSR_THUMB pin (10k pull-down is fine for SCLK)
    constexpr int PIN_STRIP_DIN = D2;    // FSR_INDEX pin
    constexpr int PIN_STRIP_DOUT = D0;   // MyoWare EMG pin
    constexpr int PIN_STRIP_CS = D10;    // M2 encoder pin (10k pull-up keeps CS idle high)
    constexpr int PIN_STRIP_DRDY = D7;   // M1 encoder pin (10k pull-up)
    constexpr int PIN_ENCODER[MOTOR_COUNT] = {-1, -1};
    constexpr int PIN_FSR_THUMB = -1;
    constexpr int PIN_FSR_INDEX = -1;
#endif
    // Tie on the strip's J3 in both cases: START to GND, RESET/PWDN/CLKSEL to 3V3, CLK unconnected.
    constexpr int PIN_STRIP_START = -1;
    constexpr int PIN_STRIP_RESET = -1;
    constexpr int PIN_STRIP_PWDN = -1;
    constexpr int PIN_STRIP_CLKSEL = -1;
}
