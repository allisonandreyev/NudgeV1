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
        volatile long pulses[2];  // one per encoder interrupt below
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
        float batteryVolts = Board::BATTERY_FULL_V;
        float forceThumb = 0, forceIndex = 0;
        // Set below BATTERY_EMPTY_V: the hand may open but not close until the battery recovers
        volatile bool batteryEmpty = false;

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

        float ReadBatteryVolts()
        {
            return analogReadMilliVolts(Board::PIN_BATTERY) * Board::BATTERY_DIVIDER / 1000.0f;
        }

        void SetLed(bool on) { digitalWrite(Board::PIN_LED, on ? LOW : HIGH); }  // on when LOW

        /** Off normally; a short flash every second when low; slow blink when empty. */
        void UpdateLed(uint32_t now)
        {
            if (batteryEmpty) SetLed(now % 2000 < 1000);
            else if (batteryVolts < Board::BATTERY_LOW_V) SetLed(now % 1000 < 100);
            else SetLed(false);
        }

        /** Closing is refused while the battery is empty; opening is always allowed. */
        bool MayMove(int m, float target)
        {
            if (!batteryEmpty || target <= axes[m].Position()) return true;
            Serial.println("Battery empty: not closing. Charge over USB-C.");
            return false;
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
                // ~1 s smoothing, so motor current dips don't count as an empty battery
                batteryVolts += (ReadBatteryVolts() - batteryVolts) * 0.01f;
                bool grip = forceThumb > GRIP_LIMIT || forceIndex > GRIP_LIMIT;
                if (!batteryEmpty && batteryVolts < Board::BATTERY_EMPTY_V)
                {
                    batteryEmpty = true;
                    AxisGuard guard;
                    for (auto& a : axes) a.Stop();
                    Serial.println(">> Battery empty: motor stopped");
                }
                else if (batteryEmpty && batteryVolts > Board::BATTERY_EMPTY_V + 0.15f)
                {
                    batteryEmpty = false;
                }
                UpdateLed(now);

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
        pinMode(Board::PIN_LED, OUTPUT);
        SetLed(false);
        for (int m = 0; m < Board::MOTOR_COUNT; m++)
        {
            ledcAttach(Board::PIN_MOTOR_IN1[m], PWM_HZ, PWM_BITS);
            ledcAttach(Board::PIN_MOTOR_IN2[m], PWM_HZ, PWM_BITS);
            ledcWrite(Board::PIN_MOTOR_IN1[m], 0);
            ledcWrite(Board::PIN_MOTOR_IN2[m], 0);

            MotorAxis::Config cfg;
            cfg.hasEncoder = Board::PIN_ENCODER[m] >= 0;
            cfg.openEndStop = Board::OPEN_END_STOP;
            cfg.timeoutMs = Board::MOVE_TIMEOUT_MS;
            cfg.stallMs = Board::STALL_MS;
            axes[m] = MotorAxis(cfg);
            if (cfg.hasEncoder)
            {
                pinMode(Board::PIN_ENCODER[m], INPUT_PULLUP);  // R5 also pulls up
                attachInterrupt(digitalPinToInterrupt(Board::PIN_ENCODER[m]), m == 0 ? OnPulse0 : OnPulse1, RISING);
            }
        }
        if (Board::PIN_FSR_THUMB >= 0) pinMode(Board::PIN_FSR_THUMB, INPUT);
        if (Board::PIN_FSR_INDEX >= 0) pinMode(Board::PIN_FSR_INDEX, INPUT);
        analogReadResolution(12);
        batteryVolts = ReadBatteryVolts();
        batteryEmpty = batteryVolts < Board::BATTERY_EMPTY_V;

        Preferences prefs;
        prefs.begin("nudge", true);
        for (int m = 0; m < Board::MOTOR_COUNT; m++) axes[m].SetTravelPulses(prefs.getLong(m == 0 ? "travel0" : "travel1", 0));
        prefs.end();

        axisLock = xSemaphoreCreateMutex();

        // Safe start. With an end stop, open the hand gently until the motor reaches it;
        // without one (spool and elastic), the hand is taken as open where it is.
        Wake(Board::OPEN_END_STOP);
        uint32_t now = millis();
        for (int m = 0; m < Board::MOTOR_COUNT; m++) axes[m].Home(now);
        xTaskCreate(ControlTask, "motors", 4096, nullptr, 3, nullptr);
        Serial.println(Board::OPEN_END_STOP ? ">> Motors: homing (opening the hand)"
                                            : ">> Motor: hand taken as open at power-up");
    }

    void MoveGrasp(float amount, float speed)
    {
        Wake(true);
        AxisGuard guard;
        uint32_t now = millis();
        for (int m = 0; m < Board::MOTOR_COUNT; m++)
            if (MayMove(m, amount / 255.0f)) axes[m].MoveTo(amount / 255.0f, Speed(speed), now);
    }

    void MoveRetract(float amount, float speed)
    {
        Wake(true);
        AxisGuard guard;
        uint32_t now = millis();
        // "retract 255" = fully open, same as the old servo command
        for (int m = 0; m < Board::MOTOR_COUNT; m++)
            if (MayMove(m, 1.0f - amount / 255.0f)) axes[m].MoveTo(1.0f - amount / 255.0f, Speed(speed), now);
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

    uint8_t BatteryPercent()
    {
        // Single LiPo cell: roughly linear from empty (motor cut-off) to full
        float pct = (batteryVolts - Board::BATTERY_EMPTY_V) / (Board::BATTERY_FULL_V - Board::BATTERY_EMPTY_V) * 100;
        return (uint8_t)(fminf(100, fmaxf(0, pct)) + 0.5f);
    }

    bool HasForceSensors() { return Board::PIN_FSR_THUMB >= 0 || Board::PIN_FSR_INDEX >= 0; }
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
            if (n < 2 || id < 1 || id > Board::MOTOR_COUNT) { Serial.printf("motor <1-%d> <percent closed> [speed]\n", Board::MOTOR_COUNT); return true; }
            Wake(true);
            AxisGuard guard;
            if (MayMove(id - 1, percent / 100.0f)) axes[id - 1].MoveTo(percent / 100.0f, Speed(s), millis());
            return true;
        }
        if (cmd.equalsIgnoreCase("calibrate"))
        {
            if (batteryEmpty) { Serial.println("Battery empty: charge before calibrating."); return true; }
            Wake(true);
            AxisGuard guard;
            uint32_t now = millis();
            for (auto& a : axes) a.FindTravel(now);
            Serial.println(">> Motors: calibrating travel (open, then close until it stalls, then open)");
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
            if (HasForceSensors()) Serial.printf("Force thumb %u, index %u (grip limit %u)\n", ForceThumb(), ForceIndex(), GRIP_LIMIT);
            Serial.printf("Battery %.2f V (%u%%%s). Driver %s\n", batteryVolts, BatteryPercent(),
                          batteryEmpty ? ", empty: closing disabled" : batteryVolts < Board::BATTERY_LOW_V ? ", low" : "",
                          driverAwake ? "awake" : "asleep");
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
