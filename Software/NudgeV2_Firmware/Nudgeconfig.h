#pragma once
#include <Arduino.h>

// =====================================================================
//  Nudge N20 Firmware - configuration
//  Board: Seeed XIAO ESP32-S3 (V2 schematic pin map)
//  Every pin lives here. Set an optional pin to -1 to disable it.
// =====================================================================

// ---------------- Sensors ----------------
constexpr int PIN_EMG_FLEXOR   = D0;   // MyoWare ENV output, grip muscle
constexpr int PIN_EMG_EXTENSOR = -1;   // optional 2nd MyoWare, open muscle (no free pin on V2 yet)
constexpr int PIN_FSR_THUMB    = D1;   // FSR with 10k pull-down
constexpr int PIN_FSR_INDEX    = D2;   // FSR with 10k pull-down
constexpr int PIN_VBAT_SENSE   = D3;   // 100k/100k battery divider
constexpr float VBAT_DIVIDER   = 2.0f; // battery mV = pin mV * this

// ---------------- DRV8833 motor driver ----------------
constexpr int PIN_MOTOR_EN = D6;       // DRV8833 SLP (HIGH = awake)

// Motor 1 = four fingers
constexpr int PIN_M1_IN1   = D4;
constexpr int PIN_M1_IN2   = D5;
constexpr int PIN_M1_ENC_A = D7;

// Motor 2 = thumb
constexpr int PIN_M2_IN1   = D8;
constexpr int PIN_M2_IN2   = D9;
constexpr int PIN_M2_ENC_A = D10;

// Optional physical stop button to GND (uses internal pull-up)
constexpr int PIN_ESTOP_BUTTON = -1;

// ---------------- Motor calibration (MEASURE THESE) ----------------
// Flip if a motor runs the wrong way on "grasp" (closing must count UP).
constexpr bool M1_REVERSED = false;
constexpr bool M2_REVERSED = false;

// Encoder counts from fully open (0) to fully closed. Find them with the
// "jog" and "status" commands, then enter them here.
constexpr long M1_CLOSED_COUNTS = 400;
constexpr long M2_CLOSED_COUNTS = 300;

constexpr uint8_t  DEFAULT_SPEED_PCT  = 60;    // normal drive strength
constexpr uint8_t  MIN_SPEED_PCT      = 30;    // floor when slowing near target
constexpr long     SLOWDOWN_COUNTS    = 40;    // start slowing this close to target
constexpr long     POSITION_TOLERANCE = 5;     // "arrived" window in counts
constexpr uint32_t PWM_FREQ_HZ        = 20000; // above hearing range
constexpr uint8_t  PWM_RES_BITS       = 10;

// ---------------- Safety ----------------
constexpr uint32_t STALL_TIME_MS   = 200;  // no encoder movement this long = stalled
constexpr uint32_t STALL_GRACE_MS  = 150;  // ignore stalls during spin-up
constexpr uint32_t MOVE_TIMEOUT_MS = 4000; // hard limit on any single move
constexpr uint32_t THUMB_DELAY_MS  = 150;  // thumb starts this long after fingers
constexpr int      FSR_STOP_MV     = 2000; // grip force limit (FSR pin millivolts)
constexpr int      VBAT_MIN_MV     = 3400; // refuse to grasp below this

// ---------------- Timing ----------------
constexpr uint32_t SENSOR_RATE_HZ  = 200;  // MUST match the Edge Impulse training rate
constexpr uint32_t CONTROL_RATE_HZ = 100;  // motor control loop
constexpr uint32_t BLE_RATE_HZ     = 25;   // packets to the phone app

// ---------------- AI (Edge Impulse) ----------------
// 0 = build without a model (manual commands only)
// 1 = include your Edge Impulse library (edit the #include in the .ino)
#define USE_EDGE_IMPULSE 0

constexpr float    AI_CONFIDENCE    = 0.80f; // ignore predictions below this
constexpr int      AI_STABILITY     = 3;     // same label N times in a row to act
constexpr uint32_t AI_STRIDE_MS     = 50;    // run the model this often
// With a single EMG sensor the model can only tell GRIP from REST, so REST
// must open the hand ("hold while squeezing"). With a flexor + extensor pair
// and an OPEN label, set this to false so the hand stays closed until OPEN.
constexpr bool     AI_REST_RELEASES = true;

constexpr int NUM_EMG = (PIN_EMG_EXTENSOR >= 0) ? 2 : 1;