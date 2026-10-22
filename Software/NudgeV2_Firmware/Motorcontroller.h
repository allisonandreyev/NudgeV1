#pragma once
#include <Arduino.h>
#include "NudgeConfig.h"

// Replaces ServoController for the two-N20 (DRV8833) build.
// Same role as before: Init(), ParseCommand(), grasp/release abstractions,
// AI actuation gate. Commands are queued, so ParseCommand() is safe to call
// from the BLE callback task; Update() runs them from loop().

enum class MotorState : uint8_t { IDLE, MOVING, ARRIVED, STALLED, TIMEOUT, JOGGING };

class Motor
{
public:
    void begin(int in1, int in2, int encA, bool reversed);
    void moveTo(long target, uint8_t speedPct);
    void jog(int signedSpeedPct, uint32_t ms);
    void coast();
    void update();
    void zero() { counts = 0; }
    long position() const { return counts; }
    long targetPos() const { return target; }
    MotorState state() const { return st; }
    bool active() const { return st == MotorState::MOVING || st == MotorState::JOGGING; }

    volatile long   counts = 0;  // updated by the encoder interrupt
    volatile int8_t dir    = 0;  // +1 closing, -1 opening, 0 stopped

private:
    void drive(int signedPct);
    int  in1 = -1, in2 = -1, enc = -1;
    bool rev = false;
    long target = 0;
    uint8_t speed = 0;
    MotorState st = MotorState::IDLE;
    uint32_t moveStart = 0, lastProgressMs = 0, jogEnd = 0;
    long lastCounts = 0;
};

class MotorController
{
public:
    static void Init();
    static void Update();                  // call every loop()
    static void ParseCommand(String cmd);  // thread-safe, queued

    static void MoveGrasp(uint8_t speedPct = DEFAULT_SPEED_PCT);
    static void MoveRelease(uint8_t speedPct = DEFAULT_SPEED_PCT);
    static void StopAll(const char* reason);

    static void EnableAIActuation(bool enable) { aiActuationEnabled = enable; }
    static bool IsAIActuationEnabled() { return aiActuationEnabled; }

    // Latest sensor values, supplied by the main sketch
    static void SetForceMv(int mv)   { forceMv = mv; }
    static void SetBatteryMv(int mv) { batteryMv = mv; }

    static bool IsClosing() { return closing; }
    static Motor& Get(int idx) { return idx == 0 ? fingers : thumb; }
    static const char* StateName(MotorState s);

private:
    static void Execute(const char* cmd);
    static void Status();
    static void Help();
    static void UpdateEnable();

    static Motor fingers, thumb;
    static QueueHandle_t cmdQueue;
    static bool aiActuationEnabled, closing;
    static bool thumbPending;
    static uint32_t thumbStartAt, lastControlUs;
    static uint8_t pendingThumbSpeed;
    static int forceMv, batteryMv;
};