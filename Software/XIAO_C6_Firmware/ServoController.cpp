#include <Arduino.h>
#include <Wire.h>
#include "ServoController.h"
#include <Adafruit_PWMServoDriver.h>

// Static member definitions
ServoState ServoController::servo[SERVO_COUNT];
Adafruit_PWMServoDriver ServoController::pwm = Adafruit_PWMServoDriver(PCA9685_ADDR);
TaskHandle_t ServoController::servoTaskHandle = NULL;
QueueHandle_t ServoController::servoQueue = NULL;
bool ServoController::randomMode = false;

void ServoController::WriteServo(uint8_t id, float angle)
{
    if (id >= SERVO_COUNT) return;

    /**
     * DUAL-LAYER PROTECTION for DSS-M15S 270:
     * 1. constrain ensures the input angle never exceeds our 255 safe limit.
     * 2. map scales that angle based on the servo's physical 270 range.
     */
    angle = constrain(angle, 0.0f, SERVO_MAX_ANGLE);

    // Pulse calculation: 0 to 270 maps to 500us to 2500us
    float pulse = SERVO_MIN_US + ((SERVO_MAX_US - SERVO_MIN_US) * angle / PHYSICAL_MAX_ANGLE);

    pwm.setPWM(id, 0, microsecondsToTicks(pulse));
}

void ServoController::ServoTask(void *parameter)
{
    ServoCommand cmd;

    while (true)
    {
        while (xQueueReceive(servoQueue, &cmd, 0) == pdTRUE)
        {
            if (cmd.type == CommandType::STOP_ALL) {
                for(int i = 0; i < SERVO_COUNT; i++) {
                    servo[i].moving = false;
                    servo[i].target = servo[i].current;
                    pwm.setPWM(i, 0, 4096);
                }
                Serial.println(">> EMERGENCY STOP: All servos halted.");
                continue;
            }

            if (cmd.id >= SERVO_COUNT) continue;

            if (cmd.type == CommandType::MOVE) {
                // Hard software limit of 255 applied to target
                servo[cmd.id].target = constrain(cmd.angle, 0.0f, SERVO_MAX_ANGLE);
                servo[cmd.id].speed = constrain(cmd.speed, 1.0f, 300.0f);
                servo[cmd.id].moving = true;
                servo[cmd.id].activeTime = 0;

                Serial.printf("Servo %d -> %.1f deg (Safe Limit: 255) @ %.1f dps\n",
                             cmd.id, servo[cmd.id].target, servo[cmd.id].speed);
            }
        }

        for (int i = 0; i < SERVO_COUNT; i++)
        {
            if (!servo[i].moving) {
                servo[i].activeTime = 0;
                continue;
            }

            servo[i].activeTime += SERVO_UPDATE_MS;

            if(servo[i].activeTime > ALLOWED_ACTIVE_TIME)
            {
                servo[i].moving = false;
                servo[i].target = servo[i].current;
                pwm.setPWM(i, 0, 4096);
                Serial.printf("Servo %d timeout (%dMS). Power cut.\r\n", i, servo[i].activeTime);
                continue;
            }

            float difference = servo[i].target - servo[i].current;
            float movement = servo[i].speed * (SERVO_UPDATE_MS / 1000.0);

            float nextPosition;
            if (abs(difference) <= max(movement, SERVO_THRESHOLD))
            {
                nextPosition = servo[i].target;
                servo[i].moving = false;
            }
            else if (difference > 0)
                nextPosition = servo[i].current + movement;
            else
                nextPosition = servo[i].current - movement;

            if (abs(nextPosition - servo[i].current) > 0.01f) {
                WriteServo(i, nextPosition);
                servo[i].current = nextPosition;
            }
        }

        vTaskDelay(pdMS_TO_TICKS(SERVO_UPDATE_MS));
    }
}

void ServoController::Help()
{
    Serial.println("\nCommands:");
    Serial.println("Servo <id> <angle> <speed>");
    Serial.println("engage");
    Serial.println("disengage");
    Serial.println("grasp <angle> <speed>");
    Serial.println("retract <angle> <speed>");
    Serial.println("stop");
    Serial.println("status");
    Serial.println("help\n");
}

void ServoController::Status()
{
    for (int i = 0; i < SERVO_COUNT; i++)
    {
        Serial.printf("%d: %.1f -> %.1f %s\n", i,
                     servo[i].current, servo[i].target,
                     servo[i].moving ? "MOVING" : "IDLE");
    }
}

void ServoController::ParseCommand(String cmd)
{
    cmd.trim();

    if (cmd.equalsIgnoreCase("help")) { Help(); return; }
    if (cmd.equalsIgnoreCase("status")) { Status(); return; }
    if (cmd.equalsIgnoreCase("stop")) { StopAll(); return; }
    if (cmd.equalsIgnoreCase("engage")) { EngagePTO(); return; }
    if (cmd.equalsIgnoreCase("disengage")) { DisengagePTO(); return; }

    int id; float a; float speed;
    if (sscanf(cmd.c_str(), "Servo %d %f %f", &id, &a, &speed) == 3) {
        SetServo(id, a, speed);
        return;
    }

    if (sscanf(cmd.c_str(), "grasp %f %f", &a, &speed) == 2) {
        MoveGrasp(a, speed);
        return;
    }

    if (sscanf(cmd.c_str(), "retract %f %f", &a, &speed) == 2) {
        MoveRetract(a, speed);
        return;
    }

    Serial.println("Unknown command");
}

void ServoController::Init()
{
    Wire.begin(I2C_SDA, I2C_SCL);
    pwm.begin();
    pwm.setOscillatorFrequency(27000000);
    pwm.setPWMFreq(PWM_FREQ);
    delay(20);

    for (int i = 0; i < SERVO_COUNT; i++)
    {
        servo[i].current = 0;
        servo[i].target = 0;
        servo[i].speed = 30;
        servo[i].moving = false;
        servo[i].activeTime = 0;
        WriteServo(i, 0);
    }

    servoQueue = xQueueCreate(20, sizeof(ServoCommand));
    xTaskCreate(ServoTask, "ServoTask", 4096, nullptr, 2, &servoTaskHandle);
    Serial.println("Servo controller ready");
}

void ServoController::SetServo(uint8_t id, float angle, float speed)
{
    ServoCommand c = { CommandType::MOVE, id, angle, speed };
    xQueueSend(servoQueue, &c, portMAX_DELAY);
}

void ServoController::StopAll()
{
    ServoCommand c = { CommandType::STOP_ALL, 0, 0, 0 };
    xQueueSend(servoQueue, &c, portMAX_DELAY);
}

void ServoController::MovePTO(float angle, float speed)
{
    SetServo(PTO_SERVO_ID, angle, speed);
}

void ServoController::EngagePTO(float speed)
{
    MovePTO(PTO_ENGAGED_ANGLE, speed);
}

void ServoController::DisengagePTO(float speed)
{
    MovePTO(PTO_DISENGAGED_ANGLE, speed);
}

void ServoController::MoveGrasp(float angle, float speed)
{
    for (int i = GRASP_START_ID; i <= GRASP_END_ID; i++) {
        SetServo(i, angle, speed);
    }
    SetServo(RETRACT_SERVO_ID, 0.0f, speed);
}

void ServoController::MoveRetract(float angle, float speed)
{
    SetServo(RETRACT_SERVO_ID, angle, speed);
    for (int i = GRASP_START_ID; i <= GRASP_END_ID; i++) {
        SetServo(i, 0.0f, speed);
    }
}
