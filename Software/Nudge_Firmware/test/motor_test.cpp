// Checks MotorAxis (position control, homing, calibration, stall and grip) against a
// simulated geared motor with end stops. Build and run: see README in this folder.
#include "../MotorAxis.h"
#include <cmath>
#include <cstdio>
#include <cstdlib>

static int failures = 0;
#define CHECK(cond, ...) do { if (!(cond)) { failures++; std::printf("FAIL: " __VA_ARGS__); std::printf("\n"); } } while (0)

/** Motor + gearbox + finger linkage: moves at up to 1 pulse/ms at full duty between 0 and [travel]. */
struct Plant
{
    double position;  // true position in pulses, 0 = open end stop
    double travel;
    double obstacle = 1e9;  // something in the hand at this position
    long pulses = 0;        // what a single-channel encoder reports: every pulse, either direction
    double partial = 0;

    void Step(float duty, int ms)
    {
        if (std::fabs(duty) < 0.3f) return;  // too weak to overcome friction
        double limitHigh = std::fmin(travel, obstacle);
        double next = std::fmin(limitHigh, std::fmax(0.0, position + duty * ms));
        partial += std::fabs(next - position);
        position = next;
        long whole = (long)partial;
        pulses += whole;
        partial -= whole;
    }
};

static uint32_t Run(MotorAxis& axis, Plant& plant, uint32_t now, int maxMs, bool grip = false, double gripAt = 1e9)
{
    for (int t = 0; t < maxMs; t += 10)
    {
        float duty = axis.Update(now, plant.pulses, grip && plant.position >= gripAt);
        plant.Step(duty, 10);
        now += 10;
        auto s = axis.GetState();
        if (s == MotorAxis::State::Idle || s == MotorAxis::State::Stalled || s == MotorAxis::State::Gripping) break;
    }
    return now;
}

int main()
{
    // Homing from the middle: opens until the end stop, calls that 0
    {
        MotorAxis axis;
        Plant plant{300, 600};
        axis.Home(0);
        Run(axis, plant, 0, 5000);
        CHECK(axis.GetState() == MotorAxis::State::Idle, "homing didn't finish");
        CHECK(axis.Homed() && std::fabs(axis.Position()) < 0.01f, "homed position %.3f", axis.Position());
        CHECK(plant.position < 1, "hand not open after homing (%.0f)", plant.position);
    }

    // Calibration learns the real travel, then leaves the hand open
    {
        MotorAxis axis;
        Plant plant{200, 850};
        axis.FindTravel(0);
        uint32_t now = Run(axis, plant, 0, 6000);
        // FindTravel goes homing -> finding travel -> moving back open
        for (int i = 0; i < 3 && axis.GetState() != MotorAxis::State::Idle; i++) now = Run(axis, plant, now, 6000);
        CHECK(std::abs(axis.TravelPulses() - 850) < 30, "learned travel %ld, real 850", axis.TravelPulses());
        CHECK(plant.position < 40, "not reopened after calibration (%.0f)", plant.position);
    }

    // Move to closed and to half-way
    {
        MotorAxis axis;
        Plant plant{0, 600};
        axis.MoveTo(1.0f, 1.0f, 0);
        uint32_t now = Run(axis, plant, 0, 4000);
        CHECK(axis.GetState() == MotorAxis::State::Idle, "close didn't finish");
        CHECK(std::fabs(plant.position - 600) < 20, "closed at %.0f of 600", plant.position);
        axis.MoveTo(0.5f, 1.0f, now);
        Run(axis, plant, now, 4000);
        CHECK(std::fabs(plant.position - 300) < 25, "half-way at %.0f, wanted 300", plant.position);
        CHECK(std::fabs(axis.Position() - plant.position / 600) < 0.03f, "tracked %.3f vs real %.3f", axis.Position(), plant.position / 600);
    }

    // Force sensor says the hand is gripping: closing stops there
    {
        MotorAxis axis;
        Plant plant{0, 600};
        axis.MoveTo(1.0f, 1.0f, 0);
        Run(axis, plant, 0, 4000, true, 350);
        CHECK(axis.GetState() == MotorAxis::State::Gripping, "grip limit ignored");
        CHECK(plant.position < 380, "kept closing to %.0f after grip", plant.position);
    }

    // Blocked by an obstacle without force sensors: stall detection stops the motor
    {
        MotorAxis axis;
        Plant plant{0, 600, 250};
        axis.MoveTo(1.0f, 1.0f, 0);
        uint32_t now = Run(axis, plant, 0, 4000);
        CHECK(axis.GetState() == MotorAxis::State::Stalled, "stall not detected");
        CHECK(axis.Update(now + 10, plant.pulses, false) == 0, "still driving after stall");
    }

    // No encoder: timed moves
    {
        MotorAxisConfig cfg;
        cfg.hasEncoder = false;
        cfg.travelMs = 600;  // matches the plant: 600 pulses at 1 pulse/ms
        MotorAxis axis(cfg);
        Plant plant{0, 600};
        axis.MoveTo(0.5f, 1.0f, 0);
        Run(axis, plant, 0, 3000);
        CHECK(axis.GetState() == MotorAxis::State::Idle, "timed move didn't finish");
        CHECK(std::fabs(plant.position - 300) < 60, "timed half-way at %.0f, wanted ~300", plant.position);
    }

    std::printf("%s (%d failures)\n", failures ? "FAILED" : "all motor checks passed", failures);
    return failures ? 1 : 0;
}
