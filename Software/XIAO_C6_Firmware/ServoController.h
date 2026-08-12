#pragma once
#include <Arduino.h>
#include <Wire.h>
#include <Adafruit_PWMServoDriver.h>

#define PCA9685_ADDR 0x40

// XIAO ESP32-C6 Default I2C Pins
#define I2C_SDA 4
#define I2C_SCL 5

#define PWM_FREQ 50

// Servo calibration for DSS-M15S (270 degree model)
#define SERVO_MIN_US 500
#define SERVO_MAX_US 2500
#define PHYSICAL_MAX_ANGLE 270.0f // The servo's actual mechanical capability

// Software Limit (Restricting range to prevent spool binding)
#define SERVO_MAX_ANGLE 255.0f

#define SERVO_COUNT 6

// Pin Definitions
#define PTO_SERVO_ID 0
#define GRASP_START_ID 1
#define GRASP_END_ID 4
#define RETRACT_SERVO_ID 5

// PTO Calibration
#define PTO_ENGAGED_ANGLE 40.0f
#define PTO_DISENGAGED_ANGLE 0.0f

// Servo update rate
#define SERVO_UPDATE_MS 10
#define ALLOWED_ACTIVE_TIME 4000
#define SERVO_THRESHOLD 2.0f

enum class CommandType {
    MOVE,
    STOP_ALL,
    SET_PULSE
};

struct ServoCommand
{
    CommandType type;
    uint8_t id;
    float angle;
    float speed;
};

struct ServoState
{
    float current;
    float target;
    float speed;
    bool moving;
    uint16_t activeTime;
};

class ServoController
{
public:
    ServoController();
    ~ServoController();
    static void Init();
    static void ParseCommand(String cmd);
    static void SetServo(uint8_t id, float angle, float speed);
    static void StopAll();

    // High-level Abstractions
    static void MovePTO(float angle, float speed);
    static void MoveGrasp(float angle, float speed);
    static void MoveRetract(float angle, float speed);

    // PTO Specific Actions
    static void EngagePTO(float speed = 100.0f);
    static void DisengagePTO(float speed = 100.0f);

private:
    static ServoState servo[SERVO_COUNT];
    static void ServoTask(void *parameter);

    // cmdline functions
    static void Status();
    static void Help();
    static void WriteServo(uint8_t id, float angle);

    // helper function
    static uint16_t microsecondsToTicks(float us) { return (uint32_t)(us * 4096.0 / 20000.0); }

    // variables
    static Adafruit_PWMServoDriver pwm;
    static TaskHandle_t servoTaskHandle;
    static QueueHandle_t servoQueue;
    static bool randomMode;
};
