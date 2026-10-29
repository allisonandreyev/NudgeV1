#include "MotorController.h"
#include "BoardConfig.h"
#include "MotorAxis.h"
#include <Preferences.h>

namespace MotorController
{
    namespace
    {
        constexpr uint32_t PWM_HZ = 20000;  // above hearing
        constexpr uint8_t PWM_BITS = 8;
        constexpr uint32_t CONTROL_MS = 10;
        constexpr float MAX_APP_SPEED = 300;  // the app's speed slider tops out here

        // Closing stops once either finger presses this hard (raw ADC). Tune with "status".
        constexpr uint16_t GRIP_LIMIT = 2500;

        MotorAxis axes[Board::MOTOR_COUNT];
        volatile long pulses[Board::MOTOR_COUNT];
        portMUX_TYPE lock = portMUX_INITIALIZER_UNLOCKED;  // encoder counts
        SemaphoreHandle_t axisLock;  // axes are changed by commands and updated by the control task

        struct AxisGuard
        {
            AxisGuard() { xSemaphoreTake(axisLock, portMAX_DELAY); }
            ~AxisGuard() { xSemaphoreGive(axisLock); }
        };
        bool aiActuation = false;
        bool driverAwake = false;

        // Smoothed sensor readings, updated by the control task
        float battery = 100;
        float forceThumb = 0, forceIndex = 0;

        void IRAM_ATTR OnPulse0() { pulses[0]++; }
        void IRAM_ATTR OnPulse1() { pulses[1]++; }

        void Wake(bool awake)
        {
            driverAwake = awake;
            digitalWrite(Board::PIN_MOTOR_EN, awake ? HIGH : LOW);
        }

        /** duty: -1..1, + closes. Fast decay: PWM one input, hold the other low. */
        void Drive(int m, float duty)
        {
            float d = duty * Board::CLOSE_DIRECTION[m];
            uint32_t pwm = (uint32_t)(fminf(fabsf(d), 1.0f) * ((1 << PWM_BITS) - 1));
            if (d > 0)
            {
                ledcWrite(Board::PIN_MOTOR_IN1[m], pwm);
                ledcWrite(Board::PIN_MOTOR_IN2[m], 0);
            }
            else if (d < 0)
            {
                ledcWrite(Board::PIN_MOTOR_IN1[m], 0);
                ledcWrite(Board::PIN_MOTOR_IN2[m], pwm);
            }
            else
            {
                // Both high = brake, which holds the fingers where they are
                ledcWrite(Board::PIN_MOTOR_IN1[m], (1 << PWM_BITS) - 1);
                ledcWrite(Board::PIN_MOTOR_IN2[m], (1 << PWM_BITS) - 1);
            }
        }

        float ReadForce(int pin) { return pin >= 0 ? analogRead(pin) : 0; }

        float ReadBatteryPercent()
        {
            float volts = analogReadMilliVolts(Board::PIN_BATTERY) * Board::BATTERY_DIVIDER / 1000.0f;
            // Single LiPo cell: roughly linear between 3.3 V (empty) and 4.2 V (full)
            return fminf(100, fmaxf(0, (volts - 3.3f) / 0.9f * 100));
        }

        const char* StateName(MotorAxis::State s)
        {
            switch (s)
            {
                case MotorAxis::State::Idle: return "idle";
                case MotorAxis::State::Moving: return "moving";
                case MotorAxis::State::Homing: return "homing";
                case MotorAxis::State::FindingTravel: return "calibrating";
                case MotorAxis::State::Stalled: return "stalled";
                case MotorAxis::State::Gripping: return "gripping";
            }
            return "?";
        }

        void SaveTravel()
        {
            Preferences prefs;
            prefs.begin("nudge", false);
            for (int m = 0; m < Board::MOTOR_COUNT; m++) prefs.putLong(m == 0 ? "travel0" : "travel1", axes[m].TravelPulses());
            prefs.end();
        }

        void ControlTask(void*)
        {
            bool wasCalibrating = false;
            for (;;)
            {
                uint32_t now = millis();
                forceThumb += (ReadForce(Board::PIN_FSR_THUMB) - forceThumb) * 0.3f;
                forceIndex += (ReadForce(Board::PIN_FSR_INDEX) - forceIndex) * 0.3f;
                battery += (ReadBatteryPercent() - battery) * 0.01f;
                bool grip = forceThumb > GRIP_LIMIT || forceIndex > GRIP_LIMIT;

                long counts[Board::MOTOR_COUNT];
                portENTER_CRITICAL(&lock);
                for (int m = 0; m < Board::MOTOR_COUNT; m++) counts[m] = pulses[m];
                portEXIT_CRITICAL(&lock);

                bool calibrating = false;
                {
                    AxisGuard guard;
                    for (int m = 0; m < Board::MOTOR_COUNT; m++)
                    {
                        float duty = axes[m].Update(now, counts[m], grip);
                        if (driverAwake) Drive(m, duty);
                        calibrating |= axes[m].GetState() == MotorAxis::State::FindingTravel;
                    }
                }
                // Keep newly learned travel across power cycles
                if (wasCalibrating && !calibrating) SaveTravel();
                wasCalibrating = calibrating;
                vTaskDelay(pdMS_TO_TICKS(CONTROL_MS));
            }
        }

        float Speed(float appSpeed) { return appSpeed / MAX_APP_SPEED; }
    }

    void Init()
    {
        pinMode(Board::PIN_MOTOR_EN, OUTPUT);
        Wake(false);
        for (int m = 0; m < Board::MOTOR_COUNT; m++)
        {
            ledcAttach(Board::PIN_MOTOR_IN1[m], PWM_HZ, PWM_BITS);
            ledcAttach(Board::PIN_MOTOR_IN2[m], PWM_HZ, PWM_BITS);
            ledcWrite(Board::PIN_MOTOR_IN1[m], 0);
            ledcWrite(Board::PIN_MOTOR_IN2[m], 0);

            MotorAxis::Config cfg;
            cfg.hasEncoder = Board::PIN_ENCODER[m] >= 0;
            axes[m] = MotorAxis(cfg);
            if (cfg.hasEncoder)
            {
                pinMode(Board::PIN_ENCODER[m], INPUT_PULLUP);  // R5/R6 also pull up
                attachInterrupt(digitalPinToInterrupt(Board::PIN_ENCODER[m]), m == 0 ? OnPulse0 : OnPulse1, RISING);
            }
        }
        if (Board::PIN_FSR_THUMB >= 0) pinMode(Board::PIN_FSR_THUMB, INPUT);
        if (Board::PIN_FSR_INDEX >= 0) pinMode(Board::PIN_FSR_INDEX, INPUT);
        analogReadResolution(12);
        battery = ReadBatteryPercent();

        Preferences prefs;
        prefs.begin("nudge", true);
        for (int m = 0; m < Board::MOTOR_COUNT; m++) axes[m].SetTravelPulses(prefs.getLong(m == 0 ? "travel0" : "travel1", 0));
        prefs.end();

        axisLock = xSemaphoreCreateMutex();

        // Safe start: open the hand gently until each motor reaches its end stop
        Wake(true);
        uint32_t now = millis();
        for (int m = 0; m < Board::MOTOR_COUNT; m++) axes[m].Home(now);
        xTaskCreate(ControlTask, "motors", 4096, nullptr, 3, nullptr);
        Serial.println(">> Motors: homing (opening the hand)");
    }

    void MoveGrasp(float amount, float speed)
    {
        Wake(true);
        AxisGuard guard;
        uint32_t now = millis();
        for (auto& a : axes) a.MoveTo(amount / 255.0f, Speed(speed), now);
    }

    void MoveRetract(float amount, float speed)
    {
        Wake(true);
        AxisGuard guard;
        uint32_t now = millis();
        // "retract 255" = fully open, same as the old servo command
        for (auto& a : axes) a.MoveTo(1.0f - amount / 255.0f, Speed(speed), now);
    }

    void StopAll()
    {
        {
            AxisGuard guard;
            for (auto& a : axes) a.Stop();
        }
        aiActuation = false;
        Wake(false);  // asleep = both motors coast
    }

    void EnableAIActuation(bool enable)
    {
        aiActuation = enable;
        Serial.printf("AI Actuation: %s\n", enable ? "ENABLED" : "DISABLED");
    }

    bool IsAIActuationEnabled() { return aiActuation; }

    uint8_t BatteryPercent() { return (uint8_t)(battery + 0.5f); }
    uint16_t ForceThumb() { return (uint16_t)forceThumb; }
    uint16_t ForceIndex() { return (uint16_t)forceIndex; }

    bool ParseCommand(const String& raw)
    {
        String cmd = raw;
        cmd.trim();
        float a = 255, s = MAX_APP_SPEED;
        int id = 0;

        if (cmd.equalsIgnoreCase("stop")) { StopAll(); return true; }
        if (cmd.equalsIgnoreCase("ai_start")) { EnableAIActuation(true); return true; }
        if (cmd.equalsIgnoreCase("ai_stop")) { EnableAIActuation(false); return true; }
        if (cmd.startsWith("grasp")) { sscanf(cmd.c_str(), "grasp %f %f", &a, &s); MoveGrasp(a, s); return true; }
        if (cmd.startsWith("retract")) { sscanf(cmd.c_str(), "retract %f %f", &a, &s); MoveRetract(a, s); return true; }
        if (cmd.startsWith("motor"))
        {
            float percent = 0;
            int n = sscanf(cmd.c_str(), "motor %d %f %f", &id, &percent, &s);
            if (n < 2 || id < 1 || id > Board::MOTOR_COUNT) { Serial.println("motor <1|2> <percent closed> [speed]"); return true; }
            Wake(true);
            AxisGuard guard;
            axes[id - 1].MoveTo(percent / 100.0f, Speed(s), millis());
            return true;
        }
        if (cmd.equalsIgnoreCase("calibrate"))
        {
            Wake(true);
            AxisGuard guard;
            uint32_t now = millis();
            for (auto& a : axes) a.FindTravel(now);
            Serial.println(">> Motors: calibrating travel (open, then close until stall, then open)");
            return true;
        }
        if (cmd.equalsIgnoreCase("status"))
        {
            for (int m = 0; m < Board::MOTOR_COUNT; m++)
            {
                Serial.printf("Motor %d: %3.0f%% closed, %s, travel %ld pulses%s\n", m + 1, axes[m].Position() * 100,
                              StateName(axes[m].GetState()), axes[m].TravelPulses(),
                              Board::PIN_ENCODER[m] < 0 ? " (no encoder, timed)" : "");
            }
            Serial.printf("Force thumb %u, index %u (grip limit %u). Battery %u%%. Driver %s\n",
                          ForceThumb(), ForceIndex(), GRIP_LIMIT, BatteryPercent(), driverAwake ? "awake" : "asleep");
            return true;
        }
        if (cmd.equalsIgnoreCase("engage") || cmd.equalsIgnoreCase("disengage"))
        {
            Serial.println("This board has no clutch; ignoring.");
            return true;
        }
        return false;
    }
}
