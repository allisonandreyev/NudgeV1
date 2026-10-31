#pragma once
#include <math.h>
#include <stdint.h>

/**
 * Position control for one DC motor with a single-channel encoder, or none.
 * No Arduino dependencies, so it can be tested on a computer (see test/).
 *
 * Position is a fraction of the calibrated travel: 0 = hand open, 1 = closed.
 * If opening has a hard end stop, homing drives into it to find 0. If not (a spool with
 * elastic opening the hand), the power-up position is taken as open and homing just
 * returns to count 0.
 * A single-channel encoder only counts pulses, so direction comes from the drive command.
 * Without an encoder, position is estimated from drive time.
 *
 * Call Update() regularly (every ~10 ms) with the encoder's total pulse count; apply the
 * returned duty to the motor (+ closes, - opens, 0 = stopped).
 */
struct MotorAxisConfig
{
    bool hasEncoder = true;
    bool openEndStop = true;      // the hand stops against something when fully open
    long travelPulses = 600;      // pulses from fully open to fully closed (learned by Calibrate)
    uint32_t travelMs = 1500;     // full stroke time at full duty, used without an encoder
    float minDuty = 0.35f;        // below this the motor doesn't move
    float homingDuty = 0.4f;      // gentle duty for homing and finding travel
    float slowdown = 0.15f;       // start slowing this far (fraction of travel) from the target
    float tolerance = 0.02f;      // close enough to the target
    uint32_t stallMs = 300;       // no pulses for this long while driving = stalled
    uint32_t timeoutMs = 4000;    // give up on any single move after this long
};

class MotorAxis
{
public:
    enum class State : uint8_t { Idle, Moving, Homing, FindingTravel, Stalled, Gripping };

    using Config = MotorAxisConfig;

    explicit MotorAxis(const Config& c = Config()) : cfg(c) {}

    /** Drive to [fraction] (0 open .. 1 closed) at up to [speed] (0..1 of full duty). */
    void MoveTo(float fraction, float speed, uint32_t now)
    {
        target = clamp01(fraction);
        maxDuty = clampDuty(speed);
        Begin(State::Moving, now);
    }

    /** Open until the motor stalls against the end stop (or, without one, back to count 0); that's position 0. */
    void Home(uint32_t now) { Begin(State::Homing, now); }

    /** Home, then close until stall to learn the full travel, then open again. Needs an encoder. */
    void FindTravel(uint32_t now)
    {
        findTravelAfterHoming = cfg.hasEncoder;
        Begin(State::Homing, now);
    }

    void Stop()
    {
        state = State::Idle;
        findTravelAfterHoming = false;
    }

    /**
     * [pulses]: total encoder pulses ever counted (ignored without an encoder).
     * [gripLimit]: true while a force sensor says the hand is gripping firmly; closing stops.
     * Returns the duty to apply.
     */
    float Update(uint32_t now, long pulses, bool gripLimit)
    {
        // Integrate movement since the last update, in the direction we were driving
        long delta = pulses - lastPulses;
        lastPulses = pulses;
        uint32_t dt = now - lastUpdate;
        lastUpdate = now;
        if (cfg.hasEncoder)
        {
            if (delta > 0) lastPulseTime = now;
            if (lastDuty != 0) positionPulses += (lastDuty > 0 ? 1 : -1) * (float)delta;
        }
        else
        {
            positionPulses += lastDuty * cfg.travelPulses * (float)dt / cfg.travelMs;
        }

        float duty = 0;
        bool driving = state == State::Moving || state == State::Homing || state == State::FindingTravel;
        if (driving && now - stateStart > cfg.timeoutMs)
        {
            state = State::Stalled;
        }
        else if (state == State::Homing)
        {
            duty = -cfg.homingDuty;
            bool open = cfg.openEndStop
                ? Stalled(now) || (!cfg.hasEncoder && now - stateStart > cfg.travelMs + 200)
                : positionPulses <= 0 || Stalled(now);
            if (open)
            {
                positionPulses = 0;
                homed = true;
                if (findTravelAfterHoming)
                {
                    Begin(State::FindingTravel, now);
                    duty = cfg.homingDuty;
                }
                else
                {
                    state = State::Idle;
                    duty = 0;
                }
            }
        }
        else if (state == State::FindingTravel)
        {
            duty = cfg.homingDuty;
            if (Stalled(now))
            {
                if (positionPulses > 20) cfg.travelPulses = (long)positionPulses;
                findTravelAfterHoming = false;
                target = 0;
                maxDuty = cfg.homingDuty;
                Begin(State::Moving, now);
                duty = -cfg.homingDuty;
            }
        }
        else if (state == State::Moving)
        {
            float error = target - Position();
            if (fabsf(error) <= cfg.tolerance)
            {
                state = State::Idle;
            }
            else if (error > 0 && gripLimit)
            {
                state = State::Gripping;  // something is in the hand: hold here
            }
            else if (cfg.hasEncoder && Stalled(now))
            {
                state = State::Stalled;
            }
            else
            {
                // Full speed, easing off near the target so it doesn't overshoot
                float magnitude = fminf(1.0f, fabsf(error) / cfg.slowdown) * maxDuty;
                duty = (error > 0 ? 1 : -1) * fmaxf(magnitude, cfg.minDuty);
            }
        }

        lastDuty = duty;
        return duty;
    }

    float Position() const { return cfg.travelPulses > 0 ? positionPulses / cfg.travelPulses : 0; }
    State GetState() const { return state; }
    bool Homed() const { return homed; }
    long TravelPulses() const { return cfg.travelPulses; }
    void SetTravelPulses(long p) { if (p > 20) cfg.travelPulses = p; }

private:
    void Begin(State s, uint32_t now)
    {
        state = s;
        stateStart = now;
        lastPulseTime = now;
    }

    bool Stalled(uint32_t now) const { return cfg.hasEncoder && now - lastPulseTime > cfg.stallMs; }

    float clampDuty(float speed) const { return fmaxf(cfg.minDuty, fminf(1.0f, speed)); }
    static float clamp01(float v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

    Config cfg;
    State state = State::Idle;
    bool homed = false;
    bool findTravelAfterHoming = false;
    float target = 0;
    float maxDuty = 1;
    float lastDuty = 0;
    float positionPulses = 0;  // float so timed moves don't lose small steps
    long lastPulses = 0;
    uint32_t lastUpdate = 0;
    uint32_t stateStart = 0;
    uint32_t lastPulseTime = 0;
};
